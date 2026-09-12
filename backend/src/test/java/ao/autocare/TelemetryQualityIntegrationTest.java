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
 * Qualidade dos dados de GPS: descarte de posições más, prioridade do odómetro
 * do aparelho e agregação de posições em viagens.
 */
class TelemetryQualityIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    private String bearer;
    private String key;
    private String assetId;

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

    private JsonNode publish(Map<String, Object> position) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-Q", "key", key, "position", position))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
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

    /** Cria empresa, ativo e aparelho, e guarda a chave de publicação. */
    private void setUpFleet(String email, String tag) throws Exception {
        bearer = register(email).bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião " + tag), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-Q", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    // ---- filtros de qualidade ----------------------------------------
    @Test
    void discardsPositionsWithTooFewSatellites() throws Exception {
        setUpFleet("q1@teste.ao", "Q-1");

        JsonNode weak = publish(point(LAT, LON, "satellites", 2));
        assertThat(weak.get("accepted").asBoolean()).isFalse();
        assertThat(weak.get("reason").asText()).contains("2 satélites");

        // Com fixação boa entra.
        assertThat(publish(point(LAT, LON, "satellites", 9)).get("accepted").asBoolean()).isTrue();
        assertThat(send(get("/api/v1/assets/" + assetId + "/positions"), null, 200)
                .get("content")).hasSize(1);
    }

    @Test
    void discardsPositionsWithPoorDeclaredAccuracy() throws Exception {
        setUpFleet("q2@teste.ao", "Q-2");

        JsonNode vague = publish(point(LAT, LON, "accuracyM", 350));
        assertThat(vague.get("accepted").asBoolean()).isFalse();
        assertThat(vague.get("reason").asText()).contains("350");

        assertThat(publish(point(LAT, LON, "accuracyM", 8)).get("accepted").asBoolean()).isTrue();
    }

    @Test
    void discardsJumpsThatWouldImplyAnImpossibleSpeed() throws Exception {
        setUpFleet("q3@teste.ao", "Q-3");
        Instant t0 = Instant.now().minus(10, ChronoUnit.MINUTES);

        assertThat(publish(point(LAT, LON, "recordedAt", t0.toString()))
                .get("accepted").asBoolean()).isTrue();

        // 1,1 km em 5 segundos = ~800 km/h: impossível para um camião.
        JsonNode jump = publish(point(LAT + 0.01, LON,
                "recordedAt", t0.plusSeconds(5).toString()));
        assertThat(jump.get("accepted").asBoolean()).isFalse();
        assertThat(jump.get("reason").asText()).contains("km/h");

        // O mesmo deslocamento em 5 minutos é perfeitamente plausível.
        assertThat(publish(point(LAT + 0.01, LON,
                "recordedAt", t0.plus(5, ChronoUnit.MINUTES).toString()))
                .get("accepted").asBoolean()).isTrue();
    }

    @Test
    void prefersTheDeviceOdometerOverSummedStraightLines() throws Exception {
        setUpFleet("q4@teste.ao", "Q-4");
        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        // O ativo andou 2,2 km em linha recta entre os extremos, mas o odómetro
        // do aparelho — que conta voltas de roda — diz 3,5 km (curvas incluídas).
        publish(point(LAT, LON, "odometerKm", 10000.0, "speedKph", 30,
                "recordedAt", t0.toString()));
        publish(point(LAT + 0.01, LON, "odometerKm", 10001.8, "speedKph", 40,
                "recordedAt", t0.plus(15, ChronoUnit.MINUTES).toString()));
        publish(point(LAT + 0.02, LON, "odometerKm", 10003.5, "speedKph", 20,
                "recordedAt", t0.plus(30, ChronoUnit.MINUTES).toString()));

        JsonNode track = send(get("/api/v1/assets/" + assetId + "/track"), null, 200);
        assertThat(track.get("points").asInt()).isEqualTo(3);
        assertThat(track.get("distanceKm").asDouble()).isEqualTo(3.5);
    }

    @Test
    void fallsBackToGeometryWhenTheOdometerIsResetOrAbsent() throws Exception {
        setUpFleet("q5@teste.ao", "Q-5");
        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        // Odómetro a recuar = aparelho substituído; não se pode confiar nele.
        publish(point(LAT, LON, "odometerKm", 90000.0, "recordedAt", t0.toString()));
        publish(point(LAT + 0.02, LON, "odometerKm", 12.0,
                "recordedAt", t0.plus(30, ChronoUnit.MINUTES).toString()));

        JsonNode track = send(get("/api/v1/assets/" + assetId + "/track"), null, 200);
        // Volta ao cálculo geométrico: ~2,2 km entre os dois pontos.
        assertThat(track.get("distanceKm").asDouble()).isBetween(2.0, 2.5);
    }

    // ---- viagens ------------------------------------------------------
    @Test
    void groupsPositionsIntoATripBetweenIgnitionOnAndOff() throws Exception {
        setUpFleet("q6@teste.ao", "Q-6");
        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        publish(point(LAT, LON, "ignition", true, "speedKph", 20,
                "recordedAt", t0.toString()));
        publish(point(LAT + 0.01, LON, "ignition", true, "speedKph", 60,
                "recordedAt", t0.plus(10, ChronoUnit.MINUTES).toString()));
        publish(point(LAT + 0.02, LON, "ignition", true, "speedKph", 45,
                "recordedAt", t0.plus(20, ChronoUnit.MINUTES).toString()));
        // Ignição desligada fecha a viagem.
        publish(point(LAT + 0.02, LON, "ignition", false, "speedKph", 0,
                "recordedAt", t0.plus(22, ChronoUnit.MINUTES).toString()));

        JsonNode trips = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200);
        assertThat(trips.get("content")).hasSize(1);
        JsonNode trip = trips.get("content").get(0);
        assertThat(trip.get("open").asBoolean()).isFalse();
        assertThat(trip.get("positionCount").asInt()).isEqualTo(3);
        assertThat(trip.get("maxSpeedKph").asDouble()).isEqualTo(60.0);
        assertThat(trip.get("distanceKm").asDouble()).isBetween(2.0, 2.5);
        assertThat(trip.get("durationMinutes").asInt()).isEqualTo(20);
    }

    @Test
    void aShortStopDoesNotSplitTheTrip() throws Exception {
        setUpFleet("q7@teste.ao", "Q-7");
        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        publish(point(LAT, LON, "ignition", true, "speedKph", 30,
                "recordedAt", t0.toString()));
        // Paragem de 2 minutos (semáforo) — abaixo dos 5 minutos que fecham.
        publish(point(LAT + 0.01, LON, "ignition", true, "speedKph", 25,
                "recordedAt", t0.plus(2, ChronoUnit.MINUTES).toString()));
        publish(point(LAT + 0.02, LON, "ignition", true, "speedKph", 35,
                "recordedAt", t0.plus(4, ChronoUnit.MINUTES).toString()));

        JsonNode trips = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200);
        assertThat(trips.get("content")).hasSize(1);
        assertThat(trips.get("content").get(0).get("open").asBoolean()).isTrue();
        assertThat(trips.get("content").get(0).get("positionCount").asInt()).isEqualTo(3);
    }

    @Test
    void aLongStopClosesTheTripAndTheNextMovementStartsANewOne() throws Exception {
        setUpFleet("q8@teste.ao", "Q-8");
        Instant t0 = Instant.now().minus(3, ChronoUnit.HOURS);

        publish(point(LAT, LON, "ignition", true, "speedKph", 30,
                "recordedAt", t0.toString()));
        publish(point(LAT + 0.02, LON, "ignition", true, "speedKph", 40,
                "recordedAt", t0.plus(20, ChronoUnit.MINUTES).toString()));
        // Uma hora depois volta a andar: é outra viagem.
        publish(point(LAT + 0.04, LON, "ignition", true, "speedKph", 30,
                "recordedAt", t0.plus(80, ChronoUnit.MINUTES).toString()));
        publish(point(LAT + 0.06, LON, "ignition", true, "speedKph", 35,
                "recordedAt", t0.plus(95, ChronoUnit.MINUTES).toString()));

        JsonNode trips = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200);
        assertThat(trips.get("content")).hasSize(2);
        // A mais recente primeiro, ainda aberta; a anterior já fechada.
        assertThat(trips.get("content").get(0).get("open").asBoolean()).isTrue();
        assertThat(trips.get("content").get(1).get("open").asBoolean()).isFalse();
    }

    @Test
    void parkingDriftIsNotRecordedAsATrip() throws Exception {
        setUpFleet("q9@teste.ao", "Q-9");
        Instant t0 = Instant.now().minus(60, ChronoUnit.MINUTES);

        // Ativo estacionado com o motor a trabalhar: o GPS oscila poucos metros.
        publish(point(LAT, LON, "ignition", true, "speedKph", 0,
                "recordedAt", t0.toString()));
        publish(point(LAT + 0.00002, LON, "ignition", true, "speedKph", 0,
                "recordedAt", t0.plus(1, ChronoUnit.MINUTES).toString()));
        publish(point(LAT, LON, "ignition", false, "speedKph", 0,
                "recordedAt", t0.plus(2, ChronoUnit.MINUTES).toString()));

        // Menos de 100 m não é viagem — foi descartada ao fechar.
        assertThat(send(get("/api/v1/trips"), null, 200).get("content")).isEmpty();
    }
}
