package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A encomenda deixa de ser um telefonema.
 *
 * <p>Hoje a compra acontece assim: alguém olha para a lista do que falta, liga
 * ao fornecedor, e o que foi pedido fica na memória de quem ligou. Quando a
 * peça não chega, não há a quem mostrar nada — nem o que se encomendou, nem o
 * preço combinado, nem quem autorizou.
 *
 * <p>O que estes testes protegem é sobretudo a <b>aprovação</b>, que é a parte
 * fácil de fazer mal: obrigar a aprovar um filtro de oito mil kwanzas faz com
 * que ninguém use o sistema; deixar passar dez milhões sem assinatura faz com
 * que o sistema não sirva para nada. E protegem a conta dos totais, que nunca
 * pode vir do ecrã.
 */
class OrdemDeCompraIntegrationTest extends AbstractIntegrationTest {

    private String dono;
    private String gestor;
    private String fornecedor;
    private String filtro;

    private JsonNode send(String auth, MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private void montar(String email) throws Exception {
        dono = register(email, "Transportes Compra").bearer();
        fornecedor = send(dono, post("/api/v1/suppliers"), Map.of(
                "name", "Auto Peças de Luanda", "kind", "PARTS",
                "taxId", "5417000111", "email", "vendas@autopecas.ao",
                "iban", "AO06 0040 0000 1111 2222 3333 4",
                "paymentTerms", "30 dias"), 201).get("id").asText();
        filtro = send(dono, post("/api/v1/parts"), Map.of(
                "name", "Filtro de óleo", "category", "FILTER", "unit", "un",
                "partNumber", "1R-0716"), 201).get("id").asText();
    }

    private void convidarGestor(String email) throws Exception {
        String token = send(dono, post("/api/v1/team/invitations"),
                Map.of("email", email, "role", "MANAGER"), 201).get("token").asText();
        MvcResult r = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Gestor Compras", "password", "Gestor!2026"))))
                .andExpect(status().isOk()).andReturn();
        gestor = "Bearer " + json.readTree(r.getResponse().getContentAsByteArray())
                .get("accessToken").asText();
    }

    private Map<String, Object> ordem(int quantidade, int preco) {
        Map<String, Object> linha = new HashMap<>();
        linha.put("partId", filtro);
        linha.put("description", "Filtro de óleo do motor");
        linha.put("supplierPartNumber", "1R-0716");
        linha.put("quantity", quantidade);
        linha.put("unit", "un");
        linha.put("unitPrice", preco);

        Map<String, Object> req = new HashMap<>();
        req.put("supplierId", fornecedor);
        req.put("lines", List.of(linha));
        return req;
    }

    private void limites(Integer automatico, Integer dono) throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("autoApproveBelow", automatico);
        cfg.put("ownerApprovalAbove", dono);
        send(this.dono, put("/api/v1/purchase-orders/settings"), cfg, 200);
    }

    // ==== Os totais ==========================================================

    @Test
    void osTotaisSaemDasLinhasENaoDoEcra() throws Exception {
        montar("oc1@teste.ao");

        // O cliente manda um total à escolha; o servidor ignora-o e soma.
        Map<String, Object> req = ordem(10, 8500);
        req.put("taxAmount", 1000);
        req.put("discountAmount", 500);
        JsonNode o = send(dono, post("/api/v1/purchase-orders"), req, 201);

        assertThat(o.get("subtotal").decimalValue()).isEqualByComparingTo(new BigDecimal("85000"));
        // 85 000 − 500 de desconto + 1 000 de IVA
        assertThat(o.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("85500"));
        assertThat(o.get("number").asText()).startsWith("OC-");
        assertThat(o.get("statusLabel").asText()).isEqualTo("Rascunho");
        // A fotografia do fornecedor ficou guardada na ordem.
        assertThat(o.get("supplierIban").asText()).startsWith("AO06");
        assertThat(o.get("paymentTerms").asText()).isEqualTo("30 dias");
    }

    @Test
    void aRetencaoNaFonteSaiDoQueSeTransfere() throws Exception {
        montar("oc2@teste.ao");
        // Uma oficina: prestação de serviços leva 6,5 % retidos na fonte.
        String oficina = send(dono, post("/api/v1/suppliers"), Map.of(
                "name", "Oficina Central", "kind", "WORKSHOP", "withholdingTax", 6.5), 201)
                .get("id").asText();

        Map<String, Object> linha = new HashMap<>();
        linha.put("description", "Revisão completa");
        linha.put("quantity", 1);
        linha.put("unitPrice", 100000);
        JsonNode o = send(dono, post("/api/v1/purchase-orders"),
                Map.of("supplierId", oficina, "lines", List.of(linha)), 201);

        assertThat(o.get("total").decimalValue()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(o.get("withholdingAmount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("6500"));
        // O que se transfere de facto. Transferir o total é pagar a mais.
        assertThat(o.get("payable").decimalValue()).isEqualByComparingTo(new BigDecimal("93500"));
    }

    @Test
    void umaOrdemSemLinhasNaoEUmaOrdem() throws Exception {
        montar("oc3@teste.ao");
        send(dono, post("/api/v1/purchase-orders"),
                Map.of("supplierId", fornecedor, "lines", List.of()), 400);
    }

    // ==== A aprovação ========================================================

    @Test
    void abaixoDoLimiteAOrdemAprovaSeSozinha() throws Exception {
        montar("oc4@teste.ao");
        limites(50000, 500000);

        // Oito mil e quinhentos: obrigar a aprovar isto faz com que ninguém
        // use o sistema para comprar um filtro.
        String id = send(dono, post("/api/v1/purchase-orders"), ordem(1, 8500), 201)
                .get("id").asText();
        JsonNode depois = send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);

        assertThat(depois.get("status").asText()).isEqualTo("APPROVED");
        assertThat(depois.get("approvedByLabel").isNull()).isFalse();
        assertThat(depois.toString()).contains("Abaixo do limite");
    }

    @Test
    void acimaDoLimiteSoODonoAprova() throws Exception {
        montar("oc5@teste.ao");
        convidarGestor("gestor@oc5.ao");
        limites(50000, 500000);

        // Um milhão: acima do limite do dono.
        String id = send(gestor, post("/api/v1/purchase-orders"), ordem(100, 10000), 201)
                .get("id").asText();
        JsonNode submetida = send(gestor, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);
        assertThat(submetida.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(submetida.get("quemAprova").asText()).contains("dono");

        // O gestor vê porque não pode, em vez de um botão escondido sem explicação.
        assertThat(submetida.get("podem").get("aprovar").asBoolean()).isFalse();
        assertThat(submetida.get("podem").get("porqueNaoAprova").asText())
                .contains("só o dono aprova");
        send(gestor, post("/api/v1/purchase-orders/" + id + "/approve"), null, 403);

        // O dono aprova.
        JsonNode aprovada = send(dono, post("/api/v1/purchase-orders/" + id + "/approve"), null, 200);
        assertThat(aprovada.get("status").asText()).isEqualTo("APPROVED");
        assertThat(aprovada.get("approvedByLabel").asText()).isNotBlank();
    }

    @Test
    void noMeioBastaQuemGere() throws Exception {
        montar("oc6@teste.ao");
        convidarGestor("gestor@oc6.ao");
        limites(50000, 500000);

        String id = send(gestor, post("/api/v1/purchase-orders"), ordem(10, 10000), 201)
                .get("id").asText();
        send(gestor, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);
        JsonNode aprovada = send(gestor, post("/api/v1/purchase-orders/" + id + "/approve"), null, 200);
        assertThat(aprovada.get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void rejeitarSemMotivoNaoPassaEACorreccaoVoltaAoInicio() throws Exception {
        montar("oc7@teste.ao");
        limites(1000, 500000);

        String id = send(dono, post("/api/v1/purchase-orders"), ordem(10, 10000), 201)
                .get("id").asText();
        send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);

        // Devolver uma ordem sem dizer o que está mal faz com que ela volte igual.
        send(dono, post("/api/v1/purchase-orders/" + id + "/reject"), Map.of("motivo", ""), 400);

        JsonNode rejeitada = send(dono, post("/api/v1/purchase-orders/" + id + "/reject"),
                Map.of("motivo", "Preço acima do cotado; renegociar."), 200);
        assertThat(rejeitada.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejeitada.get("rejectedReason").asText()).contains("renegociar");

        // Corrigir devolve-a a rascunho: quem a rejeitou tem de a ver outra vez.
        JsonNode corrigida = send(dono, put("/api/v1/purchase-orders/" + id), ordem(10, 8500), 200);
        assertThat(corrigida.get("status").asText()).isEqualTo("DRAFT");
        assertThat(corrigida.has("rejectedReason"))
                .as("o motivo da rejeição desaparece quando a ordem é corrigida").isFalse();
    }

    // ==== O percurso =========================================================

    @Test
    void enviadaEConfirmadaSaoEstadosDiferentes() throws Exception {
        montar("oc8@teste.ao");
        limites(1000000, null);

        String id = send(dono, post("/api/v1/purchase-orders"), ordem(5, 8500), 201)
                .get("id").asText();
        send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);

        // Confirmar sem ter enviado não faz sentido.
        send(dono, post("/api/v1/purchase-orders/" + id + "/confirm"), null, 409);

        // Sem servidor de email configurado, o envio diz porquê em vez de
        // fingir que saiu.
        send(dono, post("/api/v1/purchase-orders/" + id + "/send"), null, 409);
    }

    @Test
    void oHistoricoDizQuemFezOQueEQuando() throws Exception {
        montar("oc9@teste.ao");
        limites(1000, 500000);

        String id = send(dono, post("/api/v1/purchase-orders"), ordem(10, 10000), 201)
                .get("id").asText();
        send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);
        send(dono, post("/api/v1/purchase-orders/" + id + "/approve"), null, 200);

        JsonNode o = send(dono, get("/api/v1/purchase-orders/" + id), null, 200);
        JsonNode eventos = o.get("eventos");
        assertThat(eventos).hasSizeGreaterThanOrEqualTo(3);
        assertThat(eventos.get(0).get("rotulo").asText()).isEqualTo("Criada");
        assertThat(eventos.get(eventos.size() - 1).get("rotulo").asText()).isEqualTo("Aprovada");
        // «Quem autorizou isto?» passa a ter resposta.
        assertThat(eventos.get(eventos.size() - 1).get("byLabel").asText()).isNotBlank();
    }

    @Test
    void umaOrdemAprovadaJaNaoSeAltera() throws Exception {
        montar("oc10@teste.ao");
        limites(1000000, null);
        String id = send(dono, post("/api/v1/purchase-orders"), ordem(5, 8500), 201)
                .get("id").asText();
        send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);
        send(dono, put("/api/v1/purchase-orders/" + id), ordem(50, 8500), 409);
    }

    // ==== O que já vai a caminho =============================================

    @Test
    void oPainelDeixaDeMandarComprarOQueJaFoiEncomendado() throws Exception {
        montar("oc11@teste.ao");
        limites(1000000, null);

        JsonNode porReceber = send(dono, get("/api/v1/purchase-orders/on-order"), null, 200);
        assertThat(porReceber.size()).isZero();

        String id = send(dono, post("/api/v1/purchase-orders"), ordem(40, 8500), 201)
                .get("id").asText();
        send(dono, post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);

        JsonNode depois = send(dono, get("/api/v1/purchase-orders/on-order"), null, 200);
        assertThat(depois.get(filtro).decimalValue()).isEqualByComparingTo(new BigDecimal("40"));

        // Uma ordem anulada deixa de contar: o que foi anulado não vem a caminho.
        send(dono, post("/api/v1/purchase-orders/" + id + "/cancel"),
                Map.of("motivo", "Fornecedor sem stock"), 200);
        assertThat(send(dono, get("/api/v1/purchase-orders/on-order"), null, 200).size()).isZero();
    }

    // ==== Quem pode ver ======================================================

    @Test
    void umTecnicoNaoVeAsCompras() throws Exception {
        montar("oc12@teste.ao");
        String token = send(dono, post("/api/v1/team/invitations"),
                Map.of("email", "tecnico@oc12.ao", "role", "TECHNICIAN"), 201)
                .get("token").asText();
        MvcResult r = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Técnico", "password", "Tecnico!2026"))))
                .andExpect(status().isOk()).andReturn();
        String tecnico = "Bearer " + json.readTree(r.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        send(tecnico, get("/api/v1/purchase-orders"), null, 403);
    }
}
