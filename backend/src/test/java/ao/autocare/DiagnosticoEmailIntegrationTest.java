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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * <p>O que se protege aqui são as quatro respostas que resolvem o problema: a
 * incoerência que se apanha sem rede (porto e segurança trocados), a ligação
 * que não abre (quase sempre o alojamento a bloquear a saída), a resposta do
 * servidor em cru — que é o que permite pedir ajuda a alguém — e a máscara do
 * campo da palavra-passe, que ia dentro da palavra-passe e garantia a recusa.
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

    private void configurar(Map<String, Object> cfg) throws Exception {
        send(put("/api/v1/integrations/email"), cfg, 200);
    }

    private Map<String, Object> base(int porto, String seguranca, String host) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("host", host);
        cfg.put("port", porto);
        cfg.put("from", "avisos@diagnostico.ao");
        cfg.put("security", seguranca);
        return cfg;
    }

    private JsonNode testar() throws Exception {
        return send(post("/api/v1/integrations/email/test"),
                Map.of("to", "gestor@diagnostico.ao"), 200);
    }

    private JsonNode configurarETestar(int porto, String seguranca, String host) throws Exception {
        configurar(base(porto, seguranca, host));
        return testar();
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
        JsonNode r = configurarETestar(587, "STARTTLS", "servidor-que-nao-existe.invalid");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("não existe");
    }

    // ==== A resposta do servidor, em cru ====================================

    @Test
    void aRespostaDoServidorChegaAoEcra() throws Exception {
        // Um servidor que atende e recusa logo. É o caso em que a configuração
        // está certa e o problema é do outro lado — e a única coisa que ajuda
        // é ver o que ele respondeu.
        abrirServidor(47397, (in, out) -> {
            out.print("421 4.7.0 Try again later, closing connection" + FIM);
            out.flush();
        });

        JsonNode r = configurarETestar(47397, "NONE", "127.0.0.1");
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("detail").asText())
                .as("o que o servidor respondeu tem de aparecer, em cru")
                .contains("421");

        // E fica guardado, para quem voltar ao ecrã dias depois ainda o ler.
        JsonNode estado = send(get("/api/v1/integrations"), null, 200);
        assertThat(estado.get("smtpOk").asBoolean()).isFalse();
        assertThat(estado.get("smtpLastError").asText()).contains("421");
    }

    @Test
    void aAutenticacaoRecusadaNomeiaOUtilizadorQueFoiTentado() throws Exception {
        abrirServidor(47400, (in, out) -> responderSmtp(in, out, null));

        Map<String, Object> cfg = base(47400, "NONE", "127.0.0.1");
        cfg.put("username", "avisos@diagnostico.ao");
        cfg.put("password", "errada");
        configurar(cfg);

        JsonNode r = testar();
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("hint").asText())
                .as("quem lê tem de ver que credenciais foram tentadas")
                .contains("avisos@diagnostico.ao")
                .contains("caixa de correio");
    }

    @Test
    void umUtilizadorSemArrobaRecebeASugestaoDoEnderecoCompleto() throws Exception {
        abrirServidor(47401, (in, out) -> responderSmtp(in, out, null));

        Map<String, Object> cfg = base(47401, "NONE", "127.0.0.1");
        cfg.put("username", "avisos");
        cfg.put("password", "errada");
        configurar(cfg);

        JsonNode r = testar();
        assertThat(r.get("hint").asText())
                .contains("avisos@diagnostico.ao")
                .contains("endereço completo");
    }

    // ==== A máscara do campo, que ia dentro da palavra-passe ================

    @Test
    void aMascaraDoCampoNaoVaiParaDentroDaPalavraPasse() throws Exception {
        // A armadilha: o campo mostra pontos quando já há palavra-passe
        // guardada, e quem clica e escreve a seguir manda «••••••••senha». Não
        // sendo igual à máscara, passava e era guardada com os pontos à frente
        // — autenticação recusada para sempre, com a palavra-passe certa
        // escrita no ecrã. Não é erro de quem escreve; era do campo.
        List<String> pedidos = new CopyOnWriteArrayList<>();
        abrirServidor(47398, (in, out) -> responderSmtp(in, out, pedidos));

        Map<String, Object> cfg = base(47398, "NONE", "127.0.0.1");
        cfg.put("username", "avisos@diagnostico.ao");
        cfg.put("password", "••••••••segredo-real");
        configurar(cfg);
        testar();

        assertThat(pedidos).as("o servidor recebeu um pedido de autenticação").isNotEmpty();
        String credenciais = decodificar(pedidos);
        assertThat(credenciais).as("a palavra-passe segue sem os pontos da máscara")
                .contains("segredo-real")
                .doesNotContain("•");
    }

    // ==== O que foi realmente enviado =======================================

    @Test
    void utilizadorSemPalavraPasseNaoSeConfundeComPalavraPasseErrada() throws Exception {
        // Guardar com a máscara no campo significa «não mudar». Se nunca houve
        // palavra-passe, fica utilizador sem palavra-passe — e o servidor
        // responde 535, igual a uma palavra-passe errada. São coisas
        // diferentes e o sistema tem de as separar.
        Map<String, Object> cfg = base(47402, "NONE", "127.0.0.1");
        cfg.put("username", "geral@diagnostico.ao");
        cfg.put("password", "••••••••");
        configurar(cfg);

        JsonNode r = testar();
        assertThat(r.get("ok").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("não há nenhuma palavra-passe");
        assertThat(r.get("hint").asText()).contains("apague");
    }

    @Test
    void aFormaDaPalavraPasseAparecePraQuemTemDeADepurar() throws Exception {
        abrirServidor(47403, (in, out) -> responderSmtp(in, out, null));

        Map<String, Object> cfg = base(47403, "NONE", "127.0.0.1");
        cfg.put("username", "geral@diagnostico.ao");
        cfg.put("password", "doze-letras ");
        configurar(cfg);

        JsonNode r = testar();
        assertThat(r.get("ok").asBoolean()).isFalse();
        String detalhe = r.get("detail").asText();
        assertThat(detalhe).as("o utilizador e a forma da palavra-passe, nunca a palavra-passe")
                .contains("geral@diagnostico.ao")
                .contains("caracteres")
                .doesNotContain("doze-letras");
    }

    @Test
    void aConversaComOServidorApareceSemAsCredenciais() throws Exception {
        java.util.List<String> pedidos = new CopyOnWriteArrayList<>();
        abrirServidor(47404, (in, out) -> responderSmtp(in, out, pedidos));

        Map<String, Object> cfg = base(47404, "NONE", "127.0.0.1");
        cfg.put("username", "geral@diagnostico.ao");
        cfg.put("password", "palavra-passe-secreta");
        configurar(cfg);

        JsonNode r = testar();
        assertThat(r.get("ok").asBoolean()).isFalse();
        String dialogo = r.get("dialog").asText();

        // O que interessa ver: o que o servidor ofereceu e onde recusou.
        // O que interessa ver: que mecanismos o servidor ofereceu, qual foi
        // escolhido, e onde recusou.
        assertThat(dialogo).as("diálogo: [" + dialogo + "]")
                .contains("535")
                .contains("AUTH")
                .contains("PLAIN");
        // O que nunca pode aparecer: as credenciais, nem em base64.
        assertThat(dialogo).doesNotContain("palavra-passe-secreta");
        String emBase64 = java.util.Base64.getEncoder().encodeToString(
                "palavra-passe-secreta".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(dialogo).doesNotContain(emBase64);
        assertThat(dialogo).contains("credenciais ocultas");
    }

    @Test
    void umEspacoNoFimDaPalavraPasseJaNaoEDeitadoFora() throws Exception {
        java.util.List<String> pedidos = new CopyOnWriteArrayList<>();
        abrirServidor(47405, (in, out) -> responderSmtp(in, out, pedidos));

        // Há palavras-passe que acabam em espaço. Cortá-lo é inventar outra
        // palavra-passe e depois culpar quem a escreveu.
        Map<String, Object> cfg = base(47405, "NONE", "127.0.0.1");
        cfg.put("username", "geral@diagnostico.ao");
        cfg.put("password", "com-espaco-no-fim ");
        configurar(cfg);
        testar();

        assertThat(decodificar(pedidos)).contains("com-espaco-no-fim ");
    }

    // ==== Servidor SMTP de brincar ==========================================

    private static final String FIM = "\r\n";

    /** O que um atendimento faz com a ligação. */
    private interface Atendimento {
        void atender(BufferedReader in, PrintWriter out) throws Exception;
    }

    private void abrirServidor(int porto, Atendimento atendimento) throws Exception {
        smtp = new ServerSocket(porto, 5, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            while (!smtp.isClosed()) {
                try (Socket s = smtp.accept()) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    atendimento.atender(in, out);
                } catch (Exception ignorado) {
                    // fechado
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Anuncia AUTH e recusa-a, guardando tudo o que o cliente enviou.
     *
     * <p>Guarda-se cada linha e não só a do AUTH: o JavaMail pode escolher
     * PLAIN — que leva as credenciais na própria linha — ou LOGIN, que as manda
     * nas duas linhas seguintes, em resposta aos avisos 334. O teste tem de
     * valer nos dois casos, senão passa a depender de um detalhe da biblioteca.
     */
    private static void responderSmtp(BufferedReader in, PrintWriter out, List<String> pedidos)
            throws Exception {
        out.print("220 teste ESMTP" + FIM);
        out.flush();
        String linha;
        boolean aAutenticar = false;
        int avisos = 0;
        while ((linha = in.readLine()) != null) {
            if (pedidos != null) {
                pedidos.add(linha);
            }
            String maiusculas = linha.toUpperCase();
            if (maiusculas.startsWith("EHLO") || maiusculas.startsWith("HELO")) {
                out.print("250-teste" + FIM + "250 AUTH PLAIN LOGIN" + FIM);
            } else if (maiusculas.startsWith("AUTH LOGIN")) {
                // VXNlcm5hbWU6 = «Username:»
                aAutenticar = true;
                avisos = 0;
                out.print("334 VXNlcm5hbWU6" + FIM);
            } else if (aAutenticar && avisos == 0) {
                // UGFzc3dvcmQ6 = «Password:»
                avisos = 1;
                out.print("334 UGFzc3dvcmQ6" + FIM);
            } else if (aAutenticar) {
                aAutenticar = false;
                out.print("535 5.7.8 Error: authentication failed" + FIM);
            } else if (maiusculas.startsWith("AUTH")) {
                out.print("535 5.7.8 Error: authentication failed" + FIM);
            } else if (maiusculas.startsWith("QUIT")) {
                out.print("221 adeus" + FIM);
                out.flush();
                return;
            } else {
                out.print("250 OK" + FIM);
            }
            out.flush();
        }
    }

    /**
     * Tudo o que veio em base64, descodificado e junto.
     *
     * <p>Serve para PLAIN (as credenciais na linha do AUTH) e para LOGIN (uma
     * linha para o utilizador, outra para a palavra-passe). Tenta descodificar
     * cada pedaço e ignora o que não for base64 — os comandos em claro.
     */
    private static String decodificar(List<String> pedidos) {
        StringBuilder sb = new StringBuilder();
        for (String pedido : pedidos) {
            for (String pedaco : pedido.trim().split("\\s+")) {
                if (pedaco.length() < 4) {
                    continue;
                }
                try {
                    byte[] cru = Base64.getDecoder().decode(pedaco);
                    sb.append(new String(cru, StandardCharsets.UTF_8)).append(' ');
                } catch (IllegalArgumentException ignorado) {
                    // não era base64: é um comando em claro
                }
            }
        }
        return sb.toString();
    }
}
