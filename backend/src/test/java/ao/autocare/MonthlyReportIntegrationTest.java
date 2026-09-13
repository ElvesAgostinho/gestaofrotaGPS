package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.report.MonthlyReportMailer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O relatório mensal: sai em PDF com o que o mês teve (ordens, custo, paragens,
 * combustível, pendentes) e segue sozinho por email a quem gere, uma vez por
 * mês, pelo servidor da empresa.
 */
class MonthlyReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MonthlyReportMailer mailer;

    private ServerSocket smtp;
    private final CopyOnWriteArrayList<String> recebidos = new CopyOnWriteArrayList<>();
    private String bearer;
    private String orgId;
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

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("mensal@teste.ao", "Transportes Mensal").bearer();
        orgId = send(get("/api/v1/organization"), null, 200).get("id").asText();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(post("/api/v1/assets"), Map.of("tag", "MN-1", "name", "Camião Mensal", "assetTypeId", tipo), 201)
                .get("id").asText();
        // Uma ordem concluída este mês, com mão de obra.
        String id = send(post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "CORRECTIVE", "title", "Travões"), 201)
                .get("id").asText();
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id + "/labor"), Map.of("technicianLabel", "Zé", "hours", 3, "hourlyRate", 5000), 200);
        send(post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "Pastilhas"), 200);
        // Um abastecimento.
        send(post("/api/v1/assets/" + assetId + "/fuel"), Map.of("liters", 120, "pricePerLiter", 300, "meterValue", 1000), 201);

        smtp = new ServerSocket(47396, 5, java.net.InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (!smtp.isClosed()) {
                try (Socket s = smtp.accept()) {
                    atender(s);
                } catch (Exception ignored) {
                    // fechado
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        smtp.close();
    }

    private void atender(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        out.print("220 teste ESMTP\r\n");
        out.flush();
        String linha;
        StringBuilder dados = null;
        while ((linha = in.readLine()) != null) {
            if (dados != null) {
                if (linha.equals(".")) {
                    recebidos.add(dados.toString());
                    dados = null;
                    out.print("250 OK\r\n");
                } else {
                    dados.append(linha).append('\n');
                }
            } else if (linha.toUpperCase().startsWith("EHLO") || linha.toUpperCase().startsWith("HELO")) {
                out.print("250-teste\r\n250 8BITMIME\r\n");
            } else if (linha.toUpperCase().startsWith("DATA")) {
                dados = new StringBuilder();
                out.print("354 fim\r\n");
            } else if (linha.toUpperCase().startsWith("QUIT")) {
                out.print("221 adeus\r\n");
                out.flush();
                return;
            } else {
                out.print("250 OK\r\n");
            }
            out.flush();
        }
    }

    @Test
    void oPdfDoMesTemOsNumerosDoMesEOsPendentes() throws Exception {
        YearMonth mes = YearMonth.now(ZoneId.of("Africa/Luanda"));
        byte[] pdf = mvc.perform(get("/api/v1/reports/monthly.pdf?month=" + mes).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String t = texto(pdf);
        assertThat(t).contains("Relatório mensal").containsIgnoringCase("Ordens concluídas");
        assertThat(t).contains("MN-1").contains("Camião Mensal");
        assertThat(t).containsIgnoringCase("Combustível").contains("120");
        assertThat(t.replace(' ', ' ')).contains("15 000"); // 3 h × 5 000 de mão de obra
        // Um mês mal escrito é recusado com uma frase, não com um erro técnico.
        JsonNode erro = send(get("/api/v1/reports/monthly.pdf?month=agosto"), null, 400);
        assertThat(erro.get("message").asText()).contains("AAAA-MM");
    }

    @Test
    void oRelatorioSeguePorEmailPeloServidorDaEmpresaComOPdfAnexoEUmaVezPorMes() throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("host", "127.0.0.1");
        cfg.put("port", 47396);
        cfg.put("from", "frota@mensal.ao");
        cfg.put("security", "NONE");
        send(put("/api/v1/integrations/email"), cfg, 200);

        YearMonth mes = YearMonth.now(ZoneId.of("Africa/Luanda"));
        int enviados = mailer.enviar(orgId, mes, false);
        assertThat(enviados).isEqualTo(1); // o dono
        assertThat(recebidos).hasSize(1);
        String mail = recebidos.get(0);
        assertThat(mail).contains("To: mensal@teste.ao").contains("relat").contains("application/pdf");
        assertThat(mail).contains("relatorio-mensal-" + mes + ".pdf");

        // Dentro da aplicação também ficou, com o link.
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).startsWith("Relatório mensal");
            assertThat(n.get("link").asText()).isEqualTo("/relatorios?mes=" + mes);
        });

        // O mesmo mês não se repete; forçado, repete.
        assertThat(mailer.enviar(orgId, mes, false)).isZero();
        assertThat(recebidos).hasSize(1);
        assertThat(mailer.enviar(orgId, mes, true)).isEqualTo(1);
        assertThat(recebidos).hasSize(2);

        // Desligado nas definições: não sai.
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/organization"),
                Map.of("monthlyReportEnabled", false), 200);
        assertThat(mailer.enviar(orgId, mes.minusMonths(1), false)).isZero();
        assertThat(recebidos).hasSize(2);
    }
}
