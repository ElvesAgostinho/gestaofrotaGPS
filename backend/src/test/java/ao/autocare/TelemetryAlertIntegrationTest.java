package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.telemetry.CommsWatch;
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

/** Alertas de telemetria: excesso de velocidade e perda de comunicação. */
class TelemetryAlertIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    @Autowired
    private CommsWatch commsWatch;

    private String bearer;
    private String assetId;
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

    private void publish(double lat, double lon, double speed, Instant at) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", lat);
        position.put("longitude", lon);
        position.put("speedKph", speed);
        position.put("satellites", 9);
        position.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-A", "key", key, "position", position))))
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
                Map.of("externalId", "IMEI-A", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    private JsonNode alerts() throws Exception {
        return send(get("/api/v1/telemetry/alerts"), null, 200).get("content");
    }

    // ---- velocidade ---------------------------------------------------
    @Test
    void withoutAnyLimitDefinedThereIsNoSpeedWatch() throws Exception {
        setUpFleet("al1@teste.ao", "CAM-001");

        // 140 km/h, mas ninguém definiu limite nenhum: não se inventa um.
        publish(LAT, LON, 140, Instant.now().minus(10, ChronoUnit.MINUTES));
        assertThat(alerts()).isEmpty();
    }

    @Test
    void raisesOneEpisodePerSpeedingRunAndKeepsThePeak() throws Exception {
        setUpFleet("al2@teste.ao", "CAM-002");
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 80), 200);

        Instant t0 = Instant.now().minus(30, ChronoUnit.MINUTES);
        publish(LAT, LON, 75, t0);                                    // dentro do limite
        assertThat(alerts()).isEmpty();

        publish(LAT + 0.002, LON, 95, t0.plus(2, ChronoUnit.MINUTES));  // excesso
        publish(LAT + 0.004, LON, 112, t0.plus(4, ChronoUnit.MINUTES)); // pior
        publish(LAT + 0.006, LON, 99, t0.plus(6, ChronoUnit.MINUTES));  // ainda em excesso

        JsonNode abertos = alerts();
        assertThat(abertos).hasSize(1);          // um episódio, não três alertas
        JsonNode alerta = abertos.get(0);
        assertThat(alerta.get("kind").asText()).isEqualTo("SPEEDING");
        assertThat(alerta.get("open").asBoolean()).isTrue();
        assertThat(alerta.get("peakValue").asDouble()).isEqualTo(112.0);
        assertThat(alerta.get("limitValue").asDouble()).isEqualTo(80.0);
        assertThat(alerta.get("assetTag").asText()).isEqualTo("CAM-002");

        // Volta ao limite: o episódio fecha.
        publish(LAT + 0.008, LON, 70, t0.plus(8, ChronoUnit.MINUTES));
        assertThat(alerts().get(0).get("open").asBoolean()).isFalse();

        // Novo excesso mais tarde é um episódio novo.
        publish(LAT + 0.010, LON, 100, t0.plus(12, ChronoUnit.MINUTES));
        assertThat(alerts()).hasSize(2);
    }

    @Test
    void toleranceAvoidsAlertsRightOnTheLimit() throws Exception {
        setUpFleet("al3@teste.ao", "CAM-003");
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 80), 200);

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        publish(LAT, LON, 83, t0);   // dentro da margem de 5 km/h
        assertThat(alerts()).isEmpty();

        publish(LAT + 0.002, LON, 86, t0.plus(2, ChronoUnit.MINUTES)); // acima da margem
        assertThat(alerts()).hasSize(1);
    }

    @Test
    void theFleetDefaultAppliesToAssetsWithoutTheirOwnLimit() throws Exception {
        setUpFleet("al4@teste.ao", "CAM-004");
        send(patch("/api/v1/organization"), Map.of("defaultSpeedLimitKph", 60), 200);

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        publish(LAT, LON, 90, t0);
        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).get("limitValue").asDouble()).isEqualTo(60.0);
    }

    @Test
    void theAssetLimitBeatsTheFleetDefault() throws Exception {
        setUpFleet("al5@teste.ao", "CAM-005");
        send(patch("/api/v1/organization"), Map.of("defaultSpeedLimitKph", 60), 200);
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 120), 200);

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        publish(LAT, LON, 90, t0);   // acima de 60, mas abaixo dos 120 deste ativo
        assertThat(alerts()).isEmpty();
    }

    @Test
    void theZoneLimitBeatsEverythingElse() throws Exception {
        setUpFleet("al6@teste.ao", "CAM-006");
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 120), 200);

        Map<String, Object> obra = new HashMap<>();
        obra.put("name", "Obra Luanda Sul");
        obra.put("kind", "CIRCLE");
        obra.put("centerLatitude", LAT);
        obra.put("centerLongitude", LON);
        obra.put("radiusM", 1000);
        obra.put("speedLimitKph", 20);
        send(post("/api/v1/geofences"), obra, 201);

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        // Dentro da obra a 40 km/h: abaixo do limite do camião, acima do da obra.
        publish(LAT, LON, 40, t0);
        JsonNode dentro = alerts();
        assertThat(dentro).hasSize(1);
        assertThat(dentro.get(0).get("limitValue").asDouble()).isEqualTo(20.0);
        assertThat(dentro.get(0).get("geofenceName").asText()).isEqualTo("Obra Luanda Sul");
    }

    // ---- perda de comunicação ------------------------------------------
    @Test
    void raisesAnAlertWhenADeviceGoesSilentAndClosesItWhenItReturns() throws Exception {
        setUpFleet("al7@teste.ao", "CAM-007");

        // Última comunicação há uma hora — acima dos 30 min de silêncio.
        publish(LAT, LON, 30, Instant.now().minus(60, ChronoUnit.MINUTES));

        String orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();
        assertThat(commsWatch.scan(orgId, Instant.now())).isEqualTo(1);

        JsonNode abertos = alerts();
        assertThat(abertos).hasSize(1);
        assertThat(abertos.get(0).get("kind").asText()).isEqualTo("COMMS_LOST");
        assertThat(abertos.get(0).get("open").asBoolean()).isTrue();
        assertThat(abertos.get(0).get("message").asText()).contains("deixou de comunicar");

        // Correr outra vez não duplica o alerta.
        assertThat(commsWatch.scan(orgId, Instant.now())).isZero();
        assertThat(alerts()).hasSize(1);

        // O aparelho volta a falar: o episódio fecha.
        publish(LAT, LON, 25, Instant.now());
        assertThat(alerts().get(0).get("open").asBoolean()).isFalse();
    }

    @Test
    void aDeviceThatNeverReportedIsNotACommunicationLoss() throws Exception {
        setUpFleet("al8@teste.ao", "CAM-008");
        String orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();

        // Instalado mas ainda sem qualquer posição: é instalação por concluir.
        assertThat(commsWatch.scan(orgId, Instant.now())).isZero();
        assertThat(alerts()).isEmpty();
    }

    // ---- consultas -----------------------------------------------------
    @Test
    void filtersByKindAndByOpenAndAcknowledges() throws Exception {
        setUpFleet("al9@teste.ao", "CAM-009");
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 50), 200);

        Instant t0 = Instant.now().minus(20, ChronoUnit.MINUTES);
        publish(LAT, LON, 90, t0);
        publish(LAT + 0.002, LON, 40, t0.plus(2, ChronoUnit.MINUTES)); // fecha o episódio

        assertThat(send(get("/api/v1/telemetry/alerts?kind=SPEEDING"), null, 200)
                .get("content")).hasSize(1);
        assertThat(send(get("/api/v1/telemetry/alerts?kind=COMMS_LOST"), null, 200)
                .get("content")).isEmpty();
        assertThat(send(get("/api/v1/telemetry/alerts?open=true"), null, 200)
                .get("content")).isEmpty();
        send(get("/api/v1/telemetry/alerts?kind=INVENTADO"), null, 400);

        String alertId = alerts().get(0).get("id").asText();
        assertThat(send(post("/api/v1/telemetry/alerts/" + alertId + "/acknowledge"), null, 200)
                .get("acknowledged").asBoolean()).isTrue();

        assertThat(send(get("/api/v1/assets/" + assetId + "/telemetry-alerts"), null, 200)
                .get("content")).hasSize(1);
    }

    @Test
    void rejectsImplausibleSpeedLimits() throws Exception {
        setUpFleet("al10@teste.ao", "CAM-010");
        send(patch("/api/v1/assets/" + assetId), Map.of("speedLimitKph", 900), 400);
        send(patch("/api/v1/organization"), Map.of("defaultSpeedLimitKph", 900), 400);
    }
}
