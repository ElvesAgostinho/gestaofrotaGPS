package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ordens de serviço «a sério»: tempo real por técnico (cronómetro → mão de
 * obra), lista de verificação obrigatória antes de concluir, fotografia do
 * «depois» quando a empresa a exige, e quem pediu aprovação sabe da decisão.
 */
class WorkOrderTimerIntegrationTest extends AbstractIntegrationTest {

    private String dono;
    private String assetId;

    private JsonNode send(String token, MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", token);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private String convidar(String email, String role, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("role", role);
        String token = send(dono, post("/api/v1/team/invitations"), body, 201).get("token").asText();
        JsonNode res = json.readTree(mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return "Bearer " + res.get("accessToken").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        dono = register("cron@teste.ao", "Oficina Cronómetro").bearer();
        String tipo = send(dono, post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(dono, post("/api/v1/assets"), Map.of("tag", "CR-1", "name", "Camião", "assetTypeId", tipo), 201)
                .get("id").asText();
    }

    @Test
    void oCronometroDoTecnicoViraMaoDeObraComAsHorasReais() throws Exception {
        String tecnico = convidar("tec@teste.ao", "TECHNICIAN", "Ana Técnica");
        String id = send(dono, post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "CORRECTIVE",
                "title", "Travões"), 201).get("id").asText();

        JsonNode aCorrer = send(tecnico, post("/api/v1/work-orders/" + id + "/timer/start"), null, 200);
        assertThat(aCorrer.get("status").asText()).isEqualTo("IN_PROGRESS"); // começar a contar é começar a trabalhar
        assertThat(aCorrer.get("timers")).hasSize(1);
        assertThat(aCorrer.get("timers").get(0).get("userName").asText()).isEqualTo("Ana Técnica");

        // Um segundo «iniciar» na mesma ordem não duplica.
        send(tecnico, post("/api/v1/work-orders/" + id + "/timer/start"), null, 200);
        assertThat(send(dono, get("/api/v1/work-orders/" + id), null, 200).get("timers")).hasSize(1);

        JsonNode parada = send(tecnico, post("/api/v1/work-orders/" + id + "/timer/stop"), null, 200);
        assertThat(parada.get("timers")).isEmpty();
        assertThat(parada.get("labor")).hasSize(2); // o segundo «iniciar» fechou o primeiro
        assertThat(parada.get("labor").get(0).get("technicianLabel").asText()).isEqualTo("Ana Técnica");
        assertThat(parada.get("labor").get(0).get("hours").asDouble()).isEqualTo(0.1); // mínimo de 0,1 h
        assertThat(parada.get("totalLaborHours").asDouble()).isEqualTo(0.2);

        // Parar sem ter iniciado é um erro dito com clareza.
        JsonNode erro = send(tecnico, post("/api/v1/work-orders/" + id + "/timer/stop"), null, 409);
        assertThat(erro.get("message").asText()).contains("nenhum cronómetro");

        // Um cronómetro esquecido fecha-se ao concluir.
        send(tecnico, post("/api/v1/work-orders/" + id + "/timer/start"), null, 200);
        JsonNode concluida = send(tecnico, post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "Feito"), 200);
        assertThat(concluida.get("timers")).isEmpty();
        assertThat(concluida.get("labor")).hasSize(3);
    }

    @Test
    void semConfirmarAsTarefasNaoSeConcluiEAEmpresaPodeExigirFotografiaDoDepois() throws Exception {
        JsonNode om = send(dono, post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "PREVENTIVE",
                "title", "Revisão", "tasks", List.of(Map.of("title", "Mudar óleo"), Map.of("title", "Filtro de ar"))), 201);
        String id = om.get("id").asText();
        send(dono, post("/api/v1/work-orders/" + id + "/start"), null, 200);

        JsonNode erro = send(dono, post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "x"), 409);
        assertThat(erro.get("message").asText()).contains("2 tarefa(s) por confirmar").contains("Mudar óleo");

        for (JsonNode t : om.get("tasks")) {
            send(dono, post("/api/v1/work-orders/" + id + "/tasks/" + t.get("id").asText()), Map.of("done", true), 200);
        }

        // A empresa passa a exigir fotografia do «depois».
        JsonNode org = send(dono, patch("/api/v1/organization"), Map.of("closeRequiresAfterPhoto", true,
                "maintenanceApprovalLimit", 50000), 200);
        assertThat(org.get("closeRequiresAfterPhoto").asBoolean()).isTrue();
        assertThat(org.get("maintenanceApprovalLimit").asDouble()).isEqualTo(50000.0);

        JsonNode semFoto = send(dono, post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "x"), 409);
        assertThat(semFoto.get("message").asText()).contains("fotografia do «depois»");

        mvc.perform(multipart("/api/v1/work-orders/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "depois.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 0, 0}))
                        .param("kind", "AFTER").header("Authorization", dono))
                .andExpect(status().isOk());
        JsonNode ok = send(dono, post("/api/v1/work-orders/" + id + "/complete"), Map.of("resolution", "Feito"), 200);
        assertThat(ok.get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void quemPedeAprovacaoFicaASaberDaDecisao() throws Exception {
        String gestor = convidar("gestor@teste.ao", "MANAGER", "Rui Gestor");
        // Limite de 50 000: abaixo aprova sozinho; acima vai ao dono.
        send(dono, patch("/api/v1/organization"), Map.of("maintenanceApprovalLimit", 50000), 200);

        String id = send(gestor, post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "CORRECTIVE",
                "title", "Caixa de velocidades", "estimatedCost", 900000), 201).get("id").asText();
        JsonNode pendente = send(gestor, post("/api/v1/work-orders/" + id + "/request-approval"), null, 200);
        assertThat(pendente.get("status").asText()).isEqualTo("AWAITING_APPROVAL");

        // O dono foi avisado de que há um pedido…
        JsonNode avisosDono = send(dono, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisosDono).anySatisfy(n -> assertThat(n.get("title").asText()).contains("Aprovacao pendente"));

        // …e o gestor fica a saber da decisão.
        send(dono, post("/api/v1/work-orders/" + id + "/reject"), Map.of("note", "Pedir segundo orçamento à oficina B"), 200);
        JsonNode avisosGestor = send(gestor, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisosGestor).anySatisfy(n -> {
            assertThat(n.get("title").asText()).contains("Orçamento rejeitado");
            assertThat(n.get("body").asText()).contains("segundo orçamento");
        });

        // Abaixo do limite: aprovação automática, sem incomodar ninguém.
        String pequena = send(gestor, post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "CORRECTIVE",
                "title", "Lâmpada", "estimatedCost", 4000), 201).get("id").asText();
        JsonNode auto = send(gestor, post("/api/v1/work-orders/" + pequena + "/request-approval"), null, 200);
        assertThat(auto.get("status").asText()).isEqualTo("APPROVED");
        assertThat(auto.get("approvalNote").asText()).contains("automatica");
    }
}
