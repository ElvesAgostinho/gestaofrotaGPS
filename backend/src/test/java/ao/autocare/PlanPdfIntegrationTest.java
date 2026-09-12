package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class PlanPdfIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode postJson(String url, Object body, int expect) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @Test
    void generatesMaintenancePlanPdfForAsset() throws Exception {
        bearer = register("pdf1@teste.ao").bearer();

        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Retroescavadora"), 201)
                .get("id").asText();
        String assetId = postJson("/api/v1/assets", Map.of(
                "tag", "RE-001", "name", "Retroescavadora", "assetTypeId", typeId,
                "model", "BL71B", "serialNumber", "VCE0BL71C00012345", "modelYear", 2023,
                "responsibleLabel", "Departamento de Manutenção",
                "objective", "Garantir a máxima disponibilidade e vida útil.",
                "initialMeterValue", 1240), 201).get("id").asText();

        mvc.perform(put("/api/v1/assets/" + assetId + "/criticality").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("productionImpact", 4, "safetyImpact", 5, "financialImpact", 5))))
                .andExpect(status().isOk());

        postJson("/api/v1/checklist-templates", Map.of(
                "name", "Inspeção diária (antes do arranque)", "estimatedMinutes", 15,
                "items", List.of(
                        Map.of("text", "Nível do óleo do motor", "verification", "VERIFY"),
                        Map.of("text", "Travões", "verification", "TEST", "critical", true))), 201);

        String planId = postJson("/api/v1/maintenance-plans", Map.of(
                "name", "Plano preventivo BL71B",
                "notes", "Utilizar apenas peças originais Volvo.",
                "tasks", List.of(
                        Map.of("systemName", "Lubrificação", "title", "Lubrificar articulações e pinos",
                                "tools", "Bomba de massa, massa EP2",
                                "triggers", List.of(Map.of("type", "METER_INTERVAL",
                                        "meterKind", "HOURMETER", "interval", 50))),
                        Map.of("systemName", "Motor", "title", "Trocar óleo do motor e filtro",
                                "triggers", List.of(Map.of("type", "METER_INTERVAL",
                                        "meterKind", "HOURMETER", "interval", 250))),
                        Map.of("systemName", "Sistema Hidráulico", "title", "Trocar filtro hidráulico",
                                "triggers", List.of(Map.of("type", "METER_INTERVAL",
                                        "meterKind", "HOURMETER", "interval", 500))))), 201)
                .get("id").asText();
        postJson("/api/v1/assets/" + assetId + "/maintenance-plans", Map.of("planId", planId), 201);

        MvcResult res = mvc.perform(get("/api/v1/assets/" + assetId + "/maintenance-plan.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdf = res.getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(pdf.length).isGreaterThan(3000);
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void pdfWorksEvenWithoutPlanOrChecklist() throws Exception {
        bearer = register("pdf2@teste.ao").bearer();
        String typeId = postJson("/api/v1/asset-types", Map.of("name", "Gerador"), 201).get("id").asText();
        String assetId = postJson("/api/v1/assets",
                Map.of("tag", "GER-1", "name", "Gerador", "assetTypeId", typeId), 201).get("id").asText();

        MvcResult res = mvc.perform(get("/api/v1/assets/" + assetId + "/maintenance-plan.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(
                new String(res.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/assets/x/maintenance-plan.pdf")).andExpect(status().isUnauthorized());
    }
}
