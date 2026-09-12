package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class AssetPhotoIntegrationTest extends AbstractIntegrationTest {

    // PNG 1x1 válido
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private String bearer;

    private String newAsset() throws Exception {
        String typeId = json.readTree(mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Retroescavadora"))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        return json.readTree(mvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("tag", "RE-001", "name", "Retroescavadora", "assetTypeId", typeId))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode upload(String assetId, String kind, byte[] bytes, String filename, String ct) throws Exception {
        var req = multipart("/api/v1/assets/" + assetId + "/photos")
                .file(new MockMultipartFile("file", filename, ct, bytes))
                .header("Authorization", bearer);
        if (kind != null) req.param("kind", kind);
        MvcResult res = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void uploadsPhotoServesItAndMarksFirstAsPrimary() throws Exception {
        bearer = register("photo1@teste.ao").bearer();
        String assetId = newAsset();

        JsonNode p = upload(assetId, "PLATE", PNG, "matricula.png", "image/png");
        org.assertj.core.api.Assertions.assertThat(p.get("primary").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(p.get("kind").asText()).isEqualTo("PLATE");
        org.assertj.core.api.Assertions.assertThat(p.get("width").asInt()).isEqualTo(1);
        String url = p.get("url").asText();
        org.assertj.core.api.Assertions.assertThat(url).contains("/api/v1/files/").contains("sig=");

        // o URL assinado serve os bytes sem autenticação
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(r -> org.assertj.core.api.Assertions.assertThat(
                        r.getResponse().getContentType()).startsWith("image/png"));

        // aparece na ficha do ativo
        mvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", bearer))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.primaryPhotoUrl").isNotEmpty());
    }

    @Test
    void secondPhotoNotPrimaryUntilSet() throws Exception {
        bearer = register("photo2@teste.ao").bearer();
        String assetId = newAsset();
        upload(assetId, null, PNG, "a.png", "image/png");
        JsonNode second = upload(assetId, "DAMAGE", PNG, "b.png", "image/png");
        org.assertj.core.api.Assertions.assertThat(second.get("primary").asBoolean()).isFalse();

        mvc.perform(patch("/api/v1/assets/" + assetId + "/photos/" + second.get("id").asText())
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("primary", true, "caption", "Fissura no braço"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.caption").value("Fissura no braço"));

        mvc.perform(get("/api/v1/assets/" + assetId + "/photos").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].caption").value("Fissura no braço"));
    }

    @Test
    void rejectsUnsupportedFormat() throws Exception {
        bearer = register("photo3@teste.ao").bearer();
        String assetId = newAsset();
        mvc.perform(multipart("/api/v1/assets/" + assetId + "/photos")
                        .file(new MockMultipartFile("file", "notas.txt", "text/plain", "olá".getBytes()))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesPhotoAndPromotesNextToPrimary() throws Exception {
        bearer = register("photo4@teste.ao").bearer();
        String assetId = newAsset();
        String firstId = upload(assetId, null, PNG, "a.png", "image/png").get("id").asText();
        upload(assetId, null, PNG, "b.png", "image/png");

        mvc.perform(delete("/api/v1/assets/" + assetId + "/photos/" + firstId).header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/assets/" + assetId + "/photos").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void fileUrlRejectsBadSignature() throws Exception {
        bearer = register("photo5@teste.ao").bearer();
        String assetId = newAsset();
        String url = upload(assetId, null, PNG, "a.png", "image/png").get("url").asText();
        String tampered = url.replaceAll("sig=.*", "sig=123.abc");
        mvc.perform(get(tampered)).andExpect(status().isForbidden());
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        bearer = register("photoA@teste.ao", "Empresa A").bearer();
        String assetId = newAsset();
        upload(assetId, null, PNG, "a.png", "image/png");

        String bearerB = register("photoB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/assets/" + assetId + "/photos").header("Authorization", bearerB))
                .andExpect(status().isNotFound());
    }
}
