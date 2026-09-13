package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.budget.BudgetService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Orçamento anual: o previsto contra o real que sai das ordens e dos
 * abastecimentos — e o aviso aos 80 % e aos 100 %.
 */
class BudgetIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BudgetService budgets;

    private String bearer;
    private String camiao;
    private String filial;
    private final int ano = LocalDate.now().getYear();

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
        bearer = register("orcamento@teste.ao", "Transportes Orçamento").bearer();
        Map<String, Object> loc = new HashMap<>();
        loc.put("name", "Filial de Benguela");
        loc.put("kind", "BRANCH");
        filial = send(post("/api/v1/locations"), loc, 201).get("id").asText();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201)
                .get("id").asText();
        Map<String, Object> a = new HashMap<>();
        a.put("tag", "CM-1");
        a.put("name", "Camião");
        a.put("assetTypeId", tipo);
        a.put("locationId", filial);
        a.put("initialMeterValue", 1000);
        camiao = send(post("/api/v1/assets"), a, 201).get("id").asText();

        // Uma ordem concluída de 300 000 e um abastecimento de 50 000.
        String om = send(post("/api/v1/work-orders"), Map.of("assetId", camiao, "type", "CORRECTIVE", "title", "Travões"), 201)
                .get("id").asText();
        send(post("/api/v1/work-orders/" + om + "/start"), Map.of("stopAsset", false), 200);
        send(post("/api/v1/work-orders/" + om + "/parts"), Map.of("partName", "Pastilhas", "quantity", 2, "unitCost", 150_000), 200);
        send(post("/api/v1/work-orders/" + om + "/complete"), Map.of("resolution", "Feito"), 200);
        Map<String, Object> fuel = new HashMap<>();
        fuel.put("liters", 200);
        fuel.put("totalCost", 50_000);
        fuel.put("meterValue", 1500);
        send(post("/api/v1/assets/" + camiao + "/fuel"), fuel, 201);
    }

    @Test
    void previstoContraRealPorViaturaFilialEFrota() throws Exception {
        send(post("/api/v1/budgets"), Map.of("scope", "ASSET", "assetId", camiao, "category", "MAINTENANCE", "amount", 1_000_000), 201);
        send(post("/api/v1/budgets"), Map.of("scope", "LOCATION", "locationId", filial, "category", "TOTAL", "amount", 400_000), 201);
        send(post("/api/v1/budgets"), Map.of("scope", "ORG", "category", "FUEL", "amount", 60_000), 201);

        JsonNode r = send(get("/api/v1/budgets"), null, 200);
        assertThat(r.get("year").asInt()).isEqualTo(ano);
        JsonNode itens = r.get("items");
        assertThat(itens).hasSize(3);
        Map<String, JsonNode> por = new HashMap<>();
        for (JsonNode b : itens) por.put(b.get("scope").asText(), b);

        assertThat(por.get("ASSET").get("actual").asDouble()).isEqualTo(300_000.0);
        assertThat(por.get("ASSET").get("percent").asInt()).isEqualTo(30);
        assertThat(por.get("ASSET").get("target").asText()).isEqualTo("CM-1");

        assertThat(por.get("LOCATION").get("actual").asDouble()).isEqualTo(350_000.0); // 300 000 + 50 000
        assertThat(por.get("LOCATION").get("percent").asInt()).isEqualTo(88);
        assertThat(por.get("LOCATION").get("status").asText()).isEqualTo("WARNING");

        assertThat(por.get("ORG").get("actual").asDouble()).isEqualTo(50_000.0);
        assertThat(por.get("ORG").get("percent").asInt()).isEqualTo(83);

        // O mesmo âmbito e categoria no mesmo ano não se repete.
        send(post("/api/v1/budgets"), Map.of("scope", "ORG", "category", "FUEL", "amount", 1), 409);
    }

    @Test
    void avisaAos80EAos100UmaVezPorMarco() throws Exception {
        String id = send(post("/api/v1/budgets"), Map.of("scope", "ORG", "category", "TOTAL", "amount", 400_000), 201)
                .get("id").asText();
        // 350 000 de 400 000 = 88 % → aviso dos 80 %
        assertThat(budgets.notifyOverruns()).isEqualTo(1);
        assertThat(budgets.notifyOverruns()).isZero();
        JsonNode avisos = send(get("/api/v1/notifications?size=20"), null, 200);
        assertThat(avisos.toString()).contains("Orçamento a 88 %");

        // Mais 100 000 de combustível → 112 % → aviso dos 100 %, uma vez.
        Map<String, Object> fuel = new HashMap<>();
        fuel.put("liters", 400);
        fuel.put("totalCost", 100_000);
        fuel.put("meterValue", 2500);
        send(post("/api/v1/assets/" + camiao + "/fuel"), fuel, 201);
        assertThat(budgets.notifyOverruns()).isEqualTo(1);
        assertThat(budgets.notifyOverruns()).isZero();
        assertThat(send(get("/api/v1/notifications?size=20"), null, 200).toString()).contains("Orçamento esgotado");

        JsonNode b = send(get("/api/v1/budgets"), null, 200).get("items").get(0);
        assertThat(b.get("id").asText()).isEqualTo(id);
        assertThat(b.get("status").asText()).isEqualTo("EXCEEDED");
        assertThat(b.get("remaining").asDouble()).isEqualTo(-50_000.0);
    }

    @Test
    void quemNaoVeCustosNaoVeOrcamentos() throws Exception {
        String token = send(post("/api/v1/team/invitations"), Map.of("email", "mec@t.ao", "role", "TECHNICIAN"), 201)
                .get("token").asText();
        JsonNode res = json.readTree(mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "M", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        mvc.perform(get("/api/v1/budgets").header("Authorization", "Bearer " + res.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }
}
