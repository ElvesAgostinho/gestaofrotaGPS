package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Correções da auditoria ao bloqueio de viaturas.
 *
 * <p>Cada teste aqui corresponde a um problema concreto encontrado na auditoria.
 */
class LockAuditFixesIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    private String bearer;
    private String assetId;
    private String deviceId;
    private String key;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

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
                                "deviceId", "IMEI-FIX", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        JsonNode device = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-FIX", "assetId", assetId), 201);
        deviceId = device.get("device").get("id").asText();
        key = device.get("ingestKey").asText();
    }

    private Map<String, Object> pedido(String motivo, String categoria) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", motivo);
        body.put("reasonCategory", categoria);
        body.put("acknowledged", true);
        return body;
    }

    private void parkVehicle() throws Exception {
        Instant t0 = Instant.now().minus(6, ChronoUnit.MINUTES);
        publish(0, t0);
        publish(0, t0.plus(2, ChronoUnit.MINUTES));
        publish(0, t0.plus(4, ChronoUnit.MINUTES));
    }

    // ---- 🔴 dois bloqueios contraditórios não coexistem ------------------
    @Test
    void twoLocksCannotBePendingAtTheSameTime() throws Exception {
        setUpFleet("fx1@teste.ao", "CAM-001");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES)); // em marcha

        send(post("/api/v1/assets/" + assetId + "/lock"), pedido("Furto", "THEFT"), 201);

        JsonNode conflito = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Outra vez", "OTHER"), 409);
        assertThat(conflito.get("message").asText())
                .contains("por resolver")
                .contains("ENGINE_STOP");
    }

    /**
     * O desbloqueio é a exceção, e tem de ser.
     *
     * <p>A correção original travava qualquer comando enquanto houvesse outro
     * por resolver. Isso evitava um bloqueio e um desbloqueio em fila ao mesmo
     * tempo — mas deixava uma viatura com um bloqueio por confirmar SEM FORMA
     * NENHUMA de ser desbloqueada, que é pior do que o problema que resolvia.
     */
    @Test
    void anUnlockSupersedesAPendingLockInsteadOfBeingRefused() throws Exception {
        setUpFleet("fx1b@teste.ao", "CAM-001B");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES));

        String bloqueio = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto", "THEFT"), 201).get("id").asText();

        send(post("/api/v1/assets/" + assetId + "/unlock"), pedido("Enganei-me", "OTHER"), 201);

        JsonNode antigo = send(get("/api/v1/assets/" + assetId + "/commands"), null, 200)
                .get("content").get(1);
        assertThat(antigo.get("id").asText()).isEqualTo(bloqueio);
        assertThat(antigo.get("status").asText()).isEqualTo("SUPERSEDED");
    }

    // ---- 🔴 confirmação: declarada não é o mesmo que provada ---------------
    @Test
    void aManualConfirmationIsLabelledAsADeclarationNotAsProof() throws Exception {
        setUpFleet("fx2@teste.ao", "CAM-002");
        parkVehicle();

        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto participado", "THEFT"), 201).get("id").asText();
        // Sem fornecedor o envio falha, por isso não há nada para confirmar.
        JsonNode aprovado = send(post("/api/v1/commands/" + id + "/approve"), null, 200);
        assertThat(aprovado.get("status").asText()).isEqualTo("FAILED");

        // Só um comando enviado pode ser confirmado.
        send(post("/api/v1/commands/" + id + "/confirm"), null, 409);
    }

    // ---- 🔴 protocolo do aparelho ----------------------------------------
    @Test
    void theLockStatusSaysTheDeviceCommandsWereNeverVerified() throws Exception {
        setUpFleet("fx3@teste.ao", "CAM-003");

        JsonNode estado = send(get("/api/v1/assets/" + assetId + "/lock"), null, 200);
        assertThat(estado.get("deviceExternalId").asText()).isEqualTo("IMEI-FIX");
        // Sem sincronização, o sistema não sabe se este aparelho aceita o comando
        // — e diz isso, em vez de assumir que sim.
        assertThat(estado.hasNonNull("immobiliserSupported")).isFalse();
        assertThat(estado.get("commandsSyncedLabel").asText()).contains("não verificados");
    }

    @Test
    void syncingADeviceWithoutAProviderFailsClearly() throws Exception {
        setUpFleet("fx4@teste.ao", "CAM-004");

        assertThat(send(post("/api/v1/gps-devices/" + deviceId + "/sync"), null, 409)
                .get("message").asText()).contains("Servidor Traccar");
    }

    // ---- 🔴 estado da ligação ao Traccar ----------------------------------
    @Test
    void theProviderStatusEndpointSaysItIsNotConfigured() throws Exception {
        setUpFleet("fx5@teste.ao", "CAM-005");

        JsonNode saude = send(get("/api/v1/telemetry/traccar/status"), null, 200);
        assertThat(saude.get("configured").asBoolean()).isFalse();
        assertThat(saude.get("reachable").asBoolean()).isFalse();
        assertThat(saude.get("failureReason").asText()).contains("Configurações");
    }

    // ---- 🟠 categoria do motivo -------------------------------------------
    @Test
    void theReasonHasACategoryBesidesTheFreeText() throws Exception {
        setUpFleet("fx6@teste.ao", "CAM-006");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES));

        JsonNode categorias = send(get("/api/v1/commands/reason-categories"), null, 200);
        assertThat(categorias.size()).isGreaterThanOrEqualTo(6);

        JsonNode cmd = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Participação 123/2026 na 3.ª esquadra", "THEFT"), 201);
        assertThat(cmd.get("reasonCategory").asText()).isEqualTo("THEFT");
        assertThat(cmd.get("reasonCategoryLabel").asText()).contains("Furto");
        // O estado anterior fica registado, para a auditoria se reconstituir.
        assertThat(cmd.get("previousLockState").asText()).isEqualTo("FREE");
    }

    // ---- 🟠 histórico acessível -------------------------------------------
    @Test
    void theCommandHistoryIsReachableForTheFleetAndForOneAsset() throws Exception {
        setUpFleet("fx7@teste.ao", "CAM-007");
        publish(60, Instant.now().minus(2, ChronoUnit.MINUTES));
        send(post("/api/v1/assets/" + assetId + "/lock"), pedido("Furto", "THEFT"), 201);

        assertThat(send(get("/api/v1/commands"), null, 200).get("content")).hasSize(1);
        assertThat(send(get("/api/v1/assets/" + assetId + "/commands"), null, 200)
                .get("content")).hasSize(1);

        JsonNode registo = send(get("/api/v1/commands"), null, 200).get("content").get(0);
        // Tudo o que uma auditoria precisa de reconstituir.
        assertThat(registo.get("requestedByName").asText()).isNotBlank();
        assertThat(registo.get("reason").asText()).contains("Furto");
        assertThat(registo.get("requestLatitude").asDouble()).isEqualTo(LAT);
        assertThat(registo.get("requestSpeedKph").asDouble()).isEqualTo(60.0);
        assertThat(registo.get("statusLabel").asText()).isNotBlank();
    }
}
