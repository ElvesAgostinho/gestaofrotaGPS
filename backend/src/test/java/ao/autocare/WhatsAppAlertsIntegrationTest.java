package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Avisos no telemóvel pelo WhatsApp (API oficial da Meta), contra um servidor
 * de brincar que fala como o verdadeiro: exige o token, o id do número e o
 * corpo de uma mensagem de modelo.
 */
@TestPropertySource(properties = {
        "autocare.whatsapp.api-url=http://127.0.0.1:47393",
        "autocare.whatsapp.token=TOKEN-DE-TESTE",
        "autocare.whatsapp.phone-id=1234567890",
        "autocare.whatsapp.template=aviso_frota",
        "autocare.whatsapp.template-lang=pt_PT"
})
class WhatsAppAlertsIntegrationTest extends AbstractIntegrationTest {

    private HttpServer meta;
    private final List<JsonNode> mensagens = new ArrayList<>();
    private volatile boolean recusar = false;
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

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("wa@teste.ao", "Transportes Zap").bearer();
        meta = HttpServer.create(new InetSocketAddress("127.0.0.1", 47393), 0);
        meta.createContext("/", this::atender);
        meta.start();
    }

    @AfterEach
    void tearDown() {
        meta.stop(0);
    }

    private synchronized void atender(HttpExchange t) throws java.io.IOException {
        String corpo = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String auth = t.getRequestHeaders().getFirst("Authorization");
        int codigo;
        String resposta;
        if (!"Bearer TOKEN-DE-TESTE".equals(auth)) {
            codigo = 401;
            resposta = "{\"error\":{\"message\":\"Invalid OAuth access token\"}}";
        } else if (!t.getRequestURI().getPath().equals("/1234567890/messages") || recusar) {
            codigo = 400;
            resposta = "{\"error\":{\"message\":\"(#131030) Recipient phone number not in allowed list\"}}";
        } else {
            mensagens.add(json.readTree(corpo));
            codigo = 200;
            resposta = "{\"messages\":[{\"id\":\"wamid.1\"}]}";
        }
        byte[] b = resposta.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", "application/json");
        t.sendResponseHeaders(codigo, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    private void excessoDeVelocidade(String imei) throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "ZP-" + imei, "name", "Camião", "assetTypeId", typeId, "speedLimitKph", 60), 201)
                .get("id").asText();
        String key = send(post("/api/v1/gps-devices"), Map.of("externalId", imei, "assetId", assetId), 201)
                .get("ingestKey").asText();
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", -8.8383);
        position.put("longitude", 13.2344);
        position.put("speedKph", 110);
        position.put("satellites", 9);
        position.put("recordedAt", Instant.now().minus(5, ChronoUnit.MINUTES).toString());
        mvc.perform(post("/api/v1/telemetry/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("deviceId", imei, "key", key, "position", position))))
                .andExpect(status().isOk());
    }

    @Test
    void oEcraDizQueHaWhatsAppEQueFaltaONumero() throws Exception {
        JsonNode canais = send(get("/api/v1/notifications/channels"), null, 200);
        assertThat(canais.get("phoneConfigured").asBoolean()).isTrue();
        assertThat(canais.get("phoneChannel").asText()).isEqualTo("WhatsApp");
        assertThat(canais.has("myPhone")).isFalse();

        // Sem número, o teste diz isso mesmo — e não envia nada.
        JsonNode teste = send(post("/api/v1/notifications/channels/test"), null, 200);
        assertThat(teste.get("sent").asBoolean()).isFalse();
        assertThat(teste.get("message").asText()).contains("Não tem número");
        assertThat(mensagens).isEmpty();
    }

    @Test
    void umAvisoGraveSaiPeloWhatsAppComOModeloAprovado() throws Exception {
        // O número mete-se no perfil, em qualquer formato razoável; guarda-se internacional.
        JsonNode perfil = send(patch("/api/v1/users/me"), Map.of("phone", "923 000 111"), 200);
        assertThat(perfil.get("phone").asText()).isEqualTo("+244923000111");

        JsonNode teste = send(post("/api/v1/notifications/channels/test"), null, 200);
        assertThat(teste.get("sent").asBoolean()).isTrue();
        assertThat(mensagens).hasSize(1);
        assertThat(mensagens.get(0).get("to").asText()).isEqualTo("244923000111");
        assertThat(mensagens.get(0).get("type").asText()).isEqualTo("template");
        assertThat(mensagens.get(0).at("/template/name").asText()).isEqualTo("aviso_frota");
        assertThat(mensagens.get(0).at("/template/language/code").asText()).isEqualTo("pt_PT");
        mensagens.clear();

        excessoDeVelocidade("IMEI-ZAP-1");

        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("severity").asText()).isEqualTo("WARNING");
        assertThat(avisos.get(0).get("phoneState").asText()).isEqualTo("SENT");
        assertThat(mensagens).hasSize(1);
        String texto = mensagens.get(0).at("/template/components/0/parameters/0/text").asText();
        assertThat(texto).contains("Transportes Zap").contains("Excesso de velocidade").contains("[ZP-IMEI-ZAP-1]");
    }

    @Test
    void quandoAMetaRecusaFicaFalhadoENaoEnviado() throws Exception {
        send(patch("/api/v1/users/me"), Map.of("phone", "+244923000222"), 200);
        recusar = true;
        excessoDeVelocidade("IMEI-ZAP-2");
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos.get(0).get("phoneState").asText()).isEqualTo("FAILED");
        assertThat(mensagens).isEmpty();

        JsonNode teste = send(post("/api/v1/notifications/channels/test"), null, 200);
        assertThat(teste.get("sent").asBoolean()).isFalse();
        assertThat(teste.get("message").asText()).contains("recusou");
    }

    @Test
    void quemDesligaOTelemovelNumaCategoriaNaoRecebe() throws Exception {
        send(patch("/api/v1/users/me"), Map.of("phone", "+244923000333"), 200);
        JsonNode prefs = send(get("/api/v1/notifications/preferences"), null, 200);
        assertThat(prefs.get(0).get("phone").asBoolean()).isTrue(); // por omissão ligado
        JsonNode gps = send(patch("/api/v1/notifications/preferences/GPS"), Map.of("phone", false), 200);
        assertThat(gps.get("phone").asBoolean()).isFalse();
        assertThat(gps.get("inApp").asBoolean()).isTrue();

        excessoDeVelocidade("IMEI-ZAP-3");
        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).hasSize(1); // dentro da aplicação continua a avisar
        assertThat(avisos.get(0).get("phoneState").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(mensagens).isEmpty();
    }

    @Test
    void umNumeroInvalidoOuRepetidoNaoEntra() throws Exception {
        JsonNode erro = send(patch("/api/v1/users/me"), Map.of("phone", "abc"), 400);
        assertThat(erro.get("message").asText()).contains("formato internacional");
        send(patch("/api/v1/users/me"), Map.of("phone", "+244923000444"), 200);
        String outro = register("wa2@teste.ao", "Outra").bearer();
        JsonNode dup = json.readTree(mvc.perform(patch("/api/v1/users/me").header("Authorization", outro)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+244923000444\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsByteArray());
        assertThat(dup.get("message").asText()).contains("outra conta");
        // Vazio retira o número.
        JsonNode semNumero = send(patch("/api/v1/users/me"), Map.of("phone", ""), 200);
        assertThat(semNumero.has("phone")).isFalse();
    }
}
