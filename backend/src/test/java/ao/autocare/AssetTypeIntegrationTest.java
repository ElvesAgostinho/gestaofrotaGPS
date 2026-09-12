package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class AssetTypeIntegrationTest extends AbstractIntegrationTest {

    private JsonNode create(String token, Map<String, Object> payload) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void createsWithEightStandardSystemsFromReferenceDocument() throws Exception {
        String token = register("at1@teste.ao").token();

        JsonNode type = create(token, Map.of("name", "Retroescavadora", "category", "MACHINE"));

        assertThatSystems(type);
        // primeiro sistema é o motor
        org.assertj.core.api.Assertions.assertThat(type.get("systems").get(0).get("code").asText())
                .isEqualTo("ENGINE");
        org.assertj.core.api.Assertions.assertThat(type.get("primaryMeter").asText())
                .isEqualTo("HOURMETER");
    }

    private void assertThatSystems(JsonNode type) {
        org.assertj.core.api.Assertions.assertThat(type.get("systems")).hasSize(8);
    }

    @Test
    void canOptOutOfStandardSystemsAndProvideOwn() throws Exception {
        String token = register("at2@teste.ao").token();

        JsonNode generator = create(token, Map.of(
                "name", "Gerador Diesel",
                "category", "GENERATOR",
                "useStandardSystems", false,
                "systems", List.of(
                        Map.of("code", "ENGINE", "name", "Motor"),
                        Map.of("code", "ALTERNATOR", "name", "Alternador"),
                        Map.of("code", "CONTROL", "name", "Painel de Controlo"))));

        org.assertj.core.api.Assertions.assertThat(generator.get("systems")).hasSize(3);
        org.assertj.core.api.Assertions.assertThat(generator.get("category").asText())
                .isEqualTo("GENERATOR");
    }

    @Test
    void rejectsDuplicateName() throws Exception {
        String token = register("at3@teste.ao").token();
        create(token, Map.of("name", "Camião"));

        mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "camião"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listsAndDeletes() throws Exception {
        String token = register("at4@teste.ao").token();
        String id = create(token, Map.of("name", "Empilhadora")).get("id").asText();

        mvc.perform(get("/api/v1/asset-types").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/v1/asset-types/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        String tokenA = register("atA@teste.ao", "Empresa A").token();
        String tokenB = register("atB@teste.ao", "Empresa B").token();
        create(tokenA, Map.of("name", "Máquina A"));

        mvc.perform(get("/api/v1/asset-types").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
