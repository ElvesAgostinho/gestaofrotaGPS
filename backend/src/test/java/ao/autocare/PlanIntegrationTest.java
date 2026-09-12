package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class PlanIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode postJson(String url, Object body, int expect) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private String newAssetAt(double hours) throws Exception {
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Retroescavadora"), 201)
                .get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "RE-001", "name", "Retroescavadora", "assetTypeId", typeId,
                "initialMeterValue", hours), 201).get("id").asText();
        return assetId;
    }

    private Object meterTrigger(int interval) {
        return Map.of("type", "METER_INTERVAL", "meterKind", "HOURMETER", "interval", interval);
    }

    private Map<String, Object> planBody() {
        return Map.of(
                "name", "Plano preventivo — Retroescavadora BL71B",
                "description", "8 sistemas · 50/250/500/1000/2000 h",
                "tasks", List.of(
                        Map.of("systemCode", "ENGINE", "systemName", "Motor",
                                "title", "Lubrificar articulações e pinos",
                                "triggers", List.of(meterTrigger(50))),
                        Map.of("systemCode", "ENGINE", "systemName", "Motor",
                                "title", "Trocar óleo do motor e filtro de óleo",
                                "tools", "Chave de filtro, tabuleiro, óleo 15W40",
                                "triggers", List.of(meterTrigger(250)),
                                "parts", List.of(Map.of("name", "Filtro de óleo", "quantity", 1),
                                                 Map.of("name", "Óleo 15W40", "quantity", 12, "unit", "L"))),
                        Map.of("systemCode", "HYDRAULIC", "systemName", "Sistema Hidráulico",
                                "title", "Trocar filtro hidráulico de retorno",
                                "triggers", List.of(meterTrigger(500)))));
    }

    @Test
    void createsHourBasedPlanWithTasksTriggersAndParts() throws Exception {
        bearer = register("plan1@teste.ao").bearer();
        JsonNode plan = postJson("/api/v1/maintenance-plans", planBody(), 201);

        assertThatTasks(plan, 3);
        JsonNode oil = plan.get("tasks").get(1);
        org.assertj.core.api.Assertions.assertThat(oil.get("title").asText()).contains("óleo do motor");
        org.assertj.core.api.Assertions.assertThat(oil.get("triggers").get(0).get("interval").asInt()).isEqualTo(250);
        org.assertj.core.api.Assertions.assertThat(oil.get("parts")).hasSize(2);
    }

    private void assertThatTasks(JsonNode plan, int n) {
        org.assertj.core.api.Assertions.assertThat(plan.get("tasks")).hasSize(n);
    }

    @Test
    void assignsPlanToAssetAndComputesNextDue() throws Exception {
        bearer = register("plan2@teste.ao").bearer();
        String assetId = newAssetAt(1000);
        String planId = postJson("/api/v1/maintenance-plans", planBody(), 201).get("id").asText();

        JsonNode assigned = postJson("/api/v1/assets/" + assetId + "/maintenance-plans",
                Map.of("planId", planId, "startFromNow", true), 201);

        org.assertj.core.api.Assertions.assertThat(assigned.get("tasks")).hasSize(3);
        JsonNode lube = assigned.get("tasks").get(0);   // lubrificação 50h
        org.assertj.core.api.Assertions.assertThat(lube.get("nextDueMeter").asInt()).isEqualTo(1050);
        org.assertj.core.api.Assertions.assertThat(lube.get("status").asText()).isEqualTo("OK");
    }

    @Test
    void meterReadingMakesTaskOverdueThenCompletingResetsTheClock() throws Exception {
        bearer = register("plan3@teste.ao").bearer();
        String assetId = newAssetAt(1000);
        String planId = postJson("/api/v1/maintenance-plans", planBody(), 201).get("id").asText();
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans",
                Map.of("planId", planId, "startFromNow", true), 201);

        // leitura sobe 60 h -> a lubrificação (50 h) fica vencida
        postJson("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings",
                Map.of("value", 1060), 201);

        JsonNode plans = json.readTree(mvc.perform(get("/api/v1/assets/" + assetId + "/maintenance-plans")
                        .header("Authorization", bearer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode lube = plans.get(0).get("tasks").get(0);
        org.assertj.core.api.Assertions.assertThat(lube.get("status").asText()).isEqualTo("OVERDUE");
        org.assertj.core.api.Assertions.assertThat(lube.get("remainingMeter").asInt()).isEqualTo(-10);
        org.assertj.core.api.Assertions.assertThat(plans.get(0).get("overdue").asInt()).isEqualTo(1);

        // marcar como executada às 1060 h
        String taskStateId = lube.get("id").asText();
        JsonNode done = postJson("/api/v1/assets/" + assetId + "/plan-tasks/" + taskStateId + "/complete",
                Map.of("meterValue", 1060, "performedByLabel", "Equipa de manutenção",
                        "notes", "Massa EP2 aplicada"), 200);

        JsonNode lubeAfter = done.get("plan").get("tasks").get(0);
        org.assertj.core.api.Assertions.assertThat(lubeAfter.get("status").asText()).isEqualTo("OK");
        org.assertj.core.api.Assertions.assertThat(lubeAfter.get("nextDueMeter").asInt()).isEqualTo(1110);
        org.assertj.core.api.Assertions.assertThat(lubeAfter.get("lastDoneMeter").asInt()).isEqualTo(1060);

        mvc.perform(get("/api/v1/assets/" + assetId + "/plan-task-completions")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].performedByLabel").value("Equipa de manutenção"));
    }

    @Test
    void cannotAssignSamePlanTwice() throws Exception {
        bearer = register("plan4@teste.ao").bearer();
        String assetId = newAssetAt(0);
        String planId = postJson("/api/v1/maintenance-plans", planBody(), 201).get("id").asText();
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans", Map.of("planId", planId), 201);
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans", Map.of("planId", planId), 409);
    }

    @Test
    void rejectsMeterTriggerWithoutMeterKind() throws Exception {
        bearer = register("plan5@teste.ao").bearer();
        postJson("/api/v1/maintenance-plans", Map.of(
                "name", "Mau plano",
                "tasks", List.of(Map.of("title", "X",
                        "triggers", List.of(Map.of("type", "METER_INTERVAL", "interval", 100))))),
                400);
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        bearer = register("planA@teste.ao", "Empresa A").bearer();
        postJson("/api/v1/maintenance-plans", planBody(), 201);
        String other = register("planB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/maintenance-plans").header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
