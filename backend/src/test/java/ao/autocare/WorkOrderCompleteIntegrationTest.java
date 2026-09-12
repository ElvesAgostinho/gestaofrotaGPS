package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A ficha de ordem de manutenção completa.
 *
 * <p>O que se verifica aqui é o que uma empresa grande tem mesmo de responder:
 * quanto custou <b>ao todo</b>, incluindo a oficina de fora; quanto tempo a
 * máquina esteve parada e o que isso vale; se foi feito dentro do prazo; e se a
 * mesma avaria já aconteceu antes.
 */
class WorkOrderCompleteIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String assetId;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("ordens@teste.ao").bearer();
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camiao"), 201)
                .get("id").asText();

        Map<String, Object> ativo = new HashMap<>();
        ativo.put("tag", "CAM-800");
        ativo.put("name", "Camiao Scania");
        ativo.put("assetTypeId", typeId);
        // 5.000 Kz por hora parada: é isto que dá número à paragem.
        ativo.put("downtimeCostPerHour", 5_000);
        assetId = send(post("/api/v1/assets"), ativo, 201).get("id").asText();
    }

    private Map<String, Object> ordem(String titulo) {
        Map<String, Object> w = new HashMap<>();
        w.put("assetId", assetId);
        w.put("type", "CORRECTIVE");
        w.put("title", titulo);
        return w;
    }

    private String criar(Map<String, Object> body) throws Exception {
        return send(post("/api/v1/work-orders"), body, 201).get("id").asText();
    }

    private JsonNode ver(String id) throws Exception {
        return send(get("/api/v1/work-orders/" + id), null, 200);
    }

    private JsonNode aviso(String id, String codigo) throws Exception {
        for (JsonNode i : ver(id).get("insights")) {
            if (i.get("code").asText().equals(codigo)) {
                return i;
            }
        }
        return null;
    }

    // ---- prazo --------------------------------------------------------------
    @Test
    void everyOrderGetsADeadlineFromItsPriority() throws Exception {
        Map<String, Object> urgente = ordem("Travoes sem pressao");
        urgente.put("priority", "URGENT");
        JsonNode w = ver(criar(urgente));

        // Sem prazo nenhum, toda a ordem está a horas e o cumprimento é 100%.
        assertThat(w.hasNonNull("dueAt")).isTrue();
        Instant prazo = Instant.parse(w.get("dueAt").asText());
        assertThat(prazo).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
        assertThat(prazo).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
    }

    @Test
    void anExplicitDeadlineWins() throws Exception {
        Instant meu = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = ordem("Revisao");
        body.put("priority", "LOW");
        body.put("dueAt", meu.toString());

        assertThat(ver(criar(body)).get("dueAt").asText()).startsWith(meu.toString().substring(0, 19));
    }

    @Test
    void meetingTheDeadlineIsStampedAtClosingTime() throws Exception {
        String id = criar(ordem("Substituir filtro"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Filtro substituido"), 200);

        // Fixado no fecho: mudar a regra de prazos amanhã não pode reescrever a
        // história de cumprimento de ontem.
        assertThat(fechada.get("slaMet").asBoolean()).isTrue();
    }

    // ---- custo total --------------------------------------------------------
    @Test
    void theTotalCostIncludesLabourPartsAndTheOutsideWorkshop() throws Exception {
        String id = criar(ordem("Reparacao de caixa"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);

        // 4 h a 2.500 Kz = 10.000
        Map<String, Object> mao = new HashMap<>();
        mao.put("technicianLabel", "Equipa interna");
        mao.put("hours", 4);
        mao.put("hourlyRate", 2_500);
        send(post("/api/v1/work-orders/" + id + "/labor"), mao, 200);

        // Peça avulsa: 2 × 15.000 = 30.000
        Map<String, Object> peca = new HashMap<>();
        peca.put("partName", "Rolamento");
        peca.put("quantity", 2);
        peca.put("unitCost", 15_000);
        send(post("/api/v1/work-orders/" + id + "/parts"), peca, 200);

        // Oficina externa: 120.000 — o que antes desaparecia das contas.
        Map<String, Object> externo = new HashMap<>();
        externo.put("supplier", "Oficina Central de Luanda");
        externo.put("description", "Retificacao da caixa de velocidades");
        externo.put("invoiceNumber", "FT 2026/338");
        externo.put("cost", 120_000);
        externo.put("warrantyMonths", 6);
        JsonNode w = send(post("/api/v1/work-orders/" + id + "/external-services"), externo, 200);

        assertThat(w.get("totalLaborCost").asDouble()).isEqualTo(10_000.0);
        assertThat(w.get("totalPartsCost").asDouble()).isEqualTo(30_000.0);
        assertThat(w.get("totalExternalCost").asDouble()).isEqualTo(120_000.0);
        assertThat(w.get("totalCost").asDouble()).isEqualTo(160_000.0);

        assertThat(w.get("externalServices")).hasSize(1);
        JsonNode s = w.get("externalServices").get(0);
        assertThat(s.get("supplier").asText()).isEqualTo("Oficina Central de Luanda");
        // A garantia da oficina fica com data, não na cabeça de quem tratou.
        assertThat(s.hasNonNull("warrantyUntil")).isTrue();
        assertThat(s.get("underWarrantyNow").asBoolean()).isTrue();
    }

    @Test
    void whatTheWarrantyPaidBackIsNotACostToTheCompany() throws Exception {
        String id = criar(ordem("Avaria em garantia"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);

        Map<String, Object> externo = new HashMap<>();
        externo.put("supplier", "Concessionario");
        externo.put("description", "Substituicao de injectores");
        externo.put("cost", 200_000);
        send(post("/api/v1/work-orders/" + id + "/external-services"), externo, 200);

        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Coberto pela garantia", "warrantyRecovered", 200_000), 200);

        assertThat(fechada.get("totalExternalCost").asDouble()).isEqualTo(200_000.0);
        assertThat(fechada.get("totalCost").asDouble()).isEqualTo(0.0);
    }

    // ---- paragem ------------------------------------------------------------
    @Test
    void downtimeIsMeasuredAndPriced() throws Exception {
        String id = criar(ordem("Motor fundido"));

        Instant inicio = Instant.now().minus(6, ChronoUnit.HOURS);
        send(post("/api/v1/work-orders/" + id + "/start"),
                Map.of("startedAt", inicio.toString(), "stopAsset", true), 200);

        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Motor substituido"), 200);

        // 6 horas paradas a 5.000 Kz = 30.000 Kz que não estão em fatura nenhuma.
        assertThat(fechada.get("downtimeHours").asDouble()).isBetween(5.9, 6.1);
        assertThat(fechada.get("downtimeCost").asDouble()).isBetween(29_500.0, 30_500.0);
    }

    @Test
    void withoutADowntimeRateThePriceIsAbsentNotZero() throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Reboque"), 201)
                .get("id").asText();
        String semTarifa = send(post("/api/v1/assets"),
                Map.of("tag", "REB-01", "name", "Reboque", "assetTypeId", typeId), 201)
                .get("id").asText();

        Map<String, Object> body = ordem("Chassis");
        body.put("assetId", semTarifa);
        String id = criar(body);
        send(post("/api/v1/work-orders/" + id + "/start"),
                Map.of("startedAt", Instant.now().minus(3, ChronoUnit.HOURS).toString(),
                        "stopAsset", true), 200);
        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Soldado"), 200);

        assertThat(fechada.get("downtimeHours").asDouble()).isBetween(2.9, 3.1);
        // Zero diria "não custou nada", que é falso. Ausente diz "não se sabe".
        assertThat(fechada.hasNonNull("downtimeCost")).isFalse();
    }

    // ---- avisos -------------------------------------------------------------
    @Test
    void aFailureRepeatingOnTheSameSystemIsFlagged() throws Exception {
        Map<String, Object> primeira = ordem("Fuga no circuito hidraulico");
        primeira.put("systemCode", "HIDRAULICO");
        String id1 = criar(primeira);
        send(post("/api/v1/work-orders/" + id1 + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id1 + "/complete"),
                Map.of("resolution", "Mangueira substituida"), 200);

        Map<String, Object> segunda = ordem("Outra fuga no hidraulico");
        segunda.put("systemCode", "HIDRAULICO");
        String id2 = criar(segunda);

        JsonNode a = aviso(id2, "REPEAT_FAILURE");
        assertThat(a).isNotNull();
        assertThat(a.get("detail").asText())
                .contains("HIDRAULICO")
                .contains("procurar a causa");
    }

    @Test
    void aDifferentSystemIsNotARepeat() throws Exception {
        Map<String, Object> primeira = ordem("Fuga hidraulica");
        primeira.put("systemCode", "HIDRAULICO");
        criar(primeira);

        Map<String, Object> segunda = ordem("Bateria descarregada");
        segunda.put("systemCode", "ELECTRICO");
        assertThat(aviso(criar(segunda), "REPEAT_FAILURE")).isNull();
    }

    @Test
    void aBudgetBlownWideOpenIsFlagged() throws Exception {
        Map<String, Object> body = ordem("Revisao geral");
        body.put("estimatedCost", 100_000);
        String id = criar(body);
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);

        Map<String, Object> externo = new HashMap<>();
        externo.put("supplier", "Oficina");
        externo.put("description", "Trabalho a mais do que o previsto");
        externo.put("cost", 300_000);
        send(post("/api/v1/work-orders/" + id + "/external-services"), externo, 200);

        JsonNode w = ver(id);
        assertThat(w.get("costOverrunPercent").asDouble()).isEqualTo(200.0);
        assertThat(aviso(id, "BUDGET_OVERRUN")).isNotNull();
    }

    @Test
    void withoutABudgetThereIsNoOverrunToReport() throws Exception {
        String id = criar(ordem("Sem orcamento"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        Map<String, Object> mao = new HashMap<>();
        mao.put("hours", 3);
        mao.put("hourlyRate", 2_000);
        send(post("/api/v1/work-orders/" + id + "/labor"), mao, 200);

        // Zero sugeriria que se acertou em cheio; não há nada com que comparar.
        assertThat(ver(id).hasNonNull("costOverrunPercent")).isFalse();
        assertThat(aviso(id, "BUDGET_OVERRUN")).isNull();
    }

    @Test
    void aCorrectiveClosedWithoutACauseIsPointedOut() throws Exception {
        String id = criar(ordem("Avaria sem causa apurada"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Reparado"), 200);

        JsonNode a = aviso(id, "NO_ROOT_CAUSE");
        assertThat(a).isNotNull();
        assertThat(a.get("detail").asText()).contains("vai voltar");

        // Com causa registada, o aviso desaparece.
        String outro = criar(ordem("Avaria com causa"));
        send(post("/api/v1/work-orders/" + outro + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + outro + "/complete"),
                Map.of("resolution", "Reparado",
                        "rootCause", "Filtro de ar entupido por trabalho em terreno com poeira",
                        "correctiveAction", "Passar a limpar o filtro de duas em duas semanas"),
                200);
        assertThat(aviso(outro, "NO_ROOT_CAUSE")).isNull();
        assertThat(ver(outro).get("correctiveAction").asText()).contains("duas semanas");
    }

    @Test
    void warningsNeverBlockAnything() throws Exception {
        // Ordem com tudo por preencher: abre, anda e fecha na mesma. Impedir o
        // registo não corrige a realidade, só faz com que ela não seja registada.
        String id = criar(ordem("As tres da manha, com pressa"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Resolvido"), 200);
        assertThat(fechada.get("status").asText()).isEqualTo("DONE");
    }

    // ---- anulação -----------------------------------------------------------
    @Test
    void cancellingRequiresAReasonThatStaysOnRecord() throws Exception {
        String id = criar(ordem("Ordem a anular"));

        // Anular sem razão é indistinguível de trabalho escondido.
        send(post("/api/v1/work-orders/" + id + "/cancel"), Map.of(), 400);

        JsonNode anulada = send(post("/api/v1/work-orders/" + id + "/cancel"),
                Map.of("reason", "Aberta por engano no ativo errado"), 200);
        assertThat(anulada.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(anulada.get("cancellationReason").asText()).contains("ativo errado");
    }

    // ---- ficha completa -----------------------------------------------------
    @Test
    void theFormCarriesEverythingALargeCompanyNeedsToAnswer() throws Exception {
        Map<String, Object> body = ordem("Revisao das 2000 horas");
        body.put("type", "PREVENTIVE");
        body.put("priority", "HIGH");
        body.put("systemCode", "MOTOR");
        body.put("estimatedHours", 8);
        body.put("estimatedCost", 250_000);
        body.put("requiresShutdown", true);
        body.put("safetyNotes", "Bloquear a ignicao e sinalizar a area antes de comecar");
        body.put("underWarranty", true);
        body.put("warrantyReference", "GAR-2026-114");
        body.put("tasks", List.of(Map.of("title", "Mudar oleo", "systemName", "Motor")));

        JsonNode w = ver(criar(body));

        assertThat(w.get("systemCode").asText()).isEqualTo("MOTOR");
        assertThat(w.get("estimatedHours").asDouble()).isEqualTo(8.0);
        assertThat(w.get("estimatedCost").asDouble()).isEqualTo(250_000.0);
        assertThat(w.get("requiresShutdown").asBoolean()).isTrue();
        assertThat(w.get("safetyNotes").asText()).contains("Bloquear a ignicao");
        assertThat(w.get("underWarranty").asBoolean()).isTrue();
        assertThat(w.get("warrantyReference").asText()).isEqualTo("GAR-2026-114");
        assertThat(w.hasNonNull("dueAt")).isTrue();
        assertThat(w.get("tasks")).hasSize(1);
    }

    @Test
    void closingRecordsTheMeterAtTheEndNotJustAtTheStart() throws Exception {
        String id = criar(ordem("Revisao"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);

        JsonNode fechada = send(post("/api/v1/work-orders/" + id + "/complete"),
                Map.of("resolution", "Feito", "meterValue", 5_000, "closingMeterValue", 5_010),
                200);
        assertThat(fechada.get("meterValue").asDouble()).isEqualTo(5_000.0);
        assertThat(fechada.get("closingMeterValue").asDouble()).isEqualTo(5_010.0);
    }

    @Test
    void verifyingRecordsWhoVerified() throws Exception {
        String id = criar(ordem("Para verificar"));
        send(post("/api/v1/work-orders/" + id + "/start"), Map.of(), 200);
        send(post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "Ok"), 200);

        JsonNode v = send(post("/api/v1/work-orders/" + id + "/verify"), null, 200);
        assertThat(v.get("status").asText()).isEqualTo("VERIFIED");
        assertThat(v.get("verifiedByName").asText()).isEqualTo("Utilizador Teste");
    }
}
