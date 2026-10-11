package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Que peça serve que equipamento — e o que a substitui.
 *
 * <p>É a diferença entre um inventário e um catálogo de peças. «Filtro de óleo»
 * é o nome de cinquenta peças diferentes; a que serve o Hino não serve o Hilux,
 * e montá-la custa um motor. O sistema tem de poder dizer «esta peça não é
 * deste equipamento» — e, quando não sabe, tem de dizer que não sabe.
 *
 * <p>Estes testes protegem as três coisas que fazem isso funcionar: a regra de
 * encaixe (família, marca, modelo, motor, anos — com «qualquer» no que fica
 * vazio), os quatro veredictos (serve, não serve, incerto, sem informação) e a
 * procura pelo número que vem impresso na caixa, seja ele de que marca for.
 */
class CompatibilidadePecasIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private final Map<String, String> tipos = new HashMap<>();

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

    private String tipo(String nome, String categoria, String medidor) throws Exception {
        String chave = nome + categoria;
        if (!tipos.containsKey(chave)) {
            tipos.put(chave, send(post("/api/v1/asset-types"),
                    Map.of("name", nome, "category", categoria, "primaryMeter", medidor), 201)
                    .get("id").asText());
        }
        return tipos.get(chave);
    }

    private String viatura(String tag, String tipoNome, Map<String, Object> extra) throws Exception {
        Map<String, Object> corpo = new HashMap<>(Map.of(
                "tag", tag, "name", tipoNome, "assetTypeId", tipo(tipoNome, "VEHICLE", "ODOMETER")));
        corpo.putAll(extra);
        return send(post("/api/v1/assets"), corpo, 201).get("id").asText();
    }

    private String peca(String nome, String categoria) throws Exception {
        return send(post("/api/v1/parts"),
                Map.of("name", nome, "category", categoria, "unit", "un"), 201).get("id").asText();
    }

    // ==== A regra de encaixe ================================================

    @Test
    void aPecaDeUmCamiaoNaoServeUmaPickup() throws Exception {
        bearer = register("fit1@teste.ao", "Transportes Encaixe").bearer();

        String camiao = viatura("CAM-F1", "Camião basculante",
                Map.of("manufacturer", "Hino", "model", "500 FM", "modelYear", 2018));
        String pickup = viatura("PIC-F1", "Pick-up 4x4",
                Map.of("manufacturer", "Toyota", "model", "Hilux 2.4 D-4D", "modelYear", 2020));

        String filtroCamiao = peca("Filtro de óleo Hino", "FILTER");
        send(post("/api/v1/parts/" + filtroCamiao + "/applications"),
                Map.of("make", "Hino", "model", "500"), 201);

        // No camião serve e diz porquê.
        JsonNode doCamiao = send(get("/api/v1/assets/" + camiao + "/compatible-parts"), null, 200);
        assertThat(doCamiao.get("pecas")).hasSize(1);
        assertThat(doCamiao.get("pecas").get(0).get("veredicto").asText()).isEqualTo("SERVE");
        assertThat(doCamiao.get("pecas").get(0).get("porque").asText()).contains("Hino");

        // Na pick-up não aparece — e aparece a contagem do que ainda ninguém classificou.
        JsonNode daPickup = send(get("/api/v1/assets/" + pickup + "/compatible-parts"), null, 200);
        assertThat(daPickup.get("pecas")).isEmpty();
        assertThat(daPickup.get("equipamento").get("make").asText()).isEqualTo("Toyota");
    }

    @Test
    void aFamiliaSozinhaServeTodaAFamilia() throws Exception {
        bearer = register("fit2@teste.ao", "Transportes Família").bearer();
        String camiaoA = viatura("CAM-F2", "Camião basculante", Map.of("manufacturer", "Hino"));
        String camiaoB = viatura("CAM-F3", "Camião basculante", Map.of("manufacturer", "Mercedes"));
        String ligeiro = viatura("LIG-F1", "Ligeiro de passageiros", Map.of("manufacturer", "Toyota"));

        String correias = peca("Correias do motor", "WEAR");
        send(post("/api/v1/parts/" + correias + "/applications"),
                Map.of("family", "TRUCK_HEAVY"), 201);

        assertThat(send(get("/api/v1/assets/" + camiaoA + "/compatible-parts"), null, 200)
                .get("pecas")).hasSize(1);
        assertThat(send(get("/api/v1/assets/" + camiaoB + "/compatible-parts"), null, 200)
                .get("pecas")).as("outra marca, mesma família").hasSize(1);
        assertThat(send(get("/api/v1/assets/" + ligeiro + "/compatible-parts"), null, 200)
                .get("pecas")).as("um ligeiro não leva correias de camião").isEmpty();
    }

    @Test
    void oMotorDecideQuandoAMarcaEOModeloNaoChegam() throws Exception {
        bearer = register("fit3@teste.ao", "Transportes Motor").bearer();
        String comMotor = viatura("PIC-F2", "Pick-up 4x4", Map.of(
                "manufacturer", "Toyota", "model", "Hilux", "engine", "2.8 GD-6", "modelYear", 2021));
        String outroMotor = viatura("PIC-F3", "Pick-up 4x4", Map.of(
                "manufacturer", "Toyota", "model", "Hilux", "engine", "2.4 D-4D", "modelYear", 2021));
        String semMotor = viatura("PIC-F4", "Pick-up 4x4", Map.of(
                "manufacturer", "Toyota", "model", "Hilux", "modelYear", 2021));

        String filtro = peca("Filtro de óleo 2.8 GD-6", "FILTER");
        send(post("/api/v1/parts/" + filtro + "/applications"),
                Map.of("make", "Toyota", "model", "Hilux", "engine", "2.8 GD-6"), 201);

        JsonNode certa = send(get("/api/v1/assets/" + comMotor + "/compatible-parts"), null, 200);
        assertThat(certa.get("pecas").get(0).get("veredicto").asText()).isEqualTo("SERVE");

        // O mesmo Hilux do mesmo ano, com outro motor: não serve.
        assertThat(send(get("/api/v1/assets/" + outroMotor + "/compatible-parts"), null, 200)
                .get("pecas")).isEmpty();

        // Sem o motor registado, a resposta honesta é «não se pode confirmar» —
        // e não «serve». A peça aparece, mas marcada.
        JsonNode duvida = send(get("/api/v1/assets/" + semMotor + "/compatible-parts"), null, 200);
        assertThat(duvida.get("pecas").get(0).get("veredicto").asText()).isEqualTo("INCERTO");
        assertThat(duvida.get("pecas").get(0).get("porque").asText()).contains("motor");
    }

    @Test
    void oAnoDeFabricoLimitaOIntervalo() throws Exception {
        bearer = register("fit4@teste.ao", "Transportes Ano").bearer();
        String novo = viatura("LIG-F2", "Ligeiro de passageiros",
                Map.of("manufacturer", "Toyota", "model", "Corolla", "modelYear", 2022));
        String velho = viatura("LIG-F3", "Ligeiro de passageiros",
                Map.of("manufacturer", "Toyota", "model", "Corolla", "modelYear", 2008));
        String semAno = viatura("LIG-F4", "Ligeiro de passageiros",
                Map.of("manufacturer", "Toyota", "model", "Corolla"));

        String pastilhas = peca("Pastilhas de travão Corolla 2019+", "WEAR");
        send(post("/api/v1/parts/" + pastilhas + "/applications"),
                Map.of("make", "Toyota", "model", "Corolla", "yearFrom", 2019), 201);

        assertThat(send(get("/api/v1/assets/" + novo + "/compatible-parts"), null, 200)
                .get("pecas").get(0).get("veredicto").asText()).isEqualTo("SERVE");
        assertThat(send(get("/api/v1/assets/" + velho + "/compatible-parts"), null, 200)
                .get("pecas")).isEmpty();
        assertThat(send(get("/api/v1/assets/" + semAno + "/compatible-parts"), null, 200)
                .get("pecas").get(0).get("veredicto").asText()).isEqualTo("INCERTO");
    }

    @Test
    void oAnoInicialNaoPodeSerMaiorQueOFinal() throws Exception {
        bearer = register("fit5@teste.ao", "Transportes Erro").bearer();
        String p = peca("Filtro qualquer", "FILTER");
        send(post("/api/v1/parts/" + p + "/applications"),
                Map.of("yearFrom", 2020, "yearTo", 2015), 400);
    }

    // ==== «Esta peça serve este equipamento», num clique ====================

    @Test
    void aAplicacaoCopiaSeDoEquipamento() throws Exception {
        bearer = register("fit6@teste.ao", "Transportes Clique").bearer();
        String carro = viatura("LIG-F5", "Ligeiro de passageiros", Map.of(
                "manufacturer", "Toyota", "model", "Corolla", "engine", "1.6 VVT-i",
                "modelYear", 2019));
        String filtro = peca("Filtro de ar", "FILTER");

        JsonNode criada = send(
                post("/api/v1/parts/" + filtro + "/applications/from-asset/" + carro), null, 201);
        assertThat(criada.get("make").asText()).isEqualTo("Toyota");
        assertThat(criada.get("model").asText()).isEqualTo("Corolla");
        assertThat(criada.get("engine").asText()).isEqualTo("1.6 VVT-i");
        assertThat(criada.get("yearFrom").asInt()).isEqualTo(2019);
        assertThat(criada.get("resumo").asText()).contains("Corolla");

        // E com family=true guarda só a família, para a peça servir a classe toda.
        String massa = peca("Massa lubrificante", "WEAR");
        JsonNode soFamilia = send(
                post("/api/v1/parts/" + massa + "/applications/from-asset/" + carro
                        + "?familyOnly=true"), null, 201);
        assertThat(soFamilia.has("make")).as("sem marca: serve a classe toda").isFalse();
        assertThat(soFamilia.get("family").asText()).isNotBlank();

        // E depois pode-se apagar.
        send(delete("/api/v1/part-applications/" + criada.get("id").asText()), null, 200);
        assertThat(send(get("/api/v1/parts/" + filtro + "/applications"), null, 200)).isEmpty();
    }

    // ==== Referências cruzadas ==============================================

    @Test
    void oNumeroDaCaixaEncontraOArtigo() throws Exception {
        bearer = register("fit7@teste.ao", "Transportes Balcão").bearer();
        String filtro = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro de combustível", "category", "FILTER", "unit", "un",
                "partNumber", "23401-1332"), 201).get("id").asText();

        send(post("/api/v1/parts/" + filtro + "/cross-refs"), Map.of(
                "number", "WK 842/2", "brand", "Mann", "kind", "EQUIVALENT"), 201);
        send(post("/api/v1/parts/" + filtro + "/cross-refs"), Map.of(
                "number", "FF5052", "brand", "Fleetguard", "kind", "EQUIVALENT"), 201);

        // Pelo número do artigo.
        JsonNode pelaRef = send(get("/api/v1/parts/lookup?number=23401"), null, 200);
        assertThat(pelaRef).hasSize(1);
        assertThat(pelaRef.get(0).get("encontradoPor").asText()).contains("artigo");

        // E pelo número do equivalente, que é o que o fornecedor tem.
        JsonNode peloEquivalente = send(get("/api/v1/parts/lookup?number=WK 842/2"), null, 200);
        assertThat(peloEquivalente).hasSize(1);
        assertThat(peloEquivalente.get(0).get("partId").asText()).isEqualTo(filtro);
        assertThat(peloEquivalente.get(0).get("encontradoPor").asText()).contains("Mann");

        // A mesma referência duas vezes na mesma peça é erro.
        send(post("/api/v1/parts/" + filtro + "/cross-refs"),
                Map.of("number", "wk 842/2", "kind", "EQUIVALENT"), 409);

        assertThat(send(get("/api/v1/parts/" + filtro + "/cross-refs"), null, 200)).hasSize(2);
    }

    // ==== A especificação de um lubrificante =================================

    @Test
    void umLubrificanteSemEspecificacaoNaoPassa() throws Exception {
        bearer = register("fit8@teste.ao", "Transportes Óleo").bearer();

        // «Óleo do motor» não é artigo nenhum: há dez e nove estragam o motor.
        JsonNode erro = send(post("/api/v1/parts"),
                Map.of("name", "Óleo do motor", "category", "LUBRICANT", "unit", "L"), 400);
        assertThat(erro.toString()).contains("especificação");

        // Com a norma no nome, o sistema tira-a de lá e não incomoda ninguém.
        JsonNode comNorma = send(post("/api/v1/parts"),
                Map.of("name", "Óleo do motor 15W-40", "category", "LUBRICANT", "unit", "L"), 201);
        assertThat(comNorma.get("specification").asText()).isEqualTo("15W-40");

        // E indicada à mão também serve.
        JsonNode aMao = send(post("/api/v1/parts"), Map.of(
                "name", "Óleo da transmissão", "category", "LUBRICANT", "unit", "L",
                "specification", "ATF Dexron III"), 201);
        assertThat(aMao.get("specification").asText()).isEqualTo("ATF Dexron III");

        // Um filtro não precisa: identifica-se pela referência, não pela norma.
        send(post("/api/v1/parts"),
                Map.of("name", "Filtro de ar", "category", "FILTER", "unit", "un"), 201);
    }

    // ==== O catálogo preenche a compatibilidade sozinho =====================

    @Test
    void aplicarOCatalogoDeclaraAFamiliaDasPecas() throws Exception {
        bearer = register("fit9@teste.ao", "Transportes Catálogo").bearer();
        String camiao = viatura("CAM-F9", "Camião basculante",
                Map.of("manufacturer", "Hino", "modelYear", 2019, "initialMeterValue", 100_000));
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + camiao),
                null, 200);

        // As peças que o plano criou no armazém ficaram marcadas como de camião.
        JsonNode doCamiao = send(get("/api/v1/assets/" + camiao + "/compatible-parts"), null, 200);
        assertThat(doCamiao.get("pecas")).as("o catálogo declarou a família").isNotEmpty();
        assertThat(doCamiao.get("pecas").get(0).get("veredicto").asText()).isEqualTo("SERVE");

        // E o mesmo filtro não aparece como compatível com um ligeiro.
        String ligeiro = viatura("LIG-F9", "Ligeiro de passageiros", Map.of("manufacturer", "Toyota"));
        assertThat(send(get("/api/v1/assets/" + ligeiro + "/compatible-parts"), null, 200)
                .get("pecas")).isEmpty();

        // Aplicar o mesmo plano a um segundo camião não duplica as aplicações.
        String outro = viatura("CAM-F10", "Camião basculante", Map.of("manufacturer", "Mercedes"));
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + outro),
                null, 200);
        JsonNode aplicacoes = send(get("/api/v1/parts/"
                + doCamiao.get("pecas").get(0).get("partId").asText() + "/applications"), null, 200);
        assertThat(aplicacoes).hasSize(1);
    }

    // ==== O painel de consumíveis avisa =====================================

    @Test
    void oPainelAvisaQuandoAPecaDoPlanoNaoServeOEquipamento() throws Exception {
        bearer = register("fit10@teste.ao", "Transportes Aviso").bearer();

        // Um camião com o plano de camiões: tudo em ordem.
        String camiao = viatura("CAM-F11", "Camião basculante",
                Map.of("manufacturer", "Hino", "modelYear", 2019, "initialMeterValue", 100_000));
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + camiao),
                null, 200);
        send(post("/api/v1/assets/" + camiao + "/meters/ODOMETER/readings"),
                Map.of("value", 109_700), 201);

        JsonNode painel = send(get("/api/v1/consumables?dias=365"), null, 200);
        assertThat(painel.get("resumo").get("pecasIncompativeis").asInt()).isZero();
        boolean algumaServe = false;
        for (JsonNode s : painel.get("filtros")) {
            for (JsonNode peca : s.get("pecas")) {
                if (peca.has("veredicto") && "SERVE".equals(peca.get("veredicto").asText())) {
                    algumaServe = true;
                }
            }
        }
        assertThat(algumaServe).as("o painel confirma a compatibilidade peça a peça").isTrue();

        // Agora um ligeiro com o mesmo plano de camião — o erro que acontece a
        // sério quando alguém aplica o plano errado. O painel tem de o dizer.
        String ligeiro = viatura("LIG-F11", "Ligeiro de passageiros",
                Map.of("manufacturer", "Toyota", "initialMeterValue", 100_000));
        send(post("/api/v1/maintenance-plans/from-catalog/TRUCK_HEAVY/apply?assetId=" + ligeiro),
                null, 200);
        send(post("/api/v1/assets/" + ligeiro + "/meters/ODOMETER/readings"),
                Map.of("value", 109_700), 201);

        JsonNode depois = send(get("/api/v1/consumables?dias=365"), null, 200);
        assertThat(depois.get("resumo").get("pecasIncompativeis").asInt())
                .as("as peças de camião não servem o ligeiro").isPositive();
    }

    // ==== Quem pode ver e quem pode declarar ================================

    @Test
    void oMotoristaNaoVeOCatalogoDePecas() throws Exception {
        bearer = register("fit11@teste.ao", "Transportes Acesso").bearer();
        String carro = viatura("LIG-F12", "Ligeiro de passageiros", Map.of("manufacturer", "Toyota"));
        String driverId = send(post("/api/v1/drivers"),
                Map.of("name", "Osvaldo Peça", "phone", "+244 923 111 222",
                        "licenseNumber", "AO-PEC-1"), 201).get("id").asText();
        JsonNode cred = send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        JsonNode login = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", cred.get("loginId").asText(),
                        "password", cred.get("password").asText()), 200);
        String motorista = "Bearer " + login.get("accessToken").asText();

        send(motorista, get("/api/v1/assets/" + carro + "/compatible-parts"), null, 403);
        send(motorista, get("/api/v1/parts/lookup?number=123"), null, 403);
    }
}
