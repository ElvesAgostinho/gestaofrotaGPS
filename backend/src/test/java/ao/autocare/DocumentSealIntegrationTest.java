package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O selo de autenticidade: cada PDF emitido leva um código; quem o recebe
 * verifica, sem conta, que foi emitido por esta empresa e que o ficheiro
 * não foi alterado. E o registo de auditoria sai em ficheiro.
 */
class DocumentSealIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern CODIGO = Pattern.compile("([A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4})");

    private String bearer;
    private String omId;
    private String assetId;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private byte[] pdf(String caminho) throws Exception {
        return mvc.perform(get(caminho).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("selo@teste.ao", "Transportes Selo, Lda.").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(post("/api/v1/assets"), Map.of("tag", "SL-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
        omId = send(post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "CORRECTIVE", "title", "Farol"), 201)
                .get("id").asText();
    }

    @Test
    void aOrdemImpressaLevaCodigoQueSeVerificaSemConta() throws Exception {
        byte[] pdf = pdf("/api/v1/work-orders/" + omId + "/print.pdf");
        String t = texto(pdf);
        assertThat(t).contains("Código de verificação").contains("/verificar/");
        Matcher m = CODIGO.matcher(t);
        assertThat(m.find()).isTrue();
        String code = m.group(1);

        // Sem conta: o código existe, é desta empresa, desta ordem.
        JsonNode v = json.readTree(mvc.perform(get("/api/v1/public/verify/" + code))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(v.get("found").asBoolean()).isTrue();
        assertThat(v.get("organization").asText()).isEqualTo("Transportes Selo, Lda.");
        assertThat(v.get("kindLabel").asText()).isEqualTo("Ordem de manutenção");
        assertThat(v.get("reference").asText()).startsWith("OM-");
        assertThat(v.get("issuedBy").asText()).isEqualTo("Utilizador Teste");
        assertThat(v.has("fileMatches")).isFalse();

        // Com o ficheiro: é exatamente o que saiu daqui.
        JsonNode com = json.readTree(mvc.perform(multipart("/api/v1/public/verify/" + code.toLowerCase())
                        .file(new MockMultipartFile("file", "om.pdf", "application/pdf", pdf)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(com.get("fileMatches").asBoolean()).isTrue();

        // Um ficheiro alterado — nem que seja um byte — não bate.
        byte[] alterado = pdf.clone();
        alterado[alterado.length / 2] ^= 0x01;
        JsonNode falso = json.readTree(mvc.perform(multipart("/api/v1/public/verify/" + code)
                        .file(new MockMultipartFile("file", "om.pdf", "application/pdf", alterado)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(falso.get("fileMatches").asBoolean()).isFalse();

        // Um código inventado não existe.
        JsonNode nada = json.readTree(mvc.perform(get("/api/v1/public/verify/ZZZZ-ZZZZ-ZZZZ"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(nada.get("found").asBoolean()).isFalse();
    }

    @Test
    void oHistoricoDaViaturaTambemLevaSelo() throws Exception {
        String t = texto(pdf("/api/v1/assets/" + assetId + "/history.pdf"));
        Matcher m = CODIGO.matcher(t);
        assertThat(m.find()).isTrue();
        JsonNode v = json.readTree(mvc.perform(get("/api/v1/public/verify/" + m.group(1)))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(v.get("kindLabel").asText()).isEqualTo("Histórico de manutenção");
        assertThat(v.get("reference").asText()).isEqualTo("SL-1");
    }

    @Test
    void oRegistoDeAuditoriaSaiEmFicheiro() throws Exception {
        String csv = mvc.perform(get("/api/v1/reports/audit.csv").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Data e hora;Utilizador;Ação").contains("work_order.create").contains("Utilizador Teste");
        String pdfTexto = texto(pdf("/api/v1/reports/audit.pdf"));
        assertThat(pdfTexto).contains("Registo de auditoria").contains("asset.create");
    }
}
