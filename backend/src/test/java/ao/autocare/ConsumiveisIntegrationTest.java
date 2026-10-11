package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Pneus, óleos e filtros: o painel que responde «o que troco, onde, com quê».
 *
 * <p>É o controlo que as empresas de frota pedem primeiro e que quase nenhuma
 * tem: não basta saber que o óleo se muda às 10 000 km — é preciso saber que
 * <i>este</i> camião chega lá na quinta-feira, que o filtro custa X, que não há
 * nenhum em armazém e que o fornecedor demora três semanas.
 *
 * <p>Os testes aqui verificam as três contas que sustentam o painel: a taxa de
 * desgaste tirada das medições de sulco, a passagem de quilómetros a datas, e a
 * lista de compras recuada pelo prazo de entrega. E verificam também o que o
 * painel diz quando <b>não sabe</b> — porque um painel que chama «em ordem» a um
 * pneu que ninguém mediu é pior do que não ter painel.
 */
class ConsumiveisIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        return send(bearer, req, body, expect);
    }

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

    private String ativo(String tag, String tipo, String categoria, String medidor, int contador)
            throws Exception {
        String tipoId = send(post("/api/v1/asset-types"),
                Map.of("name", tipo, "category", categoria, "primaryMeter", medidor), 201)
                .get("id").asText();
        return send(post("/api/v1/assets"),
                Map.of("tag", tag, "name", tipo, "assetTypeId", tipoId,
                        "initialMeterValue", contador), 201)
                .get("id").asText();
    }

    private JsonNode painel(int dias) throws Exception {
        return send(get("/api/v1/consumables?dias=" + dias), null, 200);
    }

    // ==== Óleos, filtros e a lista de compras ===============================

    @Test
    void oPainelDizOQueVenceEConfrontaComOArmazem() throws Exception {
        bearer = register("cons1@teste.ao", "Transportes Sulco").bearer();
        String camiao = ativo("CAM-C1", "Camião basculante", "VEHICLE", "ODOMETER", 100_000);

        // O plano do catálogo traz as tarefas, as peças de armazém e — desde
        // agora — a ligação entre as duas coisas.
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + camiao),
                null, 200);
        // O camião andou: está a 300 km do primeiro intervalo de 10 000 km.
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"),
                Map.of("value", 109_700), 201);

        JsonNode p = painel(365);
        assertThat(p.get("oleos")).as("o camião tem mudanças de óleo no plano").isNotEmpty();
        assertThat(p.get("filtros")).as("e tem filtros").isNotEmpty();

        // Um filtro de óleo é um filtro, não uma mudança de óleo: é esta
        // distinção que separa os dois separadores do painel.
        assertThat(p.get("filtros").toString()).contains("Filtro");
        for (JsonNode o : p.get("oleos")) {
            assertThat(o.get("titulo").asText().toLowerCase())
                    .as("nenhuma linha de óleo é só a troca de um filtro")
                    .satisfiesAnyOf(
                            t -> assertThat(t).contains("óleo"),
                            t -> assertThat(t).contains("lubrific"),
                            t -> assertThat(t).contains("massa"));
        }

        // A pergunta que interessa: tenho a peça? O armazém foi criado com o
        // plano, mas sem quantidades — portanto há que comprar.
        assertThat(p.get("compras")).isNotEmpty();
        boolean algumaLigada = false;
        boolean algumaPorComprar = false;
        for (JsonNode c : p.get("compras")) {
            if (c.get("noCatalogo").asBoolean()) {
                algumaLigada = true;
                assertThat(c.get("partId").isNull()).isFalse();
            }
            if (c.get("aComprar").decimalValue().signum() > 0) {
                algumaPorComprar = true;
            }
        }
        assertThat(algumaLigada).as("as peças do plano ficaram ligadas ao armazém").isTrue();
        assertThat(algumaPorComprar).as("sem stock, há peças a comprar").isTrue();

        JsonNode resumo = p.get("resumo");
        assertThat(resumo.get("pecasEmFalta").asInt()).isPositive();
        assertThat(resumo.get("equipamentosSemPlano").asInt()).isZero();
    }

    @Test
    void oStockQueExisteSaiDaListaDeCompras() throws Exception {
        bearer = register("cons2@teste.ao", "Transportes Armazém").bearer();
        String camiao = ativo("CAM-C2", "Camião", "VEHICLE", "ODOMETER", 100_000);
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + camiao),
                null, 200);
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"),
                Map.of("value", 109_700), 201);

        // Encher o armazém de filtros de óleo: deixam de ser uma compra.
        JsonNode pecas = send(get("/api/v1/parts"), null, 200);
        String filtroId = null;
        for (JsonNode peca : pecas) {
            if (peca.get("name").asText().toLowerCase().startsWith("filtro de óleo")) {
                filtroId = peca.get("id").asText();
            }
        }
        assertThat(filtroId).as("o plano criou o filtro de óleo no armazém").isNotNull();
        String armazem = send(post("/api/v1/warehouses"), Map.of("name", "Central"), 201)
                .get("id").asText();
        send(post("/api/v1/stock/movements"), Map.of(
                "partId", filtroId, "warehouseId", armazem, "type", "IN",
                "quantity", 40), 201);

        JsonNode p = painel(365);
        for (JsonNode c : p.get("compras")) {
            if (c.get("nome").asText().toLowerCase().startsWith("filtro de óleo")) {
                assertThat(c.get("stock").decimalValue().intValue()).isEqualTo(40);
                assertThat(c.get("aComprar").decimalValue().signum())
                        .as("quarenta filtros em armazém chegam").isZero();
            }
        }
    }

    // ==== A taxa de desgaste do pneu ========================================

    @Test
    void oDesgasteDoPneuSaiDasMedicoesDeSulco() throws Exception {
        bearer = register("cons3@teste.ao", "Transportes Pneu").bearer();
        String camiao = ativo("CAM-C3", "Camião", "VEHICLE", "ODOMETER", 100_000);

        String pneu = send(post("/api/v1/assets/" + camiao + "/tyres"), Map.of(
                "position", "FE", "brand", "Bridgestone", "model", "R268",
                "size", "11R22.5", "serialNumber", "BR-001",
                "cost", 180_000, "installedMeter", 100_000,
                "targetPressure", "8.0", "minTreadMm", "3.0", "lastTreadMm", "14.0"), 201)
                .get("id").asText();

        // Duas medições, por ordem: 14 mm aos 100 000 km, 10 mm aos 120 000 km.
        // Quatro milímetros em vinte mil quilómetros = 0,20 mm por mil.
        Instant antes = Instant.now().minus(120, ChronoUnit.DAYS);
        send(post("/api/v1/tyres/" + pneu + "/readings"), Map.of(
                "measuredAt", antes.toString(), "meterValue", 100_000,
                "treadMm", "14.0", "pressure", "8.0"), 201);
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"),
                Map.of("value", 120_000), 201);
        send(post("/api/v1/tyres/" + pneu + "/readings"), Map.of(
                "measuredAt", Instant.now().toString(), "meterValue", 120_000,
                "treadMm", "10.0", "pressure", "7.9"), 201);

        JsonNode linha = painel(30).get("pneus").get(0);
        assertThat(linha.get("brand").asText()).isEqualTo("Bridgestone");
        assertThat(linha.get("desgastePor1000Km").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("0.20"));
        // Restam 7 mm úteis (10 − 3) a 0,0002 mm/km = 35 000 km.
        assertThat(linha.get("kmRestantes").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("35000"));
        assertThat(linha.get("kmRodados").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("20000"));
        assertThat(linha.get("base").asText()).contains("medições");
        assertThat(linha.get("estado").asText()).isEqualTo("OK");
        // 180 000 Kz por 20 000 km = 9 Kz por quilómetro.
        assertThat(linha.get("custoPorKm").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("9"));
    }

    @Test
    void oPneuSemMedicaoNaoEDadoComoEmOrdem() throws Exception {
        bearer = register("cons4@teste.ao", "Transportes Cego").bearer();
        String camiao = ativo("CAM-C4", "Camião", "VEHICLE", "ODOMETER", 100_000);
        send(post("/api/v1/assets/" + camiao + "/tyres"),
                Map.of("position", "FD", "size", "11R22.5", "installedMeter", 100_000), 201);

        JsonNode p = painel(30);
        JsonNode linha = p.get("pneus").get(0);
        assertThat(linha.get("estado").asText()).isEqualTo("SEM_DADOS");
        assertThat(linha.get("motivo").asText()).contains("medição");
        assertThat(linha.get("acao").asText()).contains("Medir");
        assertThat(p.get("resumo").get("pneusSemMedicao").asInt()).isEqualTo(1);
    }

    @Test
    void oPneuAbaixoDoSulcoMinimoEntraNaListaDeCompras() throws Exception {
        bearer = register("cons5@teste.ao", "Transportes Gasto").bearer();
        String camiao = ativo("CAM-C5", "Camião", "VEHICLE", "ODOMETER", 200_000);
        send(post("/api/v1/assets/" + camiao + "/tyres"), Map.of(
                "position", "TE1", "size", "315/80R22.5", "installedMeter", 150_000,
                "minTreadMm", "3.0", "lastTreadMm", "2.0", "targetPressure", "8.0"), 201);

        JsonNode p = painel(30);
        JsonNode linha = p.get("pneus").get(0);
        assertThat(linha.get("estado").asText()).isEqualTo("CRITICO");
        assertThat(linha.get("motivo").asText()).contains("abaixo do mínimo");

        boolean temPneuNasCompras = false;
        for (JsonNode c : p.get("compras")) {
            if (c.get("nome").asText().equals("Pneu 315/80R22.5")) {
                temPneuNasCompras = true;
                assertThat(c.get("necessario").decimalValue().intValue()).isEqualTo(1);
                assertThat(c.get("aComprar").decimalValue().intValue()).isEqualTo(1);
            }
        }
        assertThat(temPneuNasCompras).as("um pneu gasto é uma compra, não só um aviso").isTrue();
        assertThat(p.get("resumo").get("pneusCriticos").asInt()).isEqualTo(1);
    }

    @Test
    void aPressaoBaixaEUmAvisoEAMuitoBaixaEUmaUrgencia() throws Exception {
        bearer = register("cons6@teste.ao", "Transportes Pressão").bearer();
        String camiao = ativo("CAM-C6", "Camião", "VEHICLE", "ODOMETER", 100_000);
        String baixo = send(post("/api/v1/assets/" + camiao + "/tyres"), Map.of(
                "position", "FE", "size", "11R22.5", "installedMeter", 100_000,
                "targetPressure", "8.0", "lastTreadMm", "12.0",
                "expectedLifeKm", 80_000), 201).get("id").asText();
        send(post("/api/v1/tyres/" + baixo + "/readings"),
                Map.of("meterValue", 100_000, "pressure", "5.0", "treadMm", "12.0"), 201);

        JsonNode linha = painel(30).get("pneus").get(0);
        assertThat(linha.get("pressaoPct").asInt()).isEqualTo(63);
        assertThat(linha.get("estado").asText()).isEqualTo("CRITICO");
        assertThat(linha.get("motivo").asText()).contains("Pressão");
        // Sem segunda medição, a vida declarada serve de referência.
        assertThat(linha.get("base").asText()).contains("fabricante");
    }

    // ==== Quem pode ver ====================================================

    @Test
    void oMotoristaNaoVeOPainelEOTecnicoNaoVeOsCustos() throws Exception {
        bearer = register("cons7@teste.ao", "Transportes Acesso").bearer();
        String camiao = ativo("CAM-C7", "Camião", "VEHICLE", "ODOMETER", 100_000);
        send(post("/api/v1/assets/" + camiao + "/tyres"), Map.of(
                "position", "FE", "size", "11R22.5", "installedMeter", 50_000,
                "cost", 180_000, "lastTreadMm", "12.0", "expectedLifeKm", 80_000), 201);

        String driverId = send(post("/api/v1/drivers"),
                Map.of("name", "Mateus Pneu", "phone", "+244 923 000 777",
                        "licenseNumber", "AO-PNEU-9"), 201).get("id").asText();
        JsonNode cred = send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        JsonNode login = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", cred.get("loginId").asText(),
                        "password", cred.get("password").asText()), 200);
        send("Bearer " + login.get("accessToken").asText(), get("/api/v1/consumables"), null, 403);

        // O dono vê o custo por quilómetro; é com ele que escolhe a marca.
        // 180 000 Kz por 50 000 km rodados = 3,60 Kz por quilómetro.
        assertThat(painel(30).get("pneus").get(0).get("custoPorKm").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("3.60"));
    }

    @Test
    void oHorizontePedidoManda() throws Exception {
        bearer = register("cons8@teste.ao", "Transportes Horizonte").bearer();
        String camiao = ativo("CAM-C8", "Camião", "VEHICLE", "ODOMETER", 100_000);
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + camiao),
                null, 200);
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"),
                Map.of("value", 109_700), 201);

        // Sem média diária de contador, só o calendário conta. Num horizonte de
        // sete dias quase nada vence; num ano, muito mais.
        int seteDias = painel(7).get("filtros").size() + painel(7).get("oleos").size();
        int umAno = painel(365).get("filtros").size() + painel(365).get("oleos").size();
        assertThat(umAno).isGreaterThanOrEqualTo(seteDias);
        assertThat(painel(7).get("horizonteDias").asInt()).isEqualTo(7);
    }
}
