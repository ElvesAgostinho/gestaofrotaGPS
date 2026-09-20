package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Uma rota nasce com viatura, e o que a viatura andou compara-se com o que
 * estava previsto — em km, em minutos e desenhado no mapa.
 */
class RouteAssignmentIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String bearer;
    private String assetId;

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
        bearer = register("rota@teste.ao", "Transportes Rota").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(post("/api/v1/assets"), Map.of("tag", "RT-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
    }

    @Test
    void oPercursoRealDeUmaViagemComparaSeComOPrevisto() throws Exception {
        // Rota prevista: 10 km, 20 minutos, com traçado desenhado.
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Volta curta");
        rota.put("originLabel", "Armazém");
        rota.put("destinationLabel", "Obra");
        rota.put("assetId", assetId);
        rota.put("expectedDistanceKm", 10);
        rota.put("expectedDurationMinutes", 20);
        rota.put("tolerancePercent", 10);
        rota.put("distanceSource", "ENGINE");
        rota.put("pathGeojson", "{\"type\":\"LineString\",\"coordinates\":[[13.23,-8.83],[13.30,-8.86]]}");
        JsonNode r = send(post("/api/v1/routes"), rota, 201);
        String routeId = r.get("id").asText();
        assertThat(r.get("pathGeojson").asText()).contains("LineString");
        assertThat(r.get("assignments").get(0).get("assetTag").asText()).isEqualTo("RT-1");

        // Sem viagens, a comparação está vazia — e não se inventa nenhuma.
        assertThat(send(get("/api/v1/routes/" + routeId + "/comparison"), null, 200)).isEmpty();

        // O GPS fecha uma viagem desta viatura; liga-se à rota.
        String key = send(post("/api/v1/gps-devices"), Map.of("externalId", "IMEI-RT", "assetId", assetId), 201)
                .get("ingestKey").asText();
        Instant t0 = Instant.now().minus(3, ChronoUnit.HOURS);
        for (int i = 0; i < 6; i++) {
            Map<String, Object> pos = new HashMap<>();
            pos.put("latitude", -8.83 - i * 0.01);
            pos.put("longitude", 13.23 + i * 0.01);
            pos.put("speedKph", i == 5 ? 0 : 40);
            pos.put("satellites", 9);
            pos.put("recordedAt", t0.plus(i * 5L, ChronoUnit.MINUTES).toString());
            mvc.perform(post("/api/v1/telemetry/positions").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("deviceId", "IMEI-RT", "key", key, "position", pos))))
                    .andExpect(status().isOk());
        }
        JsonNode viagens = send(get("/api/v1/assets/" + assetId + "/trips"), null, 200).get("content");
        assertThat(viagens).isNotEmpty();
        String tripId = viagens.get(0).get("id").asText();
        jdbcTemplate.update("update trips set route_id = ? where id = ?", routeId, tripId);

        JsonNode comp = send(get("/api/v1/routes/" + routeId + "/comparison"), null, 200);
        assertThat(comp).hasSize(1);
        JsonNode c = comp.get(0);
        assertThat(c.get("assetTag").asText()).isEqualTo("RT-1");
        assertThat(c.get("track").size()).isGreaterThan(1);           // o percurso real, para desenhar
        assertThat(c.get("pathGeojson").asText()).contains("LineString"); // e o previsto, por baixo
        assertThat(c.get("actualDistanceKm").asDouble()).isGreaterThan(0.0);
        assertThat(c.hasNonNull("distanceDeltaKm")).isTrue();
        // Andou muito mais do que os 10 km previstos: passa a tolerância de 10 %.
        assertThat(c.get("outOfTolerance").asBoolean()).isTrue();
    }

    @Test
    void aMesmaRotaServeVariasViaturasEAUltimaNaoSeRetira() throws Exception {
        String tipo = send(get("/api/v1/asset-types"), null, 200).get(0).get("id").asText();
        String outro = send(post("/api/v1/assets"),
                Map.of("tag", "RT-2", "name", "Camião 2", "assetTypeId", tipo), 201).get("id").asText();
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Luanda — Lobito");
        rota.put("originLabel", "Luanda");
        rota.put("destinationLabel", "Lobito");
        rota.put("assetId", assetId);
        String routeId = send(post("/api/v1/routes"), rota, 201).get("id").asText();

        send(post("/api/v1/routes/" + routeId + "/assignments"),
                Map.of("assetId", outro, "plannedFor", "2026-10-05"), 201);
        JsonNode lista = send(get("/api/v1/routes"), null, 200);
        JsonNode aRota = null;
        for (JsonNode x : lista) {
            if (x.get("id").asText().equals(routeId)) {
                aRota = x;
            }
        }
        assertThat(aRota).isNotNull();
        assertThat(aRota.get("assignments")).hasSize(2);
        List<String> tags = List.of(aRota.get("assignments").get(0).get("assetTag").asText(),
                aRota.get("assignments").get(1).get("assetTag").asText());
        assertThat(tags).containsExactlyInAnyOrder("RT-1", "RT-2");
    }
}
