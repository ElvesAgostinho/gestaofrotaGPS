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

    /**
     * Numa viatura, o contador é o odómetro: o MTBF tem de vir em quilómetros.
     *
     * <p>Antes somava-se o contador fosse ele qual fosse e chamava-se «horas»
     * ao resultado — um camião com 12 000 km aparecia com um MTBF de «12 000 h»
     * e cumpria a meta de 500 h por acidente. Um indicador que engana é pior do
     * que não ter indicador nenhum.
     */
    @Test
    void oMtbfDeUmaViaturaVemEmQuilometrosENaoEmHoras() throws Exception {
        bearer = register("kpi4@teste.ao").bearer();
        String typeId = postJson("/api/v1/asset-types",
                Map.of("name", "Camião basculante", "category", "VEHICLE", "primaryMeter", "ODOMETER"),
                201).get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "CAM-001", "name", "Camião", "assetTypeId", typeId), 201).get("id").asText();

        // 12 000 km no período
        postJson("/api/v1/assets/" + assetId + "/meters/ODOMETER/readings",
                Map.of("value", 120_000, "readingAt", Instant.now().minus(2, ChronoUnit.DAYS).toString()), 201);
        postJson("/api/v1/assets/" + assetId + "/meters/ODOMETER/readings",
                Map.of("value", 132_000), 201);

        JsonNode wo = postJson("/api/v1/work-orders", Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Avaria na embraiagem",
                "failure", Map.of("description", "Embraiagem a patinar")), 201);
        String woId = wo.get("id").asText();
        Instant start = Instant.now().minus(4, ChronoUnit.HOURS);
        postJson("/api/v1/work-orders/" + woId + "/start", Map.of("startedAt", start.toString()), 200);
        postJson("/api/v1/work-orders/" + woId + "/complete",
                Map.of("completedAt", start.plus(2, ChronoUnit.HOURS).toString(),
                        "resolution", "Kit substituído"), 200);

        JsonNode kpi = getJson("/api/v1/kpis?assetId=" + assetId);
        org.assertj.core.api.Assertions.assertThat(kpi.get("operatingKm").asDouble()).isEqualTo(12_000.0);
        org.assertj.core.api.Assertions.assertThat(kpi.get("operatingHours").asDouble()).isZero();

        JsonNode mtbfKm = findMetric(kpi.get("metrics"), "mtbf_km");
        org.assertj.core.api.Assertions.assertThat(mtbfKm.get("value").asDouble()).isEqualTo(12_000.0);
        org.assertj.core.api.Assertions.assertThat(mtbfKm.get("unit").asText()).isEqualTo("km");
        org.assertj.core.api.Assertions.assertThat(mtbfKm.get("meetsTarget").asBoolean()).isFalse();

        // E não se mostra um MTBF em horas para quem não tem horímetro.
        org.assertj.core.api.Assertions.assertThat(temMetrica(kpi.get("metrics"), "mtbf")).isFalse();
    }

    private boolean temMetrica(JsonNode metrics, String key) {
        for (JsonNode m : metrics) {
            if (m.get("key").asText().equals(key)) return true;
        }
        return false;
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
