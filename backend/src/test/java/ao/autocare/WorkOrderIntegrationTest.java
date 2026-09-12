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

class WorkOrderIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    ao.autocare.modules.workorder.WorkOrderService woService;

    private String bearer;

    private JsonNode postJson(String url, Object body, int expect) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : json.writeValueAsString(body)))
                .andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private String assetWithDuePlan() throws Exception {
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Retroescavadora"), 201)
                .get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "RE-001", "name", "Retroescavadora", "assetTypeId", typeId,
                "initialMeterValue", 1000), 201).get("id").asText();
        String planId = postJson("/api/v1/maintenance-plans", Map.of(
                "name", "Plano",
                "tasks", List.of(
                        Map.of("systemName", "Motor", "title", "Trocar óleo do motor",
                                "triggers", List.of(Map.of("type", "METER_INTERVAL",
                                        "meterKind", "HOURMETER", "interval", 250))))), 201)
                .get("id").asText();
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans",
                Map.of("planId", planId, "startFromNow", true), 201);
        // subir o horímetro para vencer a tarefa
        postJson("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings", Map.of("value", 1260), 201);
        return assetId;
    }

    @Test
    void generatesPreventiveWorkOrderFromDuePlanTasksAndNumbersIt() throws Exception {
        bearer = register("wo1@teste.ao").bearer();
        String assetId = assetWithDuePlan();

        JsonNode wo = postJson("/api/v1/work-orders/from-due", Map.of("assetId", assetId), 201);
        // OM-2026-000001: a numeracao passou a levar o ano, para o numero
        // dizer alguma coisa sobre o volume de ordens do ano.
        org.assertj.core.api.Assertions.assertThat(wo.get("number").asText()).matches("OM-\\d{4}-\\d{6}");
        org.assertj.core.api.Assertions.assertThat(wo.get("type").asText()).isEqualTo("PREVENTIVE");
        org.assertj.core.api.Assertions.assertThat(wo.get("tasks")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(wo.get("priority").asText()).isEqualTo("HIGH");
    }

    @Test
    void fullLifecycle_start_labor_parts_complete_resetsPlanClock() throws Exception {
        bearer = register("wo2@teste.ao").bearer();
        String assetId = assetWithDuePlan();

        // peça + armazém + stock
        String partId = postJson("/api/v1/parts",
                Map.of("name", "Filtro de óleo", "unit", "un"), 201).get("id").asText();
        String whId = postJson("/api/v1/warehouses", Map.of("name", "Armazém"), 201).get("id").asText();
        postJson("/api/v1/stock/movements", Map.of(
                "partId", partId, "warehouseId", whId, "type", "IN", "quantity", 5, "unitCost", 10000), 201);

        String woId = postJson("/api/v1/work-orders/from-due", Map.of("assetId", assetId), 201)
                .get("id").asText();

        postJson("/api/v1/work-orders/" + woId + "/start", Map.of("stopAsset", true), 200);
        postJson("/api/v1/work-orders/" + woId + "/labor",
                Map.of("technicianLabel", "José", "hours", 2.5), 200);
        postJson("/api/v1/work-orders/" + woId + "/parts",
                Map.of("partId", partId, "warehouseId", whId, "quantity", 1), 200);

        JsonNode done = postJson("/api/v1/work-orders/" + woId + "/complete",
                Map.of("resolution", "Óleo e filtro trocados", "meterValue", 1260), 200);
        org.assertj.core.api.Assertions.assertThat(done.get("status").asText()).isEqualTo("DONE");
        org.assertj.core.api.Assertions.assertThat(done.get("totalLaborHours").asDouble()).isEqualTo(2.5);

        // stock desceu para 4
        JsonNode part = getJson("/api/v1/parts/" + partId);
        org.assertj.core.api.Assertions.assertThat(part.get("totalQuantity").asInt()).isEqualTo(4);

        // a tarefa do plano voltou a OK, próximo vencimento 1260 + 250 = 1510
        JsonNode plans = getJson("/api/v1/assets/" + assetId + "/maintenance-plans");
        JsonNode task = plans.get(0).get("tasks").get(0);
        org.assertj.core.api.Assertions.assertThat(task.get("status").asText()).isEqualTo("OK");
        org.assertj.core.api.Assertions.assertThat(task.get("nextDueMeter").asInt()).isEqualTo(1510);

        // ativo voltou a operacional
        JsonNode asset = getJson("/api/v1/assets/" + assetId);
        org.assertj.core.api.Assertions.assertThat(asset.get("status").asText()).isEqualTo("OPERATIONAL");

        // verificação
        postJson("/api/v1/work-orders/" + woId + "/verify", null, 200);
        org.assertj.core.api.Assertions.assertThat(
                getJson("/api/v1/work-orders/" + woId).get("status").asText()).isEqualTo("VERIFIED");
    }

    @Test
    void correctiveWorkOrderWithFailureMarksAssetDown() throws Exception {
        bearer = register("wo3@teste.ao").bearer();
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Gerador"), 201).get("id").asText();
        String assetId = postJson("/api/v1/assets",
                Map.of("tag", "GER-1", "name", "Gerador", "assetTypeId", typeId), 201).get("id").asText();

        JsonNode wo = postJson("/api/v1/work-orders", Map.of(
                "assetId", assetId, "type", "CORRECTIVE",
                "title", "Não arranca", "priority", "URGENT",
                "failure", Map.of("description", "Motor de arranque queimado", "systemCode", "ELECTRICAL")),
                201);
        postJson("/api/v1/work-orders/" + wo.get("id").asText() + "/start", null, 200);

        JsonNode asset = getJson("/api/v1/assets/" + assetId);
        org.assertj.core.api.Assertions.assertThat(asset.get("status").asText()).isEqualTo("DOWN");

        postJson("/api/v1/work-orders/" + wo.get("id").asText() + "/complete",
                Map.of("resolution", "Motor de arranque substituído"), 200);
        org.assertj.core.api.Assertions.assertThat(
                getJson("/api/v1/assets/" + assetId).get("status").asText()).isEqualTo("OPERATIONAL");
    }

    @Test
    void listsAndFiltersByStatus() throws Exception {
        bearer = register("wo4@teste.ao").bearer();
        String assetId = assetWithDuePlan();
        postJson("/api/v1/work-orders/from-due", Map.of("assetId", assetId), 201);

        mvc.perform(get("/api/v1/work-orders").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/work-orders?status=OPEN").header("Authorization", bearer))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/v1/work-orders?status=DONE").header("Authorization", bearer))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void autoGeneratePreventiveCreatesOncePerAsset() throws Exception {
        bearer = register("wo5@teste.ao").bearer();
        String assetId = assetWithDuePlan();

        boolean first = woService.autoGeneratePreventive(assetId);
        boolean second = woService.autoGeneratePreventive(assetId);

        org.assertj.core.api.Assertions.assertThat(first).isTrue();
        org.assertj.core.api.Assertions.assertThat(second).isFalse(); // já existe uma aberta

        mvc.perform(get("/api/v1/work-orders").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title",
                        org.hamcrest.Matchers.containsString("automática")));
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        bearer = register("woA@teste.ao", "Empresa A").bearer();
        String assetId = assetWithDuePlan();
        String woId = postJson("/api/v1/work-orders/from-due", Map.of("assetId", assetId), 201)
                .get("id").asText();

        String other = register("woB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/work-orders/" + woId).header("Authorization", other))
                .andExpect(status().isNotFound());
    }
}
