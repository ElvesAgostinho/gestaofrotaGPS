package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O mesmo relatório em CSV, Excel e PDF — e a «pasta da viatura» em PDF.
 *
 * <p>O Excel é lido como o que é (um zip com XML) e o PDF é lido de volta com
 * o pdfbox: não chega o ficheiro abrir, tem de ter lá os dados certos.
 */
class ExportFormatsIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
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

    private MvcResult ficheiro(String caminho, String tipoEsperado) throws Exception {
        MvcResult r = mvc.perform(get(caminho).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentType()).contains(tipoEsperado);
        return r;
    }

    private String textoDoPdf(String caminho) throws Exception {
        MvcResult r = ficheiro(caminho, "application/pdf");
        try (PDDocument doc = PDDocument.load(r.getResponse().getContentAsByteArray())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /** O conteúdo de sheet1.xml dentro do .xlsx. */
    private static String folhaDoExcel(byte[] xlsx) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("o .xlsx não tem xl/worksheets/sheet1.xml");
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("export@teste.ao", "Transportes Cunene, Lda.").bearer();
        String typeId = send(post("/api/v1/asset-types"),
                Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201).get("id").asText();
        Map<String, Object> ativo = new HashMap<>();
        ativo.put("tag", "CN-01");
        ativo.put("name", "Camião Scania R450");
        ativo.put("assetTypeId", typeId);
        ativo.put("plate", "LD-45-67-AB");
        ativo.put("initialMeterValue", 120_000);
        assetId = send(post("/api/v1/assets"), ativo, 201).get("id").asText();

        // Uma ordem concluída, com resolução: é o que a pasta guarda.
        String om = send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Fuga de óleo no motor",
                "description", "Mancha de óleo debaixo da viatura"), 201).get("id").asText();
        send(post("/api/v1/work-orders/" + om + "/start"), Map.of("stopAsset", true), 200);
        send(post("/api/v1/work-orders/" + om + "/complete"),
                Map.of("resolution", "Vedante do cárter substituído", "meterValue", 120_450), 200);
    }

    @Test
    void oMesmoRelatorioSaiEmCsvExcelEPdf() throws Exception {
        // CSV — como sempre
        MvcResult csv = ficheiro("/api/v1/reports/assets.csv", "text/csv");
        String textoCsv = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(textoCsv).contains("Etiqueta;Nome").contains("CN-01;Camião Scania R450");
        assertThat(csv.getResponse().getHeader("Content-Disposition")).contains(".csv");

        // Excel — um zip válido, com o cabeçalho e a linha
        MvcResult xlsx = ficheiro("/api/v1/reports/assets.xlsx", "spreadsheetml");
        assertThat(xlsx.getResponse().getHeader("Content-Disposition")).contains("ativos-").contains(".xlsx");
        String folha = folhaDoExcel(xlsx.getResponse().getContentAsByteArray());
        assertThat(folha).contains("<t xml:space=\"preserve\">Etiqueta</t>")
                .contains("CN-01").contains("Camião Scania R450").contains("<autoFilter");

        // PDF — com o timbre da empresa e os dados
        String pdf = textoDoPdf("/api/v1/reports/assets.pdf");
        assertThat(pdf).contains("Transportes Cunene").contains("Inventário de ativos")
                .contains("CN-01").contains("Scania");

        // Uma extensão inventada não existe
        mvc.perform(get("/api/v1/reports/assets.docx").header("Authorization", bearer))
                .andExpect(status().is(404));
    }

    @Test
    void asOrdensSaemEmExcelComNumerosComoNumeros() throws Exception {
        MvcResult xlsx = ficheiro("/api/v1/reports/work-orders.xlsx", "spreadsheetml");
        String folha = folhaDoExcel(xlsx.getResponse().getContentAsByteArray());
        assertThat(folha).contains("Fuga de óleo no motor");
        // As datas vão como datas (estilo 3, número de série do Excel), não como texto
        assertThat(folha).containsPattern("<c r=\"K2\" s=\"3\"><v>4[0-9]{4}");
    }

    @Test
    void aPastaDaViaturaTemAsOrdensFeitas() throws Exception {
        String texto = textoDoPdf("/api/v1/assets/" + assetId + "/history.pdf");
        assertThat(texto)
                .contains("Transportes Cunene")
                .containsIgnoringCase("Histórico de manutenção")
                .contains("Camião Scania R450")
                .contains("LD-45-67-AB")
                .contains("Fuga de óleo no motor")
                .contains("Vedante do cárter substituído")
                .containsPattern("120[\s  ]450 km")
                .contains("1")            // uma ordem
                .contains("ordens de manutenção");
    }

    @Test
    void oContadorLidoAoFecharAOrdemAtualizaAFicha() throws Exception {
        // A ordem do setUp fechou aos 120 450 km; o ativo tinha 120 000.
        JsonNode ativo = send(get("/api/v1/assets/" + assetId), null, 200);
        assertThat(ativo.get("meters").get(0).get("currentValue").asDouble()).isEqualTo(120_450.0);
        // e a leitura fica no histórico do contador, marcada como vinda da ordem
        JsonNode leituras = send(get("/api/v1/assets/" + assetId + "/meters/ODOMETER/readings"), null, 200);
        assertThat(leituras.get("content").get(0).get("source").asText()).isEqualTo("WORK_ORDER");

        // Um valor abaixo do contador atual não recua nada.
        String om = send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Farol partido"), 201).get("id").asText();
        send(post("/api/v1/work-orders/" + om + "/start"), Map.of("stopAsset", false), 200);
        send(post("/api/v1/work-orders/" + om + "/complete"),
                Map.of("resolution", "Trocado", "meterValue", 100_000), 200);
        ativo = send(get("/api/v1/assets/" + assetId), null, 200);
        assertThat(ativo.get("meters").get(0).get("currentValue").asDouble()).isEqualTo(120_450.0);
    }

    @Test
    void aPastaSemOrdensDizQueNaoHa() throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Gerador"), 201).get("id").asText();
        String novo = send(post("/api/v1/assets"),
                Map.of("tag", "GR-9", "name", "Gerador novo", "assetTypeId", typeId), 201).get("id").asText();
        String texto = textoDoPdf("/api/v1/assets/" + novo + "/history.pdf");
        assertThat(texto).contains("Ainda não há ordens de manutenção");
    }

    @Test
    void quemNaoVeCustosNaoOsRecebeNaPasta() throws Exception {
        String token = send(post("/api/v1/team/invitations"),
                Map.of("email", "mecanico@teste.ao", "role", "TECHNICIAN"), 201).get("token").asText();
        JsonNode res = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mecânico", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(s -> {
                    try { return json.readTree(s); } catch (Exception e) { throw new RuntimeException(e); }
                });
        String mecanico = "Bearer " + res.get("accessToken").asText();
        MvcResult r = mvc.perform(get("/api/v1/assets/" + assetId + "/history.pdf")
                        .header("Authorization", mecanico))
                .andExpect(status().isOk()).andReturn();
        String texto;
        try (PDDocument doc = PDDocument.load(r.getResponse().getContentAsByteArray())) {
            texto = new PDFTextStripper().getText(doc);
        }
        assertThat(texto).contains("Fuga de óleo").doesNotContain("custo total de manutenção");
        // O dono vê o custo
        assertThat(textoDoPdf("/api/v1/assets/" + assetId + "/history.pdf"))
                .contains("custo total de manutenção");
    }
}
