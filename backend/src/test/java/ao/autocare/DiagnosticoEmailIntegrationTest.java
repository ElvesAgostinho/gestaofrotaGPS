package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quando o email falha, o sistema tem de dizer <b>porquê</b>.
 *
 * <p>Este teste existe por causa de um relato: «pus as credenciais do SMTP,
 * não funciona, dá sempre erro». E a frase que o sistema devolvia era «não foi
 * possível enviar, verifique o servidor, o porto e as credenciais» — que não
 * aponta para nada e manda procurar em três sítios ao mesmo tempo. Com uma
 * mensagem assim, nem quem escreveu o programa consegue ajudar.
 *
 * <p>O que se protege aqui são as três respostas que resolvem o problema: a
 * incoerência que se apanha sem rede (porto e segurança trocados), a ligação
 * que não abre (quase sempre o alojamento a bloquear a saída) e a resposta do
 * servidor em cru, que é o que permite pedir ajuda a alguém.
 */
class DiagnosticoEmailIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private ServerSocket smtp;

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
        bearer = register("diag@teste.ao", "Transportes Diagnóstico").bearer();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (smtp != null && !smtp.isClosed()) {
            smtp.close();
        }
    }

    private JsonNode configurarETestar(int porto, String seguranca, String host) throws Exception {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("host", host);
        cfg.put("port", porto);
        cfg.put("from", "avisos@diagnostico.ao");
        cfg.put("security", seguranca);
        send(put("/api/v1/integrations/email"), cfg, 200);
        return send(post("/api/v1/integrations/email/test"),
                Map.of("to", "gestor@diagnostico.ao"), 200);
    }

    // ==== O que se apanha sem rede nenhuma ==================================

    @Test
    void oPortoEASegurancaTrocadosSaoApanhadosAntesDeTentar() throws Exception {
        // 465 fala TLS desde o primeiro byte. Pedir-lhe STARTTLS dá um silêncio
        // até ao fim do tempo de espera — e esse erro parece «servidor em baixo».
        JsonNode r = configurarETestar(465, "STARTTLS", "smtp.example.ao");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("hint").asText()).contains("465").contains("SSL");

        // E o contrário.
        JsonNode r2 = configurarETestar(587, "SSL", "smtp.example.ao");
        assertThat(r2.get("ok").asBoolean()).isFalse();
        assertThat(r2.get("hint").asText()).contains("587").contains("STARTTLS");

        // O porto 25 está bloqueado em praticamente todos os alojamentos.
        JsonNode r3 = configurarETestar(25, "NONE", "smtp.example.ao");
        assertThat(r3.get("ok").asBoolean()).isFalse();
        assertThat(r3.get("hint").asText()).contains("25");
    }

    // ==== A ligação que não abre ============================================

    @Test
    void quandoNaoSeChegaAoServidorDizSeQueEOAlojamento() throws Exception {
        // Um porto onde não está ninguém: é o que se vê quando o alojamento
        // bloqueia a saída de email, que é a causa mais comum de todas.
        JsonNode r = configurarETestar(47396, "NONE", "127.0.0.1");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("não consegue abrir ligação");
        assertThat(r.get("detail").asText()).contains("47396");
        assertThat(r.get("hint").asText()).contains("587").contains("465");
    }

    @Test
    void umNomeDeServidorQueNaoExisteDizSeQueENome() throws Exception {
        JsonNode r = configurarETestar(587, "STARTTLS",
                "servidor-que-nao-existe.invalid");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("não existe");
    }

    // ==== A resposta do servidor, em cru ====================================

    @Test
    void aRespostaDoServidorChegaAoEcra() throws Exception {
        // Um servidor que atende e recusa logo. É o caso em que a configuração
        // está certa e o problema é do outro lado — e a única coisa que ajuda
        // é ver o que ele respondeu.
        smtp = new ServerSocket(47397, 5, java.net.InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (!smtp.isClosed()) {
                try (Socket s = smtp.accept()) {
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.print("421 4.7.0 Try again later, closing connection\r\n");
                    out.flush();
                } catch (Exception ignorado) {
                    // fechado
                }
            }
        });
        t.setDaemon(true);
        t.start();

        JsonNode r = configurarETestar(47397, "NONE", "127.0.0.1");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("detail").asText())
                .as("o que o servidor respondeu tem de aparecer, em cru")
                .contains("421");

        // E fica guardado, para quem voltar ao ecrã dias depois ainda o ler.
        JsonNode estado = send(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/integrations"), null, 200);
        assertThat(estado.get("smtpOk").asBoolean()).isFalse();
        assertThat(estado.get("smtpLastError").asText()).contains("421");
    }
}
