package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pneus: montar, medir, rodar, desmontar — e o custo por quilómetro que sai
 * daí. É a pergunta que a compra de pneus faz: que marca dura mais?
 */
class TyreIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String camiao;

    private JsonNode send(String b, MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", b);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        return send(bearer, req, body, expect);
    }

    private Map<String, Object> pneu(String posicao, String marca, double custo) {
        Map<String, Object> m = new HashMap<>();
        m.put("position", posicao);
        m.put("brand", marca);
        m.put("model", "Regional");
        m.put("size", "315/80 R22.5");
        m.put("cost", custo);
        m.put("targetPressure", 8.5);
        m.put("lastTreadMm", 16);
        return m;
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("pneus@teste.ao", "Transportes Pneu").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201)
                .get("id").asText();
        camiao = send(post("/api/v1/assets"), Map.of("tag", "CM-1", "name", "Camião", "assetTypeId", tipo,
                "initialMeterValue", 100_000), 201).get("id").asText();
    }

    @Test
    void montarMedirDesmontarEOCustoPorKm() throws Exception {
        JsonNode t = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("FE", "Michelin", 180_000), 201);
        assertThat(t.get("position").asText()).isEqualTo("FE");
        assertThat(t.get("installedMeter").asDouble()).isEqualTo(100_000.0); // contador atual do camião
        assertThat(t.get("status").asText()).isEqualTo("INSTALLED");
        assertThat(t.get("distanceRun").asDouble()).isEqualTo(0.0);
        assertThat(t.has("alert")).isFalse();
        String id = t.get("id").asText();

        // A mesma posição não leva dois pneus.
        JsonNode erro = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("fe", "Bridgestone", 150_000), 409);
        assertThat(erro.get("message").asText()).contains("FE");

        // O camião anda 40 000 km.
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"), Map.of("value", 140_000), 201);
        JsonNode lista = send(get("/api/v1/assets/" + camiao + "/tyres"), null, 200);
        assertThat(lista.get(0).get("distanceRun").asDouble()).isEqualTo(40_000.0);
        assertThat(lista.get(0).get("costPerUnit").asDouble()).isEqualTo(4.5); // 180 000 / 40 000

        // Medição: sulco abaixo do mínimo dispara o alerta.
        JsonNode d = send(post("/api/v1/tyres/" + id + "/readings"), Map.of("treadMm", 2.5, "pressure", 8.2), 201);
        assertThat(d.get("tyre").get("lastTreadMm").asDouble()).isEqualTo(2.5);
        assertThat(d.get("tyre").get("alert").asText()).contains("Sulco abaixo do mínimo");
        assertThat(d.get("readings")).hasSize(1);

        // Pressão a 60 % do alvo também avisa.
        d = send(post("/api/v1/tyres/" + id + "/readings"), Map.of("treadMm", 12, "pressure", 5.0), 201);
        assertThat(d.get("tyre").get("alert").asText()).contains("Pressão baixa");

        // Desmontar por desgaste: fim de vida, com o custo fechado.
        JsonNode fim = send(post("/api/v1/tyres/" + id + "/remove"), Map.of("reason", "WORN", "retire", true), 200);
        assertThat(fim.get("status").asText()).isEqualTo("RETIRED");
        assertThat(fim.get("removedMeter").asDouble()).isEqualTo(140_000.0);
        assertThat(fim.get("costPerUnit").asDouble()).isEqualTo(4.5);
        // e a posição fica livre
        send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("FE", "Bridgestone", 150_000), 201);
    }

    @Test
    void rotacaoTrocaAsPosicoes() throws Exception {
        String a = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("TE1", "Michelin", 1), 201).get("id").asText();
        String b = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("TD1", "Michelin", 1), 201).get("id").asText();
        JsonNode r = send(post("/api/v1/tyres/" + a + "/rotate"), Map.of("otherTyreId", b), 200);
        assertThat(r.get(0).get("position").asText()).isEqualTo("TD1");
        assertThat(r.get(1).get("position").asText()).isEqualTo("TE1");
    }

    @Test
    void paraStockPerdeAPosicaoEPodeVoltar() throws Exception {
        String a = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("FD", "Pirelli", 120_000), 201).get("id").asText();
        JsonNode s = send(post("/api/v1/tyres/" + a + "/remove"),
                Map.of("reason", "RETREAD", "retire", false, "note", "Para recauchutar"), 200);
        assertThat(s.get("status").asText()).isEqualTo("STOCK");
        assertThat(s.has("position")).isFalse();
        assertThat(send(get("/api/v1/tyres"), null, 200)).hasSize(1);
    }

    @Test
    void semPermissaoDeCustosNaoVeOCusto() throws Exception {
        send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("FE", "Michelin", 180_000), 201);
        String token = send(post("/api/v1/team/invitations"), Map.of("email", "viewer@t.ao", "role", "VIEWER"), 201)
                .get("token").asText();
        JsonNode res = json.readTree(mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "V", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String viewer = "Bearer " + res.get("accessToken").asText();
        JsonNode lista = send(viewer, get("/api/v1/assets/" + camiao + "/tyres"), null, 200);
        assertThat(lista.get(0).has("cost")).isFalse();
        assertThat(lista.get(0).get("brand").asText()).isEqualTo("Michelin");
        // e não monta pneus
        send(viewer, post("/api/v1/assets/" + camiao + "/tyres"), pneu("FD", "X", 1), 403);
    }

    @Test
    void medicoesForaDeGamaSaoRecusadas() throws Exception {
        String a = send(post("/api/v1/assets/" + camiao + "/tyres"), pneu("FE", "Michelin", 1), 201).get("id").asText();
        send(post("/api/v1/tyres/" + a + "/readings"), Map.of("treadMm", 99), 400);
        send(post("/api/v1/tyres/" + a + "/readings"), Map.of(), 400);
        send(patch("/api/v1/tyres/" + a), Map.of("brand", "Michelin", "model", "X Multi"), 200);
    }
}
