package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.workorder.WorkOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * «Onde ponho o limite que a viatura deve atingir para ir à manutenção?»
 *
 * <p>O limite é o intervalo: a cada N km (ou horas, ou dias). O contador —
 * escrito à mão ou alimentado pelo GPS — aproxima-se, a tarefa passa a «a
 * vencer», depois a «vencida», e a ordem preventiva é gerada sozinha.
 */
class MaintenanceIntervalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WorkOrderService workOrderService;

    private String bearer;
    private String tipoCamiao;
    private String tipoGerador;

    private JsonNode send(String bearer, MockHttpServletRequestBuilder req, Object body, int expect)
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

    private String ativo(String tag, String tipoId, double leituraInicial) throws Exception {
        return send(bearer, post("/api/v1/assets"), Map.of(
                "tag", tag, "name", "Ativo " + tag, "assetTypeId", tipoId,
                "initialMeterValue", leituraInicial), 201).get("id").asText();
    }

    private JsonNode leitura(String assetId, String kind, double valor) throws Exception {
        return send(bearer, post("/api/v1/assets/" + assetId + "/meters/" + kind + "/readings"),
                Map.of("value", valor, "readingAt", Instant.now().toString()), 201);
    }

    private JsonNode tarefa(String assetId) throws Exception {
        JsonNode planos = send(bearer, get("/api/v1/assets/" + assetId + "/maintenance-plans"), null, 200);
        assertThat(planos).hasSize(1);
        return planos.get(0).get("tasks").get(0);
    }

    private JsonNode resumoNaLista(String assetId) throws Exception {
        for (JsonNode a : send(bearer, get("/api/v1/assets?size=100"), null, 200).get("content")) {
            if (assetId.equals(a.get("id").asText())) {
                return a.get("nextMaintenance");
            }
        }
        throw new AssertionError("ativo não está na lista");
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("intervalo@teste.ao", "Transportes Intervalo").bearer();
        tipoCamiao = send(bearer, post("/api/v1/asset-types"),
                Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201).get("id").asText();
        tipoGerador = send(bearer, post("/api/v1/asset-types"),
                Map.of("name", "Gerador", "primaryMeter", "HOURMETER"), 201).get("id").asText();
    }

    @Test
    void limiteEmKmVenceQuandoOContadorLaChegaEGeraOrdemSozinho() throws Exception {
        String camiao = ativo("CM-01", tipoCamiao, 48_000);

        // Revisão a cada 5 000 km; a última foi aos 46 000 → a próxima é aos 51 000.
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Revisão geral");
        body.put("everyKm", 5000);
        body.put("lastDoneMeter", 46_000);
        body.put("lastDoneAt", Instant.now().minus(40, ChronoUnit.DAYS).toString());
        JsonNode plano = send(bearer, post("/api/v1/assets/" + camiao + "/maintenance-interval"), body, 201);
        assertThat(plano.get("planName").asText()).isEqualTo("Revisão geral — cada 5000 km");

        JsonNode t = tarefa(camiao);
        assertThat(t.get("nextDueMeter").asDouble()).isEqualTo(51_000.0);
        assertThat(t.get("remainingMeter").asDouble()).isEqualTo(3_000.0);
        assertThat(t.get("nextDueMeterKind").asText()).isEqualTo("ODOMETER");
        assertThat(t.get("status").asText()).isEqualTo("OK");

        // A lista de ativos já diz «em 3 000 km» sem abrir a ficha.
        JsonNode resumo = resumoNaLista(camiao);
        assertThat(resumo.get("title").asText()).isEqualTo("Revisão geral");
        assertThat(resumo.get("remainingMeter").asDouble()).isEqualTo(3_000.0);

        // O contador anda (à mão ou pelo GPS — o caminho é o mesmo): a 200 km do limite, «a vencer».
        leitura(camiao, "ODOMETER", 50_800);
        assertThat(tarefa(camiao).get("status").asText()).isEqualTo("DUE_SOON");
        assertThat(resumoNaLista(camiao).get("status").asText()).isEqualTo("DUE_SOON");

        // Passou o limite: vencida, e a rotina da manhã abre a ordem preventiva.
        leitura(camiao, "ODOMETER", 51_150);
        assertThat(tarefa(camiao).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(tarefa(camiao).get("remainingMeter").asDouble()).isEqualTo(-150.0);

        // O que a rotina das 06:00 (MaintenanceScheduler) faz por cada ativo com tarefas vencidas.
        workOrderService.autoGeneratePreventive(camiao);
        JsonNode ordens = send(bearer, get("/api/v1/work-orders?size=50"), null, 200).get("content");
        assertThat(ordens).hasSize(1);
        assertThat(ordens.get(0).get("type").asText()).isEqualTo("PREVENTIVE");
        assertThat(ordens.get(0).get("assetId").asText()).isEqualTo(camiao);

        // Correr outra vez não duplica a ordem.
        // O que a rotina das 06:00 (MaintenanceScheduler) faz por cada ativo com tarefas vencidas.
        workOrderService.autoGeneratePreventive(camiao);
        assertThat(send(bearer, get("/api/v1/work-orders?size=50"), null, 200).get("content")).hasSize(1);
    }

    @Test
    void geradorContaPorHorasENaoAceitaKm() throws Exception {
        String gerador = ativo("GR-01", tipoGerador, 1_200);

        JsonNode erro = send(bearer, post("/api/v1/assets/" + gerador + "/maintenance-interval"),
                Map.of("everyKm", 5000), 400);
        assertThat(erro.get("message").asText()).contains("conta por horas");

        send(bearer, post("/api/v1/assets/" + gerador + "/maintenance-interval"),
                Map.of("title", "Mudança de óleo", "everyHours", 250), 201);
        JsonNode t = tarefa(gerador);
        assertThat(t.get("nextDueMeter").asDouble()).isEqualTo(1_450.0);
        assertThat(t.get("remainingMeter").asDouble()).isEqualTo(250.0);
        assertThat(t.get("nextDueMeterKind").asText()).isEqualTo("HOURMETER");
    }

    @Test
    void semIntervaloNenhumRecusa() throws Exception {
        String camiao = ativo("CM-02", tipoCamiao, 10);
        JsonNode erro = send(bearer, post("/api/v1/assets/" + camiao + "/maintenance-interval"),
                Map.of("title", "Nada"), 400);
        assertThat(erro.get("message").asText()).contains("Indique o intervalo");
    }

    @Test
    void ultimaRevisaoNaoPodeSerMaiorDoQueOContadorAtual() throws Exception {
        String camiao = ativo("CM-03", tipoCamiao, 10_000);
        JsonNode erro = send(bearer, post("/api/v1/assets/" + camiao + "/maintenance-interval"),
                Map.of("everyKm", 5000, "lastDoneMeter", 12_000), 400);
        assertThat(erro.get("message").asText()).contains("maior do que a leitura atual");
    }

    @Test
    void oMesmoIntervaloNoutroCamiaoReutilizaOPlanoEDefinirOutraVezSubstitui() throws Exception {
        String a = ativo("CM-04", tipoCamiao, 1_000);
        String b = ativo("CM-05", tipoCamiao, 2_000);
        send(bearer, post("/api/v1/assets/" + a + "/maintenance-interval"), Map.of("everyKm", 10_000), 201);
        send(bearer, post("/api/v1/assets/" + b + "/maintenance-interval"), Map.of("everyKm", 10_000), 201);
        assertThat(send(bearer, get("/api/v1/maintenance-plans"), null, 200)).hasSize(1);

        // Mudar o limite do mesmo camião para o mesmo plano não duplica a atribuição.
        send(bearer, post("/api/v1/assets/" + a + "/maintenance-interval"),
                Map.of("everyKm", 10_000, "lastDoneMeter", 500), 201);
        JsonNode t = tarefa(a);
        assertThat(t.get("nextDueMeter").asDouble()).isEqualTo(10_500.0);

        // Um intervalo diferente é outro plano; o ativo passa a ter dois.
        send(bearer, post("/api/v1/assets/" + a + "/maintenance-interval"),
                Map.of("title", "Travões", "everyKm", 20_000, "everyDays", 180), 201);
        assertThat(send(bearer, get("/api/v1/maintenance-plans"), null, 200)).hasSize(2);
        assertThat(send(bearer, get("/api/v1/assets/" + a + "/maintenance-plans"), null, 200)).hasSize(2);
    }

    @Test
    void intervaloSoPorDiasFuncionaMesmoSemContador() throws Exception {
        String camiao = ativo("CM-06", tipoCamiao, 0);
        send(bearer, post("/api/v1/assets/" + camiao + "/maintenance-interval"),
                Map.of("title", "Inspeção periódica", "everyDays", 30,
                        "lastDoneAt", Instant.now().minus(25, ChronoUnit.DAYS).toString()), 201);
        JsonNode t = tarefa(camiao);
        assertThat(t.get("remainingDays").asInt()).isBetween(4, 5);
        assertThat(t.get("status").asText()).isEqualTo("DUE_SOON");
    }

    @Test
    void quemNaoGerePlanosNaoDefineLimites() throws Exception {
        String camiao = ativo("CM-07", tipoCamiao, 10);
        // Convidar um leitor.
        String token = send(bearer, post("/api/v1/team/invitations"),
                Map.of("email", "leitor@teste.ao", "role", "VIEWER"), 201).get("token").asText();
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", "Leitor", "password", "palavraForte1"), 200);
        String leitor = "Bearer " + res.get("accessToken").asText();
        send(leitor, post("/api/v1/assets/" + camiao + "/maintenance-interval"),
                Map.of("everyKm", 5000), 403);
        send(leitor, get("/api/v1/assets/" + camiao + "/maintenance-plans"), null, 200);
    }

    @Test
    void listaDeAtivosSemPlanoNaoInventaProximaManutencao() throws Exception {
        String camiao = ativo("CM-08", tipoCamiao, 10);
        assertThat(resumoNaLista(camiao)).isNull();
    }
}
