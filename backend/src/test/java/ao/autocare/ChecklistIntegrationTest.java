package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ChecklistIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private String postJson(String url, Object body, int expect) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expect)).andReturn();
        return r.getResponse().getContentAsString();
    }

    private String newAsset() throws Exception {
        String typeId = json.readTree(postJson("/api/v1/asset-types",
                Map.of("name", "Retroescavadora"), 201)).get("id").asText();
        return json.readTree(postJson("/api/v1/assets",
                Map.of("tag", "RE-001", "name", "Retroescavadora", "assetTypeId", typeId), 201))
                .get("id").asText();
    }

    private static final List<Map<String, Object>> DAILY_ITEMS = List.of(
            Map.of("text", "Nível do óleo do motor", "verification", "VERIFY"),
            Map.of("text", "Nível do líquido de arrefecimento", "verification", "VERIFY"),
            Map.of("text", "Vazamentos (óleo, combustível, hidráulico)", "verification", "INSPECT"),
            Map.of("text", "Travões", "verification", "TEST", "critical", true),
            Map.of("text", "Alarme de marcha atrás", "verification", "TEST"),
            Map.of("text", "Extintor de incêndio", "verification", "VERIFY", "critical", true));

    @Test
    void createsDailyInspectionTemplateWithVerificationTypes() throws Exception {
        bearer = register("chk1@teste.ao").bearer();

        JsonNode t = json.readTree(postJson("/api/v1/checklist-templates", Map.of(
                "name", "Inspeção diária (antes do arranque)",
                "estimatedMinutes", 15,
                "items", DAILY_ITEMS), 201));

        org.assertj.core.api.Assertions.assertThat(t.get("items")).hasSize(6);
        org.assertj.core.api.Assertions.assertThat(t.get("estimatedMinutes").asInt()).isEqualTo(15);
        org.assertj.core.api.Assertions.assertThat(t.get("items").get(3).get("verification").asText())
                .isEqualTo("TEST");
        org.assertj.core.api.Assertions.assertThat(t.get("items").get(3).get("critical").asBoolean())
                .isTrue();
    }

    @Test
    void recordsExecutionFromTemplateAllOkThenWithIssue() throws Exception {
        bearer = register("chk2@teste.ao").bearer();
        String assetId = newAsset();
        String templateId = json.readTree(postJson("/api/v1/checklist-templates", Map.of(
                "name", "Inspeção diária", "estimatedMinutes", 15, "items", DAILY_ITEMS), 201))
                .get("id").asText();

        // execução sem itens -> parte do modelo, tudo OK
        JsonNode ok = json.readTree(postJson(
                "/api/v1/assets/" + assetId + "/checklist-executions",
                Map.of("templateId", templateId, "meterValue", 1240), 201));
        org.assertj.core.api.Assertions.assertThat(ok.get("outcome").asText()).isEqualTo("OK");
        org.assertj.core.api.Assertions.assertThat(ok.get("itemsOk").asInt()).isEqualTo(6);

        // execução com um item NOT_OK -> outcome ISSUES
        JsonNode issues = json.readTree(postJson(
                "/api/v1/assets/" + assetId + "/checklist-executions",
                Map.of("templateId", templateId, "meterValue", 1252,
                        "items", List.of(
                                Map.of("text", "Nível do óleo do motor", "verification", "VERIFY", "result", "OK"),
                                Map.of("text", "Travões", "verification", "TEST", "critical", true,
                                        "result", "NOT_OK", "note", "Curso do pedal longo"))), 201));
        org.assertj.core.api.Assertions.assertThat(issues.get("outcome").asText()).isEqualTo("ISSUES");
        org.assertj.core.api.Assertions.assertThat(issues.get("itemsNotOk").asInt()).isEqualTo(1);

        mvc.perform(get("/api/v1/assets/" + assetId + "/checklist-executions")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].outcome").value("ISSUES"));
    }

    @Test
    void updateTemplateReplacesItems() throws Exception {
        bearer = register("chk3@teste.ao").bearer();
        String id = json.readTree(postJson("/api/v1/checklist-templates",
                Map.of("name", "X", "items", List.of(Map.of("text", "A"), Map.of("text", "B"))), 201))
                .get("id").asText();

        mvc.perform(put("/api/v1/checklist-templates/" + id).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "X2",
                                "items", List.of(Map.of("text", "C"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("X2"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].text").value("C"));
    }

    @Test
    void rejectsTemplateWithoutItems() throws Exception {
        bearer = register("chk4@teste.ao").bearer();
        postJson("/api/v1/checklist-templates", Map.of("name", "Vazio", "items", List.of()), 400);
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        bearer = register("chkA@teste.ao", "Empresa A").bearer();
        postJson("/api/v1/checklist-templates",
                Map.of("name", "Da Empresa A", "items", List.of(Map.of("text", "item"))), 201);

        String other = register("chkB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/checklist-templates").header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
