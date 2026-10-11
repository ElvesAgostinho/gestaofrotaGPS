package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O fornecedor deixa de ser um nome e um telefone.
 *
 * <p>O que lá estava servia para registar que a oficina X arranjou a viatura.
 * Para <b>comprar</b> é preciso outra coisa: a quem se manda a encomenda (e não
 * é a mesma pessoa a quem se manda a factura), se os papéis dele estão em
 * ordem, e sobretudo <b>a quanto e em quantos dias</b>.
 *
 * <p>O prazo é o número que mais decide, e é por isso que o teste insiste
 * nele: quem importa tem de somar o despacho ao trânsito, porque uma peça só
 * serve quando sai da alfândega — não quando embarca.
 */
class FornecedoresEPrecosIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private String peca(String nome, String referencia) throws Exception {
        return send(post("/api/v1/parts"), Map.of(
                "name", nome, "category", "FILTER", "unit", "un", "partNumber", referencia), 201)
                .get("id").asText();
    }

    // ==== O prazo que conta =================================================

    @Test
    void oPrazoDeQuemImportaSomaODespachoAoTransito() throws Exception {
        bearer = register("forn1@teste.ao", "Transportes Prazo").bearer();

        Map<String, Object> importador = new HashMap<>();
        importador.put("name", "Peças do Oriente");
        importador.put("kind", "PARTS");
        importador.put("importer", true);
        importador.put("incoterm", "CIF");
        importador.put("avgLeadTimeDays", 28);
        importador.put("customsDays", 12);
        JsonNode criado = send(post("/api/v1/suppliers"), importador, 201);
        assertThat(criado.get("prazoTotalDias").asInt())
                .as("28 dias de trânsito mais 12 de despacho")
                .isEqualTo(40);

        Map<String, Object> local = new HashMap<>();
        local.put("name", "Casa do Camião");
        local.put("kind", "PARTS");
        local.put("avgLeadTimeDays", 5);
        assertThat(send(post("/api/v1/suppliers"), local, 201).get("prazoTotalDias").asInt())
                .isEqualTo(5);
    }

    @Test
    void osDadosFiscaisEBancariosFicamGuardados() throws Exception {
        bearer = register("forn2@teste.ao", "Transportes Fiscal").bearer();

        Map<String, Object> req = new HashMap<>();
        req.put("name", "Serviços Mecânicos Luanda");
        req.put("kind", "WORKSHOP");
        req.put("taxId", "5417123456");
        req.put("vatRegime", "GERAL");
        req.put("withholdingTax", 6.5);
        req.put("iban", "AO06 0040 0000 1234 5678 9012 3");
        req.put("bankName", "BFA");
        req.put("minOrderValue", 50000);
        String id = send(post("/api/v1/suppliers"), req, 201).get("id").asText();

        JsonNode lista = send(get("/api/v1/suppliers"), null, 200);
        JsonNode f = null;
        for (JsonNode x : lista) {
            if (id.equals(x.get("id").asText())) {
                f = x;
            }
        }
        assertThat(f).isNotNull();
        // A retenção na fonte não é um detalhe de contabilista: é a diferença
        // entre o valor da factura e o valor a transferir.
        assertThat(f.get("withholdingTax").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("6.50"));
        assertThat(f.get("iban").asText()).startsWith("AO06");
        assertThat(f.get("vatRegime").asText()).isEqualTo("GERAL");
    }

    // ==== As pessoas ========================================================

    @Test
    void soHaUmContactoPrincipalEEDeleQueSaiAOrdemDeCompra() throws Exception {
        bearer = register("forn3@teste.ao", "Transportes Contacto").bearer();
        String id = send(post("/api/v1/suppliers"),
                Map.of("name", "Auto Peças", "kind", "PARTS"), 201).get("id").asText();

        send(post("/api/v1/suppliers/" + id + "/contacts"), Map.of(
                "name", "Mário Vendas", "role", "SALES",
                "email", "mario@autopecas.ao", "primaryContact", true), 201);
        send(post("/api/v1/suppliers/" + id + "/contacts"), Map.of(
                "name", "Ana Contas", "role", "FINANCE",
                "email", "ana@autopecas.ao"), 201);

        JsonNode contactos = send(get("/api/v1/suppliers/" + id + "/contacts"), null, 200);
        assertThat(contactos).hasSize(2);
        assertThat(contactos.get(0).get("primaryContact").asBoolean())
                .as("o principal vem primeiro").isTrue();
        assertThat(contactos.get(0).get("roleLabel").asText()).isEqualTo("Comercial");

        // Promover outro despromove o anterior: dois principais é não ter nenhum.
        String segundoId = contactos.get(1).get("id").asText();
        send(put("/api/v1/supplier-contacts/" + segundoId), Map.of(
                "name", "Ana Contas", "role", "FINANCE",
                "email", "ana@autopecas.ao", "primaryContact", true), 200);

        JsonNode depois = send(get("/api/v1/suppliers/" + id + "/contacts"), null, 200);
        int principais = 0;
        for (JsonNode c : depois) {
            if (c.get("primaryContact").asBoolean()) {
                principais++;
            }
        }
        assertThat(principais).isEqualTo(1);
    }

    // ==== Os papéis =========================================================

    @Test
    void osDocumentosDizemSeEstaoForaDePrazo() throws Exception {
        bearer = register("forn4@teste.ao", "Transportes Papéis").bearer();
        String id = send(post("/api/v1/suppliers"),
                Map.of("name", "Oficina Central", "kind", "WORKSHOP"), 201).get("id").asText();

        LocalDate hoje = LocalDate.now(java.time.ZoneOffset.UTC);
        send(post("/api/v1/suppliers/" + id + "/documents"), Map.of(
                "kind", "ALVARA", "number", "ALV-99",
                "expiresAt", hoje.plusYears(1).toString()), 201);
        send(post("/api/v1/suppliers/" + id + "/documents"), Map.of(
                "kind", "CERTIDAO_NAO_DIVIDA",
                "expiresAt", hoje.plusDays(10).toString()), 201);
        send(post("/api/v1/suppliers/" + id + "/documents"), Map.of(
                "kind", "DECLARACAO_INICIO",
                "expiresAt", hoje.minusDays(30).toString()), 201);

        JsonNode docs = send(get("/api/v1/suppliers/" + id + "/documents"), null, 200);
        assertThat(docs).hasSize(3);
        // Vêm pela validade: o que já expirou primeiro, que é o que urge.
        assertThat(docs.get(0).get("estado").asText()).isEqualTo("EXPIRADO");
        assertThat(docs.get(0).get("kindLabel").asText()).contains("início de actividade");
        assertThat(docs.get(1).get("estado").asText()).isEqualTo("A_EXPIRAR");
        assertThat(docs.get(1).get("diasAteExpirar").asInt()).isEqualTo(10);
        assertThat(docs.get(2).get("estado").asText()).isEqualTo("VALIDO");

        // A validade não pode ser anterior à emissão.
        send(post("/api/v1/suppliers/" + id + "/documents"), Map.of(
                "kind", "SEGURO",
                "issuedAt", hoje.toString(),
                "expiresAt", hoje.minusDays(1).toString()), 400);
    }

    // ==== Os preços =========================================================

    @Test
    void oMaisBaratoEOMaisRapidoSaoFornecedoresDiferentes() throws Exception {
        bearer = register("forn5@teste.ao", "Transportes Preço").bearer();
        String filtro = peca("Filtro de combustível", "23401-1332");

        Map<String, Object> barato = new HashMap<>();
        barato.put("name", "Importadora Barata");
        barato.put("kind", "PARTS");
        barato.put("importer", true);
        barato.put("avgLeadTimeDays", 21);
        String baratoId = send(post("/api/v1/suppliers"), barato, 201).get("id").asText();

        Map<String, Object> rapido = new HashMap<>();
        rapido.put("name", "Casa do Camião");
        rapido.put("kind", "PARTS");
        rapido.put("avgLeadTimeDays", 5);
        String rapidoId = send(post("/api/v1/suppliers"), rapido, 201).get("id").asText();

        JsonNode p1 = send(post("/api/v1/suppliers/" + baratoId + "/prices"), Map.of(
                "partId", filtro, "price", 8500, "supplierPartNumber", "WK 842/2"), 201);
        JsonNode p2 = send(post("/api/v1/suppliers/" + rapidoId + "/prices"), Map.of(
                "partId", filtro, "price", 9200), 201);

        // O prazo da cotação, quando não é indicado, é o do fornecedor.
        assertThat(p1.get("leadTimeDays").asInt()).isEqualTo(21);
        assertThat(p2.get("leadTimeDays").asInt()).isEqualTo(5);

        JsonNode comparacao = send(get("/api/v1/parts/" + filtro + "/prices"), null, 200);
        assertThat(comparacao.get("precos")).hasSize(2);
        assertThat(comparacao.get("maisBaratoId").asText()).isEqualTo(p1.get("id").asText());
        assertThat(comparacao.get("maisRapidoId").asText()).isEqualTo(p2.get("id").asText());
    }

    @Test
    void cotarDeNovoSubstituiEmVezDeAcumular() throws Exception {
        bearer = register("forn6@teste.ao", "Transportes Cotação").bearer();
        String filtro = peca("Filtro de ar", "17801-1");
        String id = send(post("/api/v1/suppliers"),
                Map.of("name", "Auto Peças", "kind", "PARTS"), 201).get("id").asText();

        send(post("/api/v1/suppliers/" + id + "/prices"),
                Map.of("partId", filtro, "price", 8000), 201);
        send(post("/api/v1/suppliers/" + id + "/prices"),
                Map.of("partId", filtro, "price", 9500), 201);

        JsonNode precos = send(get("/api/v1/suppliers/" + id + "/prices"), null, 200);
        assertThat(precos).as("um fornecedor tem um preço por peça").hasSize(1);
        assertThat(precos.get(0).get("price").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("9500"));
    }

    @Test
    void umaCotacaoForaDeValidadeNaoGanhaOMaisBarato() throws Exception {
        bearer = register("forn7@teste.ao", "Transportes Validade").bearer();
        String filtro = peca("Filtro hidráulico", "HF-6553");
        LocalDate hoje = LocalDate.now(java.time.ZoneOffset.UTC);

        String velho = send(post("/api/v1/suppliers"),
                Map.of("name", "Preço de Antigamente", "kind", "PARTS"), 201).get("id").asText();
        String actual = send(post("/api/v1/suppliers"),
                Map.of("name", "Preço de Hoje", "kind", "PARTS"), 201).get("id").asText();

        // Um preço de há dois anos ganharia sempre, e mandava comprar onde já
        // não é barato.
        send(post("/api/v1/suppliers/" + velho + "/prices"), Map.of(
                "partId", filtro, "price", 3000,
                "quotedAt", hoje.minusYears(2).toString(),
                "validUntil", hoje.minusYears(2).plusMonths(1).toString()), 201);
        JsonNode firme = send(post("/api/v1/suppliers/" + actual + "/prices"), Map.of(
                "partId", filtro, "price", 7000,
                "validUntil", hoje.plusMonths(3).toString()), 201);

        JsonNode comparacao = send(get("/api/v1/parts/" + filtro + "/prices"), null, 200);
        assertThat(comparacao.get("maisBaratoId").asText())
                .as("entre cotações firmes, não a histórica")
                .isEqualTo(firme.get("id").asText());
        // Mas a histórica continua à vista, marcada.
        boolean temExpirada = false;
        for (JsonNode p : comparacao.get("precos")) {
            if (p.get("expirada").asBoolean()) {
                temExpirada = true;
            }
        }
        assertThat(temExpirada).isTrue();
    }

    // ==== Quem pode ver =====================================================

    @Test
    void oTecnicoVeOsContactosMasNaoOsPrecos() throws Exception {
        bearer = register("forn8@teste.ao", "Transportes Acesso").bearer();
        String id = send(post("/api/v1/suppliers"),
                Map.of("name", "Auto Peças", "kind", "PARTS"), 201).get("id").asText();
        send(post("/api/v1/suppliers/" + id + "/contacts"),
                Map.of("name", "Mário", "role", "SALES", "phone", "+244 923 000 111"), 201);

        String convite = send(post("/api/v1/team/invitations"),
                Map.of("email", "tecnico@acesso.ao", "role", "TECHNICIAN"), 201)
                .get("token").asText();
        MvcResult aceite = mvc.perform(post("/api/v1/invitations/" + convite + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Técnico Acesso", "password", "Tecnico!2026"))))
                .andExpect(status().isOk()).andReturn();
        String tecnico = "Bearer " + json.readTree(aceite.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        // Precisa de saber a quem telefonar quando a peça não chega.
        mvc.perform(get("/api/v1/suppliers/" + id + "/contacts").header("Authorization", tecnico))
                .andExpect(status().isOk());
        // Mas dinheiro é de quem o pode ver.
        mvc.perform(get("/api/v1/suppliers/" + id + "/prices").header("Authorization", tecnico))
                .andExpect(status().isForbidden());
    }
}
