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
 * O painel abre com «o que está mal hoje»: viatura parada, documento
 * caducado, aparelho calado, ordem fora do prazo — cada um com o link para
 * resolver. Uma empresa sem problemas vê zero, e isso é verdade, não omissão.
 */
class TodayIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode grupo(JsonNode hoje, String key) {
        for (JsonNode g : hoje.get("groups")) {
            if (g.get("key").asText().equals(key)) {
                return g;
            }
        }
        return null;
    }

    @Test
    void semProblemasOPainelDizZeroEComProblemasListaCadaUmComLink() throws Exception {
        bearer = register("hoje@teste.ao").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of("tag", "HJ-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();

        JsonNode limpo = send(get("/api/v1/dashboard"), null, 200).get("today");
        assertThat(limpo.get("total").asInt()).isZero();
        assertThat(limpo.get("groups")).isEmpty();

        // Documento caducado há 3 dias.
        Map<String, Object> doc = new HashMap<>();
        doc.put("title", "Seguro");
        doc.put("kind", "INSURANCE");
        doc.put("expiresAt", Instant.now().minus(3, ChronoUnit.DAYS).toString());
        send(post("/api/v1/assets/" + assetId + "/documents"), doc, 201);

        // Aparelho registado que nunca comunicou.
        send(post("/api/v1/gps-devices"), Map.of("externalId", "IMEI-HJ", "assetId", assetId, "name", "GT06 cabine"), 201);

        // Ordem corretiva iniciada: a viatura fica parada; prazo já passado.
        JsonNode om = send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Não arranca", "priority", "URGENT",
                "dueAt", Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                "failure", Map.of("description", "Motor de arranque", "systemCode", "ELECTRICAL")), 201);
        send(post("/api/v1/work-orders/" + om.get("id").asText() + "/start"), null, 200);

        JsonNode hoje = send(get("/api/v1/dashboard"), null, 200).get("today");
        assertThat(hoje.get("total").asInt()).isEqualTo(4);

        JsonNode paradas = grupo(hoje, "down");
        assertThat(paradas.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(paradas.get("items").get(0).get("assetTag").asText()).isEqualTo("HJ-1");
        assertThat(paradas.get("items").get(0).get("link").asText()).isEqualTo("/ativos/" + assetId);

        JsonNode docs = grupo(hoje, "documents");
        assertThat(docs.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(docs.get("items").get(0).get("title").asText()).isEqualTo("Seguro · Seguro");
        assertThat(docs.get("items").get(0).get("detail").asText()).startsWith("Caducado há");

        JsonNode gps = grupo(hoje, "gps");
        assertThat(gps.get("items").get(0).get("title").asText()).isEqualTo("GT06 cabine");
        assertThat(gps.get("items").get(0).get("detail").asText()).isEqualTo("Nunca comunicou");

        JsonNode atrasadas = grupo(hoje, "late_orders");
        assertThat(atrasadas.get("items").get(0).get("title").asText()).contains("Não arranca");
        assertThat(atrasadas.get("items").get(0).get("detail").asText()).contains("Prazo passou há 2");
        assertThat(atrasadas.get("items").get(0).get("link").asText()).isEqualTo("/ordens/" + om.get("id").asText());
    }
}
