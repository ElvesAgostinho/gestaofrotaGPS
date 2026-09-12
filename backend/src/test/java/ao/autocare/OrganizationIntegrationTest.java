package ao.autocare;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OrganizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void showsCurrentOrganisationWithCounts() throws Exception {
        String token = register("org1@teste.ao", "Terraplanagens do Sul, Lda.").token();

        // um tipo de ativo + um ativo + um local
        String typeId = json.readTree(mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Escavadora"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(post("/api/v1/assets").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("tag", "ESC-1", "name", "Escavadora 1", "assetTypeId", typeId))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/locations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Estaleiro"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/organization").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Terraplanagens do Sul, Lda."))
                .andExpect(jsonPath("$.type").value("COMPANY"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.assetCount").value(1))
                .andExpect(jsonPath("$.assetTypeCount").value(1))
                .andExpect(jsonPath("$.locationCount").value(1))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void ownerCanRename() throws Exception {
        String token = register("org2@teste.ao", "Nome Antigo").token();

        mvc.perform(patch("/api/v1/organization").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nome Novo, Lda."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Novo, Lda."));

        mvc.perform(get("/api/v1/organization").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.name").value("Nome Novo, Lda."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/organization")).andExpect(status().isUnauthorized());
    }
}
