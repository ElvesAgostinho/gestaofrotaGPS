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
 * A viatura sai do corredor da rota e quem gere sabe na hora; enquanto anda
 * dentro dele, ninguém é incomodado. E a qualquer momento se pergunta onde
 * vai e a que horas chega.
 */
class RouteDeviationIntegrationTest extends AbstractIntegrationTest {

    /** Uma reta de 20 km para norte, ponto a ponto: é a rota prevista. */
    private static final double LON = 13.2344;
    private static final double LAT0 = -8.8383;

    private String bearer;
    private String assetId;
    private String key;
    private String routeId;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private void posicao(double lat, double lon, double kph, Instant at) throws Exception {
        Map<String, Object> pos = new HashMap<>();
        pos.put("latitude", lat);
        pos.put("longitude", lon);
        pos.put("speedKph", kph);
        pos.put("ignition", true);
        pos.put("satellites", 9);
        pos.put("recordedAt", at.toString());
        mvc.perform(post("/api/v1/telemetry/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("deviceId", "IMEI-DES", "key", key, "position", pos))))
                .andExpect(status().isOk());
    }

    private JsonNode avisosDeDesvio() throws Exception {
        JsonNode todos = send(get("/api/v1/notifications?size=50"), null, 200).get("content");
        return todos;
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("desvio@teste.ao", "Transportes Desvio").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(post("/api/v1/assets"), Map.of("tag", "DS-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
        key = send(post("/api/v1/gps-devices"), Map.of("externalId", "IMEI-DES", "assetId", assetId), 201)
                .get("ingestKey").asText();

        // A rota: uma reta de 0,18° de latitude (~20 km) para norte, corredor de 500 m.
        StringBuilder coords = new StringBuilder("[");
        for (int i = 0; i <= 20; i++) {
            coords.append(i > 0 ? "," : "").append("[").append(LON).append(",").append(LAT0 + i * 0.009).append("]");
        }
        coords.append("]");
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Reta do norte");
        rota.put("originLabel", "Sul");
        rota.put("destinationLabel", "Norte");
        rota.put("assetId", assetId);
        rota.put("expectedDistanceKm", 20);
        rota.put("expectedDurationMinutes", 30);
        rota.put("distanceSource", "ENGINE");
        rota.put("corridorMeters", 500);
        rota.put("pathGeojson", "{\"type\":\"LineString\",\"coordinates\":" + coords + "}");
        JsonNode r = send(post("/api/v1/routes"), rota, 201);
        routeId = r.get("id").asText();
        assertThat(r.get("corridorMeters").asInt()).isEqualTo(500);
    }

    @Test
    void dentroDoCorredorNinguemEIncomodadoEForaDeleOAvisoSaiNaHora() throws Exception {
        Instant t0 = Instant.now().minus(30, ChronoUnit.MINUTES);
        // Vai a meio da rota, em cima da estrada: nada a dizer.
        posicao(LAT0 + 0.045, LON + 0.0005, 60, t0);            // ~55 m ao lado
        assertThat(avisosDeDesvio()).noneSatisfy(n ->
                assertThat(n.get("title").asText()).contains("Fora da rota"));

        // Sai 3 km para o lado: o aviso sai já.
        posicao(LAT0 + 0.05, LON + 0.030, 60, t0.plus(5, ChronoUnit.MINUTES));
        JsonNode avisos = avisosDeDesvio();
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).isEqualTo("Fora da rota — DS-1");
            assertThat(n.get("category").asText()).isEqualTo("GPS");
            assertThat(n.get("body").asText()).contains("km da rota «Reta do norte»").contains("corredor de 500 m");
        });
        int quantos = avisos.size();

        // Continua fora: não se repete o aviso a cada posição.
        posicao(LAT0 + 0.055, LON + 0.031, 60, t0.plus(10, ChronoUnit.MINUTES));
        assertThat(avisosDeDesvio().size()).isEqualTo(quantos);

        // Volta à estrada: o aviso deixa de fazer sentido e desaparece.
        posicao(LAT0 + 0.06, LON + 0.0004, 60, t0.plus(15, ChronoUnit.MINUTES));
        assertThat(avisosDeDesvio()).noneSatisfy(n ->
                assertThat(n.get("title").asText()).contains("Fora da rota"));
    }

    @Test
    void oEtaDizOndeVaiEAQueHorasChega() throws Exception {
        Instant agora = Instant.now();
        // A 25 % do percurso, a 60 km/h: faltam 15 km, cerca de 15 minutos.
        posicao(LAT0 + 0.045, LON, 60, agora.minus(4, ChronoUnit.MINUTES));
        posicao(LAT0 + 0.046, LON, 60, agora.minus(2, ChronoUnit.MINUTES));

        JsonNode live = send(get("/api/v1/routes/live"), null, 200);
        assertThat(live).hasSize(1);
        JsonNode v = live.get(0);
        assertThat(v.get("assetTag").asText()).isEqualTo("DS-1");
        assertThat(v.get("routeName").asText()).isEqualTo("Reta do norte");
        assertThat(v.get("progress").asDouble()).isBetween(0.20, 0.30);
        assertThat(v.get("remainingKm").asDouble()).isBetween(13.0, 17.0);
        assertThat(v.get("offRoute").asBoolean()).isFalse();
        assertThat(v.get("etaSource").asText()).isEqualTo("GPS");
        assertThat(Instant.parse(v.get("eta").asText())).isAfter(agora);
        assertThat(Instant.parse(v.get("eta").asText())).isBefore(agora.plus(30, ChronoUnit.MINUTES));

        // A mesma coisa, pela rota.
        assertThat(send(get("/api/v1/routes/" + routeId + "/live"), null, 200)).hasSize(1);
    }

    @Test
    void semTracadoOuSemPosicaoRecenteNaoSeInventaNemProgressoNemHora() throws Exception {
        // Nada foi recebido ainda: a viatura não está «a caminho» de lado nenhum.
        assertThat(send(get("/api/v1/routes/live"), null, 200)).isEmpty();

        // Uma posição de ontem não diz onde a viatura está agora.
        posicao(LAT0 + 0.045, LON, 60, Instant.now().minus(30, ChronoUnit.HOURS));
        assertThat(send(get("/api/v1/routes/live"), null, 200)).isEmpty();

        // Um corredor absurdo é recusado com uma frase, não com um erro técnico.
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Corredor impossível");
        rota.put("originLabel", "A");
        rota.put("destinationLabel", "B");
        rota.put("assetId", assetId);
        rota.put("corridorMeters", 5);
        assertThat(send(post("/api/v1/routes"), rota, 400).get("message").asText())
                .contains("entre 50 m e 20 km");
    }
}
