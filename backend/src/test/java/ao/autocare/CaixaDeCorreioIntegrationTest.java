package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A caixa de correio da empresa, com um servidor de email a sério por trás.
 *
 * <p>IMAP não se finge com um socket escrito à mão: são pastas, estados de
 * leitura, codificações e anexos dentro de árvores MIME. Com um servidor
 * verdadeiro em memória, o que se testa é o que vai acontecer em produção.
 *
 * <p>O que estes testes protegem não é «consegue ler email» — isso é a
 * biblioteca. É o que torna esta caixa diferente de um webmail qualquer: saber
 * <b>quem</b> está do outro lado. Uma mensagem de um fornecedor registado tem
 * de aparecer com o nome da empresa e não com o endereço, e tem de ser possível
 * ver só o correio dos fornecedores — que é a diferença entre procurar uma
 * mensagem durante cinco minutos e abri-la logo.
 */
class CaixaDeCorreioIntegrationTest extends AbstractIntegrationTest {

    private static final int PORTO_SMTP = 43025;
    private static final int PORTO_IMAP = 43143;
    private static final String CAIXA = "geral@transportes.ao";
    private static final String SENHA = "caixa-secreta";

    private GreenMail servidor;
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
        servidor = new GreenMail(new ServerSetup[] {
                new ServerSetup(PORTO_SMTP, "127.0.0.1", ServerSetup.PROTOCOL_SMTP),
                new ServerSetup(PORTO_IMAP, "127.0.0.1", ServerSetup.PROTOCOL_IMAP),
        });
        servidor.start();
        servidor.setUser(CAIXA, CAIXA, SENHA);

        bearer = register("correio@teste.ao", "Transportes Correio").bearer();
        configurarCaixa();
    }

    @AfterEach
    void tearDown() {
        if (servidor != null) {
            servidor.stop();
        }
    }

    private void configurarCaixa() throws Exception {
        Map<String, Object> smtp = new HashMap<>();
        smtp.put("host", "127.0.0.1");
        smtp.put("port", PORTO_SMTP);
        smtp.put("security", "NONE");
        smtp.put("username", CAIXA);
        smtp.put("password", SENHA);
        smtp.put("from", CAIXA);
        smtp.put("fromName", "Transportes Correio");
        send(put("/api/v1/integrations/email"), smtp, 200);

        Map<String, Object> imap = new HashMap<>();
        imap.put("host", "127.0.0.1");
        imap.put("port", PORTO_IMAP);
        imap.put("security", "NONE");
        imap.put("useSmtpLogin", true);
        send(put("/api/v1/integrations/imap"), imap, 200);
    }

    /** Põe uma mensagem na caixa, como se tivesse chegado. */
    private void entregar(String de, String nomeDe, String assunto, String corpo) throws Exception {
        MimeMessage m = new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        m.setFrom(new InternetAddress(de, nomeDe));
        m.setRecipient(Message.RecipientType.TO, new InternetAddress(CAIXA));
        m.setSubject(assunto, "UTF-8");
        m.setText(corpo, "UTF-8");
        servidor.getManagers().getImapHostManager()
                .getInbox(servidor.getManagers().getUserManager().getUser(CAIXA))
                .store(m);
    }

    /** O mesmo, mas com um anexo. */
    private void entregarComAnexo(String de, String assunto, String nomeDoFicheiro, byte[] dados)
            throws Exception {
        MimeMessage m = new MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        m.setFrom(new InternetAddress(de));
        m.setRecipient(Message.RecipientType.TO, new InternetAddress(CAIXA));
        m.setSubject(assunto, "UTF-8");

        MimeBodyPart texto = new MimeBodyPart();
        texto.setText("Segue a factura em anexo.", "UTF-8");
        MimeBodyPart anexo = new MimeBodyPart();
        anexo.setContent(dados, "application/pdf");
        anexo.setFileName(nomeDoFicheiro);
        anexo.setDisposition(jakarta.mail.Part.ATTACHMENT);

        MimeMultipart mp = new MimeMultipart("mixed");
        mp.addBodyPart(texto);
        mp.addBodyPart(anexo);
        m.setContent(mp);
        servidor.getManagers().getImapHostManager()
                .getInbox(servidor.getManagers().getUserManager().getUser(CAIXA))
                .store(m);
    }

    private String fornecedor(String nome, String email) throws Exception {
        return send(post("/api/v1/suppliers"),
                Map.of("name", nome, "email", email, "kind", "PARTS"), 201).get("id").asText();
    }

    // ==== O que faz desta caixa diferente de um webmail =====================

    @Test
    void oCorreioDeUmFornecedorAparecePeloNomeDaEmpresa() throws Exception {
        fornecedor("Auto Peças de Luanda", "vendas@autopecas.co.ao");
        entregar("vendas@autopecas.co.ao", "Depto Comercial",
                "Proposta de filtros", "Segue a nossa proposta.");
        entregar("alguem@desconhecido.com", "Alguém",
                "Promoção imperdível", "Clique aqui.");

        JsonNode pagina = send(get("/api/v1/mailbox/messages?folder=INBOX"), null, 200);
        assertThat(pagina.get("total").asInt()).isEqualTo(2);

        JsonNode daEmpresa = null;
        for (JsonNode m : pagina.get("mensagens")) {
            if ("Proposta de filtros".equals(m.get("assunto").asText())) {
                daEmpresa = m;
            }
        }
        assertThat(daEmpresa).isNotNull();
        // O nome que conta é o que a empresa tem no sistema, não o que vem no
        // cabeçalho do email — que quem escreve escolhe à vontade.
        assertThat(daEmpresa.get("de").get("nome").asText()).isEqualTo("Auto Peças de Luanda");
        assertThat(daEmpresa.get("de").get("entidade").get("tipo").asText()).isEqualTo("SUPPLIER");
        assertThat(daEmpresa.get("de").get("entidade").get("link").asText()).contains("/fornecedores/");
    }

    @Test
    void daParaVerSoOCorreioDeQuemEstaNoSistema() throws Exception {
        fornecedor("Auto Peças de Luanda", "vendas@autopecas.co.ao");
        entregar("vendas@autopecas.co.ao", "Comercial", "Proposta de filtros", "...");
        entregar("newsletter@qualquer.com", null, "Novidades da semana", "...");
        entregar("spam@outro.com", null, "Ganhou um prémio", "...");

        JsonNode so = send(get("/api/v1/mailbox/messages?knownOnly=true"), null, 200);
        assertThat(so.get("total").asInt()).as("só o que vem de quem está registado").isEqualTo(1);
        assertThat(so.get("mensagens").get(0).get("assunto").asText()).isEqualTo("Proposta de filtros");

        // E dá para apertar a um fornecedor só.
        JsonNode deUm = send(
                get("/api/v1/mailbox/messages?entity=vendas@autopecas.co.ao"), null, 200);
        assertThat(deUm.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void oDirectorioListaFornecedoresMotoristasEEquipa() throws Exception {
        String id = fornecedor("Casa do Camião", "geral@casadocamiao.ao");
        send(post("/api/v1/suppliers/" + id + "/contacts"), Map.of(
                "name", "Mário Vendas", "role", "SALES",
                "email", "mario@casadocamiao.ao", "primaryContact", true), 201);

        JsonNode directorio = send(get("/api/v1/mailbox/directory"), null, 200);
        assertThat(directorio.toString()).contains("Casa do Camião").contains("Mário Vendas");
        // A equipa também, porque o correio interno existe.
        assertThat(directorio.toString()).contains("correio@teste.ao");
    }

    // ==== Ler =============================================================

    @Test
    void abrirUmaMensagemMostraOCorpoEMarcaComoLida() throws Exception {
        entregar("vendas@autopecas.co.ao", "Comercial", "Prazo de entrega",
                "O filtro chega na quinta-feira.");

        JsonNode pagina = send(get("/api/v1/mailbox/messages"), null, 200);
        JsonNode linha = pagina.get("mensagens").get(0);
        assertThat(linha.get("lida").asBoolean()).isFalse();
        long uid = linha.get("uid").asLong();

        JsonNode aberta = send(get("/api/v1/mailbox/messages/" + uid + "?folder=INBOX"), null, 200);
        assertThat(aberta.get("texto").asText()).contains("quinta-feira");

        // Depois de aberta, fica lida — e a lista mostra-o.
        JsonNode depois = send(get("/api/v1/mailbox/messages"), null, 200);
        assertThat(depois.get("mensagens").get(0).get("lida").asBoolean()).isTrue();

        // E o filtro de não lidas deixa de a mostrar.
        assertThat(send(get("/api/v1/mailbox/messages?unread=true"), null, 200)
                .get("total").asInt()).isZero();
    }

    @Test
    void osAnexosListamSeEDescarregamSe() throws Exception {
        byte[] pdf = "%PDF-1.4 factura de teste".getBytes(StandardCharsets.UTF_8);
        entregarComAnexo("vendas@autopecas.co.ao", "Factura 2026/118", "factura-118.pdf", pdf);

        JsonNode pagina = send(get("/api/v1/mailbox/messages?withAttachments=true"), null, 200);
        assertThat(pagina.get("total").asInt()).isEqualTo(1);
        JsonNode linha = pagina.get("mensagens").get(0);
        assertThat(linha.get("temAnexos").asBoolean()).isTrue();
        long uid = linha.get("uid").asLong();

        JsonNode aberta = send(get("/api/v1/mailbox/messages/" + uid), null, 200);
        assertThat(aberta.get("anexos")).hasSize(1);
        JsonNode anexo = aberta.get("anexos").get(0);
        assertThat(anexo.get("nome").asText()).isEqualTo("factura-118.pdf");
        assertThat(anexo.get("previsualizavel").asBoolean())
                .as("um PDF mostra-se no ecrã em vez de obrigar a descarregar").isTrue();

        MvcResult r = mvc.perform(get("/api/v1/mailbox/messages/" + uid
                        + "/attachments/" + anexo.get("id").asText())
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(pdf);
    }

    @Test
    void asPastasVemComOsNomesQueAsPessoasUsam() throws Exception {
        JsonNode pastas = send(get("/api/v1/mailbox/folders"), null, 200);
        assertThat(pastas.toString()).contains("Entrada");
        boolean temEntrada = false;
        for (JsonNode p : pastas) {
            if ("INBOX".equals(p.get("papel").asText())) {
                temEntrada = true;
            }
        }
        assertThat(temEntrada).isTrue();
    }

    // ==== Enviar ===========================================================

    @Test
    void enviarComAnexoChegaAoServidorEFicaNosEnviados() throws Exception {
        MockMultipartFile ficheiro = new MockMultipartFile(
                "anexos", "ordem-de-compra.pdf", "application/pdf",
                "%PDF-1.4 ordem".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/mailbox/send")
                        .file(ficheiro)
                        .file(new MockMultipartFile("para", "", "text/plain",
                                "vendas@autopecas.co.ao".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("assunto", "", "text/plain",
                                "Ordem de compra OC-2026-0001".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("corpo", "", "text/plain",
                                "<p>Segue a nossa encomenda.</p>".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        assertThat(servidor.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] recebidas = servidor.getReceivedMessages();
        assertThat(recebidas).isNotEmpty();
        assertThat(recebidas[0].getSubject()).isEqualTo("Ordem de compra OC-2026-0001");
        assertThat(recebidas[0].getContent()).isInstanceOf(MimeMultipart.class);
    }

    // ==== Do correio para a cotação =========================================

    @Test
    void aPropostaQueChegouPorEmailVaiDirectaParaOPedido() throws Exception {
        // O caso real: pediu-se preço, ele respondeu com um PDF em anexo, e os
        // preços copiavam-se à mão enquanto o PDF ficava na caixa de entrada.
        String fornecedorId = fornecedor("Auto Peças de Luanda", "vendas@autopecas.co.ao");
        String peca = send(post("/api/v1/parts"), Map.of(
                "name", "Filtro de ar", "category", "FILTER", "unit", "un"), 201)
                .get("id").asText();

        Map<String, Object> linha = new HashMap<>();
        linha.put("partId", peca);
        linha.put("description", "Filtro de ar");
        linha.put("quantity", 10);
        String rfqId = send(post("/api/v1/rfqs"), Map.of(
                "title", "Filtros para os camiões",
                "supplierIds", java.util.List.of(fornecedorId),
                "lines", java.util.List.of(linha)), 201).get("id").asText();

        // O correio já sabe que este endereço é deste fornecedor; a partir daí,
        // os pedidos em aberto dele são uma pergunta directa.
        JsonNode abertos = send(get("/api/v1/rfqs/open-for/" + fornecedorId), null, 200);
        assertThat(abertos).hasSize(1);
        assertThat(abertos.get(0).get("id").asText()).isEqualTo(rfqId);

        // A proposta chega por email, em anexo.
        byte[] proposta = "%PDF-1.4 proposta 8500".getBytes(StandardCharsets.UTF_8);
        entregarComAnexo("vendas@autopecas.co.ao", "Proposta de filtros",
                "proposta-2026-118.pdf", proposta);

        JsonNode pagina = send(get("/api/v1/mailbox/messages?withAttachments=true"), null, 200);
        long uid = pagina.get("mensagens").get(0).get("uid").asLong();
        JsonNode mensagem = send(get("/api/v1/mailbox/messages/" + uid), null, 200);
        String anexoId = mensagem.get("anexos").get(0).get("id").asText();

        // E vai directa para o pedido, sem passar pelo computador de ninguém.
        JsonNode pedido = send(post("/api/v1/rfqs/" + rfqId + "/suppliers/" + fornecedorId
                + "/proposal/from-mail?folder=INBOX&uid=" + uid
                + "&attachmentId=" + anexoId), null, 200);

        JsonNode daEmpresa = pedido.get("propostas").get(0);
        assertThat(daEmpresa.get("fileName").asText()).isEqualTo("proposta-2026-118.pdf");
        assertThat(daEmpresa.get("fileId").asText()).isNotBlank();
    }

    @Test
    void aRespostaGuardaDeQueMensagemVeio() throws Exception {
        String fornecedorId = fornecedor("Casa do Camião", "geral@casadocamiao.ao");
        Map<String, Object> linha = new HashMap<>();
        linha.put("description", "Pneu 11R22.5");
        linha.put("quantity", 4);
        JsonNode rfq = send(post("/api/v1/rfqs"), Map.of(
                "title", "Pneus",
                "supplierIds", java.util.List.of(fornecedorId),
                "lines", java.util.List.of(linha)), 201);
        String rfqId = rfq.get("id").asText();
        String lineId = rfq.get("lines").get(0).get("id").asText();

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("leadTimeDays", 7);
        resposta.put("sourceFolder", "INBOX");
        resposta.put("sourceUid", 4242);
        resposta.put("precos", java.util.List.of(Map.of("lineId", lineId, "unitPrice", 180000)));
        JsonNode depois = send(post("/api/v1/rfqs/" + rfqId + "/suppliers/" + fornecedorId
                + "/response"), resposta, 200);

        JsonNode proposta = depois.get("propostas").get(0);
        // Voltar ao original é o que permite ler o que ele escreveu à volta do
        // número — «este preço é para pagamento a pronto» muda tudo.
        assertThat(proposta.get("sourceUid").asLong()).isEqualTo(4242);
        assertThat(proposta.get("sourceFolder").asText()).isEqualTo("INBOX");
    }

    // ==== Quem pode ler ====================================================

    @Test
    void umMotoristaNaoLeOCorreioDaEmpresa() throws Exception {
        String driverId = send(post("/api/v1/drivers"),
                Map.of("name", "Jorge Correio", "phone", "+244 923 444 555",
                        "licenseNumber", "AO-COR-1"), 201).get("id").asText();
        JsonNode cred = send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "identifier", cred.get("loginId").asText(),
                                "password", cred.get("password").asText()))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(login.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        mvc.perform(get("/api/v1/mailbox/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
