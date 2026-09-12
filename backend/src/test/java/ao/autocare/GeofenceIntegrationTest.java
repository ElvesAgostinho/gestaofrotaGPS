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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Geocercas: áreas, deteção de entradas e saídas, e presença atual. */
class GeofenceIntegrationTest extends AbstractIntegrationTest {

    // Centro da obra e um ponto a ~2,2 km a norte (fora de uma cerca de 500 m).
    private static final double OBRA_LAT = -8.8383;
    private static final double OBRA_LON = 13.2344;
    private static final double FORA_LAT = -8.8183;
    private static final double FORA_LON = 13.2344;

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

    private JsonNode publish(double lat, double lon, Instant at) throws Exception {
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", lat);
        position.put("longitude", lon);
        position.put("satellites", 9);
        if (at != null) {
            position.put("recordedAt", at.toString());
        }
        MvcResult r = mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-G", "key", key, "position", position))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-G", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    private Map<String, Object> circulo(String name, Object... extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("kind", "CIRCLE");
        body.put("centerLatitude", OBRA_LAT);
        body.put("centerLongitude", OBRA_LON);
        body.put("radiusM", 500);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            body.put((String) extra[i], extra[i + 1]);
        }
        return body;
    }

    // ---- testes -------------------------------------------------------
    @Test
    void detectsExitAndEntryOfACircularArea() throws Exception {
        setUpFleet("gf1@teste.ao", "RE-001");
        send(post("/api/v1/geofences"), circulo("Obra Luanda Sul"), 201);

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        // Primeira posição: só fixa o estado, não inventa uma entrada que ninguém viu.
        JsonNode primeira = publish(OBRA_LAT, OBRA_LON, t0);
        assertThat(primeira.get("geofenceEvents").asInt()).isZero();

        // Sai da obra -> um evento de saída.
        JsonNode saida = publish(FORA_LAT, FORA_LON, t0.plus(20, ChronoUnit.MINUTES));
        assertThat(saida.get("geofenceEvents").asInt()).isEqualTo(1);

        // Volta -> um evento de entrada.
        JsonNode entrada = publish(OBRA_LAT, OBRA_LON, t0.plus(40, ChronoUnit.MINUTES));
        assertThat(entrada.get("geofenceEvents").asInt()).isEqualTo(1);

        JsonNode events = send(get("/api/v1/geofence-events"), null, 200);
        assertThat(events.get("content")).hasSize(2);
        assertThat(events.get("content").get(0).get("eventType").asText()).isEqualTo("ENTER");
        assertThat(events.get("content").get(1).get("eventType").asText()).isEqualTo("EXIT");
        assertThat(events.get("content").get(0).get("assetTag").asText()).isEqualTo("RE-001");
    }

    @Test
    void doesNotRepeatEventsWhileTheAssetStaysPut() throws Exception {
        setUpFleet("gf2@teste.ao", "RE-002");
        send(post("/api/v1/geofences"), circulo("Parque"), 201);

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);
        // Cinco posições seguidas dentro: nenhum evento novo.
        for (int i = 1; i <= 5; i++) {
            assertThat(publish(OBRA_LAT + 0.0001 * i, OBRA_LON,
                    t0.plus(i * 2L, ChronoUnit.MINUTES)).get("geofenceEvents").asInt()).isZero();
        }
        assertThat(send(get("/api/v1/geofence-events"), null, 200).get("content")).isEmpty();
    }

    @Test
    void respectsAlertPreferencesPerArea() throws Exception {
        setUpFleet("gf3@teste.ao", "RE-003");
        // Só interessa saber quando sai, não quando entra.
        send(post("/api/v1/geofences"),
                circulo("Só saídas", "alertOnEnter", false, "alertOnExit", true), 201);

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);
        assertThat(publish(FORA_LAT, FORA_LON, t0.plus(10, ChronoUnit.MINUTES))
                .get("geofenceEvents").asInt()).isEqualTo(1);
        // A volta não gera nada.
        assertThat(publish(OBRA_LAT, OBRA_LON, t0.plus(20, ChronoUnit.MINUTES))
                .get("geofenceEvents").asInt()).isZero();

        JsonNode events = send(get("/api/v1/geofence-events"), null, 200);
        assertThat(events.get("content")).hasSize(1);
        assertThat(events.get("content").get(0).get("eventType").asText()).isEqualTo("EXIT");
    }

    @Test
    void detectsEntryAndExitOfAPolygon() throws Exception {
        setUpFleet("gf4@teste.ao", "RE-004");
        Map<String, Object> poligono = new HashMap<>();
        poligono.put("name", "Estaleiro");
        poligono.put("kind", "POLYGON");
        poligono.put("polygon", List.of(
                List.of(-8.83, 13.23), List.of(-8.83, 13.24),
                List.of(-8.85, 13.24), List.of(-8.85, 13.23)));
        send(post("/api/v1/geofences"), poligono, 201);

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(-8.84, 13.235, t0);                                   // dentro
        JsonNode saida = publish(-8.87, 13.235, t0.plus(10, ChronoUnit.MINUTES)); // fora, a sul
        assertThat(saida.get("geofenceEvents").asInt()).isEqualTo(1);

        JsonNode area = send(get("/api/v1/geofences"), null, 200).get(0);
        assertThat(area.get("kind").asText()).isEqualTo("POLYGON");
        assertThat(area.get("polygon")).hasSize(4);
    }

    @Test
    void anAreaLimitedToOtherAssetsIgnoresThisOne() throws Exception {
        setUpFleet("gf5@teste.ao", "RE-005");
        // Cria um segundo ativo e limita a área apenas a esse.
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Gerador"), 201)
                .get("id").asText();
        String outro = send(post("/api/v1/assets"),
                Map.of("tag", "GER-001", "name", "Gerador", "assetTypeId", typeId), 201)
                .get("id").asText();
        send(post("/api/v1/geofences"),
                circulo("Só o gerador", "assetIds", List.of(outro)), 201);

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);
        assertThat(publish(FORA_LAT, FORA_LON, t0.plus(10, ChronoUnit.MINUTES))
                .get("geofenceEvents").asInt()).isZero();
        assertThat(send(get("/api/v1/geofence-events"), null, 200).get("content")).isEmpty();

        JsonNode area = send(get("/api/v1/geofences"), null, 200).get(0);
        assertThat(area.get("appliesToWholeFleet").asBoolean()).isFalse();
        assertThat(area.get("assetIds")).hasSize(1);
    }

    @Test
    void anInactiveAreaStopsDetecting() throws Exception {
        setUpFleet("gf6@teste.ao", "RE-006");
        String areaId = send(post("/api/v1/geofences"), circulo("Desligada"), 201)
                .get("id").asText();

        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);
        send(patch("/api/v1/geofences/" + areaId), Map.of("active", false), 200);

        assertThat(publish(FORA_LAT, FORA_LON, t0.plus(10, ChronoUnit.MINUTES))
                .get("geofenceEvents").asInt()).isZero();
    }

    @Test
    void showsWhoIsInsideAnAreaRightNow() throws Exception {
        setUpFleet("gf7@teste.ao", "RE-007");
        String areaId = send(post("/api/v1/geofences"), circulo("Obra"), 201).get("id").asText();

        Instant t0 = Instant.now().minus(30, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);

        JsonNode dentro = send(get("/api/v1/geofences/" + areaId + "/inside"), null, 200);
        assertThat(dentro).hasSize(1);
        assertThat(dentro.get(0).get("assetTag").asText()).isEqualTo("RE-007");

        publish(FORA_LAT, FORA_LON, t0.plus(10, ChronoUnit.MINUTES));
        assertThat(send(get("/api/v1/geofences/" + areaId + "/inside"), null, 200)).isEmpty();
    }

    @Test
    void refusesAreasWithoutUsableGeometry() throws Exception {
        setUpFleet("gf8@teste.ao", "RE-008");

        // Círculo sem centro.
        assertThat(send(post("/api/v1/geofences"),
                Map.of("name", "Sem centro", "kind", "CIRCLE", "radiusM", 300), 400)
                .get("message").asText()).contains("centro");

        // Círculo com raio zero.
        send(post("/api/v1/geofences"), circulo("Raio zero", "radiusM", 0), 400);

        // Polígono com dois pontos.
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Linha");
        body.put("kind", "POLYGON");
        body.put("polygon", List.of(List.of(-8.83, 13.23), List.of(-8.84, 13.24)));
        assertThat(send(post("/api/v1/geofences"), body, 400).get("message").asText())
                .contains("três pontos");
    }

    @Test
    void eventsCanBeAcknowledged() throws Exception {
        setUpFleet("gf9@teste.ao", "RE-009");
        send(post("/api/v1/geofences"), circulo("Obra"), 201);

        Instant t0 = Instant.now().minus(30, ChronoUnit.MINUTES);
        publish(OBRA_LAT, OBRA_LON, t0);
        publish(FORA_LAT, FORA_LON, t0.plus(10, ChronoUnit.MINUTES));

        String eventId = send(get("/api/v1/geofence-events"), null, 200)
                .get("content").get(0).get("id").asText();
        assertThat(send(post("/api/v1/geofence-events/" + eventId + "/acknowledge"), null, 200)
                .get("acknowledged").asBoolean()).isTrue();
    }

    @Test
    void manualPositionsAlsoTriggerAreaDetection() throws Exception {
        setUpFleet("gf10@teste.ao", "RE-010");
        send(post("/api/v1/geofences"), circulo("Obra"), 201);

        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", OBRA_LAT, "longitude", OBRA_LON), 201);
        send(post("/api/v1/assets/" + assetId + "/position"),
                Map.of("latitude", FORA_LAT, "longitude", FORA_LON), 201);

        JsonNode events = send(get("/api/v1/assets/" + assetId + "/geofence-events"), null, 200);
        assertThat(events.get("content")).hasSize(1);
        assertThat(events.get("content").get(0).get("eventType").asText()).isEqualTo("EXIT");
    }
}
