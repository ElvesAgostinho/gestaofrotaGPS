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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Sem WhatsApp, uma gateway HTTP de SMS serve: o URL leva o número e o texto,
 * e um cabeçalho com a chave. Aqui a gateway é de brincar mas exige a chave.
 */
@TestPropertySource(properties = {
        "autocare.sms.gateway-url=http://127.0.0.1:47394/send?to={to}&msg={text}&from=IMBONDEIRO",
        "autocare.sms.gateway-header=X-Api-Key: chave-sms"
})
class SmsAlertsIntegrationTest extends AbstractIntegrationTest {

    private HttpServer gateway;
    private final List<String> pedidos = new ArrayList<>();
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("sms@teste.ao", "Transportes SMS").bearer();
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 47394), 0);
        gateway.createContext("/", this::atender);
        gateway.start();
    }

    @AfterEach
    void tearDown() {
        gateway.stop(0);
    }

    private synchronized void atender(HttpExchange t) throws java.io.IOException {
        int codigo = "chave-sms".equals(t.getRequestHeaders().getFirst("X-Api-Key")) ? 200 : 403;
        if (codigo == 200) {
            pedidos.add(URLDecoder.decode(t.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
        }
        t.sendResponseHeaders(codigo, -1);
        t.close();
    }

    @Test
    void aMensagemDeTesteSaiPelaGatewayComONumeroEOTexto() throws Exception {
        JsonNode canais = json.readTree(mvc.perform(get("/api/v1/notifications/channels").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(canais.get("phoneChannel").asText()).isEqualTo("SMS");

        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", "+244 923-000-555"))))
                .andExpect(status().isOk());
        JsonNode teste = json.readTree(mvc.perform(post("/api/v1/notifications/channels/test").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(teste.get("sent").asBoolean()).isTrue();
        assertThat(teste.get("message").asText()).contains("SMS").contains("+244923000555");
        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.get(0)).contains("to=+244923000555").contains("msg=IMBONDEIRO OS").contains("from=IMBONDEIRO");
    }
}
