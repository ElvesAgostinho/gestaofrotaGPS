package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Exportação de relatórios em CSV. */
class ReportIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    /** Descarrega um relatório e devolve o texto já descodificado em UTF-8. */
    private String download(String path) throws Exception {
        MvcResult r = mvc.perform(get(path).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains("attachment");
        return new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String newAsset(String tag, String name) throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Tipo " + tag), 201)
                .get("id").asText();
        return send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", name, "assetTypeId", typeId), 201)
                .get("id").asText();
    }

    // ---- formato --------------------------------------------------------
    @Test
    void writesAFileThatPortugueseExcelOpensCorrectly() throws Exception {
        bearer = register("rp1@teste.ao").bearer();
        newAsset("RE-001", "Retroescavadora");

        String csv = download("/api/v1/reports/assets.csv");

        // BOM no início: sem ele o Excel lê UTF-8 como ANSI e estraga os acentos.
        assertThat(csv).startsWith("﻿");
        // Separador ponto e vírgula, cabeçalhos em português.
        assertThat(csv).contains("Etiqueta;Nome;Tipo;Local;Estado");
        assertThat(csv).contains("RE-001;Retroescavadora");
        // Acentos intactos.
        assertThat(csv).contains("Nº de série").contains("Matrícula");
    }

    @Test
    void quotesValuesThatContainTheSeparator() throws Exception {
        bearer = register("rp2@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Máquina"), 201)
                .get("id").asText();
        send(post("/api/v1/assets"), Map.of(
                "tag", "RE-002", "name", "Retro; com ponto e vírgula",
                "assetTypeId", typeId), 201);

        String csv = download("/api/v1/reports/assets.csv");
        assertThat(csv).contains("\"Retro; com ponto e vírgula\"");
        // A linha continua a ter o número certo de colunas.
        String linha = csv.lines().filter(l -> l.startsWith("RE-002")).findFirst().orElseThrow();
        assertThat(linha).startsWith("RE-002;\"Retro; com ponto e vírgula\";");
    }

    @Test
    void writesDecimalsWithACommaAsPortugueseExcelExpects() throws Exception {
        bearer = register("rp3@teste.ao").bearer();
        String partId = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro", "minQuantity", 5, "unit", "un"), 201).get("id").asText();
        String whId = send(post("/api/v1/warehouses"), Map.of("name", "Armazém"), 201)
                .get("id").asText();

        Map<String, Object> mov = new HashMap<>();
        mov.put("partId", partId);
        mov.put("warehouseId", whId);
        mov.put("type", "IN");
        mov.put("quantity", new java.math.BigDecimal("12.50"));
        send(post("/api/v1/stock/movements"), mov, 201);

        String csv = download("/api/v1/reports/stock.csv");
        assertThat(csv).contains("Filtro").contains("12,50");
        assertThat(csv).doesNotContain("12.50");
    }

    // ---- conteúdo -------------------------------------------------------
    @Test
    void exportsWorkOrdersWithTheirNumbers() throws Exception {
        bearer = register("rp4@teste.ao").bearer();
        String assetId = newAsset("CAM-001", "Camião");
        send(post("/api/v1/work-orders"), Map.of(
                "assetId", assetId, "type", "CORRECTIVE", "title", "Fuga de óleo"), 201);

        String csv = download("/api/v1/reports/work-orders.csv");
        assertThat(csv).contains("Número;Ativo;Tipo;Estado");
        int ano = java.time.ZonedDateTime
                .now(java.time.ZoneId.of("Africa/Luanda")).getYear();
        // O CSV passou a levar os nomes em portugues: quem o abre no Excel e
        // quem gere a frota, nao quem escreveu os enums.
        assertThat(csv).contains("OM-" + ano + "-000001;CAM-001;Corretiva");
        assertThat(csv).contains("Fuga de óleo");
    }

    @Test
    void theKpiReportCarriesTheNumbersBehindTheFormulas() throws Exception {
        bearer = register("rp5@teste.ao").bearer();
        String assetId = newAsset("RE-005", "Retro");

        send(post("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings"),
                Map.of("value", 1000, "readingAt",
                        Instant.now().minus(2, ChronoUnit.DAYS).toString()), 201);
        send(post("/api/v1/assets/" + assetId + "/meters/HOURMETER/readings"),
                Map.of("value", 1200), 201);

        String csv = download("/api/v1/reports/kpis.csv?assetId=" + assetId);
        // Os quatro indicadores do documento de referência.
        assertThat(csv).contains("Disponibilidade").contains("MTBF").contains("MTTR")
                .contains("Cumprimento");
        // E os dados que os produzem, para o relatório se poder auditar.
        assertThat(csv).contains("Dados do período");
        assertThat(csv).contains("Horas de operação;200");
        assertThat(csv).contains("Nº de falhas;0");
    }

    @Test
    void exportsDocumentsPredictiveAndTrips() throws Exception {
        bearer = register("rp6@teste.ao").bearer();
        String assetId = newAsset("ESC-001", "Escavadora");

        send(post("/api/v1/assets/" + assetId + "/documents"), Map.of(
                "title", "Apólice 2027", "kind", "INSURANCE",
                "expiresAt", Instant.now().plus(200, ChronoUnit.DAYS).toString()), 201);
        send(post("/api/v1/assets/" + assetId + "/predictive/standard"), null, 201);

        assertThat(download("/api/v1/reports/documents.csv"))
                .contains("Apólice 2027").contains("Seguro").contains("Caduca em");
        assertThat(download("/api/v1/reports/predictive.csv"))
                .contains("Análise de vibração").contains("Mensal")
                .contains("Termografia").contains("Trimestral");
        // Sem viagens o ficheiro sai só com o cabeçalho — não com um erro.
        assertThat(download("/api/v1/reports/trips.csv"))
                .contains("Ativo;Início;Fim").hasLineCount(1);
    }

    @Test
    void reportsAreIsolatedBetweenCompaniesAndNeedASession() throws Exception {
        bearer = register("rp7@teste.ao").bearer();
        newAsset("RE-007", "Retro da primeira empresa");

        bearer = register("rp7b@teste.ao", "Outra Empresa").bearer();
        assertThat(download("/api/v1/reports/assets.csv"))
                .doesNotContain("Retro da primeira empresa");

        mvc.perform(get("/api/v1/reports/assets.csv")).andExpect(status().isUnauthorized());
    }

    @Test
    void fileNamesCarryTheDate() throws Exception {
        bearer = register("rp8@teste.ao").bearer();
        MvcResult r = mvc.perform(get("/api/v1/reports/assets.csv")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();

        String disposition = r.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("ativos-")
                .contains(String.valueOf(java.time.Year.now().getValue()))
                .endsWith(".csv\"");
    }
}
