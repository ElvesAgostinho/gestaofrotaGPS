package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A app do telemóvel: o motorista comunica uma avaria com fotografia num só
 * passo, sem saber o que é uma ordem; quem gere é avisado; o técnico vê as
 * suas ordens no ecrã inicial.
 */
class MobileIntegrationTest extends AbstractIntegrationTest {

    private String dono;
    private String assetId;

    private JsonNode send(String token, MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        if (token != null) {
            req = req.header("Authorization", token);
        }
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
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", name, "password", "palavraForte1"), 200);
        return "Bearer " + res.get("accessToken").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        dono = register("mob@teste.ao", "Transportes Móvel").bearer();
        String tipo = send(dono, post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        assetId = send(dono, post("/api/v1/assets"), Map.of("tag", "MB-1", "name", "Camião 1", "assetTypeId", tipo,
                "plate", "LD-11-22-AA"), 201).get("id").asText();
        send(dono, post("/api/v1/assets"), Map.of("tag", "MB-2", "name", "Camião 2", "assetTypeId", tipo), 201);
    }

    @Test
    void oMotoristaComunicaUmaAvariaComFotografiaEOsGestoresSaoAvisados() throws Exception {
        String motorista = convidar("moto@teste.ao", "DRIVER", "Manuel");

        // O ecrã inicial lista as viaturas para escolher.
        JsonNode home = send(motorista, get("/api/v1/mobile/home"), null, 200);
        assertThat(home.get("userName").asText()).isEqualTo("Manuel");
        assertThat(home.get("assets")).hasSize(2);
        assertThat(home.get("assets").get(0).get("tag").asText()).isEqualTo("MB-1");
        assertThat(home.get("assets").get(0).get("plate").asText()).isEqualTo("LD-11-22-AA");
        assertThat(home.get("myOpenOrders").asLong()).isZero();

        // Comunicar: viatura, o que se passa, contador, fotografia.
        MockMultipartFile foto = new MockMultipartFile("photo", "avaria.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0});
        JsonNode ordem = json.readTree(mvc.perform(multipart("/api/v1/mobile/breakdowns")
                        .file(foto)
                        .param("assetId", assetId)
                        .param("title", "Luz do motor acesa")
                        .param("description", "Perde força nas subidas")
                        .param("meterValue", "125430")
                        .param("stopped", "false")
                        .header("Authorization", motorista))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        assertThat(ordem.get("type").asText()).isEqualTo("CORRECTIVE");
        assertThat(ordem.get("priority").asText()).isEqualTo("HIGH");
        assertThat(ordem.get("title").asText()).isEqualTo("Luz do motor acesa");
        assertThat(ordem.get("description").asText()).contains("Comunicado do telemóvel por Manuel").contains("Perde força");
        String id = ordem.get("id").asText();

        // A fotografia ficou na ordem, como «Antes».
        JsonNode anexos = send(dono, get("/api/v1/work-orders/" + id + "/attachments"), null, 200);
        assertThat(anexos).hasSize(1);
        assertThat(anexos.get(0).get("kind").asText()).isEqualTo("BEFORE");

        // O contador foi registado.
        JsonNode leituras = send(dono, get("/api/v1/assets/" + assetId + "/meters"), null, 200);
        assertThat(leituras.toString()).contains("125430");

        // Quem gere foi avisado (grave: chegaria ao telemóvel se houvesse canal).
        JsonNode avisos = send(dono, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).isEqualTo("Avaria comunicada: MB-1");
            assertThat(n.get("severity").asText()).isEqualTo("WARNING");
            assertThat(n.get("body").asText()).contains("Manuel").contains("Luz do motor acesa");
        });

        // Viatura parada = urgente e crítico.
        JsonNode parada = json.readTree(mvc.perform(multipart("/api/v1/mobile/breakdowns")
                        .param("assetId", assetId).param("title", "Não pega").param("stopped", "true")
                        .header("Authorization", motorista))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        assertThat(parada.get("priority").asText()).isEqualTo("URGENT");
        JsonNode avisos2 = send(dono, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos2).anySatisfy(n -> {
            assertThat(n.get("title").asText()).isEqualTo("Viatura parada: MB-1");
            assertThat(n.get("severity").asText()).isEqualTo("CRITICAL");
        });
    }

    @Test
    void semTituloOuViaturaNaoAbreNadaEUmVisualizadorNaoPode() throws Exception {
        String motorista = convidar("moto2@teste.ao", "DRIVER", "Rui");
        JsonNode erro = json.readTree(mvc.perform(multipart("/api/v1/mobile/breakdowns")
                        .param("assetId", assetId).param("title", "  ")
                        .header("Authorization", motorista))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsByteArray());
        assertThat(erro.get("message").asText()).contains("o que se passa");

        String leitor = convidar("ver@teste.ao", "VIEWER", "Zé");
        mvc.perform(multipart("/api/v1/mobile/breakdowns").param("assetId", assetId).param("title", "x")
                .header("Authorization", leitor)).andExpect(status().isForbidden());
    }

    @Test
    void oTecnicoVeQuantasOrdensTemAbertas() throws Exception {
        String tecnico = convidar("tec@teste.ao", "TECHNICIAN", "Ana");
        String tecnicoId = send(dono, get("/api/v1/team/members"), null, 200).get(1).get("userId").asText();
        send(dono, post("/api/v1/work-orders"), Map.of("assetId", assetId, "type", "PREVENTIVE",
                "title", "Revisão", "assignedToUserId", tecnicoId), 201);
        JsonNode home = send(tecnico, get("/api/v1/mobile/home"), null, 200);
        assertThat(home.get("myOpenOrders").asLong()).isEqualTo(1);
        JsonNode minhas = send(tecnico, get("/api/v1/work-orders?assignedTo=me"), null, 200).get("content");
        assertThat(minhas).hasSize(1);
    }

    @Test
    void aInspecaoDiariaReprovadaNumPontoCriticoAvisaQuemGereERegistaOContador() throws Exception {
        String motorista = convidar("moto3@teste.ao", "DRIVER", "Paulo");
        Map<String, Object> exec = new HashMap<>();
        exec.put("templateName", "Inspeção diária");
        exec.put("meterValue", 88000);
        exec.put("items", java.util.List.of(
                Map.of("text", "Pneus", "critical", true, "result", "OK"),
                Map.of("text", "Travões", "critical", true, "result", "NOT_OK", "note", "Pedal vai ao fundo"),
                Map.of("text", "Rádio", "critical", false, "result", "NOT_OK")));
        JsonNode r = send(motorista, post("/api/v1/assets/" + assetId + "/checklist-executions"), exec, 201);
        assertThat(r.get("outcome").asText()).isEqualTo("ISSUES");
        assertThat(r.get("itemsNotOk").asInt()).isEqualTo(2);

        JsonNode avisos = send(dono, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).anySatisfy(n -> {
            assertThat(n.get("title").asText()).isEqualTo("Inspeção reprovada: MB-1");
            assertThat(n.get("category").asText()).isEqualTo("INSPECTION");
            assertThat(n.get("body").asText()).contains("Travões").doesNotContain("Rádio");
        });
        JsonNode leituras = send(dono, get("/api/v1/assets/" + assetId + "/meters"), null, 200);
        assertThat(leituras.toString()).contains("88000");

        // Tudo OK: sem aviso.
        exec.put("items", java.util.List.of(Map.of("text", "Pneus", "critical", true, "result", "OK")));
        exec.put("meterValue", 88010);
        send(motorista, post("/api/v1/assets/" + assetId + "/checklist-executions"), exec, 201);
        JsonNode depois = send(dono, get("/api/v1/notifications"), null, 200).get("content");
        assertThat(depois.size()).isEqualTo(avisos.size());
    }
}
