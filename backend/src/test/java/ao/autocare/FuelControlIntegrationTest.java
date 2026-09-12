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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Controlo de combustível: onde o gasóleo se perde.
 *
 * <p>O que se testa aqui não é a soma de custos — isso qualquer folha de cálculo
 * faz. É o cruzamento: litros que não cabiam no depósito, cartões usados longe
 * da viatura, quilómetros declarados que o GPS não viu. E, sobretudo, que cada
 * achado traz <b>litros e dinheiro</b> ao lado, porque é isso que faz alguém
 * agir.
 */
class FuelControlIntegrationTest extends AbstractIntegrationTest {

    private static final double LAT = -8.8383;
    private static final double LON = 13.2344;

    private String bearer;
    private String assetId;
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

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("combustivel@teste.ao").bearer();

        // Hodometro, nao horimetro: o consumo de um camiao mede-se em L/100km.
        // O tipo de ativo traz HOURMETER por omissao, que serve maquinas.
        String typeId = send(post("/api/v1/asset-types"),
                Map.of("name", "Camiao", "primaryMeter", "ODOMETER"), 201)
                .get("id").asText();

        Map<String, Object> ativo = new HashMap<>();
        ativo.put("tag", "CAM-500");
        ativo.put("name", "Camiao Volvo");
        ativo.put("assetTypeId", typeId);
        ativo.put("initialMeterValue", 10_000);
        ativo.put("tankCapacityLiters", 300);
        assetId = send(post("/api/v1/assets"), ativo, 201).get("id").asText();

        key = send(post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-FUEL", "assetId", assetId), 201)
                .get("ingestKey").asText();
    }

    /** Abastecimento no formato mínimo. */
    private Map<String, Object> deposito(double litros, double medidor, Instant quando) {
        Map<String, Object> f = new HashMap<>();
        f.put("liters", litros);
        f.put("meterValue", medidor);
        f.put("filledAt", quando.toString());
        f.put("pricePerLiter", 300);
        f.put("fullTank", true);
        return f;
    }

    private JsonNode abastecer(Map<String, Object> body) throws Exception {
        return send(post("/api/v1/assets/" + assetId + "/fuel"), body, 201);
    }

    private void publish(Instant at, double lat, double lon) throws Exception {
        Map<String, Object> pos = new HashMap<>();
        pos.put("latitude", lat);
        pos.put("longitude", lon);
        pos.put("speedKph", 0);
        pos.put("ignition", false);
        pos.put("satellites", 9);
        pos.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "deviceId", "IMEI-FUEL", "key", key, "position", pos))))
                .andExpect(status().isOk());
    }

    private JsonNode anomalias() throws Exception {
        return send(get("/api/v1/assets/" + assetId + "/fuel/anomalies"), null, 200);
    }

    private JsonNode anomaliaDoTipo(String kind) throws Exception {
        for (JsonNode a : anomalias()) {
            if (a.get("kind").asText().equals(kind)) {
                return a;
            }
        }
        return null;
    }

    /** Quatro depósitos regulares: o mínimo para haver base de consumo. */
    private void buildBaseline(Instant base) throws Exception {
        abastecer(deposito(100, 10_000, base.minus(40, ChronoUnit.DAYS)));
        // 100 L em 400 km = 25 L/100km, e assim nos seguintes.
        abastecer(deposito(100, 10_400, base.minus(30, ChronoUnit.DAYS)));
        abastecer(deposito(102, 10_800, base.minus(20, ChronoUnit.DAYS)));
        abastecer(deposito(98, 11_200, base.minus(10, ChronoUnit.DAYS)));
        abastecer(deposito(100, 11_600, base.minus(5, ChronoUnit.DAYS)));
    }

    // ---- o básico ----------------------------------------------------------
    @Test
    void consumptionIsComputedBetweenTwoFullTanks() throws Exception {
        Instant agora = Instant.now();
        abastecer(deposito(100, 10_000, agora.minus(10, ChronoUnit.DAYS)));
        JsonNode segundo = abastecer(deposito(100, 10_400, agora.minus(5, ChronoUnit.DAYS)));

        // 100 L para 400 km = 25 L/100km.
        assertThat(segundo.get("consumption").asDouble()).isEqualTo(25.0);
        assertThat(segundo.get("consumptionUnit").asText()).isEqualTo("L/100km");
        assertThat(segundo.get("distanceOrHours").asDouble()).isEqualTo(400.0);
    }

    @Test
    void aRefuelWithoutAMeterReadingIsFlaggedAsUncontrollable() throws Exception {
        Map<String, Object> semMedidor = deposito(100, 0, Instant.now());
        semMedidor.remove("meterValue");
        abastecer(semMedidor);

        JsonNode a = anomaliaDoTipo("MISSING_ODOMETER");
        assertThat(a).isNotNull();
        // Não é irregularidade — é a razão por que o controlo deixa de funcionar.
        assertThat(a.get("severity").asText()).isEqualTo("INFO");
        assertThat(a.get("detail").asText()).contains("deixa de funcionar");
    }

    // ---- litros que não cabem ----------------------------------------------
    @Test
    void moreLitresThanTheTankHoldsIsRecordedAndFlaggedNotRefused() throws Exception {
        // 380 L num depósito de 300. Antes isto era recusado — e recusar ensina
        // quem lança a baixar o número até passar, destruindo a prova.
        JsonNode r = abastecer(deposito(380, 10_500, Instant.now()));
        assertThat(r.get("liters").asDouble()).isEqualTo(380.0);

        JsonNode a = anomaliaDoTipo("VOLUME_EXCEEDS_TANK");
        assertThat(a).isNotNull();
        assertThat(a.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(a.get("litersAtRisk").asDouble()).isEqualTo(80.0);
        // 80 L a 300 Kz = 24.000 Kz. É este número que põe alguém a agir.
        assertThat(a.get("costAtRisk").asDouble()).isEqualTo(24_000.0);
        assertThat(a.get("currency").asText()).isEqualTo("AOA");
    }

    // ---- cartão longe da viatura -------------------------------------------
    @Test
    void aCardUsedFarFromTheVehicleIsCaught() throws Exception {
        Instant quando = Instant.now().minus(1, ChronoUnit.HOURS);
        // A viatura estava em Luanda...
        publish(quando, LAT, LON);

        // ...e o abastecimento foi lançado a ~40 km dali.
        Map<String, Object> f = deposito(150, 10_500, quando);
        f.put("latitude", -9.2000);
        f.put("longitude", 13.2344);
        JsonNode r = abastecer(f);

        assertThat(r.get("gpsVerified").asBoolean()).isFalse();
        // O rótulo diz a distância, que é mais útil do que "longe".
        assertThat(r.get("gpsLabel").asText()).contains("km do local")
                .doesNotContain("Não foi possível");

        JsonNode a = anomaliaDoTipo("REFUEL_AWAY_FROM_VEHICLE");
        assertThat(a).isNotNull();
        assertThat(a.get("severity").asText()).isEqualTo("CRITICAL");
        // Todo o abastecimento é suspeito, não só uma parte.
        assertThat(a.get("litersAtRisk").asDouble()).isEqualTo(150.0);
    }

    @Test
    void aRefuelAtTheVehiclesLocationPasses() throws Exception {
        Instant quando = Instant.now().minus(1, ChronoUnit.HOURS);
        publish(quando, LAT, LON);

        Map<String, Object> f = deposito(150, 10_500, quando);
        f.put("latitude", LAT);
        f.put("longitude", LON);
        JsonNode r = abastecer(f);

        assertThat(r.get("gpsVerified").asBoolean()).isTrue();
        assertThat(anomaliaDoTipo("REFUEL_AWAY_FROM_VEHICLE")).isNull();
    }

    @Test
    void withoutAPositionTheSystemSaysItCouldNotVerifyInsteadOfGreen() throws Exception {
        Map<String, Object> f = deposito(150, 10_500, Instant.now());
        f.put("latitude", LAT);
        f.put("longitude", LON);
        JsonNode r = abastecer(f);

        // Ausente = não foi possível verificar. Mostrar verde aqui seria pior do
        // que não ter verificação nenhuma: daria confiança sem fundamento.
        assertThat(r.hasNonNull("gpsVerified")).isFalse();
        assertThat(r.get("gpsLabel").asText()).contains("Não foi possível");
        assertThat(anomaliaDoTipo("REFUEL_AWAY_FROM_VEHICLE")).isNull();
    }

    // ---- duplicados e medidor ----------------------------------------------
    @Test
    void twoRefuelsMinutesApartAreFlagged() throws Exception {
        Instant quando = Instant.now().minus(2, ChronoUnit.HOURS);
        abastecer(deposito(120, 10_400, quando));
        abastecer(deposito(120, 10_401, quando.plusSeconds(600)));

        JsonNode a = anomaliaDoTipo("DUPLICATE_REFUEL");
        assertThat(a).isNotNull();
        assertThat(a.get("litersAtRisk").asDouble()).isEqualTo(120.0);
        assertThat(a.get("detail").asText()).contains("talões");
    }

    @Test
    void aMeterThatGoesBackwardsIsFlagged() throws Exception {
        Instant agora = Instant.now();
        abastecer(deposito(100, 12_000, agora.minus(5, ChronoUnit.DAYS)));
        abastecer(deposito(100, 11_500, agora.minus(1, ChronoUnit.DAYS)));

        JsonNode a = anomaliaDoTipo("ODOMETER_ROLLBACK");
        assertThat(a).isNotNull();
        assertThat(a.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(a.get("expectedValue").asDouble()).isEqualTo(12_000.0);
        assertThat(a.get("observedValue").asDouble()).isEqualTo(11_500.0);
    }

    // ---- base de consumo e desvios -----------------------------------------
    @Test
    void theBaselineComparesTheAssetWithItselfOnly() throws Exception {
        buildBaseline(Instant.now());

        JsonNode bases = send(get("/api/v1/fuel/baselines"), null, 200);
        assertThat(bases).hasSize(1);

        JsonNode b = bases.get(0);
        assertThat(b.get("assetTag").asText()).isEqualTo("CAM-500");
        assertThat(b.get("unit").asText()).isEqualTo("L/100km");
        assertThat(b.get("baseline").asDouble()).isBetween(24.0, 26.0);
        assertThat(b.get("sampleCount").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(b.get("explanation").asText()).contains("consigo próprio");
    }

    @Test
    void noBaselineIsPublishedFromTooFewTanks() throws Exception {
        Instant agora = Instant.now();
        abastecer(deposito(100, 10_000, agora.minus(20, ChronoUnit.DAYS)));
        abastecer(deposito(100, 10_400, agora.minus(10, ChronoUnit.DAYS)));

        // Acusar com base em dois depósitos seria acusar por acaso.
        assertThat(send(get("/api/v1/fuel/baselines"), null, 200)).isEmpty();
    }

    @Test
    void aConsumptionWellAboveTheAssetsOwnBaselineIsFlaggedWithTheLitresLost()
            throws Exception {
        Instant agora = Instant.now();
        buildBaseline(agora);

        // 200 L para 400 km = 50 L/100km, o dobro da base de 25.
        abastecer(deposito(200, 12_000, agora.minus(1, ChronoUnit.DAYS)));

        JsonNode a = anomaliaDoTipo("CONSUMPTION_SPIKE");
        assertThat(a).isNotNull();
        assertThat(a.get("observedValue").asDouble()).isEqualTo(50.0);
        assertThat(a.get("expectedValue").asDouble()).isBetween(24.0, 26.0);
        // Cerca de 25 L/100km a mais em 400 km = ~100 L acima do esperado.
        assertThat(a.get("litersAtRisk").asDouble()).isBetween(95.0, 105.0);
        assertThat(a.get("detail").asText()).contains("verificar");
    }

    @Test
    void aConsumptionWithinTheUsualSpreadIsNotFlagged() throws Exception {
        Instant agora = Instant.now();
        buildBaseline(agora);

        // 104 L para 400 km = 26 L/100km. Está dentro do normal deste ativo.
        abastecer(deposito(104, 12_000, agora.minus(1, ChronoUnit.DAYS)));
        assertThat(anomaliaDoTipo("CONSUMPTION_SPIKE")).isNull();
    }

    // ---- fecho de anomalias -------------------------------------------------
    @Test
    void closingAnAnomalyRequiresAnExplanation() throws Exception {
        abastecer(deposito(380, 10_500, Instant.now()));
        String id = anomaliaDoTipo("VOLUME_EXCEEDS_TANK").get("id").asText();

        // Fechar em branco é indistinguível de esconder.
        send(post("/api/v1/fuel/anomalies/" + id + "/resolve"),
                Map.of("status", "DISMISSED"), 400);

        JsonNode fechada = send(post("/api/v1/fuel/anomalies/" + id + "/resolve"),
                Map.of("status", "CONFIRMED",
                        "resolution", "Confirmado com a bomba: encheram tambem dois bidoes"),
                200);
        assertThat(fechada.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(fechada.get("statusLabel").asText()).isEqualTo("Confirmada");
        assertThat(fechada.get("resolution").asText()).contains("bidoes");

        // Fechar duas vezes não faz sentido.
        send(post("/api/v1/fuel/anomalies/" + id + "/resolve"),
                Map.of("status", "RESOLVED", "resolution", "Outra vez a mesma coisa"), 409);
    }

    // ---- painel -------------------------------------------------------------
    @Test
    void theDashboardSaysHowMuchMoneyIsUnexplained() throws Exception {
        Instant agora = Instant.now();
        abastecer(deposito(100, 10_000, agora.minus(10, ChronoUnit.DAYS)));
        abastecer(deposito(380, 10_400, agora.minus(5, ChronoUnit.DAYS)));

        JsonNode painel = send(get("/api/v1/fuel/dashboard"), null, 200);

        assertThat(painel.get("refuels").asInt()).isEqualTo(2);
        assertThat(painel.get("totalLiters").asDouble()).isEqualTo(480.0);
        assertThat(painel.get("totalCost").asDouble()).isEqualTo(144_000.0);
        // 80 L a mais do que cabe no depósito, a 300 Kz.
        assertThat(painel.get("costAtRisk").asDouble()).isEqualTo(24_000.0);
        assertThat(painel.get("litersAtRisk").asDouble()).isEqualTo(80.0);
        assertThat(painel.get("reading").asText()).contains("por explicar");

        assertThat(painel.get("byAsset")).hasSize(1);
        assertThat(painel.get("byAsset").get(0).get("assetTag").asText()).isEqualTo("CAM-500");
    }

    @Test
    void anAnomalyDismissedWithCauseStopsCountingAsMoneyAtRisk() throws Exception {
        Instant agora = Instant.now();
        abastecer(deposito(380, 10_500, agora.minus(2, ChronoUnit.DAYS)));

        String id = anomaliaDoTipo("VOLUME_EXCEEDS_TANK").get("id").asText();
        send(post("/api/v1/fuel/anomalies/" + id + "/resolve"),
                Map.of("status", "DISMISSED",
                        "resolution", "Capacidade do deposito estava mal registada, corrigida"),
                200);

        JsonNode painel = send(get("/api/v1/fuel/dashboard"), null, 200);
        // Tinha explicação: somá-la inflacionaria o número que a direção vê.
        assertThat(painel.get("costAtRisk").asDouble()).isEqualTo(0.0);
        assertThat(painel.get("reading").asText()).contains("Nada por explicar");
        // E o aviso continua honesto sobre o que não cobre.
        assertThat(painel.get("reading").asText()).contains("nunca chegaram ao sistema");
    }

    @Test
    void theDashboardAttributesFuelToTheDriverWhoWasAssigned() throws Exception {
        Instant agora = Instant.now();

        Map<String, Object> m = new HashMap<>();
        m.put("name", "Pedro Cabral");
        m.put("employeeNumber", "C-01");
        String driverId = send(post("/api/v1/drivers"), m, 201).get("id").asText();

        Map<String, Object> a = new HashMap<>();
        a.put("driverId", driverId);
        a.put("assetId", assetId);
        a.put("startedAt", agora.minus(30, ChronoUnit.DAYS).toString());
        send(post("/api/v1/driver-assignments"), a, 201);

        // Sem indicar motorista: imputa-se a quem estava atribuído na altura.
        abastecer(deposito(100, 10_400, agora.minus(2, ChronoUnit.DAYS)));

        JsonNode painel = send(get("/api/v1/fuel/dashboard"), null, 200);
        assertThat(painel.get("byDriver")).hasSize(1);
        assertThat(painel.get("byDriver").get(0).get("name").asText()).isEqualTo("Pedro Cabral");
        assertThat(painel.get("byDriver").get(0).get("liters").asDouble()).isEqualTo(100.0);
    }
}
