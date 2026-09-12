package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.telemetry.TraccarPositions;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * As posições do Traccar entram — e o sensor de combustível conta.
 *
 * <p>O Traccar é um servidor HTTP a sério levantado aqui, a responder com o
 * JSON documentado da API dele: velocidade em nós, odómetro em metros,
 * combustível em «fuel». O que este código tem de errado com mais
 * probabilidade são as unidades — e um duplo que entregasse km/h já feitos
 * deixava passar exatamente isso.
 */
class TraccarPositionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraccarPositions traccar;

    private String bearer;
    private String assetId;
    private HttpServer servidor;
    private final AtomicReference<String> devicesJson = new AtomicReference<>("[]");
    private final AtomicReference<String> positionsJson = new AtomicReference<>("[]");

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("traccar-pos@teste.ao").bearer();

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/devices", troca -> responder(troca, devicesJson.get()));
        servidor.createContext("/api/positions", troca -> responder(troca, positionsJson.get()));
        // Como o Traccar 6.6 real com token: /api/session responde 404.
        servidor.createContext("/api/session", troca -> {
            troca.sendResponseHeaders(404, -1);
            troca.close();
        });
        servidor.start();

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("url", "http://127.0.0.1:" + servidor.getAddress().getPort());
        cfg.put("token", "token-de-teste");
        send(put("/api/v1/integrations/traccar"), cfg, 200);

        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        Map<String, Object> ativo = new HashMap<>();
        ativo.put("tag", "CAM-001");
        ativo.put("name", "Camião");
        ativo.put("assetTypeId", typeId);
        ativo.put("tankCapacityLiters", 400);
        assetId = send(post("/api/v1/assets"), ativo, 201).get("id").asText();
        send(post("/api/v1/gps-devices"), Map.of("externalId", "358000000000001", "assetId", assetId), 201);

        devicesJson.set("[{\"id\":7,\"uniqueId\":\"358000000000001\",\"name\":\"CAM-001\"}]");
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, String corpo) throws java.io.IOException {
        byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(200, b.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(b);
        }
    }

    @Autowired
    private ao.autocare.repo.IntegrationSettingsRepository settingsRepo;

    private String settingsId() throws Exception {
        // O id das definições não sai pela API; vai-se buscar pela empresa.
        String orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();
        return settingsRepo.findByOrganizationId(orgId).orElseThrow().getId();
    }

    /** Uma posição no formato do Traccar. Velocidade em nós, odómetro em metros. */
    private static String posicao(long id, double lat, double lon, double nos, Instant quando,
            Map<String, Object> atributos) {
        StringBuilder a = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<String, Object> e : atributos.entrySet()) {
            if (!primeiro) a.append(",");
            primeiro = false;
            a.append("\"").append(e.getKey()).append("\":").append(e.getValue());
        }
        a.append("}");
        return "[{\"id\":" + id + ",\"deviceId\":7,\"latitude\":" + lat + ",\"longitude\":" + lon
                + ",\"speed\":" + nos + ",\"course\":90,\"altitude\":60,\"accuracy\":5,"
                + "\"fixTime\":\"" + quando + "\",\"attributes\":" + a + "}]";
    }

    // ===================================================================

    @Test
    void oTesteDeLigacaoAceitaUmTraccarRealComToken() throws Exception {
        // Num Traccar 6.6 a sério, com token, GET /api/session dá 404 e
        // /api/devices dá 200. O teste tem de usar o segundo — descoberto na
        // primeira instalação real, onde o botão dava falso negativo.
        JsonNode r = send(post("/api/v1/integrations/traccar/test"), null, 200);
        assertThat(r.get("ok").asBoolean()).isTrue();
        assertThat(r.get("message").asText()).contains("aceites");
    }

    @Test
    void aSondagemTrazAPosicaoEConverteAsUnidades() throws Exception {
        Instant agora = Instant.now().minusSeconds(60);
        positionsJson.set(posicao(1001, -8.8383, 13.2344, 30.0, agora,
                Map.of("ignition", true, "sat", 9, "odometer", 128400000, "totalDistance", 128400500,
                        "hours", 3600000L * 1240, "fuel", 250.0)));

        int aceites = traccar.sondar(settingsId());
        assertThat(aceites).isEqualTo(1);

        JsonNode live = send(get("/api/v1/telemetry/live"), null, 200);
        assertThat(live).hasSize(1);
        JsonNode cam = live.get(0);
        // 30 nós são 55,6 km/h. Deixar passar «30» como km/h esconderia
        // excessos de velocidade de 85 %.
        assertThat(cam.get("speedKph").asDouble()).isBetween(55.0, 56.0);
        assertThat(cam.get("latitude").asDouble()).isEqualTo(-8.8383);

        // As horas de motor (em milissegundos no Traccar) alimentaram o horímetro
        // do ativo — o medidor principal deste tipo.
        JsonNode ativo = send(get("/api/v1/assets/" + assetId), null, 200);
        JsonNode horimetro = null;
        for (JsonNode m : ativo.get("meters")) {
            if ("HOURMETER".equals(m.get("kind").asText())) horimetro = m;
        }
        assertThat(horimetro).isNotNull();
        assertThat(horimetro.get("currentValue").asDouble()).isEqualTo(1240.0);
        // O nível do depósito ficou no ativo.
        assertThat(ativo.get("fuelLevelLiters").asDouble()).isEqualTo(250.0);

        JsonNode defs = send(get("/api/v1/integrations"), null, 200);
        assertThat(defs.get("traccarLastPollAt").isNull()).isFalse();
        assertThat(defs.get("traccarLastPositionAt").isNull()).isFalse();
        assertThat(defs.has("traccarPollError")).isFalse();
    }

    @Test
    void aRondaAgendadaGravaOQueFez() throws Exception {
        // sondarTodas() é o que o relógio chama. Chamado daqui, passa pelo
        // mesmo caminho que em produção — e tem de deixar rasto: a última
        // ronda gravada. Foi por aqui que a primeira instalação real falhou.
        positionsJson.set(posicao(1501, -8.8383, 13.2344, 0, Instant.now().minusSeconds(30), Map.of("fuel", 100.0)));
        traccar.sondarTodas();
        JsonNode defs = send(get("/api/v1/integrations"), null, 200);
        assertThat(defs.get("traccarLastPollAt").isNull()).isFalse();
        assertThat(defs.get("traccarLastPositionAt").isNull()).isFalse();
        assertThat(send(get("/api/v1/telemetry/live"), null, 200)).hasSize(1);
    }

    @Test
    void aMesmaPosicaoNaoEntraDuasVezes() throws Exception {
        Instant agora = Instant.now().minusSeconds(60);
        positionsJson.set(posicao(2001, -8.8383, 13.2344, 0, agora, Map.of("fuel", 200.0)));
        assertThat(traccar.sondar(settingsId())).isEqualTo(1);
        // Segunda ronda, o Traccar ainda devolve a mesma última posição.
        assertThat(traccar.sondar(settingsId())).isEqualTo(0);
    }

    @Test
    void umAparelhoDoTraccarQueNaoEstaRegistadoCaEIgnorado() throws Exception {
        devicesJson.set("[{\"id\":9,\"uniqueId\":\"999\",\"name\":\"outro\"}]");
        positionsJson.set("[{\"id\":3001,\"deviceId\":9,\"latitude\":-8.8,\"longitude\":13.2,\"speed\":0,"
                + "\"fixTime\":\"" + Instant.now().minusSeconds(30) + "\",\"attributes\":{}}]");
        // Não se cria sozinho: registar cá é decisão da empresa.
        assertThat(traccar.sondar(settingsId())).isEqualTo(0);
        assertThat(send(get("/api/v1/gps-devices"), null, 200)).hasSize(1);
    }

    @Test
    void oTraccarEmBaixoFicaRegistadoSemPartirNada() throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("url", "http://127.0.0.1:1");
        cfg.put("token", "x");
        send(put("/api/v1/integrations/traccar"), cfg, 200);
        assertThat(traccar.sondar(settingsId())).isEqualTo(0);
        JsonNode defs = send(get("/api/v1/integrations"), null, 200);
        assertThat(defs.get("traccarPollError").asText()).contains("contactar");
    }

    @Test
    void oEncaminhamentoEntraComOSegredoEMaisNada() throws Exception {
        String segredo = send(post("/api/v1/integrations/traccar/forward-secret"), null, 200)
                .get("secret").asText();

        String corpo = "{\"device\":{\"id\":7,\"uniqueId\":\"358000000000001\"},"
                + "\"position\":{\"id\":4001,\"deviceId\":7,\"latitude\":-8.84,\"longitude\":13.23,"
                + "\"speed\":10,\"fixTime\":\"" + Instant.now().minusSeconds(20) + "\",\"attributes\":{\"fuel\":180}}}";

        // Sem segredo: 401. Com segredo errado: 401. É público, por isso a porta é o segredo.
        mvc.perform(post("/api/v1/telemetry/traccar/forward").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/telemetry/traccar/forward").header("X-Forward-Secret", "errado")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());

        MvcResult ok = mvc.perform(post("/api/v1/telemetry/traccar/forward").header("X-Forward-Secret", segredo)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(ok.getResponse().getContentAsString()).get("accepted").asBoolean()).isTrue();
        assertThat(send(get("/api/v1/telemetry/live"), null, 200)).hasSize(1);
    }

    // ===================================================================
    // O sensor de combustível

    @Test
    void oSensorDetetaUmAbastecimentoComAViaturaParada() throws Exception {
        Instant t0 = Instant.now().minusSeconds(600);
        positionsJson.set(posicao(5001, -8.8383, 13.2344, 0, t0, Map.of("fuel", 120.0, "odometer", 100000000)));
        traccar.sondar(settingsId());
        positionsJson.set(posicao(5002, -8.8383, 13.2344, 0, t0.plusSeconds(300),
                Map.of("fuel", 320.0, "odometer", 100000000)));
        traccar.sondar(settingsId());

        JsonNode fuel = send(get("/api/v1/assets/" + assetId + "/fuel"), null, 200).get("content");
        assertThat(fuel).hasSize(1);
        JsonNode r = fuel.get(0);
        assertThat(r.get("source").asText()).isEqualTo("SENSOR");
        assertThat(r.get("liters").asDouble()).isEqualTo(200.0);
        assertThat(r.get("notes").asText()).contains("120").contains("320");
    }

    @Test
    void umaDescidaEmAndamentoNaoEAbastecimentoNemSangria() throws Exception {
        Instant t0 = Instant.now().minusSeconds(600);
        positionsJson.set(posicao(6001, -8.8383, 13.2344, 40, t0, Map.of("fuel", 300.0)));
        traccar.sondar(settingsId());
        // 20 km depois, 15 L a menos: consumo e ondulação, não um alarme.
        positionsJson.set(posicao(6002, -8.6583, 13.2344, 40, t0.plusSeconds(1200), Map.of("fuel", 285.0)));
        traccar.sondar(settingsId());

        assertThat(send(get("/api/v1/assets/" + assetId + "/fuel"), null, 200).get("content")).isEmpty();
        assertThat(send(get("/api/v1/fuel/anomalies"), null, 200).get("content")).isEmpty();
    }

    @Test
    void oSensorDetetaUmaSangriaComAViaturaParada() throws Exception {
        Instant t0 = Instant.now().minusSeconds(3600);
        positionsJson.set(posicao(7001, -8.8383, 13.2344, 0, t0, Map.of("fuel", 300.0)));
        traccar.sondar(settingsId());
        positionsJson.set(posicao(7002, -8.8383, 13.2344, 0, t0.plusSeconds(1800), Map.of("fuel", 240.0)));
        traccar.sondar(settingsId());

        JsonNode anomalias = send(get("/api/v1/fuel/anomalies"), null, 200).get("content");
        assertThat(anomalias).hasSize(1);
        JsonNode a = anomalias.get(0);
        assertThat(a.get("kind").asText()).isEqualTo("SENSOR_DRAIN");
        assertThat(a.get("litersAtRisk").asDouble()).isEqualTo(60.0);
        assertThat(a.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(a.get("title").asText()).contains("60 L").contains("parada");

        // A mesma sangria não se repete a cada posição seguinte.
        positionsJson.set(posicao(7003, -8.8383, 13.2344, 0, t0.plusSeconds(2400), Map.of("fuel", 225.0)));
        traccar.sondar(settingsId());
        assertThat(send(get("/api/v1/fuel/anomalies"), null, 200).get("content")).hasSize(1);
    }

    @Test
    void oSensorNaoConfirmaOsLitrosDeclarados() throws Exception {
        // O sensor viu entrar 30 L…
        Instant t0 = Instant.now().minusSeconds(900);
        positionsJson.set(posicao(8001, -8.8383, 13.2344, 0, t0, Map.of("fuel", 100.0, "odometer", 50000000)));
        traccar.sondar(settingsId());
        positionsJson.set(posicao(8002, -8.8383, 13.2344, 0, t0.plusSeconds(300),
                Map.of("fuel", 130.0, "odometer", 50000000)));
        traccar.sondar(settingsId());

        // …e o motorista lança 80 L à mesma hora.
        Map<String, Object> manual = new HashMap<>();
        manual.put("liters", 80);
        manual.put("pricePerLiter", 300);
        manual.put("filledAt", t0.plusSeconds(400).toString());
        manual.put("meterValue", 50000);
        manual.put("fullTank", false);
        send(post("/api/v1/assets/" + assetId + "/fuel"), manual, 201);

        JsonNode anomalias = send(get("/api/v1/fuel/anomalies"), null, 200).get("content");
        JsonNode mismatch = null;
        for (JsonNode a : anomalias) {
            if ("SENSOR_MISMATCH".equals(a.get("kind").asText())) {
                mismatch = a;
            }
        }
        assertThat(mismatch).isNotNull();
        // 80 declarados − 30 vistos = 50 L pagos que não entraram, a 300 Kz.
        assertThat(mismatch.get("litersAtRisk").asDouble()).isEqualTo(50.0);
        assertThat(mismatch.get("costAtRisk").asDouble()).isEqualTo(15000.0);
        assertThat(mismatch.get("title").asText()).contains("80 L").contains("30 L");
    }

    @Test
    void umManualQueBateComOSensorNaoLevantaNada() throws Exception {
        Instant t0 = Instant.now().minusSeconds(900);
        positionsJson.set(posicao(9001, -8.8383, 13.2344, 0, t0, Map.of("fuel", 100.0)));
        traccar.sondar(settingsId());
        positionsJson.set(posicao(9002, -8.8383, 13.2344, 0, t0.plusSeconds(300), Map.of("fuel", 178.0)));
        traccar.sondar(settingsId());

        Map<String, Object> manual = new HashMap<>();
        manual.put("liters", 80);
        manual.put("filledAt", t0.plusSeconds(400).toString());
        manual.put("fullTank", false);
        send(post("/api/v1/assets/" + assetId + "/fuel"), manual, 201);

        for (JsonNode a : send(get("/api/v1/fuel/anomalies"), null, 200).get("content")) {
            assertThat(a.get("kind").asText()).isNotEqualTo("SENSOR_MISMATCH");
        }
    }

    @Test
    void percentagemConverteSePelaCapacidadeDoDeposito() throws Exception {
        // O aparelho diz que o sensor é em percentagem. Sem isto, 50 seriam 50 L:
        // não se adivinha, diz-se.
        String deviceId = send(get("/api/v1/gps-devices"), null, 200).get(0).get("id").asText();
        Map<String, Object> patch = new HashMap<>();
        patch.put("externalId", "358000000000001");
        patch.put("fuelUnit", "PERCENT");
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/v1/gps-devices/" + deviceId), patch, 200);

        // Depósito de 400 L; o aparelho manda «fuel»: 50 → 200 L.
        positionsJson.set(posicao(10001, -8.8383, 13.2344, 0, Instant.now().minusSeconds(30), Map.of("fuel", 50)));
        traccar.sondar(settingsId());
        assertThat(send(get("/api/v1/assets/" + assetId), null, 200).get("fuelLevelLiters").asDouble())
                .isEqualTo(200.0);
    }
}
