package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Módulo de manutenção: workflow, diagnóstico, orçamento e aprovação.
 *
 * <p>O que se testa a sério aqui é o que distingue um sistema empresarial de
 * uma lista de tarefas: que os estados não mudam ao acaso, que a aprovação de
 * despesa tem quem, quando e quanto, e que um técnico não vê dinheiro que não
 * lhe diz respeito.
 */
class MaintenanceModuleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String bearer;
    private String assetId;
    private String orgId;

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
        bearer = register("manutencao@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camiao"), 201)
                .get("id").asText();
        assetId = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-100", "name", "Camiao", "assetTypeId", typeId), 201)
                .get("id").asText();
        orgId = jdbc.queryForObject("SELECT id FROM organizations LIMIT 1", String.class);
    }

    private String criarOrdem(String titulo) throws Exception {
        Map<String, Object> w = new HashMap<>();
        w.put("assetId", assetId);
        w.put("type", "CORRECTIVE");
        w.put("title", titulo);
        return send(post("/api/v1/work-orders"), w, 201).get("id").asText();
    }

    private JsonNode ver(String id) throws Exception {
        return send(get("/api/v1/work-orders/" + id), null, 200);
    }

    // ---- numeracao ----------------------------------------------------------
    @Test
    void theOrderNumberCarriesTheYear() throws Exception {
        JsonNode w = ver(criarOrdem("Primeira do ano"));
        int ano = java.time.ZonedDateTime
                .now(java.time.ZoneId.of("Africa/Luanda")).getYear();

        // OM-2026-000001. Um contador único daria OM-2027-000998 no primeiro dia
        // de 2027, e o número deixava de dizer nada sobre o volume do ano.
        assertThat(w.get("number").asText()).startsWith("OM-" + ano + "-");
        assertThat(w.get("orderYear").asInt()).isEqualTo(ano);
    }

    // ---- workflow -----------------------------------------------------------
    @Test
    void theOrderSaysWhereItCanGoNext() throws Exception {
        JsonNode w = ver(criarOrdem("Com caminhos"));

        // Os próximos estados vêm do servidor: duas cópias da máquina de estados
        // acabam por discordar, e a que o utilizador vê seria a errada.
        assertThat(w.get("nextStatuses")).isNotEmpty();
        assertThat(w.get("nextStatuses").toString())
                .contains("DIAGNOSIS")
                .contains("IN_PROGRESS")
                .contains("CANCELLED");
    }

    @Test
    void anImpossibleTransitionIsRefusedAndSaysWhatIsPossible() throws Exception {
        String id = criarOrdem("Sem saltos");

        // Aberta não salta diretamente para concluída.
        JsonNode erro = send(post("/api/v1/work-orders/" + id + "/status"),
                Map.of("status", "DONE"), 409);
        assertThat(erro.get("message").asText())
                .contains("não pode passar")
                .contains("pode seguir para");
    }

    @Test
    void anApprovedOrderDoesNotGoBackToOpen() throws Exception {
        String id = criarOrdem("Aprovada");
        send(post("/api/v1/work-orders/" + id + "/status"),
                Map.of("status", "AWAITING_APPROVAL"), 200);
        send(post("/api/v1/work-orders/" + id + "/approve"),
                Map.of("amount", 100_000, "note", "Autorizado"), 200);

        // Se voltasse a aberta, o valor aprovado deixava de corresponder ao
        // trabalho — e a aprovação passava a não valer nada.
        send(post("/api/v1/work-orders/" + id + "/status"), Map.of("status", "OPEN"), 409);
    }

    @Test
    void everyTransitionIsRecordedWithTimeSpentInThePreviousState() throws Exception {
        String id = criarOrdem("Com histórico");
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id + "/status"),
                Map.of("status", "AWAITING_PARTS", "note", "Kit de embraiagem em falta"), 200);

        JsonNode historico = ver(id).get("statusHistory");
        assertThat(historico).hasSize(2);

        JsonNode ultima = historico.get(1);
        assertThat(ultima.get("fromStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(ultima.get("toStatus").asText()).isEqualTo("AWAITING_PARTS");
        assertThat(ultima.get("toLabel").asText()).isEqualTo("A aguardar peças");
        assertThat(ultima.get("note").asText()).contains("embraiagem");
        assertThat(ultima.get("changedByLabel").asText()).isEqualTo("Utilizador Teste");
        // É isto que explica que uma reparação de duas horas levou três semanas.
        assertThat(ultima.hasNonNull("minutesInPrevious")).isTrue();
    }

    @Test
    void aClosedOrderIsTheEndOfTheLine() throws Exception {
        String id = criarOrdem("Para fechar");
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "Feito"), 200);

        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/close"), null, 200);
        assertThat(fechada.get("status").asText()).isEqualTo("CLOSED");
        assertThat(fechada.get("nextStatuses")).isEmpty();

        send(post("/api/v1/work-orders/" + id + "/status"),
                Map.of("status", "IN_PROGRESS"), 409);
    }

    // ---- diagnostico --------------------------------------------------------
    @Test
    void theDiagnosisKeepsSymptomCauseAndSolutionApart() throws Exception {
        String id = criarOrdem("Vibra ao travar");

        Map<String, Object> d = new HashMap<>();
        d.put("symptom", "Viatura vibra durante a travagem a partir dos 60 km/h");
        d.put("diagnosis", "Discos dianteiros com desgaste irregular");
        d.put("probableCause", "Travagem prolongada em descida com carga maxima");
        d.put("recommendedAction", "Substituir discos e pastilhas dianteiras");
        JsonNode w = send(post("/api/v1/work-orders/" + id + "/diagnosis"), d, 200);

        assertThat(w.get("symptom").asText()).contains("vibra");
        assertThat(w.get("diagnosis").asText()).contains("desgaste irregular");
        assertThat(w.get("probableCause").asText()).contains("descida");
        assertThat(w.get("recommendedAction").asText()).contains("Substituir discos");
        assertThat(w.get("diagnosedByLabel").asText()).isEqualTo("Utilizador Teste");

        // Registar o diagnóstico e deixar a ordem em "aberta" faria o quadro
        // mentir sobre o que está a acontecer àquela viatura.
        assertThat(w.get("status").asText()).isEqualTo("DIAGNOSIS");
    }

    // ---- orcamentos ---------------------------------------------------------
    @Test
    void severalQuotesCanBeComparedBeforeChoosing() throws Exception {
        String id = criarOrdem("Caixa de velocidades");

        Map<String, Object> q1 = new HashMap<>();
        q1.put("supplierLabel", "Oficina Central");
        q1.put("partsAmount", 200_000);
        q1.put("laborAmount", 100_000);
        send(post("/api/v1/work-orders/" + id + "/quotes"), q1, 200);

        Map<String, Object> q2 = new HashMap<>();
        q2.put("supplierLabel", "Auto Reparadora do Sul");
        q2.put("partsAmount", 180_000);
        q2.put("laborAmount", 60_000);
        q2.put("discountAmount", 20_000);
        JsonNode w = send(post("/api/v1/work-orders/" + id + "/quotes"), q2, 200);

        assertThat(w.get("quotes")).hasSize(2);
        assertThat(w.get("status").asText()).isEqualTo("QUOTING");

        // O total é calculado, nunca aceite do pedido: 180+60-20 = 220.
        JsonNode segundo = w.get("quotes").get(1);
        assertThat(segundo.get("totalAmount").asDouble()).isEqualTo(220_000.0);

        String escolhido = segundo.get("id").asText();
        JsonNode depois = send(
                post("/api/v1/work-orders/" + id + "/quotes/" + escolhido + "/select"), null, 200);

        assertThat(depois.get("estimatedCost").asDouble()).isEqualTo(220_000.0);
        long selecionados = 0;
        for (JsonNode q : depois.get("quotes")) {
            if (q.get("selected").asBoolean()) selecionados++;
        }
        assertThat(selecionados).isEqualTo(1);
    }

    @Test
    void aQuoteNeedsToSayWhoItIsFrom() throws Exception {
        String id = criarOrdem("Sem fornecedor");
        assertThat(send(post("/api/v1/work-orders/" + id + "/quotes"),
                Map.of("partsAmount", 1000), 400)
                .get("message").asText()).contains("de quem");
    }

    // ---- aprovacao ----------------------------------------------------------
    @Test
    void belowTheCompanyLimitTheOrderApprovesItself() throws Exception {
        jdbc.update("UPDATE organizations SET maintenance_approval_limit = 500000 WHERE id = ?",
                orgId);

        String id = criarOrdem("Troca de lampada");
        Map<String, Object> q = new HashMap<>();
        q.put("supplierLabel", "Oficina Central");
        q.put("partsAmount", 15_000);
        send(post("/api/v1/work-orders/" + id + "/quotes"), q, 200);
        String quoteId = ver(id).get("quotes").get(0).get("id").asText();
        send(post("/api/v1/work-orders/" + id + "/quotes/" + quoteId + "/select"), null, 200);

        JsonNode w = send(post("/api/v1/work-orders/" + id + "/request-approval"), null, 200);

        // Obrigar um dono a aprovar a troca de uma lâmpada faz com que ninguém
        // aprove nada e o processo seja contornado.
        assertThat(w.get("status").asText()).isEqualTo("APPROVED");
        assertThat(w.get("approvalNote").asText()).contains("automatica");
        assertThat(w.get("approvedAmount").asDouble()).isEqualTo(15_000.0);
    }

    @Test
    void aboveTheLimitItWaitsForAPerson() throws Exception {
        jdbc.update("UPDATE organizations SET maintenance_approval_limit = 100000 WHERE id = ?",
                orgId);

        String id = criarOrdem("Motor fundido");
        Map<String, Object> q = new HashMap<>();
        q.put("supplierLabel", "Oficina Central");
        q.put("partsAmount", 800_000);
        send(post("/api/v1/work-orders/" + id + "/quotes"), q, 200);
        String quoteId = ver(id).get("quotes").get(0).get("id").asText();
        send(post("/api/v1/work-orders/" + id + "/quotes/" + quoteId + "/select"), null, 200);

        JsonNode w = send(post("/api/v1/work-orders/" + id + "/request-approval"), null, 200);
        assertThat(w.get("status").asText()).isEqualTo("AWAITING_APPROVAL");

        JsonNode aprovada = send(post("/api/v1/work-orders/" + id + "/approve"),
                Map.of("amount", 800_000, "note", "Autorizado pela direccao"), 200);
        assertThat(aprovada.get("status").asText()).isEqualTo("APPROVED");
        assertThat(aprovada.get("approvedByName").asText()).isEqualTo("Utilizador Teste");
        assertThat(aprovada.hasNonNull("approvedAt")).isTrue();
        assertThat(aprovada.get("approvedAmount").asDouble()).isEqualTo(800_000.0);
    }

    @Test
    void rejectingRequiresSayingWhatToChange() throws Exception {
        String id = criarOrdem("A rejeitar");
        send(post("/api/v1/work-orders/" + id + "/status"),
                Map.of("status", "AWAITING_APPROVAL"), 200);

        send(post("/api/v1/work-orders/" + id + "/reject"), Map.of("note", "nao"), 400);

        JsonNode w = send(post("/api/v1/work-orders/" + id + "/reject"),
                Map.of("note", "Valor acima do mercado, peca outro orcamento"), 200);
        assertThat(w.get("status").asText()).isEqualTo("REJECTED");
        assertThat(w.get("rejectionReason").asText()).contains("mercado");

        // Rejeitada volta a orçamento: pedir outra proposta é o caminho normal.
        assertThat(w.get("nextStatuses").toString()).contains("QUOTING");
    }

    @Test
    void thereIsNothingToApproveWithoutAnEstimate() throws Exception {
        String id = criarOrdem("Sem numeros");
        assertThat(send(post("/api/v1/work-orders/" + id + "/request-approval"), null, 400)
                .get("message").asText()).contains("orcamento");
    }

    // ---- fornecedores -------------------------------------------------------
    @Test
    void aSupplierCarriesTheTaxNumberSoInvoicesReconcile() throws Exception {
        Map<String, Object> f = new HashMap<>();
        f.put("name", "Oficina Central de Luanda");
        f.put("taxId", "5417000000");
        f.put("kind", "WORKSHOP");
        f.put("phone", "+244923000000");
        f.put("city", "Luanda");
        f.put("defaultWarrantyMonths", 6);
        JsonNode s = send(post("/api/v1/suppliers"), f, 201);

        assertThat(s.get("taxId").asText()).isEqualTo("5417000000");
        assertThat(s.get("kindLabel").asText()).isEqualTo("Oficina");

        // O NIF é único: duas fichas para o mesmo fornecedor dariam duas
        // versões do histórico de custos com ele.
        assertThat(send(post("/api/v1/suppliers"), f, 409).get("message").asText())
                .contains("5417000000");
    }

    @Test
    void aQuoteCanPointAtARegisteredSupplier() throws Exception {
        Map<String, Object> f = new HashMap<>();
        f.put("name", "Auto Reparadora");
        f.put("taxId", "5417111111");
        String supplierId = send(post("/api/v1/suppliers"), f, 201).get("id").asText();

        String id = criarOrdem("Com fornecedor");
        Map<String, Object> q = new HashMap<>();
        q.put("supplierId", supplierId);
        q.put("laborAmount", 50_000);
        JsonNode w = send(post("/api/v1/work-orders/" + id + "/quotes"), q, 200);

        assertThat(w.get("quotes").get(0).get("supplierLabel").asText())
                .isEqualTo("Auto Reparadora");

        String quoteId = w.get("quotes").get(0).get("id").asText();
        JsonNode depois = send(
                post("/api/v1/work-orders/" + id + "/quotes/" + quoteId + "/select"), null, 200);
        assertThat(depois.get("supplierName").asText()).isEqualTo("Auto Reparadora");
        assertThat(depois.get("execution").asText()).isEqualTo("EXTERNAL");
    }

    // ---- permissao de custos ------------------------------------------------
    @Test
    void aTechnicianDoesNotReceiveTheMoneyAtAll() throws Exception {
        String id = criarOrdem("Com custos");
        Map<String, Object> mao = new HashMap<>();
        mao.put("hours", 4);
        mao.put("hourlyRate", 2_500);
        send(post("/api/v1/work-orders/" + id + "/labor"), mao, 200);

        // Convidar um técnico e aceitar o convite.
        JsonNode convite = send(post("/api/v1/team/invitations"),
                Map.of("email", "tecnico@teste.ao", "name", "Tecnico",
                        "role", "TECHNICIAN"), 201);
        JsonNode conta = send(null,
                post("/api/v1/invitations/" + convite.get("token").asText() + "/accept"),
                Map.of("name", "Tecnico Silva", "password", "palavraForte1"), 200);
        String tecnico = "Bearer " + conta.get("accessToken").asText();

        JsonNode comoGestor = ver(id);
        assertThat(comoGestor.get("totalLaborCost").asDouble()).isEqualTo(10_000.0);

        JsonNode comoTecnico = send(tecnico, get("/api/v1/work-orders/" + id), null, 200);
        // Esconder no ecrã não chegava: os números continuariam a viajar na
        // resposta e bastava abrir as ferramentas do browser.
        assertThat(comoTecnico.hasNonNull("totalLaborCost")).isFalse();
        assertThat(comoTecnico.hasNonNull("totalCost")).isFalse();
        assertThat(comoTecnico.hasNonNull("totalPartsCost")).isFalse();
        assertThat(comoTecnico.get("quotes")).isEmpty();

        // O trabalho continua todo visível: o técnico precisa dele para executar.
        assertThat(comoTecnico.get("totalLaborHours").asDouble()).isEqualTo(4.0);
        assertThat(comoTecnico.get("title").asText()).isEqualTo("Com custos");
        assertThat(comoTecnico.get("labor")).hasSize(1);
    }

    // ---- nao quebrar o que ja existia ---------------------------------------
    @Test
    void theSimpleFlowStillWorksExactlyAsBefore() throws Exception {
        // Uma avaria pequena não passa por diagnóstico, orçamento nem aprovação.
        // O percurso longo é para quando é preciso, não uma obrigação nova.
        String id = criarOrdem("Avaria simples");
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        JsonNode feita = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Resolvido no local"), 200);
        assertThat(feita.get("status").asText()).isEqualTo("DONE");

        JsonNode verificada = send(post("/api/v1/work-orders/" + id + "/verify"), null, 200);
        assertThat(verificada.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(verificada.get("statusLabel").asText()).isEqualTo("Verificada");
    }
}
