package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** GPS: aparelhos, entrada de posições e mapa ao vivo. */
class TelemetryIntegrationTest extends AbstractIntegrationTest {

    // Parque de máquinas em Luanda, usado como ponto de partida.
    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    private String bearer;

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

    /** Publicação feita pelo aparelho — sem sessão de utilizador. */
    private JsonNode publish(String deviceId, String key, Map<String, Object> position, int expect)
            throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", deviceId, "key", key, "position", position))))
                .andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private static Map<String, Object> point(double lat, double lon, Object... extra) {
        Map<String, Object> p = new HashMap<>();
        p.put("latitude", lat);
        p.put("longitude", lon);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            p.put((String) extra[i], extra[i + 1]);
        }
        return p;
    }

    private String newAsset(String tag) throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina " + tag), 201)
                .get("id").asText();
        return send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
    }

    // ---- testes ------------------------------------------------------
    @Test
    void registersADeviceAndReceivesItsPositions() throws Exception {
        bearer = register("gps1@teste.ao").bearer();
        String assetId = newAsset("RE-001");

        JsonNode created = send(post("/api/v1/gps-devices"), Map.of(
                "externalId", "863071019234567", "name", "Rastreador da retro",
                "model", "Teltonika FMB920", "assetId", assetId), 201);
        String key = created.get("ingestKey").asText();
        assertThat(key).isNotBlank();
        assertThat(created.get("device").get("status").asText()).isEqualTo("NEVER_SEEN");
        assertThat(created.get("device").get("assetTag").asText()).isEqualTo("RE-001");

        JsonNode result = publish("863071019234567", key,
                point(LAT, LON, "speedKph", 42.5, "heading", 90, "ignition", true), 200);
        assertThat(result.get("accepted").asBoolean()).isTrue();
        assertThat(result.get("assetId").asText()).isEqualTo(assetId);

        // A posição atual do ativo passou a ser esta.
        JsonNode asset = send(get("/api/v1/assets/" + assetId), null, 200);
        assertThat(asset.get("latitude").asDouble()).isEqualTo(LAT);
        assertThat(asset.get("longitude").asDouble()).isEqualTo(LON);
        assertThat(asset.get("positionSource").asText()).isEqualTo("TELEMETRY");

        // E o aparelho está agora online e a mexer.
        JsonNode device = send(get("/api/v1/gps-devices/" + created.get("device").get("id").asText()),
                null, 200);
        assertThat(device.get("status").asText()).isEqualTo("ONLINE");
        assertThat(device.get("lastSeenAt").asText()).isNotBlank();
    }

    @Test
    void refusesPositionsWithoutAValidKey() throws Exception {
        bearer = register("gps2@teste.ao").bearer();
        String assetId = newAsset("RE-002");
        send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-2", "assetId", assetId), 201);

        publish("IMEI-2", "chave-errada", point(LAT, LON), 401);
        publish("IMEI-inexistente", "seja-o-que-for", point(LAT, LON), 401);
    }

    @Test
    void ignoresImpossibleCoordinatesWithoutFailingTheDevice() throws Exception {
        bearer = register("gps3@teste.ao").bearer();
        String assetId = newAsset("RE-003");
        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-3", "assetId", assetId), 201)
                .get("ingestKey").asText();

        // (0,0) é o que o aparelho envia sem sinal — aceite com 200, mas não guardado.
        JsonNode zero = publish("IMEI-3", key, point(0, 0), 200);
        assertThat(zero.get("accepted").asBoolean()).isFalse();
        assertThat(zero.get("reason").asText()).contains("sem sinal");

        // Data no futuro também não entra.
        JsonNode future = publish("IMEI-3", key, point(LAT, LON,
                "recordedAt", Instant.now().plus(2, ChronoUnit.HOURS).toString()), 200);
        assertThat(future.get("accepted").asBoolean()).isFalse();

        // O ativo continua sem posição (o JSON omite os campos nulos).
        assertThat(send(get("/api/v1/assets/" + assetId), null, 200).hasNonNull("latitude"))
                .isFalse();
    }

    @Test
    void keepsTheNewestPositionWhenTheDeviceSendsOldPointsLate() throws Exception {
        bearer = register("gps4@teste.ao").bearer();
        String assetId = newAsset("RE-004");
        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-4", "assetId", assetId), 201)
                .get("ingestKey").asText();

        Instant agora = Instant.now();
        publish("IMEI-4", key, point(LAT, LON, "recordedAt", agora.toString()), 200);
        // Ponto antigo, guardado enquanto esteve sem rede: entra no histórico...
        publish("IMEI-4", key, point(-8.90, 13.30,
                "recordedAt", agora.minus(3, ChronoUnit.HOURS).toString()), 200);

        // ...mas a posição atual não recua.
        JsonNode asset = send(get("/api/v1/assets/" + assetId), null, 200);
        assertThat(asset.get("latitude").asDouble()).isEqualTo(LAT);

        // O histórico tem os dois pontos, do mais recente para o mais antigo.
        JsonNode history = send(get("/api/v1/assets/" + assetId + "/positions"), null, 200);
        assertThat(history.get("content")).hasSize(2);
        assertThat(history.get("content").get(0).get("latitude").asDouble()).isEqualTo(LAT);
    }

    @Test
    void buildsTheTrackWithDistanceAndTopSpeed() throws Exception {
        bearer = register("gps5@teste.ao").bearer();
        String assetId = newAsset("CAM-001");
        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-5", "assetId", assetId), 201)
                .get("ingestKey").asText();

        Instant t0 = Instant.now().minus(30, ChronoUnit.MINUTES);
        // Três pontos a subir a latitude: ~1,1 km por cada 0,01°.
        publish("IMEI-5", key, point(LAT, LON, "speedKph", 20,
                "recordedAt", t0.toString()), 200);
        publish("IMEI-5", key, point(LAT + 0.01, LON, "speedKph", 55,
                "recordedAt", t0.plus(10, ChronoUnit.MINUTES).toString()), 200);
        publish("IMEI-5", key, point(LAT + 0.02, LON, "speedKph", 30,
                "recordedAt", t0.plus(20, ChronoUnit.MINUTES).toString()), 200);

        JsonNode track = send(get("/api/v1/assets/" + assetId + "/track"), null, 200);
        assertThat(track.get("points").asInt()).isEqualTo(3);
        assertThat(track.get("distanceKm").asDouble()).isBetween(2.0, 2.5);
        assertThat(track.get("maxSpeedKph").asDouble()).isEqualTo(55.0);
        assertThat(track.get("track").get(0).get("at").asText()).isNotBlank();
    }

    @Test
    void showsTheFleetOnTheLiveMap() throws Exception {
        bearer = register("gps6@teste.ao").bearer();
        String comGps = newAsset("ESC-001");
        String semPosicao = newAsset("GER-001");

        String key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-6", "assetId", comGps), 201)
                .get("ingestKey").asText();
        publish("IMEI-6", key, point(LAT, LON, "speedKph", 12, "ignition", true), 200);

        JsonNode live = send(get("/api/v1/telemetry/live"), null, 200);
        // Só entra no mapa quem tem posição conhecida.
        assertThat(live).hasSize(1);
        JsonNode row = live.get(0);
        assertThat(row.get("assetId").asText()).isEqualTo(comGps);
        assertThat(row.get("tag").asText()).isEqualTo("ESC-001");
        assertThat(row.get("moving").asBoolean()).isTrue();
        assertThat(row.get("deviceStatus").asText()).isEqualTo("ONLINE");
        assertThat(row.get("secondsSincePosition").asLong()).isLessThan(60);
        assertThat(semPosicao).isNotBlank();
    }

    @Test
    void marksAPositionByHandForAssetsWithoutADevice() throws Exception {
        bearer = register("gps7@teste.ao").bearer();
        String assetId = newAsset("GER-002");

        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", LAT, "longitude", LON, "note", "Descarregado na obra"), 201);

        JsonNode asset = send(get("/api/v1/assets/" + assetId), null, 200);
        assertThat(asset.get("positionSource").asText()).isEqualTo("MANUAL");
        assertThat(send(get("/api/v1/telemetry/live"), null, 200)).hasSize(1);

        // Coordenadas fora do mundo são recusadas com uma mensagem clara.
        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", 200, "longitude", 13.2), 400);
    }

    @Test
    void refusesTwoDevicesOnTheSameAssetAndDuplicateIdentifiers() throws Exception {
        bearer = register("gps8@teste.ao").bearer();
        String assetId = newAsset("RE-008");
        send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-8", "assetId", assetId), 201);

        assertThat(send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-8"), 409).get("message").asText())
                .contains("Já existe um aparelho");
        assertThat(send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-8-B", "assetId", assetId), 409)
                .get("message").asText()).contains("já tem o aparelho");
    }

    @Test
    void rotatingTheKeyInvalidatesThePreviousOne() throws Exception {
        bearer = register("gps9@teste.ao").bearer();
        String assetId = newAsset("RE-009");
        JsonNode created = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-9", "assetId", assetId), 201);
        String oldKey = created.get("ingestKey").asText();
        String deviceId = created.get("device").get("id").asText();

        publish("IMEI-9", oldKey, point(LAT, LON), 200);

        String newKey = send(post("/api/v1/gps-devices/" + deviceId + "/rotate-key"), null, 200)
                .get("ingestKey").asText();
        assertThat(newKey).isNotEqualTo(oldKey);

        publish("IMEI-9", oldKey, point(LAT, LON), 401);
        publish("IMEI-9", newKey, point(LAT, LON), 200);
    }

    @Test
    void onlyManagersManageDevicesAndTelemetryNeedsASession() throws Exception {
        mvc.perform(get("/api/v1/telemetry/live")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/gps-devices")).andExpect(status().isUnauthorized());
    }
}
