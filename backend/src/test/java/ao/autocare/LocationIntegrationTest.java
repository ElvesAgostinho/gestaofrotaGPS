package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class LocationIntegrationTest extends AbstractIntegrationTest {

    private JsonNode createLocation(String token, Map<String, Object> payload) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/locations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/locations")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsAndBuildsTree() throws Exception {
        String token = register("loc1@teste.ao").token();

        JsonNode site = createLocation(token, Map.of(
                "name", "Obra Luanda Sul", "kind", "SITE", "code", "OBR-01"));
        String siteId = site.get("id").asText();

        createLocation(token, Map.of(
                "name", "Parque de Máquinas", "kind", "YARD", "parentId", siteId));

        mvc.perform(get("/api/v1/locations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/v1/locations/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Obra Luanda Sul"))
                .andExpect(jsonPath("$[0].children", hasSize(1)))
                .andExpect(jsonPath("$[0].children[0].name").value("Parque de Máquinas"));
    }

    @Test
    void updatesAndDeletes() throws Exception {
        String token = register("loc2@teste.ao").token();
        String id = createLocation(token, Map.of("name", "Armazém", "kind", "WAREHOUSE"))
                .get("id").asText();

        mvc.perform(patch("/api/v1/locations/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Armazém Central"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Armazém Central"));

        mvc.perform(delete("/api/v1/locations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/locations/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void blocksDeletionWhenSublocationsExist() throws Exception {
        String token = register("loc3@teste.ao").token();
        String parentId = createLocation(token, Map.of("name", "Sede")).get("id").asText();
        createLocation(token, Map.of("name", "Piso 1", "parentId", parentId));

        mvc.perform(delete("/api/v1/locations/" + parentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sublocais")));
    }

    @Test
    void isolatesLocationsBetweenOrganizations() throws Exception {
        String tokenA = register("orgA@teste.ao", "Empresa A").token();
        String tokenB = register("orgB@teste.ao", "Empresa B").token();

        String idA = createLocation(tokenA, Map.of("name", "Local da Empresa A")).get("id").asText();

        mvc.perform(get("/api/v1/locations").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/v1/locations/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsParentCycle() throws Exception {
        String token = register("loc4@teste.ao").token();
        String aId = createLocation(token, Map.of("name", "A")).get("id").asText();
        String bId = createLocation(token, Map.of("name", "B", "parentId", aId)).get("id").asText();

        // tentar tornar A filho de B (ciclo)
        mvc.perform(patch("/api/v1/locations/" + aId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("parentId", bId))))
                .andExpect(status().isBadRequest());
    }
}
