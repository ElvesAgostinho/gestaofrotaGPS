package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Anexos, impressão e relatórios de manutenção.
 *
 * <p>São as três pontas que faltavam ao módulo. A que mais importa é a
 * impressão: um documento que sai da oficina sem assinatura não serve de nada,
 * e um que mostra valores a quem não os pode ver é uma fuga.
 */
class MaintenanceDocsIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String assetId;
    private String workOrderId;

    private JsonNode send(String token, MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (token != null) {
            req = req.header("Authorization", token);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        return send(bearer, req, body, expect);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("docs@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"),
                Map.of("name", "Camiao", "primaryMeter", "ODOMETER"), 201).get("id").asText();

        Map<String, Object> ativo = new HashMap<>();
        ativo.put("tag", "CAM-300");
        ativo.put("name", "Volvo FH");
        ativo.put("assetTypeId", typeId);
        ativo.put("plate", "LD-45-67-AB");
        ativo.put("initialMeterValue", 120_000);
        ativo.put("downtimeCostPerHour", 8_000);
        assetId = send(post("/api/v1/assets"), ativo, 201).get("id").asText();

        Map<String, Object> w = new HashMap<>();
        w.put("assetId", assetId);
        w.put("type", "CORRECTIVE");
        w.put("title", "Vibra ao travar");
        w.put("systemCode", "TRAVAGEM");
        workOrderId = send(post("/api/v1/work-orders"), w, 201).get("id").asText();
    }

    private MockMultipartFile ficheiro(String nome, String tipo) {
        return new MockMultipartFile("file", nome, tipo, "conteudo de teste".getBytes());
    }

    // ---- anexos -------------------------------------------------------------
    @Test
    void aPhotoOfTheBeforeCanBeAttached() throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .file(ficheiro("antes.jpg", "image/jpeg"))
                        .param("kind", "BEFORE")
                        .param("caption", "Discos dianteiros antes da intervencao")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode a = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(a.get("kind").asText()).isEqualTo("BEFORE");
        assertThat(a.get("kindLabel").asText()).isEqualTo("Antes");
        assertThat(a.get("name").asText()).isEqualTo("antes.jpg");
        assertThat(a.get("caption").asText()).contains("Discos dianteiros");
        // Sem id, o ecra mostrava o anexo mas nao o conseguia apagar.
        assertThat(a.get("id").asText()).isNotBlank();
        // A ligacao vem assinada, para a imagem abrir sem token no atributo src.
        assertThat(a.get("url").asText()).contains("sig=");

        assertThat(send(get("/api/v1/work-orders/" + workOrderId + "/attachments"), null, 200))
                .hasSize(1);
    }

    @Test
    void anInvoiceFromTheWorkshopIsAccepted() throws Exception {
        mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .file(ficheiro("FT-2026-882.pdf", "application/pdf"))
                        .param("kind", "INVOICE")
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        JsonNode lista = send(get("/api/v1/work-orders/" + workOrderId + "/attachments"), null, 200);
        assertThat(lista.get(0).get("contentType").asText()).isEqualTo("application/pdf");
    }

    @Test
    void anUnsupportedFormatIsRefusedWithAReadableReason() throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .file(new MockMultipartFile("file", "virus.exe",
                                "application/x-msdownload", "x".getBytes()))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Formato");
    }

    /**
     * Um pedido sem ficheiro nenhum e um erro do cliente, nao uma avaria.
     *
     * <p>Dava 500 — e o utilizador via "ocorreu um erro inesperado", que o leva
     * a pensar que o sistema esta partido quando so faltava escolher o ficheiro.
     */
    @Test
    void anUploadWithoutAFileIsRefusedAsABadRequestNotAServerFault() throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .param("kind", "BEFORE")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Nenhum ficheiro");
    }

    /**
     * Um tipo de anexo que nao existe tambem dava 500, pela mesma razao: a
     * conversao falhava antes de chegar ao controlador.
     */
    @Test
    void anUnknownAttachmentKindIsRefusedAsABadRequestAndSaysWhatIsAccepted()
            throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .file(ficheiro("foto.png", "image/png"))
                        .param("kind", "INVENTADO")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andReturn();
        String corpo = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(corpo).contains("Valor inválido");
        assertThat(corpo).contains("BEFORE");
    }

    @Test
    void attachmentsOfAClosedOrderArePartOfTheRecord() throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/work-orders/" + workOrderId + "/attachments")
                        .file(ficheiro("fatura.pdf", "application/pdf"))
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        String anexoId = json.readTree(r.getResponse().getContentAsByteArray()).get("id").asText();

        send(post("/api/v1/work-orders/" + workOrderId + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + workOrderId + "/complete"),
                Map.of("resolution", "Feito"), 200);
        send(post("/api/v1/work-orders/" + workOrderId + "/close"), null, 200);

        // Apagar a fatura depois de o custo entrar nos indicadores do ano seria
        // apagar a prova de uma despesa ja contabilizada.
        JsonNode erro = send(
                delete("/api/v1/work-orders/" + workOrderId + "/attachments/" + anexoId),
                null, 409);
        assertThat(erro.get("message").asText()).contains("registo");
    }

    // ---- impressao ----------------------------------------------------------
    @Test
    void theOrderPrintsAsASignablePdf() throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("symptom", "Vibra ao travar acima dos 60 km/h");
        d.put("diagnosis", "Discos dianteiros empenados");
        send(post("/api/v1/work-orders/" + workOrderId + "/diagnosis"), d, 200);

        Map<String, Object> mao = new HashMap<>();
        mao.put("technicianLabel", "Equipa interna");
        mao.put("hours", 4);
        mao.put("hourlyRate", 2_500);
        send(post("/api/v1/work-orders/" + workOrderId + "/labor"), mao, 200);

        MvcResult r = mvc.perform(get("/api/v1/work-orders/" + workOrderId + "/print.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();

        byte[] pdf = r.getResponse().getContentAsByteArray();
        assertThat(r.getResponse().getContentType()).contains("application/pdf");
        // Assinatura de um PDF real, nao uma pagina de erro com outro cabecalho.
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1_500);
    }

    @Test
    void aTechnicianGetsThePdfWithoutTheMoney() throws Exception {
        Map<String, Object> mao = new HashMap<>();
        mao.put("hours", 4);
        mao.put("hourlyRate", 2_500);
        send(post("/api/v1/work-orders/" + workOrderId + "/labor"), mao, 200);

        JsonNode convite = send(post("/api/v1/team/invitations"),
                Map.of("email", "mec@teste.ao", "name", "Mecanico", "role", "TECHNICIAN"), 201);
        JsonNode conta = send(null,
                post("/api/v1/invitations/" + convite.get("token").asText() + "/accept"),
                Map.of("name", "Mecanico Silva", "password", "palavraForte1"), 200);
        String tecnico = "Bearer " + conta.get("accessToken").asText();

        MvcResult r = mvc.perform(get("/api/v1/work-orders/" + workOrderId + "/print.pdf")
                        .header("Authorization", tecnico))
                .andExpect(status().isOk())
                .andReturn();

        // O PDF sai, mas mais curto: sem a seccao de custos. Um documento que
        // parece completo e omite numeros em silencio e pior do que um que
        // assume o que nao mostra -- por isso o rodape di-lo.
        byte[] pdf = r.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        MvcResult gestor = mvc.perform(get("/api/v1/work-orders/" + workOrderId + "/print.pdf")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(pdf.length)
                .isLessThan(gestor.getResponse().getContentAsByteArray().length);
    }

    // ---- relatorios ---------------------------------------------------------
    @Test
    void theCostPerAssetReportAnswersWhetherItIsWorthRepairing() throws Exception {
        send(post("/api/v1/work-orders/" + workOrderId + "/start"),
                Map.of("stopAsset", true), 200);

        Map<String, Object> mao = new HashMap<>();
        mao.put("hours", 6);
        mao.put("hourlyRate", 3_000);
        send(post("/api/v1/work-orders/" + workOrderId + "/labor"), mao, 200);
        send(post("/api/v1/work-orders/" + workOrderId + "/complete"),
                Map.of("resolution", "Discos substituidos"), 200);

        MvcResult r = mvc.perform(get("/api/v1/reports/maintenance-by-asset.csv")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        String csv = r.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("Custo por km ou hora");
        assertThat(csv).contains("CAM-300");
        // 18.000 Kz de mao de obra em 120.000 km.
        assertThat(csv).contains("18000");
    }

    @Test
    void theSupplierReportShowsWhoWasPaidAndHowLongTheyTook() throws Exception {
        String f = send(post("/api/v1/suppliers"),
                Map.of("name", "Oficina Central", "taxId", "5417009988", "city", "Luanda"), 201)
                .get("id").asText();

        Map<String, Object> q = new HashMap<>();
        q.put("supplierId", f);
        q.put("laborAmount", 150_000);
        send(post("/api/v1/work-orders/" + workOrderId + "/quotes"), q, 200);
        String quoteId = send(get("/api/v1/work-orders/" + workOrderId), null, 200)
                .get("quotes").get(0).get("id").asText();
        send(post("/api/v1/work-orders/" + workOrderId + "/quotes/" + quoteId + "/select"),
                null, 200);

        Map<String, Object> ext = new HashMap<>();
        ext.put("supplier", "Oficina Central");
        ext.put("description", "Substituicao de discos");
        ext.put("cost", 150_000);
        send(post("/api/v1/work-orders/" + workOrderId + "/external-services"), ext, 200);

        MvcResult r = mvc.perform(get("/api/v1/reports/maintenance-by-supplier.csv")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        String csv = r.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("Oficina Central");
        assertThat(csv).contains("5417009988");
        assertThat(csv).contains("150000");
    }

    @Test
    void theDowntimeReportPutsANumberOnTheStoppedVehicle() throws Exception {
        send(post("/api/v1/work-orders/" + workOrderId + "/start"),
                Map.of("startedAt",
                        java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS)
                                .toString(),
                        "stopAsset", true), 200);
        send(post("/api/v1/work-orders/" + workOrderId + "/complete"),
                Map.of("resolution", "Feito"), 200);

        MvcResult r = mvc.perform(get("/api/v1/reports/maintenance-downtime.csv")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        String csv = r.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("Custo da paragem");
        assertThat(csv).contains("CAM-300");
        // 5 horas a 8.000 Kz. Numero que nao esta em fatura nenhuma.
        assertThat(csv).contains("40000");
    }
}
