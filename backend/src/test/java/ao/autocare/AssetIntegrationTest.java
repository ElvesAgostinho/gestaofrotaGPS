package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AssetIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private String assetType(String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "category", "MACHINE"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode createAsset(Map<String, Object> payload) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void createsBackhoeLoaderLikeReferenceDocument() throws Exception {
        bearer = register("asset1@teste.ao").bearer();
        String typeId = assetType("Retroescavadora");

        JsonNode asset = createAsset(Map.ofEntries(
                Map.entry("tag", "RE-001"),
                Map.entry("name", "Retroescavadora"),
                Map.entry("assetTypeId", typeId),
                Map.entry("manufacturer", "Volvo"),
                Map.entry("model", "BL71B"),
                Map.entry("serialNumber", "VCE0BL71C00012345"),
                Map.entry("modelYear", 2023),
                Map.entry("responsibleLabel", "Departamento de Manutenção"),
                Map.entry("objective", "Garantir a máxima disponibilidade e vida útil."),
                Map.entry("initialMeterValue", 1250)));

        assert asset.get("model").asText().equals("BL71B");
        // medidor principal criado automaticamente a partir do tipo (horímetro)
        org.assertj.core.api.Assertions.assertThat(asset.get("meters")).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(asset.get("meters").get(0).get("kind").asText())
                .isEqualTo("HOURMETER");
        org.assertj.core.api.Assertions.assertThat(asset.get("meters").get(0).get("currentValue").asDouble())
                .isEqualTo(1250.0);
    }

    @Test
    void rejectsDuplicateTag() throws Exception {
        bearer = register("asset2@teste.ao").bearer();
        String typeId = assetType("Camião");
        createAsset(Map.of("tag", "CAM-1", "name", "Camião 1", "assetTypeId", typeId));

        mvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("tag", "cam-1", "name", "Outro", "assetTypeId", typeId))))
                .andExpect(status().isConflict());
    }

    @Test
    void setsCriticalityMatrixAndComputesOverall() throws Exception {
        bearer = register("asset3@teste.ao").bearer();
        String typeId = assetType("Gerador");
        String id = createAsset(Map.of("tag", "GER-1", "name", "Gerador 1", "assetTypeId", typeId))
                .get("id").asText();

        mvc.perform(put("/api/v1/assets/" + id + "/criticality")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "productionImpact", 5,
                                "safetyImpact", 5,
                                "financialImpact", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("CRITICAL"))
                .andExpect(jsonPath("$.overallManual").value(false));

        mvc.perform(get("/api/v1/assets/" + id + "/criticality").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionImpact").value(5));
    }

    @Test
    void listsPaginatedAndArchives() throws Exception {
        bearer = register("asset4@teste.ao").bearer();
        String typeId = assetType("Empilhadora");
        String id = createAsset(Map.of("tag", "EMP-1", "name", "Empilhadora 1", "assetTypeId", typeId))
                .get("id").asText();

        mvc.perform(get("/api/v1/assets").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tag").value("EMP-1"));

        mvc.perform(post("/api/v1/assets/" + id + "/archive").header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/assets").header("Authorization", bearer))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/v1/assets?archived=true").header("Authorization", bearer))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        String bearerA = register("assetA@teste.ao", "Empresa A").bearer();
        bearer = bearerA;
        String typeId = assetType("Máquina A");
        String idA = createAsset(Map.of("tag", "A-1", "name", "Ativo A", "assetTypeId", typeId))
                .get("id").asText();

        String bearerB = register("assetB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/assets/" + idA).header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void technicianDoesNotSeeMoneyOnTheAssetSheet() throws Exception {
        bearer = register("asset-dinheiro@teste.ao").bearer();
        String typeId = assetType("Camião");
        String id = createAsset(Map.of(
                "tag", "CAM-9", "name", "Camião", "assetTypeId", typeId,
                "acquisitionValue", 85000000, "downtimeCostPerHour", 120000))
                .get("id").asText();

        // O dono vê o valor.
        mvc.perform(get("/api/v1/assets/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acquisitionValue").value(85000000));

        // O técnico entra e pede a mesma ficha.
        MvcResult inv = mvc.perform(post("/api/v1/team/invitations")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "tec-dinheiro@teste.ao", "role", "TECHNICIAN"))))
                .andExpect(status().isCreated()).andReturn();
        String token = json.readTree(inv.getResponse().getContentAsString()).get("token").asText();
        MvcResult acc = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Zé", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn();
        String tecnico = "Bearer "
                + json.readTree(acc.getResponse().getContentAsString()).get("accessToken").asText();

        // A regra do cliente é uma só: sem permissão de custos, sem valores.
        // Ausente, não a zero — zero seria um valor, e um valor errado.
        mvc.perform(get("/api/v1/assets/" + id).header("Authorization", tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag").value("CAM-9"))
                .andExpect(jsonPath("$.acquisitionValue").doesNotExist())
                .andExpect(jsonPath("$.downtimeCostPerHour").doesNotExist());
    }
}
