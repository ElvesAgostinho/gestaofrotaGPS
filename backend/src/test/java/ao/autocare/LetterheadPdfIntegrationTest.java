package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Os impressos levam o timbre da empresa do cliente, não a marca do software.
 *
 * <p>Lê-se o texto de cada PDF gerado: o nome da empresa e o NIF têm de lá
 * estar, e «FLEETOS» não. Um teste que só verificasse «é um PDF» deixaria
 * passar exatamente o cabeçalho errado que existia.
 */
class LetterheadPdfIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String assetId;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private String textoDoPdf(String caminho) throws Exception {
        MvcResult r = mvc.perform(get(caminho).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .contains("application/pdf"))
                .andReturn();
        try (PDDocument doc = PDDocument.load(r.getResponse().getContentAsByteArray())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("timbre@teste.ao", "Construções Kwanza, Lda.").bearer();

        Map<String, Object> empresa = new HashMap<>();
        empresa.put("taxId", "5401234567");
        empresa.put("address", "Rua da Samba, 120");
        empresa.put("city", "Luanda");
        empresa.put("phone", "+244 923 000 000");
        empresa.put("email", "geral@kwanza.ao");
        send(patch("/api/v1/organization"), empresa, 200);

        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "CAM-001", "name", "Camião Volvo FH", "assetTypeId", typeId,
                "plate", "LD-12-34-AB", "acquisitionValue", 85000000), 201).get("id").asText();
    }

    @Test
    void aEmpresaGuardaOTimbre() throws Exception {
        JsonNode org = send(get("/api/v1/organization"), null, 200);
        assertThat(org.get("taxId").asText()).isEqualTo("5401234567");
        assertThat(org.get("city").asText()).isEqualTo("Luanda");
        assertThat(org.has("logoUrl")).isFalse();
    }

    @Test
    void aFichaDeEquipamentoSaiComOTimbreESemAMarcaDoSoftwareNoCabecalho() throws Exception {
        String texto = textoDoPdf("/api/v1/assets/" + assetId + "/sheet.pdf");
        assertThat(texto).contains("Construções Kwanza, Lda.");
        assertThat(texto).contains("NIF 5401234567");
        assertThat(texto).contains("Rua da Samba");
        assertThat(texto).contains("CAM-001").contains("LD-12-34-AB");
        assertThat(texto).doesNotContain("FLEETOS");
        // A marca do software fica no rodapé, pequena — como em qualquer ERP.
        assertThat(texto).contains("Gerado por IMBONDEIRO OS");
    }

    @Test
    void aOrdemDeServicoSaiComOTimbre() throws Exception {
        String id = send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Fuga de óleo"), 201)
                .get("id").asText();
        String texto = textoDoPdf("/api/v1/work-orders/" + id + "/print.pdf");
        assertThat(texto).contains("Construções Kwanza, Lda.").contains("NIF 5401234567");
        assertThat(texto).contains("Fuga de óleo");
        assertThat(texto).doesNotContain("FLEETOS");
    }

    @Test
    void aGuiaDeTransporteSaiComOTimbreEAsLinhas() throws Exception {
        Map<String, Object> guia = new HashMap<>();
        guia.put("assetId", assetId);
        guia.put("originLabel", "Armazém de Luanda");
        guia.put("destinationLabel", "Obra do Lobito");
        guia.put("customerName", "Cimentos do Sul");
        guia.put("items", List.of(Map.of(
                "description", "Cimento Portland 42,5", "quantity", 200, "unit", "saco",
                "weightKg", 10000, "packages", 200)));
        // O POST das guias responde 200 (e a convencao desse controlador).
        String id = send(post("/api/v1/transport-notes"), guia, 200).get("id").asText();

        String texto = textoDoPdf("/api/v1/transport-notes/" + id + "/print.pdf");
        assertThat(texto).contains("Construções Kwanza, Lda.");
        // O CSS põe o tipo em maiúsculas; o texto extraído vem assim.
        assertThat(texto.toUpperCase()).contains("GUIA DE TRANSPORTE");
        assertThat(texto).contains("GT-");
        assertThat(texto).contains("Armazém de Luanda").contains("Obra do Lobito");
        assertThat(texto).contains("Cimento Portland").contains("Cimentos do Sul");
        assertThat(texto).contains("O condutor");
    }

    @Test
    void oPlanoSaiComOTimbre() throws Exception {
        String texto = textoDoPdf("/api/v1/assets/" + assetId + "/maintenance-plan.pdf");
        assertThat(texto).contains("Construções Kwanza, Lda.").contains("NIF 5401234567");
    }

    @Test
    void oLogotipoEntraNoImpresso() throws Exception {
        // Um PNG a sério, gerado aqui: 60×30, amarelo.
        BufferedImage img = new BufferedImage(60, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 60; x++) {
            for (int y = 0; y < 30; y++) {
                img.setRGB(x, y, 0xF5A800);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);

        MvcResult r = mvc.perform(multipart("/api/v1/organization/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", png.toByteArray()))
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        JsonNode org = json.readTree(r.getResponse().getContentAsString());
        assertThat(org.get("logoUrl").asText()).contains("/api/v1/files/");
        // O URL tem de responder: um logótipo que se «guarda» e depois dá 404
        // é o erro que já cá esteve.
        String url = org.get("logoUrl").asText();
        String caminho = url.substring(url.indexOf("/api/v1/files/"));
        mvc.perform(get(caminho)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).contains("image/png"));

        // O PDF continua a sair (com a imagem embutida) e o texto continua lá.
        byte[] pdf = mvc.perform(get("/api/v1/assets/" + assetId + "/sheet.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }

        // Remover deixa de o mandar.
        send(delete("/api/v1/organization/logo"), null, 200);
        assertThat(send(get("/api/v1/organization"), null, 200).has("logoUrl")).isFalse();
    }

    @Test
    void oLogotipoTemDeSerImagemPequena() throws Exception {
        mvc.perform(multipart("/api/v1/organization/logo")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[10]))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/organization/logo")
                        .file(new MockMultipartFile("file", "x.png", "image/png",
                                new byte[3 * 1024 * 1024]))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oTecnicoImprimeAFichaSemValores() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "tec-pdf@teste.ao");
        body.put("role", "TECHNICIAN");
        String token = send(post("/api/v1/team/invitations"), body, 201).get("token").asText();
        MvcResult acc = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Zé", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn();
        String tecnico = "Bearer " + json.readTree(acc.getResponse().getContentAsString())
                .get("accessToken").asText();

        byte[] pdf = mvc.perform(get("/api/v1/assets/" + assetId + "/sheet.pdf")
                        .header("Authorization", tecnico))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texto;
        try (PDDocument doc = PDDocument.load(pdf)) {
            texto = new PDFTextStripper().getText(doc);
        }
        assertThat(texto).contains("CAM-001");
        assertThat(texto).doesNotContain("Valor de aquisição");
        assertThat(texto).doesNotContain("85");
    }
}
