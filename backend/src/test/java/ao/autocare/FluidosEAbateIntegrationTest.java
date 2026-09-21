package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.plan.PlanCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * As dores que fazem perder dinheiro e pessoas em Angola.
 *
 * <p>Duas coisas se provam aqui. Que <b>fluido que se atesta é fluido que se
 * perdeu</b>: um autocarro que leva água todas as semanas tem uma fuga, e o
 * sistema di-lo com o número em cima da mesa em vez de esperar pelo incêndio.
 * E que uma viatura que sai da frota <b>sai como deve</b>: com data, motivo,
 * contador final e o custo de toda a vida guardado, em vez de desaparecer.
 */
class FluidosEAbateIntegrationTest extends AbstractIntegrationTest {

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

    private String ativo(String tag, String tipo, String categoria, String medidor) throws Exception {
        String tipoId = send(post("/api/v1/asset-types"),
                Map.of("name", tipo, "category", categoria, "primaryMeter", medidor), 201)
                .get("id").asText();
        return send(post("/api/v1/assets"), Map.of(
                "tag", tag, "name", tipo, "assetTypeId", tipoId, "initialMeterValue", 200_000), 201)
                .get("id").asText();
    }

    // ==== Autocarro é uma família própria ===================================

    @Test
    void oAutocarroTemPlanoProprioComArrefecimentoESeguranca() {
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Autocarro de passageiros")).isEqualTo("BUS");
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Minibus 30 lugares")).isEqualTo("BUS");
        assertThat(PlanCatalog.codigoPara("VEHICLE", "Camião basculante")).isEqualTo("TRUCK_HEAVY");
        assertThat(PlanCatalog.codigoPara("IMPLEMENT", "Reboque cisterna")).isEqualTo("IMPLEMENT");
        assertThat(PlanCatalog.codigoPara("MACHINE", "Empilhadora 3 t")).isEqualTo("FORKLIFT");

        var plano = PlanCatalog.build("BUS", null).tasks();
        // O arrefecimento e o elétrico aparecem em vários intervalos: é o que
        // arde primeiro num autocarro.
        assertThat(plano).filteredOn(t -> "ARREFECIMENTO".equals(t.systemCode())).hasSizeGreaterThan(2);
        assertThat(plano).filteredOn(t -> "ELETRICO".equals(t.systemCode())).hasSizeGreaterThan(1);
        assertThat(plano).anyMatch(t -> "SEGURANCA".equals(t.systemCode()));
        assertThat(plano.toString()).contains("extintor").containsIgnoringCase("incêndio");

        var inspecao = PlanCatalog.dailyChecklist("BUS", null);
        assertThat(inspecao).isNotNull();
        assertThat(inspecao.name()).contains("autocarro");
        assertThat(inspecao.items().toString())
                .contains("Cheiro a queimado")
                .contains("Extintores")
                .contains("Saídas de emergência");

        // Termografia de três em três meses: ligações a aquecer veem-se antes.
        assertThat(PlanCatalog.predictivePrograms("BUS").toString()).contains("THERMOGRAPHY");
        // E a segurança das pessoas é o impacto máximo na criticidade.
        assertThat(PlanCatalog.criticality("BUS").safetyImpact()).isEqualTo(5);
    }

    // ==== Fluidos ===========================================================

    @Test
    void tresAtestosDeAguaEmTrintaDiasAvisamQueHaFuga() throws Exception {
        bearer = register("fluido1@teste.ao", "Rodoviária do Kwanza").bearer();
        String bus = ativo("BUS-1", "Autocarro de passageiros", "VEHICLE", "ODOMETER");

        // Os dois primeiros atestos passam sem alarido: é a vida normal.
        JsonNode um = send(post("/api/v1/assets/" + bus + "/fluid-topups"),
                Map.of("kind", "COOLANT", "liters", 1.5), 201);
        assertThat(um.hasNonNull("warning")).isFalse();
        send(post("/api/v1/assets/" + bus + "/fluid-topups"),
                Map.of("kind", "COOLANT", "liters", 1.5), 201);

        // Ao terceiro, o sistema diz o que ninguém tinha reparado.
        JsonNode tres = send(post("/api/v1/assets/" + bus + "/fluid-topups"),
                Map.of("kind", "COOLANT", "liters", 2), 201);
        assertThat(tres.get("warning").asText())
                .contains("BUS-1")
                .contains("5 L")
                .containsIgnoringCase("fuga");

        // E quem gere é avisado, não fica à espera de reparar sozinho.
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos.toString()).contains("Perda de fluido: BUS-1");

        JsonNode lista = send(get("/api/v1/assets/" + bus + "/fluid-topups"), null, 200);
        assertThat(lista).hasSize(3);
    }

    @Test
    void oLiquidoDeTravoesAvisaLogoAPrimeiraVez() throws Exception {
        bearer = register("fluido2@teste.ao", "Transportes Travão").bearer();
        String cam = ativo("CAM-T", "Camião basculante", "VEHICLE", "ODOMETER");

        JsonNode r = send(post("/api/v1/assets/" + cam + "/fluid-topups"),
                Map.of("kind", "BRAKE", "liters", 0.3), 201);
        assertThat(r.get("warning").asText())
                .containsIgnoringCase("não desaparece")
                .containsIgnoringCase("não deve sair");

        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos.toString()).contains("CRITICAL");
    }

    // ==== Abate =============================================================

    @Test
    void abaterGuardaDataMotivoContadorEOCustoDeVida() throws Exception {
        bearer = register("abate1@teste.ao", "Frota Abate").bearer();
        String cam = ativo("CAM-A", "Camião basculante", "VEHICLE", "ODOMETER");

        // Uma reparação, para haver custo de vida que contar.
        Map<String, Object> ordem = new HashMap<>();
        ordem.put("assetId", cam);
        ordem.put("type", "CORRECTIVE");
        ordem.put("title", "Reparação da caixa");
        String woId = send(post("/api/v1/work-orders"), ordem, 201).get("id").asText();
        send(post("/api/v1/work-orders/" + woId + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + woId + "/complete"),
                Map.of("resolution", "Caixa reparada"), 200);

        JsonNode abate = send(post("/api/v1/assets/" + cam + "/retire"), Map.of(
                "reason", "SOLD",
                "finalMeter", 412_000,
                "residualValue", 8_500_000,
                "notes", "Vendido à Transportes Benguela."), 200);

        assertThat(abate.get("reasonLabel").asText()).isEqualTo("Vendido");
        assertThat(abate.get("finalMeter").asDouble()).isEqualTo(412_000);
        assertThat(abate.get("meterUnit").asText()).isEqualTo("km");
        assertThat(abate.get("residualValue").asDouble()).isEqualTo(8_500_000);
        assertThat(abate.get("workOrders").asInt()).isEqualTo(1);

        // Sai das listas de trabalho...
        JsonNode lista = send(get("/api/v1/assets"), null, 200).get("content");
        assertThat(lista.toString()).doesNotContain("CAM-A");

        // ...mas fica na lista dos abatidos, com o histórico.
        JsonNode abatidos = send(get("/api/v1/assets/retired"), null, 200);
        assertThat(abatidos).hasSize(1);
        assertThat(abatidos.get(0).get("tag").asText()).isEqualTo("CAM-A");

        // Abater outra vez não faz sentido.
        send(post("/api/v1/assets/" + cam + "/retire"), Map.of("reason", "SCRAPPED"), 409);

        // E engana-se quem quiser: reverte-se.
        JsonNode revertido = send(post("/api/v1/assets/" + cam + "/unretire"), null, 200);
        assertThat(revertido.hasNonNull("retiredAt")).isFalse();
        assertThat(send(get("/api/v1/assets"), null, 200).get("content").toString()).contains("CAM-A");
    }

    @Test
    void naoSeAbateComOrdensPorFechar() throws Exception {
        bearer = register("abate2@teste.ao", "Frota Pendente").bearer();
        String cam = ativo("CAM-B", "Camião basculante", "VEHICLE", "ODOMETER");
        send(post("/api/v1/work-orders"), Map.of(
                "assetId", cam, "type", "CORRECTIVE", "title", "Ainda em curso"), 201);

        JsonNode erro = send(post("/api/v1/assets/" + cam + "/retire"),
                Map.of("reason", "SCRAPPED"), 409);
        assertThat(erro.get("message").asText()).contains("por fechar");
    }

    @Test
    void osFluidosSaoDeQuemTemAViatura() throws Exception {
        bearer = register("fluido3@teste.ao", "Empresa A").bearer();
        String meu = ativo("A-1", "Camião basculante", "VEHICLE", "ODOMETER");

        String outro = register("fluido4@teste.ao", "Empresa B").bearer();
        String antes = bearer;
        bearer = outro;
        send(post("/api/v1/assets/" + meu + "/fluid-topups"),
                Map.of("kind", "COOLANT", "liters", 1), 404);
        bearer = antes;
        assertThat(send(get("/api/v1/assets/" + meu + "/fluid-topups"), null, 200)).isEmpty();
    }

    @Test
    void osPlanosDeEmpilhadoraEAlfaiaSaoDiferentesDosDeUmaViatura() {
        var empilhadora = PlanCatalog.build("FORKLIFT", null).tasks();
        assertThat(empilhadora).anyMatch(t -> "SEGURANCA".equals(t.systemCode()));
        assertThat(empilhadora.toString()).contains("Garfos").contains("correntes");

        var alfaia = PlanCatalog.build("IMPLEMENT", null).tasks();
        assertThat(alfaia).noneMatch(t -> "MOTOR".equals(t.systemCode()));
        assertThat(alfaia.toString()).contains("Engate").contains("rolamentos");

        assertThat(PlanCatalog.dailyChecklist("IMPLEMENT", null).items().toString())
                .contains("Engate").contains("porcas de roda");
        assertThat(List.of(PlanCatalog.disponiveis()).toString()).contains("Autocarro");
    }
}
