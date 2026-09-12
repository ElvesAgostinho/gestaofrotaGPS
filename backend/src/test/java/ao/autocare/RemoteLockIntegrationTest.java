package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.command.CommandService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Bloqueio remoto do motor.
 *
 * <p>A maior parte destes testes verifica que o sistema <b>não</b> executa o
 * comando. É esse o comportamento importante: um corte no momento errado mata
 * pessoas.
 */
class RemoteLockIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    @Autowired
    private CommandService commandService;

    private String bearer;
    private String assetId;
    private String key;

    private JsonNode send(String token, MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (token != null) {
            req = req.header("Authorization", token);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        return send(bearer, req, body, expect);
    }

    /** Publica uma posição com velocidade e data explícitas. */
    private void publish(double speed, Instant at) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", LAT);
        position.put("longitude", LON);
        position.put("speedKph", speed);
        position.put("satellites", 9);
        position.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-LOCK", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-LOCK", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    /** Deixa a viatura parada com as leituras seguidas necessárias. */
    private void parkVehicle() throws Exception {
        Instant t0 = Instant.now().minus(6, ChronoUnit.MINUTES);
        publish(0, t0);
        publish(0, t0.plus(2, ChronoUnit.MINUTES));
        publish(0, t0.plus(4, ChronoUnit.MINUTES));
    }

    private Map<String, Object> pedido(String motivo) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", motivo);
        body.put("reasonCategory", "THEFT");
        body.put("acknowledged", true);
        return body;
    }

    // ---- o comando não é enviado sem condições --------------------------
    @Test
    void aLockRequestIsNotExecutedImmediately() throws Exception {
        setUpFleet("lk1@teste.ao", "CAM-001");
        parkVehicle();

        JsonNode cmd = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Viatura furtada, participação n.º 123/2026"), 201);

        // Pedir não é executar: fica à espera de aprovação.
        assertThat(cmd.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(cmd.get("statusLabel").asText()).contains("aprovação");
        assertThat(cmd.get("reason").asText()).contains("123/2026");
        assertThat(cmd.hasNonNull("sentAt")).isFalse();

        JsonNode estado = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200);
        assertThat(estado.get("locked").asBoolean()).isFalse();
    }

    @Test
    void refusesWithoutAReasonOrWithoutAcknowledgement() throws Exception {
        setUpFleet("lk2@teste.ao", "CAM-002");

        Map<String, Object> semMotivo = new HashMap<>();
        semMotivo.put("acknowledged", true);
        send(post("/api/v1/assets/" + assetId + "/lock"), semMotivo, 400);

        Map<String, Object> semConfirmacao = new HashMap<>();
        semConfirmacao.put("reason", "Só para experimentar");
        assertThat(send(post("/api/v1/assets/" + assetId + "/lock"), semConfirmacao, 400)
                .get("message").asText()).contains("Confirme");
    }

    @Test
    void aMovingVehicleIsNeverCutEvenAfterApproval() throws Exception {
        setUpFleet("lk3@teste.ao", "CAM-003");

        // Em marcha, a 60 km/h.
        Instant t0 = Instant.now().minus(4, ChronoUnit.MINUTES);
        publish(60, t0);
        publish(58, t0.plus(2, ChronoUnit.MINUTES));

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto"), 201).get("id").asText();
        JsonNode aprovado = send(post("/api/v1/commands/" + id + "/approve"), null, 200);

        // Aprovado, mas em fila — não foi enviado com a viatura a andar.
        assertThat(aprovado.get("status").asText()).isEqualTo("QUEUED");
        assertThat(aprovado.get("statusLabel").asText()).contains("viatura parar");
        assertThat(aprovado.hasNonNull("sentAt")).isFalse();

        JsonNode seguranca = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("safety");
        assertThat(seguranca.get("safe").asBoolean()).isFalse();
        assertThat(seguranca.get("reason").asText()).contains("movimento");
    }

    @Test
    void aSingleZeroReadingIsNotEnough() throws Exception {
        setUpFleet("lk4@teste.ao", "CAM-004");

        // Andava, e uma leitura deu 0 — é o que acontece com um GPS a oscilar
        // ou num semáforo. Não chega para cortar.
        Instant t0 = Instant.now().minus(4, ChronoUnit.MINUTES);
        publish(50, t0);
        publish(0, t0.plus(2, ChronoUnit.MINUTES));

        JsonNode seguranca = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("safety");
        assertThat(seguranca.get("safe").asBoolean()).isFalse();
        assertThat(seguranca.get("stoppedReadings").asInt()).isEqualTo(1);
    }

    @Test
    void aVehicleWithoutRecentPositionIsNotCut() throws Exception {
        setUpFleet("lk5@teste.ao", "CAM-005");

        // Parada, mas a última notícia é de há uma hora.
        Instant velho = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(0, velho);
        publish(0, velho.plus(1, ChronoUnit.MINUTES));
        publish(0, velho.plus(2, ChronoUnit.MINUTES));

        JsonNode seguranca = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("safety");
        assertThat(seguranca.get("safe").asBoolean()).isFalse();
        assertThat(seguranca.get("reason").asText()).contains("Sem notícias recentes");
    }

    @Test
    void anAssetThatNeverReportedIsNotCut() throws Exception {
        setUpFleet("lk6@teste.ao", "CAM-006");

        JsonNode seguranca = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("safety");
        assertThat(seguranca.get("safe").asBoolean()).isFalse();
        assertThat(seguranca.get("reason").asText()).contains("nunca comunicou");
    }

    @Test
    void anAssetWithoutADeviceCannotBeLocked() throws Exception {
        bearer = register("lk7@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina"), 201)
                .get("id").asText();
        String semGps = send(post("/api/v1/assets"),
                Map.of("tag", "RE-007", "name", "Retro", "assetTypeId", typeId), 201)
                .get("id").asText();

        assertThat(send(post("/api/v1/assets/" + semGps + "/lock"), pedido("Furto"), 409)
                .get("message").asText()).contains("não tem aparelho");
    }

    // ---- sem fornecedor não se finge que bloqueou ------------------------
    @Test
    void withoutAProviderTheSystemSaysItCannotLock() throws Exception {
        setUpFleet("lk8@teste.ao", "CAM-008");
        parkVehicle();

        JsonNode estado = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200);
        assertThat(estado.get("providerConfigured").asBoolean()).isFalse();
        assertThat(estado.get("providerName").asText()).contains("demonstração");

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto"), 201).get("id").asText();
        JsonNode depois = send(post("/api/v1/commands/" + id + "/approve"), null, 200);

        // A viatura está parada, logo a trava de segurança deixa passar; mas não
        // há fornecedor, e o sistema falha em vez de dizer que enviou.
        assertThat(depois.get("status").asText()).isEqualTo("FAILED");
        assertThat(depois.get("failureReason").asText()).contains("Traccar");
        assertThat(send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("locked").asBoolean()).isFalse();
    }

    // ---- autorização ----------------------------------------------------
    @Test
    void onlyOwnersCanLock() throws Exception {
        setUpFleet("lk9@teste.ao", "CAM-009");

        Map<String, Object> convite = new HashMap<>();
        convite.put("email", "gestor@teste.ao");
        convite.put("role", "MANAGER");
        String token = send(post("/api/v1/team/invitations"), convite, 201).get("token").asText();
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", "Gestor", "password", "palavraForte1"), 200);
        String gestor = "Bearer " + res.get("accessToken").asText();

        // Um gestor de manutenção vê o estado...
        send(gestor, get("/api/v1/assets/" + assetId + "/lock"), null, 200);
        // ...mas não bloqueia.
        send(gestor, post("/api/v1/assets/" + assetId + "/lock"), pedido("Furto"), 403);
    }

    @Test
    void withTwoOwnersTheApprovalHasToComeFromSomeoneElse() throws Exception {
        setUpFleet("lk10@teste.ao", "CAM-010");
        parkVehicle();

        Map<String, Object> convite = new HashMap<>();
        convite.put("email", "dono2@teste.ao");
        convite.put("role", "OWNER");
        String token = send(post("/api/v1/team/invitations"), convite, 201).get("token").asText();
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", "Segundo Dono", "password", "palavraForte1"), 200);
        String outroDono = "Bearer " + res.get("accessToken").asText();

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto participado"), 201).get("id").asText();

        // Quem pediu não pode aprovar o seu próprio pedido.
        assertThat(send(post("/api/v1/commands/" + id + "/approve"), null, 403)
                .get("message").asText()).contains("outra pessoa");

        // O segundo dono aprova.
        JsonNode aprovado = send(outroDono, post("/api/v1/commands/" + id + "/approve"), null, 200);
        assertThat(aprovado.get("approvedByName").asText()).isEqualTo("Segundo Dono");
    }

    // ---- ciclo de vida e auditoria ---------------------------------------
    @Test
    void unlockingIsImmediateAndNeedsNoApproval() throws Exception {
        setUpFleet("lk11@teste.ao", "CAM-011");

        // Nem sequer precisa de a viatura estar parada: não conseguir
        // desbloquear é, por si só, um perigo.
        JsonNode cmd = send(post("/api/v1/assets/" + assetId + "/unlock"),
                pedido("Situação resolvida"), 201);

        // Sem fornecedor falha, mas passou direto pelas travas — não ficou
        // à espera de aprovação nem de a viatura parar.
        assertThat(cmd.get("status").asText()).isEqualTo("FAILED");
        assertThat(cmd.get("kind").asText()).isEqualTo("ENGINE_RESUME");
    }

    @Test
    void aRequestCanBeCancelledAndDoesNotDuplicate() throws Exception {
        setUpFleet("lk12@teste.ao", "CAM-012");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES)); // em marcha

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Engano"), 201).get("id").asText();

        // Não se acumulam pedidos para o mesmo ativo.
        assertThat(send(post("/api/v1/assets/" + assetId + "/lock"), pedido("Outro"), 409)
                .get("message").asText()).contains("por resolver");

        JsonNode anulado = send(post("/api/v1/commands/" + id + "/cancel"), null, 200);
        assertThat(anulado.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(anulado.get("cancelledByName").asText()).isNotBlank();

        // Anulado, já se pode pedir de novo.
        send(post("/api/v1/assets/" + assetId + "/lock"), pedido("Agora a sério"), 201);
    }

    @Test
    void theRequestRecordsWhereTheVehicleWasAndWhoAskedWhy() throws Exception {
        setUpFleet("lk13@teste.ao", "CAM-013");
        publish(45, Instant.now().minus(2, ChronoUnit.MINUTES));

        JsonNode cmd = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto — participação 456/2026"), 201);

        assertThat(cmd.get("requestedByName").asText()).isEqualTo("Utilizador Teste");
        assertThat(cmd.get("requestedAt").asText()).isNotBlank();
        assertThat(cmd.get("reason").asText()).contains("456/2026");
        // Posição e velocidade no momento do pedido ficam gravadas.
        assertThat(cmd.get("requestLatitude").asDouble()).isEqualTo(LAT);
        assertThat(cmd.get("requestSpeedKph").asDouble()).isEqualTo(45.0);
        // E há prazo de validade: não fica pendente para sempre.
        assertThat(cmd.get("expiresAt").asText()).isNotBlank();

        assertThat(send(get("/api/v1/assets/" + assetId + "/commands"), null, 200)
                .get("content")).hasSize(1);
    }

    @Test
    void anExpiredRequestIsNeverExecuted() throws Exception {
        setUpFleet("lk14@teste.ao", "CAM-014");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES));

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto"), 201).get("id").asText();

        // Empurra o prazo para o passado, como se tivessem passado horas.
        expireCommand(id);
        commandService.processQueue();

        JsonNode depois = send(get("/api/v1/assets/" + assetId + "/commands"), null, 200)
                .get("content").get(0);
        assertThat(depois.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(depois.get("statusLabel").asText()).contains("Caducou");
        assertThat(depois.hasNonNull("sentAt")).isFalse();
    }

    @Autowired
    private ao.autocare.repo.DeviceCommandRepository commandRepository;

    @org.springframework.transaction.annotation.Transactional
    void expireCommand(String id) {
        ao.autocare.domain.DeviceCommand c = commandRepository.findById(id).orElseThrow();
        c.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        commandRepository.save(c);
    }
}
