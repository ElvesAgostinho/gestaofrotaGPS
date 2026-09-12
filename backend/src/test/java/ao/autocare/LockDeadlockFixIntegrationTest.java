package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.domain.DeviceCommand;
import ao.autocare.modules.command.CommandProvider;
import ao.autocare.modules.command.CommandService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Correções da Fase 2: o impasse que deixava uma viatura sem forma de ser
 * desbloqueada, e a espera por confirmação que nunca acabava.
 *
 * <p>Estes testes precisam de um fornecedor que <b>aceite</b> comandos — só
 * assim se chega ao estado "enviado", que é onde o problema vivia. O fornecedor
 * de demonstração recusa tudo de propósito.
 */
@Import(LockDeadlockFixIntegrationTest.StubProviderConfig.class)
class LockDeadlockFixIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    /** Fornecedor de teste: aceita o envio e nunca dá prova de execução. */
    static class StubProvider implements CommandProvider {
        List<String> supported = List.of("engineStop", "engineResume", "positionSingle");
        boolean queued = false;

        @Override
        public Dispatch dispatch(DeviceCommand command) {
            return queued ? Dispatch.queuedForOfflineDevice("777") : Dispatch.delivered("777");
        }

        @Override
        public Optional<DeviceInfo> describeDevice(String externalId) {
            return Optional.of(new DeviceInfo("42", "gt06", "online", true, supported));
        }

        @Override
        public Evidence confirmationFor(DeviceCommand command) {
            return Evidence.none(); // o protocolo silencioso: nunca confirma nada
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(true, true, name(), "6.5", null);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String name() {
            return "Fornecedor de teste";
        }
    }

    @TestConfiguration
    static class StubProviderConfig {
        @Bean
        @Primary
        StubProvider stubProvider() {
            return new StubProvider();
        }
    }

    @Autowired
    private StubProvider provider;

    @Autowired
    private CommandService commandService;

    @Autowired
    private JdbcTemplate jdbc;

    private String bearer;
    private String assetId;
    private String deviceId;
    private String key;

    @BeforeEach
    void resetProvider() {
        provider.supported = List.of("engineStop", "engineResume", "positionSingle");
        provider.queued = false;
    }

    // ---- utilitários ------------------------------------------------------
    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
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
                                "deviceId", "IMEI-DEAD", "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camiao " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        JsonNode device = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-DEAD", "assetId", assetId), 201);
        deviceId = device.get("device").get("id").asText();
        key = device.get("ingestKey").asText();
    }

    private void parkVehicle() throws Exception {
        Instant t0 = Instant.now().minus(6, ChronoUnit.MINUTES);
        publish(0, t0);
        publish(0, t0.plus(2, ChronoUnit.MINUTES));
        publish(0, t0.plus(4, ChronoUnit.MINUTES));
    }

    private Map<String, Object> pedido(String motivo, String categoria) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", motivo);
        body.put("reasonCategory", categoria);
        body.put("acknowledged", true);
        return body;
    }

    /** Bloqueio pedido, aprovado e entregue ao fornecedor. */
    private String sendALock() throws Exception {
        parkVehicle();
        String id = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto participado", "THEFT"), 201).get("id").asText();
        JsonNode aprovado = send(post("/api/v1/commands/" + id + "/approve"), null, 200);
        assertThat(aprovado.get("status").asText()).isEqualTo("SENT");
        return id;
    }

    // ---- o impasse --------------------------------------------------------
    @Test
    void anUnlockIsNeverBlockedByALockWaitingForConfirmation() throws Exception {
        setUpFleet("dl1@teste.ao", "CAM-101");
        String bloqueio = sendALock();

        // Antes: 409. A viatura ficava sem forma nenhuma de ser desbloqueada,
        // porque um comando ENVIADO também não pode ser anulado nem caduca.
        JsonNode desbloqueio = send(post("/api/v1/assets/" + assetId + "/unlock"),
                pedido("Recuperada pela policia", "RECOVERY"), 201);
        assertThat(desbloqueio.get("kind").asText()).isEqualTo("ENGINE_RESUME");
        assertThat(desbloqueio.get("status").asText()).isEqualTo("SENT");

        // O bloqueio anterior não é ignorado: fica substituído, com explicação.
        JsonNode antigo = send(get("/api/v1/commands"), null, 200).get("content").get(1);
        assertThat(antigo.get("id").asText()).isEqualTo(bloqueio);
        assertThat(antigo.get("status").asText()).isEqualTo("SUPERSEDED");
        assertThat(antigo.get("statusLabel").asText()).contains("Substitu");
        // A honestidade que interessa: o que já saiu não pode ser recolhido.
        assertThat(antigo.get("failureReason").asText()).contains("recolher");
    }

    @Test
    void aSecondLockIsStillRefusedWhileOneIsOpen() throws Exception {
        setUpFleet("dl2@teste.ao", "CAM-102");
        sendALock();

        JsonNode conflito = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Outra vez", "OTHER"), 409);
        assertThat(conflito.get("message").asText())
                .contains("por resolver")
                .contains("desbloqueio");
    }

    // ---- a espera que nunca acabava ---------------------------------------
    @Test
    void aCommandWithNoProofStopsBeingPolledAndSaysSo() throws Exception {
        setUpFleet("dl3@teste.ao", "CAM-103");
        String id = sendALock();

        // Envelhecer o envio para além do prazo de confirmação.
        jdbc.update("UPDATE device_commands SET sent_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(90, ChronoUnit.MINUTES)), id);

        commandService.pollConfirmations();

        JsonNode c = send(get("/api/v1/commands"), null, 200).get("content").get(0);
        assertThat(c.get("status").asText()).isEqualTo("UNCONFIRMED");
        assertThat(c.get("statusLabel").asText()).contains("nunca confirmou");
        // Não se sabe o estado da viatura — e o sistema não inventa um.
        assertThat(c.hasNonNull("resultingLockState")).isFalse();
        assertThat(c.hasNonNull("confirmationSource")).isFalse();

        // E o ativo deixa de estar preso: aceita comandos novos.
        assertThat(send(get("/api/v1/assets/" + assetId + "/lock"), null, 200)
                .get("locked").asBoolean()).isFalse();
        send(post("/api/v1/assets/" + assetId + "/lock"), pedido("Nova tentativa", "THEFT"), 201);
    }

    // ---- recusar cedo o que se sabe que vai falhar ------------------------
    @Test
    void aLockIsRefusedAtRequestTimeWhenTheDeviceCannotImmobilise() throws Exception {
        setUpFleet("dl4@teste.ao", "CAM-104");
        provider.supported = List.of("positionSingle"); // protocolo sem imobilização
        parkVehicle();

        JsonNode sync = send(post("/api/v1/gps-devices/" + deviceId + "/sync"), null, 200);
        assertThat(sync.get("immobiliserSupported").asBoolean()).isFalse();

        // Antes o pedido era aceite e só falhava na entrega — minutos perdidos
        // exatamente na situação em que os minutos contam.
        JsonNode recusa = send(post("/api/v1/assets/" + assetId + "/lock"),
                pedido("Furto", "THEFT"), 409);
        assertThat(recusa.get("message").asText())
                .contains("imobiliza")
                .contains("incronize");

        // O desbloqueio continua a passar: nunca se trava a via de segurança.
        send(post("/api/v1/assets/" + assetId + "/unlock"), pedido("Libertar", "OTHER"), 201);
    }

    // ---- a chave que liga os dois sistemas --------------------------------
    @Test
    void theProviderCommandIdIsVisible() throws Exception {
        setUpFleet("dl5@teste.ao", "CAM-105");
        sendALock();

        JsonNode c = send(get("/api/v1/commands"), null, 200).get("content").get(0);
        assertThat(c.get("providerCommandId").asText()).isEqualTo("777");
    }
}
