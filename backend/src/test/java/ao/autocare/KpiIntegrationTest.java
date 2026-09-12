package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class KpiIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void computesMtbfMttrAndAvailabilityFromFailuresRepairsAndDowntime() throws Exception {
        bearer = register("kpi1@teste.ao").bearer();
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Retroescavadora"), 201)
                .get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "RE-001", "name", "Retro", "assetTypeId", typeId), 201)
                .get("id").asText();

        // 200 h de operação no período (1000 -> 1200)
        postJson("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings",
                Map.of("value", 1000, "readingAt", Instant.now().minus(2, ChronoUnit.DAYS).toString()), 201);
        postJson("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings",
                Map.of("value", 1200), 201);

        // uma avaria + reparação de 3 h
        JsonNode wo = postJson("/api/v1/work-orders", Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Avaria hidráulica",
                "failure", Map.of("description", "Fuga no cilindro")), 201);
        String woId = wo.get("id").asText();
        Instant start = Instant.now().minus(4, ChronoUnit.HOURS);
        postJson("/api/v1/work-orders/" + woId + "/start", Map.of("startedAt", start.toString()), 200);
        postJson("/api/v1/work-orders/" + woId + "/complete",
                Map.of("completedAt", start.plus(3, ChronoUnit.HOURS).toString(),
                        "resolution", "Cilindro reparado"), 200);

        JsonNode kpi = getJson("/api/v1/kpis?assetId=" + assetId);

        org.assertj.core.api.Assertions.assertThat(kpi.get("failures").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(kpi.get("repairs").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(kpi.get("operatingHours").asDouble()).isEqualTo(200.0);

        var metrics = kpi.get("metrics");
        JsonNode mtbf = findMetric(metrics, "mtbf");
        JsonNode mttr = findMetric(metrics, "mttr");
        org.assertj.core.api.Assertions.assertThat(mtbf.get("value").asDouble()).isEqualTo(200.0); // 200h / 1 falha
        org.assertj.core.api.Assertions.assertThat(mtbf.get("meetsTarget").asBoolean()).isFalse();  // < 500
        org.assertj.core.api.Assertions.assertThat(mttr.get("value").asDouble()).isEqualTo(3.0);
        org.assertj.core.api.Assertions.assertThat(mttr.get("meetsTarget").asBoolean()).isTrue();   // <= 4
    }

    private JsonNode findMetric(JsonNode metrics, String key) {
        for (JsonNode m : metrics) {
            if (m.get("key").asText().equals(key)) return m;
        }
        throw new AssertionError("métrica não encontrada: " + key);
    }

    @Test
    void dashboardSummarisesFleetState() throws Exception {
        bearer = register("kpi2@teste.ao").bearer();
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Máquina"), 201).get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "M-1", "name", "Máquina 1", "assetTypeId", typeId, "initialMeterValue", 500), 201)
                .get("id").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/assets/" + assetId + "/criticality")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("productionImpact", 5, "safetyImpact", 5, "financialImpact", 5))))
                .andExpect(status().isOk());

        // plano com tarefa vencida
        String planId = postJson("/api/v1/maintenance-plans", Map.of("name", "P",
                "tasks", List.of(Map.of("title", "T", "triggers", List.of(
                        Map.of("type", "METER_INTERVAL", "meterKind", "HOURMETER", "interval", 100))))), 201)
                .get("id").asText();
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans",
                Map.of("planId", planId, "startFromNow", true), 201);
        postJson("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings", Map.of("value", 650), 201);

        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetsTotal").value(1))
                .andExpect(jsonPath("$.assetsCritical").value(1))
                .andExpect(jsonPath("$.tasksOverdue").value(1))
                .andExpect(jsonPath("$.upcoming", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.kpis.metrics", org.hamcrest.Matchers.hasSize(4)));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/kpis")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }
}
