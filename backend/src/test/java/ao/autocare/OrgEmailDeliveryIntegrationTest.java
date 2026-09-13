package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O email que a empresa configura em Configurações não é decoração: os avisos
 * saem por ele. Um servidor SMTP de brincar recebe a mensagem e guarda-a.
 */
class OrgEmailDeliveryIntegrationTest extends AbstractIntegrationTest {

    private ServerSocket smtp;
    private Thread servidor;
    private final CopyOnWriteArrayList<String> recebidos = new CopyOnWriteArrayList<>();
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
        bearer = register("mail@teste.ao", "Transportes Correio").bearer();
        smtp = new ServerSocket(47395, 5, java.net.InetAddress.getByName("127.0.0.1"));
        servidor = new Thread(() -> {
            while (!smtp.isClosed()) {
                try (Socket s = smtp.accept()) {
                    atender(s);
                } catch (Exception ignored) {
                    // fechado
                }
            }
        });
        servidor.setDaemon(true);
        servidor.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        smtp.close();
    }

    /** SMTP mínimo: aceita tudo, guarda o DATA. */
    private void atender(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        out.print("220 teste ESMTP\r\n");
        out.flush();
        String linha;
        StringBuilder dados = null;
        while ((linha = in.readLine()) != null) {
            if (dados != null) {
                if (linha.equals(".")) {
                    recebidos.add(dados.toString());
                    dados = null;
                    out.print("250 OK\r\n");
                } else {
                    dados.append(linha).append('\n');
                }
            } else if (linha.toUpperCase().startsWith("EHLO") || linha.toUpperCase().startsWith("HELO")) {
                out.print("250-teste\r\n250 8BITMIME\r\n");
            } else if (linha.toUpperCase().startsWith("DATA")) {
                dados = new StringBuilder();
                out.print("354 fim com <CRLF>.<CRLF>\r\n");
            } else if (linha.toUpperCase().startsWith("QUIT")) {
                out.print("221 adeus\r\n");
                out.flush();
                return;
            } else {
                out.print("250 OK\r\n");
            }
            out.flush();
        }
    }

    @Test
    void osAvisosSaemPeloEmailQueAEmpresaConfigurou() throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("host", "127.0.0.1");
        cfg.put("port", 47395);
        cfg.put("from", "avisos@correio.ao");
        cfg.put("fromName", "Transportes Correio");
        cfg.put("security", "NONE");
        send(put("/api/v1/integrations/email"), cfg, 200);

        // O botão «Testar» chega ao servidor da empresa.
        JsonNode teste = send(post("/api/v1/integrations/email/test"), Map.of("to", "gestor@correio.ao"), 200);
        assertThat(teste.get("ok").asBoolean()).isTrue();
        assertThat(recebidos).hasSize(1);
        assertThat(recebidos.get(0)).contains("To: gestor@correio.ao");
        recebidos.clear();

        JsonNode canais = send(get("/api/v1/notifications/channels"), null, 200);
        assertThat(canais.get("emailConfigured").asBoolean()).isTrue();

        // E os avisos a sério também — pelo mesmo servidor, com o remetente da empresa.
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201).get("id").asText();
        String assetId = send(post("/api/v1/assets"), Map.of(
                "tag", "CR-1", "name", "Camião", "assetTypeId", typeId, "speedLimitKph", 60), 201).get("id").asText();
        String key = send(post("/api/v1/gps-devices"), Map.of("externalId", "IMEI-CR", "assetId", assetId), 201)
                .get("ingestKey").asText();
        Map<String, Object> position = new HashMap<>();
        position.put("latitude", -8.8383);
        position.put("longitude", 13.2344);
        position.put("speedKph", 110);
        position.put("satellites", 9);
        position.put("recordedAt", Instant.now().minus(5, ChronoUnit.MINUTES).toString());
        mvc.perform(post("/api/v1/telemetry/positions").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("deviceId", "IMEI-CR", "key", key, "position", position))))
                .andExpect(status().isOk());

        JsonNode avisos = send(get("/api/v1/notifications"), null, 200).get("content");
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).get("emailState").asText()).isEqualTo("SENT");
        assertThat(recebidos).hasSize(1);
        String mail = recebidos.get(0);
        assertThat(mail).contains("To: mail@teste.ao").contains("avisos@correio.ao");
        assertThat(mail).containsIgnoringCase("Subject:");
    }
}
