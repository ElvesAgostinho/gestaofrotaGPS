package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pedir preços, receber a mercadoria e conferir a factura.
 *
 * <p>São os três actos que fecham o ciclo da compra, e os três em que o sistema
 * tem de proteger quem o usa de si próprio: comparar propostas de coisas
 * diferentes, receber mais do que se encomendou, e pagar uma factura que não
 * bate com a encomenda. Os três custam dinheiro e os três passam despercebidos
 * até ser tarde.
 */
class CotacaoRecepcaoFacturaIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String filtro;
    private String barato;
    private String rapido;
    private String armazem;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private void montar(String email) throws Exception {
        bearer = register(email, "Transportes Ciclo").bearer();
        filtro = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro de óleo", "category", "FILTER", "unit", "un",
                "partNumber", "1R-0716", "specification", "rosca M20"), 201).get("id").asText();
        barato = send(post("/api/v1/suppliers"), Map.of(
                "name", "Importadora Barata", "kind", "PARTS",
                "email", "vendas@barata.ao"), 201).get("id").asText();
        rapido = send(post("/api/v1/suppliers"), Map.of(
                "name", "Casa do Camião", "kind", "PARTS",
                "email", "geral@casadocamiao.ao"), 201).get("id").asText();
        armazem = send(post("/api/v1/warehouses"), Map.of("name", "Central"), 201)
                .get("id").asText();
        // Sem limites, tudo passa por aprovação do dono — que é quem testa.
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/purchase-orders/settings"),
                Map.of("autoApproveBelow", 10_000_000), 200);
    }

    private String pedido(String titulo) throws Exception {
        Map<String, Object> linha = new HashMap<>();
        linha.put("partId", filtro);
        linha.put("description", "Filtro de óleo do motor");
        linha.put("quantity", 20);
        linha.put("unit", "un");

        Map<String, Object> req = new HashMap<>();
        req.put("title", titulo);
        req.put("supplierIds", List.of(barato, rapido));
        req.put("lines", List.of(linha));
        return send(post("/api/v1/rfqs"), req, 201).get("id").asText();
    }

    private void responder(String rfqId, String supplierId, int preco, int prazo) throws Exception {
        JsonNode p = send(get("/api/v1/rfqs/" + rfqId), null, 200);
        String lineId = p.get("lines").get(0).get("id").asText();
        Map<String, Object> req = new HashMap<>();
        req.put("leadTimeDays", prazo);
        req.put("precos", List.of(Map.of("lineId", lineId, "unitPrice", preco)));
        send(post("/api/v1/rfqs/" + rfqId + "/suppliers/" + supplierId + "/response"), req, 200);
    }

    // ==== Pedir três preços =================================================

    @Test
    void aEspecificacaoVaiNoPedidoParaOsTresCotaremAMesmaCoisa() throws Exception {
        montar("ciclo1@teste.ao");
        String id = pedido("Filtros para a frota");

        JsonNode p = send(get("/api/v1/rfqs/" + id), null, 200);
        assertThat(p.get("number").asText()).startsWith("PC-");
        // Pedir «filtro de óleo» a três fornecedores e receber três preços de
        // três filtros diferentes não é uma comparação.
        assertThat(p.get("lines").get(0).get("specification").asText()).isEqualTo("rosca M20");
        assertThat(p.get("propostas")).hasSize(2);
        assertThat(p.get("leitura").asText()).contains("por responder");
    }

    @Test
    void aComparacaoDizQualEOMaisBaratoEQualEOMaisRapido() throws Exception {
        montar("ciclo2@teste.ao");
        String id = pedido("Filtros");
        responder(id, barato, 8500, 21);
        responder(id, rapido, 9200, 5);

        JsonNode p = send(get("/api/v1/rfqs/" + id), null, 200);
        JsonNode daBarata = proposta(p, barato);
        JsonNode daRapida = proposta(p, rapido);

        assertThat(daBarata.get("total").decimalValue())
                .isEqualByComparingTo(new BigDecimal("170000"));
        assertThat(daBarata.get("maisBarata").asBoolean()).isTrue();
        assertThat(daRapida.get("maisRapida").asBoolean()).isTrue();
        // Quanto é que o mais caro está acima, naquela linha.
        assertThat(daRapida.get("precos").get(0).get("acimaDoMelhorPct").asInt()).isEqualTo(8);
        assertThat(p.get("diferenca").decimalValue())
                .isEqualByComparingTo(new BigDecimal("14000"));

        // A frase que falta a quem decide.
        String leitura = p.get("leitura").asText();
        assertThat(leitura).contains("Importadora Barata").contains("Casa do Camião")
                .contains("5 dias");
    }

    @Test
    void quemNaoTemUmaLinhaNaoFicaDesclassificadoDasOutras() throws Exception {
        montar("ciclo3@teste.ao");
        String id = pedido("Filtros");
        JsonNode p0 = send(get("/api/v1/rfqs/" + id), null, 200);
        String lineId = p0.get("lines").get(0).get("id").asText();

        Map<String, Object> req = new HashMap<>();
        req.put("precos", List.of(Map.of("lineId", lineId, "unavailable", true)));
        send(post("/api/v1/rfqs/" + id + "/suppliers/" + barato + "/response"), req, 200);

        JsonNode p = send(get("/api/v1/rfqs/" + id), null, 200);
        JsonNode daBarata = proposta(p, barato);
        assertThat(daBarata.get("respondedAt").isNull()).isFalse();
        assertThat(daBarata.get("completa").asBoolean()).isFalse();
        assertThat(daBarata.get("precos").get(0).get("unavailable").asBoolean()).isTrue();
    }

    @Test
    void naoEscolherOMaisBaratoObrigaADizerPorque() throws Exception {
        montar("ciclo4@teste.ao");
        String id = pedido("Filtros");
        responder(id, barato, 8500, 21);
        responder(id, rapido, 9200, 5);

        // Escolher o mais caro pode ser a decisão certa — mas tem de ficar escrito.
        JsonNode erro = send(post("/api/v1/rfqs/" + id + "/award"),
                Map.of("supplierId", rapido), 400);
        assertThat(erro.toString()).contains("Importadora Barata");

        JsonNode adjudicado = send(post("/api/v1/rfqs/" + id + "/award"), Map.of(
                "supplierId", rapido,
                "motivo", "A máquina está parada; 16 dias mais cedo compensa a diferença."), 200);
        assertThat(adjudicado.get("status").asText()).isEqualTo("AWARDED");
        assertThat(adjudicado.get("awardedOrderId").isNull()).isFalse();

        // A ordem nasceu com o preço cotado por ele.
        JsonNode ordem = send(
                get("/api/v1/purchase-orders/" + adjudicado.get("awardedOrderId").asText()),
                null, 200);
        assertThat(ordem.get("lines").get(0).get("unitPrice").decimalValue())
                .isEqualByComparingTo(new BigDecimal("9200"));
        assertThat(ordem.get("total").decimalValue())
                .isEqualByComparingTo(new BigDecimal("184000"));

        // E o que ele cotou passa a ser o preço dele no sistema.
        JsonNode precos = send(get("/api/v1/suppliers/" + rapido + "/prices"), null, 200);
        assertThat(precos).hasSize(1);
        assertThat(precos.get(0).get("price").decimalValue())
                .isEqualByComparingTo(new BigDecimal("9200"));
    }

    @Test
    void mudarAsLinhasDepoisDeAlguemResponderTornariaAComparacaoFalsa() throws Exception {
        montar("ciclo5@teste.ao");
        String id = pedido("Filtros");
        responder(id, barato, 8500, 21);

        Map<String, Object> linha = new HashMap<>();
        linha.put("description", "Outra coisa qualquer");
        linha.put("quantity", 1);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/rfqs/" + id),
                Map.of("title", "Filtros", "supplierIds", List.of(barato),
                        "lines", List.of(linha)), 409);
    }

    // ==== Receber ===========================================================

    @Test
    void recebeSeEmPartesEOStockSoContaOQueVeioBem() throws Exception {
        montar("ciclo6@teste.ao");
        String id = pedido("Filtros");
        responder(id, barato, 8500, 21);
        JsonNode adjudicado = send(post("/api/v1/rfqs/" + id + "/award"),
                Map.of("supplierId", barato), 200);
        String ordemId = adjudicado.get("awardedOrderId").asText();
        send(post("/api/v1/purchase-orders/" + ordemId + "/submit"), null, 200);

        JsonNode ordem = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        String linhaId = ordem.get("lines").get(0).get("id").asText();

        // Primeira entrega: 12 boas e 3 partidas, de 20 encomendadas.
        Map<String, Object> primeira = new HashMap<>();
        primeira.put("warehouseId", armazem);
        primeira.put("deliveryNote", "GR-4411");
        primeira.put("lines", List.of(Map.of(
                "orderLineId", linhaId, "quantity", 12,
                "rejectedQuantity", 3, "rejectionReason", "Caixas amolgadas")));
        JsonNode recepcao = send(
                post("/api/v1/purchase-orders/" + ordemId + "/receipts"), primeira, 201);
        assertThat(recepcao.get("number").asText()).startsWith("GR-");

        JsonNode depois = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        assertThat(depois.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
        // Só o que veio bem conta como recebido.
        assertThat(depois.get("lines").get(0).get("receivedQuantity").decimalValue())
                .isEqualByComparingTo(new BigDecimal("12"));

        // E só o que veio bem entrou no armazém.
        JsonNode pecas = send(get("/api/v1/parts"), null, 200);
        JsonNode p = null;
        for (JsonNode x : pecas) {
            if (filtro.equals(x.get("id").asText())) {
                p = x;
            }
        }
        assertThat(p).isNotNull();
        assertThat(p.get("totalQuantity").decimalValue())
                .isEqualByComparingTo(new BigDecimal("12"));

        // O resto chega depois e a ordem fecha.
        Map<String, Object> segunda = new HashMap<>();
        segunda.put("warehouseId", armazem);
        segunda.put("lines", List.of(Map.of("orderLineId", linhaId, "quantity", 8)));
        send(post("/api/v1/purchase-orders/" + ordemId + "/receipts"), segunda, 201);

        JsonNode fechada = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        assertThat(fechada.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(send(get("/api/v1/purchase-orders/" + ordemId + "/receipts"), null, 200))
                .hasSize(2);

        // E o painel deixa de dizer que vem alguma coisa a caminho.
        assertThat(send(get("/api/v1/purchase-orders/on-order"), null, 200).size()).isZero();
    }

    @Test
    void receberMaisDoQueFoiEncomendadoEUmErroDeDigitacao() throws Exception {
        montar("ciclo7@teste.ao");
        String ordemId = ordemDirecta(20, 8500);
        JsonNode ordem = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        String linhaId = ordem.get("lines").get(0).get("id").asText();

        Map<String, Object> req = new HashMap<>();
        req.put("warehouseId", armazem);
        req.put("lines", List.of(Map.of("orderLineId", linhaId, "quantity", 50)));
        JsonNode erro = send(post("/api/v1/purchase-orders/" + ordemId + "/receipts"), req, 400);
        assertThat(erro.toString()).contains("só faltam");
    }

    // ==== Facturar ==========================================================

    @Test
    void aFacturaAcimaDaEncomendaEAssinalada() throws Exception {
        montar("ciclo8@teste.ao");
        String ordemId = ordemDirecta(10, 8500);

        Map<String, Object> req = new HashMap<>();
        req.put("number", "FT 2026/1180");
        req.put("orderId", ordemId);
        req.put("amount", 92000);
        req.put("issuedAt", LocalDate.now(ZoneOffset.UTC).toString());
        req.put("dueAt", LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
        JsonNode f = send(post("/api/v1/suppliers/" + barato + "/invoices"), req, 201);

        // 92 000 contra 85 000 da encomenda.
        assertThat(f.get("conferencia").asText()).contains("ACIMA").contains("7000");
        assertThat(f.get("statusLabel").asText()).isEqualTo("Por pagar");
        assertThat(f.get("diasAteVencer").asInt()).isEqualTo(30);

        // A mesma factura duas vezes é o caminho para pagar duas vezes.
        send(post("/api/v1/suppliers/" + barato + "/invoices"), req, 409);
    }

    @Test
    void aFacturaLevaOPapelDigitalizadoEMarcaSeComoPaga() throws Exception {
        montar("ciclo9@teste.ao");

        Map<String, Object> dados = new HashMap<>();
        dados.put("number", "FT 2026/2000");
        dados.put("amount", 50000);
        MockMultipartFile corpo = new MockMultipartFile("dados", "", "application/json",
                json.writeValueAsBytes(dados));
        MockMultipartFile pdf = new MockMultipartFile("ficheiro", "factura.pdf",
                "application/pdf", "%PDF-1.4 factura".getBytes(StandardCharsets.UTF_8));

        MvcResult r = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/suppliers/" + barato + "/invoices")
                        .file(corpo).file(pdf)
                        .header("Authorization", bearer))
                .andExpect(status().isCreated()).andReturn();
        JsonNode f = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(f.get("fileName").asText()).isEqualTo("factura.pdf");
        assertThat(f.get("fileId").asText()).isNotBlank();

        JsonNode paga = send(post("/api/v1/supplier-invoices/" + f.get("id").asText() + "/pay"),
                null, 200);
        assertThat(paga.get("status").asText()).isEqualTo("PAID");
        assertThat(paga.get("paidAt").isNull()).isFalse();

        // Uma factura paga não se apaga: o pagamento aconteceu.
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/supplier-invoices/" + f.get("id").asText()), null, 409);
    }

    @Test
    void aRetencaoSaiDoQueSePaga() throws Exception {
        montar("ciclo10@teste.ao");
        Map<String, Object> req = new HashMap<>();
        req.put("number", "FT 2026/3000");
        req.put("amount", 100000);
        req.put("withholdingAmount", 6500);
        JsonNode f = send(post("/api/v1/suppliers/" + barato + "/invoices"), req, 201);
        assertThat(f.get("aPagar").decimalValue()).isEqualByComparingTo(new BigDecimal("93500"));
    }

    // ==== A classificação ===================================================

    @Test
    void aClassificacaoMedeOQueAconteceuENaoOpinioes() throws Exception {
        montar("ciclo11@teste.ao");

        JsonNode semNada = send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200);
        assertThat(semNada.get("ordens").asInt()).isZero();
        assertThat(semNada.get("leitura").asText()).contains("Ainda não houve nenhuma encomenda");
        // Sem dados não se inventa uma nota.
        for (JsonNode m : semNada.get("medidas")) {
            assertThat(m.get("estado").asText()).isEqualTo("SEM_DADOS");
        }

        // Uma entrega com devoluções estraga a qualidade, e isso vê-se.
        String ordemId = ordemDirecta(10, 8500);
        JsonNode ordem = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        String linhaId = ordem.get("lines").get(0).get("id").asText();
        Map<String, Object> rec = new HashMap<>();
        rec.put("warehouseId", armazem);
        rec.put("lines", List.of(Map.of(
                "orderLineId", linhaId, "quantity", 8,
                "rejectedQuantity", 2, "rejectionReason", "Fora de especificação")));
        send(post("/api/v1/purchase-orders/" + ordemId + "/receipts"), rec, 201);

        JsonNode ficha = send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200);
        assertThat(ficha.get("ordens").asInt()).isEqualTo(1);
        JsonNode qualidade = medida(ficha, "Qualidade");
        assertThat(qualidade.get("valor").asInt()).isEqualTo(80);
        assertThat(qualidade.get("detalhe").asText()).contains("Devolvidas");
        assertThat(qualidade.get("estado").asText()).isEqualTo("MAU");
        assertThat(ficha.get("leitura").asText()).contains("falha em qualidade");
    }

    // ==== O que a volta completa apanhou ====================================

    @Test
    void aOrdemHerdaOPrazoQueOFornecedorPrometeu() throws Exception {
        // Encontrado a fazer a volta completa: a ordem nascia sem data de
        // entrega e a pontualidade deste fornecedor nunca se chegava a medir,
        // porque so se mede contra a data pedida.
        montar("volta1@teste.ao");
        String id = pedido("Filtros");
        responder(id, rapido, 9200, 5);
        JsonNode adj = send(post("/api/v1/rfqs/" + id + "/award"), Map.of(
                "supplierId", rapido, "motivo", "Unico que respondeu"), 200);

        JsonNode ordem = send(get("/api/v1/purchase-orders/"
                + adj.get("awardedOrderId").asText()), null, 200);
        assertThat(ordem.get("expectedAt").asText())
                .as("cinco dias a partir de hoje, que foi o que ele prometeu")
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(5).toString());
        assertThat(ordem.get("notes").asText()).contains("prazo prometido: 5 dias");
    }

    @Test
    void quemNaoRespondeContaMesmoQuandoSePedePorTelefone() throws Exception {
        // Tambem da volta: a medida de resposta so contava pedidos enviados por
        // email, e ficava vazia para sempre em quem pede preco por telefone --
        // que e metade de Angola.
        montar("volta2@teste.ao");
        String id = pedido("Filtros");
        responder(id, rapido, 9200, 5);
        // O outro foi convidado e nunca respondeu.

        // Quem respondeu conta logo; quem ficou calado so conta quando o pedido
        // fecha -- ate la ainda pode responder, e penaliza-lo seria injusto.
        assertThat(medida(send(get("/api/v1/suppliers/" + rapido + "/scorecard"), null, 200),
                "Resposta a pedidos").get("valor").asInt()).isEqualTo(100);
        assertThat(medida(send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200),
                "Resposta a pedidos").get("estado").asText()).isEqualTo("SEM_DADOS");

        send(post("/api/v1/rfqs/" + id + "/award"),
                Map.of("supplierId", rapido, "motivo", "O outro nem respondeu"), 200);

        // Fechado o pedido, quem respondeu tem 100% e quem ficou calado tem 0%.
        JsonNode quemRespondeu = send(get("/api/v1/suppliers/" + rapido + "/scorecard"), null, 200);
        assertThat(medida(quemRespondeu, "Resposta a pedidos").get("valor").asInt()).isEqualTo(100);

        JsonNode quemCalou = send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200);
        JsonNode m = medida(quemCalou, "Resposta a pedidos");
        assertThat(m.get("valor").asInt()).isZero();
        assertThat(m.get("detalhe").asText()).contains("0 de 1");
    }

    // ==== O que veio mal ====================================================

    @Test
    void oQueVeioMalFicaNumaListaAteAlguemOResolver() throws Exception {
        montar("dev1@teste.ao");
        String ordemId = ordemDirecta(10, 8500);
        JsonNode ordem = send(get("/api/v1/purchase-orders/" + ordemId), null, 200);
        String linhaId = ordem.get("lines").get(0).get("id").asText();

        Map<String, Object> rec = new HashMap<>();
        rec.put("warehouseId", armazem);
        rec.put("lines", List.of(Map.of(
                "orderLineId", linhaId, "quantity", 8,
                "rejectedQuantity", 2, "rejectionReason", "Caixas molhadas")));
        send(post("/api/v1/purchase-orders/" + ordemId + "/receipts"), rec, 201);

        // Antes, isto não gerava nada e a caixa ficava a um canto do armazém.
        JsonNode pendentes = send(get("/api/v1/purchase-returns"), null, 200);
        assertThat(pendentes).hasSize(1);
        JsonNode d = pendentes.get(0);
        assertThat(d.get("description").asText()).contains("Filtro");
        assertThat(d.get("quantity").decimalValue()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(d.get("reason").asText()).isEqualTo("Caixas molhadas");
        assertThat(d.get("resolutionLabel").asText()).isEqualTo("Por resolver");
        // Quanto vale, ao preço da encomenda: é o que o fornecedor deve.
        assertThat(d.get("valor").decimalValue()).isEqualByComparingTo(new BigDecimal("17000"));

        // E a qualidade dele diz que ficou por resolver.
        JsonNode ficha = send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200);
        assertThat(medida(ficha, "Qualidade").get("detalhe").asText())
                .contains("2 ainda por resolver");

        // Uma nota de crédito sem número não se concilia com nada.
        send(post("/api/v1/purchase-returns/" + d.get("id").asText() + "/resolve"),
                Map.of("resolution", "CREDITED"), 400);

        JsonNode resolvida = send(
                post("/api/v1/purchase-returns/" + d.get("id").asText() + "/resolve"),
                Map.of("resolution", "CREDITED", "creditNote", "NC 2026/88",
                        "creditAmount", 17000), 200);
        assertThat(resolvida.get("resolutionLabel").asText()).isEqualTo("Creditada");
        assertThat(resolvida.get("creditNote").asText()).isEqualTo("NC 2026/88");

        // Sai da lista do que há a fazer, mas continua a poder ser consultada.
        assertThat(send(get("/api/v1/purchase-returns"), null, 200)).isEmpty();
        assertThat(send(get("/api/v1/purchase-returns?pendingOnly=false"), null, 200)).hasSize(1);

        JsonNode depois = send(get("/api/v1/suppliers/" + barato + "/scorecard"), null, 200);
        assertThat(medida(depois, "Qualidade").get("detalhe").asText())
                .contains("todas resolvidas");
    }

    @Test
    void haQuatroDesfechosPorqueNemTudoSeDevolve() throws Exception {
        montar("dev2@teste.ao");
        String ordemId = ordemDirecta(10, 8500);
        String linhaId = send(get("/api/v1/purchase-orders/" + ordemId), null, 200)
                .get("lines").get(0).get("id").asText();
        Map<String, Object> rec = new HashMap<>();
        rec.put("warehouseId", armazem);
        rec.put("lines", List.of(Map.of(
                "orderLineId", linhaId, "quantity", 9,
                "rejectedQuantity", 1, "rejectionReason", "Rosca errada")));
        send(post("/api/v1/purchase-orders/" + ordemId + "/receipts"), rec, 201);
        String id = send(get("/api/v1/purchase-returns"), null, 200).get(0).get("id").asText();

        // Às vezes não compensa o frete de volta — e isso tem de caber.
        JsonNode aceite = send(post("/api/v1/purchase-returns/" + id + "/resolve"),
                Map.of("resolution", "ACCEPTED",
                        "note", "Uma unidade só; não compensa o transporte de volta."), 200);
        assertThat(aceite.get("resolutionLabel").asText()).isEqualTo("Aceite assim mesmo");
        assertThat(aceite.get("resolutionNote").asText()).contains("não compensa");

        send(post("/api/v1/purchase-returns/" + id + "/resolve"),
                Map.of("resolution", "INVENTADO"), 400);
    }

    // ==== Auxiliares ========================================================

    /** Uma ordem aprovada directamente, sem passar por cotação. */
    private String ordemDirecta(int quantidade, int preco) throws Exception {
        Map<String, Object> linha = new HashMap<>();
        linha.put("partId", filtro);
        linha.put("description", "Filtro de óleo do motor");
        linha.put("quantity", quantidade);
        linha.put("unitPrice", preco);
        List<Map<String, Object>> linhas = new ArrayList<>();
        linhas.add(linha);

        String id = send(post("/api/v1/purchase-orders"),
                Map.of("supplierId", barato, "lines", linhas), 201).get("id").asText();
        send(post("/api/v1/purchase-orders/" + id + "/submit"), null, 200);
        return id;
    }

    private static JsonNode proposta(JsonNode pedido, String supplierId) {
        for (JsonNode p : pedido.get("propostas")) {
            if (supplierId.equals(p.get("supplierId").asText())) {
                return p;
            }
        }
        throw new AssertionError("Proposta não encontrada");
    }

    private static JsonNode medida(JsonNode ficha, String nome) {
        for (JsonNode m : ficha.get("medidas")) {
            if (nome.equals(m.get("nome").asText())) {
                return m;
            }
        }
        throw new AssertionError("Medida não encontrada: " + nome);
    }
}
