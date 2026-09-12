package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Viagens com sítios nomeados, infrações e pontuação, de ponta a ponta.
 *
 * <p>A viagem entra por onde entra na vida real — posições publicadas pelo
 * aparelho — e o que se verifica é o que fica do outro lado: quem conduzia, de
 * onde para onde, e o que correu mal pelo caminho.
 */
class DrivingIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String assetId;
    private String driverId;
    private String key;

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

    /** Publica uma posição como o aparelho faria. */
    private void publish(Instant at, double lat, double lon, double speed,
                         boolean ignition, Double odometer) throws Exception {
        Map<String, Object> pos = new HashMap<>();
        pos.put("latitude", lat);
        pos.put("longitude", lon);
        pos.put("speedKph", speed);
        pos.put("ignition", ignition);
        pos.put("satellites", 9);
        pos.put("recordedAt", at.toString());
        if (odometer != null) {
            pos.put("odometerKm", odometer);
        }
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-DRV", "key", key, "position", pos))))
                .andExpect(status().isOk());
    }

    private void branch(String name, double lat, double lon, int radius) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("kind", "BRANCH");
        b.put("latitude", lat);
        b.put("longitude", lon);
        b.put("radiusMeters", radius);
        send(post("/api/v1/locations"), b, 201);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("conducao@teste.ao").bearer();

        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camiao"), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-700", "name", "Camiao", "assetTypeId", typeId), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-DRV", "assetId", assetId), 201)
                .get("ingestKey").asText();

        Map<String, Object> m = new HashMap<>();
        m.put("name", "Manuel Kiala");
        m.put("employeeNumber", "M-01");
        driverId = send(post("/api/v1/drivers"), m, 201).get("id").asText();

        branch("Filial de Luanda", -8.8383, 13.2344, 400);
        branch("Filial de Viana", -8.8500, 13.2500, 500);
    }

    /**
     * Meio-dia em Luanda, há alguns dias.
     *
     * <p>As viagens de teste são ancoradas a uma hora fixa do dia, e não a
     * {@code now()}: com {@code now()} menos umas horas, um teste que corresse
     * de madrugada caía depois das 22h, ganhava uma infração de condução
     * noturna, e a pontuação dava outro número — falhando conforme a hora a que
     * alguém o corresse.
     */
    private Instant middayDaysAgo(int dias) {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Luanda"))
                .minusDays(dias)
                .withHour(12).withMinute(0).withSecond(0).withNano(0)
                .toInstant();
    }

    private void assignDriver(Instant from) throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("driverId", driverId);
        a.put("assetId", assetId);
        a.put("startedAt", from.toString());
        send(post("/api/v1/driver-assignments"), a, 201);
    }

    /**
     * Uma viagem de Luanda para Viana com uma travagem brusca pelo caminho.
     *
     * @return instante em que a viagem começou
     */
    private Instant driveWithAHarshBrake(Instant inicio) throws Exception {
        publish(inicio, -8.8383, 13.2344, 40, true, 1000.0);
        publish(inicio.plusSeconds(20), -8.8400, 13.2360, 60, true, 1000.3);
        // 60 → 5 km/h em 4 s = 13,75 km/h/s, acima do limiar de 12.
        publish(inicio.plusSeconds(24), -8.8405, 13.2365, 5, true, 1000.4);
        publish(inicio.plusSeconds(60), -8.8450, 13.2420, 50, true, 1000.9);
        publish(inicio.plusSeconds(120), -8.8500, 13.2500, 10, true, 1002.0);
        // Ignição desligada: a viagem fecha, com o fim no ponto anterior.
        publish(inicio.plusSeconds(480), -8.8500, 13.2500, 0, false, 1002.0);
        return inicio;
    }

    /**
     * Viagem de 100 km com uma travagem brusca.
     *
     * <p>Os intervalos entre leituras ficam abaixo dos 5 minutos de propósito:
     * acima disso a viagem fecha por paragem e passam a ser duas, cada uma com
     * distância a menos — e a pontuação deixava de ter percurso que chegue.
     * A distância vem do <b>odómetro do aparelho</b>, que tem prioridade sobre
     * a soma geométrica.
     */
    private void longTripWithOneHarshBrake(Instant inicio, double odometroInicial)
            throws Exception {
        publish(inicio, -8.8383, 13.2344, 60, true, odometroInicial);
        publish(inicio.plusSeconds(20), -8.8400, 13.2360, 60, true, odometroInicial + 0.4);
        // 60 → 5 km/h em 4 s = 13,75 km/h/s: travagem brusca.
        publish(inicio.plusSeconds(24), -8.8405, 13.2365, 5, true, odometroInicial + 0.5);
        publish(inicio.plusSeconds(60), -8.8430, 13.2400, 60, true, odometroInicial + 1.0);
        publish(inicio.plusSeconds(300), -8.8460, 13.2440, 60, true, odometroInicial + 40.0);
        publish(inicio.plusSeconds(540), -8.8490, 13.2480, 60, true, odometroInicial + 80.0);
        publish(inicio.plusSeconds(780), -8.8500, 13.2500, 60, true, odometroInicial + 100.0);
        publish(inicio.plusSeconds(900), -8.8500, 13.2500, 0, false, odometroInicial + 100.0);
    }

    // ---- sítios nomeados ---------------------------------------------------
    @Test
    void aTripSaysWhereItStartedAndEndedByName() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        JsonNode viagens = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200);
        JsonNode t = viagens.get("content").get(0);

        assertThat(t.get("open").asBoolean()).isFalse();
        assertThat(t.get("startPlaceName").asText()).isEqualTo("Filial de Luanda");
        assertThat(t.get("startPlaceKind").asText()).isEqualTo("BRANCH");
        assertThat(t.get("endPlaceName").asText()).isEqualTo("Filial de Viana");
        assertThat(t.get("endPlaceKind").asText()).isEqualTo("BRANCH");
    }

    @Test
    void aPlaceThatMatchesNothingIsLeftUnnamedInsteadOfGuessed() throws Exception {
        Instant inicio = middayDaysAgo(2);
        // Longe de qualquer filial registada.
        publish(inicio, -12.5000, 13.4000, 40, true, 500.0);
        publish(inicio.plusSeconds(60), -12.5050, 13.4050, 50, true, 501.5);
        publish(inicio.plusSeconds(600), -12.5100, 13.4100, 0, false, 503.0);

        JsonNode t = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200)
                .get("content").get(0);

        // Sem nome inventado: o ecrã mostra as coordenadas, que é a verdade.
        assertThat(t.hasNonNull("startPlaceName")).isFalse();
        assertThat(t.get("startPlaceKind").asText()).isEqualTo("UNKNOWN");
        assertThat(t.get("startLatitude").asDouble()).isEqualTo(-12.5);
    }

    // ---- quem conduzia -----------------------------------------------------
    @Test
    void theTripIsAttributedToWhoeverWasDrivingWhenItStarted() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        JsonNode t = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200)
                .get("content").get(0);
        assertThat(t.get("driverName").asText()).isEqualTo("Manuel Kiala");
    }

    @Test
    void aTripWithNobodyAssignedHasNoDriverInsteadOfAGuess() throws Exception {
        Instant inicio = middayDaysAgo(2);
        // Atribuição começa DEPOIS da viagem: não cobre aquele momento.
        assignDriver(Instant.now());
        driveWithAHarshBrake(inicio);

        JsonNode t = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200)
                .get("content").get(0);
        // Pôr o nome de quem conduz hoje num processo disciplinar de ontem seria
        // acusar a pessoa errada.
        assertThat(t.hasNonNull("driverName")).isFalse();
    }

    // ---- infrações ---------------------------------------------------------
    @Test
    void aHarshBrakeDuringTheTripBecomesAnInfraction() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        JsonNode lista = send(get("/api/v1/driving-events"), null, 200);
        assertThat(lista.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);

        JsonNode e = lista.get("content").get(0);
        assertThat(e.get("kind").asText()).isEqualTo("HARSH_BRAKE");
        assertThat(e.get("kindLabel").asText()).isEqualTo("Travagem brusca");
        assertThat(e.get("driverName").asText()).isEqualTo("Manuel Kiala");
        // Medido e limiar: a infração tem de poder ser contestada com números.
        assertThat(e.get("measuredValue").asDouble()).isGreaterThan(12.0);
        assertThat(e.get("thresholdValue").asDouble()).isEqualTo(12.0);
        assertThat(e.get("placeName").asText()).isEqualTo("Filial de Luanda");

        JsonNode t = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200)
                .get("content").get(0);
        assertThat(t.get("harshBrakeCount").asInt()).isEqualTo(1);
    }

    @Test
    void anInfractionCanBeDismissedWithAReasonThatStaysOnRecord() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        String eventId = send(get("/api/v1/driving-events"), null, 200)
                .get("content").get(0).get("id").asText();

        // Sem razão escrita não se anula nada: seria apagar em silêncio o que
        // não convém.
        send(post("/api/v1/driving-events/" + eventId + "/dismiss"),
                Map.of("reason", "abc"), 400);

        JsonNode anulada = send(post("/api/v1/driving-events/" + eventId + "/dismiss"),
                Map.of("reason", "Travou para nao atropelar uma crianca na estrada"), 200);
        assertThat(anulada.get("dismissed").asBoolean()).isTrue();
        assertThat(anulada.get("dismissReason").asText()).contains("crianca");

        // Anular duas vezes não faz sentido.
        send(post("/api/v1/driving-events/" + eventId + "/dismiss"),
                Map.of("reason", "Outra vez a mesma coisa"), 409);
    }

    // ---- pontuação ---------------------------------------------------------
    @Test
    void aShortDistanceGivesNoScoreInsteadOfAFlatteringOne() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        JsonNode s = send(get("/api/v1/drivers/" + driverId + "/score"), null, 200);

        // Dois quilómetros não chegam para julgar ninguém.
        assertThat(s.get("insufficientData").asBoolean()).isTrue();
        assertThat(s.hasNonNull("score")).isFalse();
        assertThat(s.get("explanation").asText()).contains("50 km");
    }

    @Test
    void aRealDistanceGivesAScoreThatCanBeRecomputedByHand() throws Exception {
        Instant inicio = middayDaysAgo(3);
        assignDriver(inicio.minusSeconds(3600));

        longTripWithOneHarshBrake(inicio, 2000.0);

        JsonNode s = send(get("/api/v1/drivers/" + driverId + "/score"), null, 200);

        assertThat(s.get("insufficientData").asBoolean()).isFalse();
        assertThat(s.get("distanceKm").asDouble()).isEqualTo(100.0);
        assertThat(s.get("harshBrakeCount").asInt()).isEqualTo(1);
        assertThat(s.get("totalPenalty").asDouble()).isEqualTo(3.0);

        // A fórmula tem de fechar à mão: 100 − (3 ÷ 100 × 100) = 97.
        assertThat(s.get("score").asDouble()).isEqualTo(97.0);
        assertThat(s.get("formula").asText()).contains("100 −");
        assertThat(s.get("bandLabel").asText()).isEqualTo("Excelente");
    }

    @Test
    void aDismissedInfractionStopsCountingTowardsTheScore() throws Exception {
        Instant inicio = middayDaysAgo(3);
        assignDriver(inicio.minusSeconds(3600));

        longTripWithOneHarshBrake(inicio, 3000.0);

        assertThat(send(get("/api/v1/drivers/" + driverId + "/score"), null, 200)
                .get("score").asDouble()).isEqualTo(97.0);

        String eventId = send(get("/api/v1/driving-events"), null, 200)
                .get("content").get(0).get("id").asText();
        send(post("/api/v1/driving-events/" + eventId + "/dismiss"),
                Map.of("reason", "Sensor do aparelho com leitura errada, confirmado na oficina"),
                200);

        JsonNode depois = send(get("/api/v1/drivers/" + driverId + "/score"), null, 200);
        assertThat(depois.get("totalPenalty").asDouble()).isEqualTo(0.0);
        assertThat(depois.get("score").asDouble()).isEqualTo(100.0);
    }

    @Test
    void theFleetSummaryRanksDriversAndCountsInfractions() throws Exception {
        Instant inicio = middayDaysAgo(2);
        assignDriver(inicio.minusSeconds(3600));
        driveWithAHarshBrake(inicio);

        JsonNode resumo = send(get("/api/v1/driving/summary"), null, 200);
        assertThat(resumo.get("harshBrake").asInt()).isEqualTo(1);
        assertThat(resumo.get("totalEvents").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(resumo.get("ranking")).isNotEmpty();
        assertThat(resumo.get("ranking").get(0).get("driverName").asText())
                .isEqualTo("Manuel Kiala");
    }
}
