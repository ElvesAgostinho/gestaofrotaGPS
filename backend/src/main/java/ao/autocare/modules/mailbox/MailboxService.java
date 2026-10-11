package ao.autocare.modules.mailbox;

import ao.autocare.common.ApiException;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.integration.IntegrationService;
import ao.autocare.modules.mailbox.EntityDirectory.Entidade;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Anexo;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Envio;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Estado;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Mensagem;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Pagina;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Pasta;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Pessoa;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Resumo;
import ao.autocare.security.SecretBox;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A caixa de correio da empresa, dentro do sistema.
 *
 * <p>Enviar já se fazia. Faltava <b>receber</b> — e é o que transforma isto no
 * sítio onde o trabalho acontece em vez de um sítio por onde o trabalho passa.
 * A resposta do fornecedor à ordem de compra, a factura em anexo, a
 * confirmação do prazo: tudo isso vive hoje numa caixa de correio que ninguém
 * liga ao equipamento nem à encomenda.
 *
 * <p>É uma caixa <b>da empresa</b> e não de cada pessoa: há um endereço
 * (geral@…), e quem tiver a permissão vê o mesmo correio. É deliberado — numa
 * empresa de frota é assim que funciona, e a alternativa seria pedir a cada
 * utilizador as credenciais da sua caixa, que é coisa que ninguém dá.
 *
 * <p>Sobre o desempenho: cada pedido abre e fecha a sua ligação IMAP. Guardar
 * ligações abertas seria mais rápido e muito mais frágil — uma ligação IMAP
 * morre sozinha ao fim de uns minutos, e o estado partilhado entre pedidos é a
 * origem da maior parte dos defeitos difíceis. A lista trabalha sobre as
 * {@value #JANELA} mensagens mais recentes da pasta, que é a janela que alguém
 * usa de facto; procurar mais fundo é trabalho do servidor de email.
 */
@Service
public class MailboxService {

    /** Quantas mensagens recentes entram na janela de trabalho. */
    public static final int JANELA = 500;

    /** Tamanho máximo de um anexo a enviar. */
    private static final long MAX_ANEXO = 15L * 1024 * 1024;

    private final IntegrationService integrations;
    private final EntityDirectory directory;
    private final SecretBox cofre;
    private final AuditService audit;

    public MailboxService(
            IntegrationService integrations,
            EntityDirectory directory,
            SecretBox cofre,
            AuditService audit) {
        this.integrations = integrations;
        this.directory = directory;
        this.cofre = cofre;
        this.audit = audit;
    }

    // ==== Estado ============================================================

    @Transactional(readOnly = true)
    public Estado estado(String orgId) {
        IntegrationSettings s = integrations.forOrganization(orgId);
        boolean podeEnviar = s.hasSmtp();
        if (!s.hasImap()) {
            return new Estado(false, podeEnviar, s.getSmtpFrom(), null,
                    podeEnviar
                            ? "O envio está configurado mas a receção não. Indique o servidor "
                                    + "IMAP em Configurações → Credenciais para ler o correio aqui."
                            : "Configure o servidor de email em Configurações → Credenciais.");
        }
        return new Estado(true, podeEnviar, s.getSmtpFrom(),
                s.getImapHost() + ":" + porta(s), null);
    }

    // ==== Pastas ============================================================

    public List<Pasta> pastas(String orgId) {
        return comLigacao(orgId, store -> {
            List<Pasta> pastas = new ArrayList<>();
            for (Folder f : store.getDefaultFolder().list("*")) {
                if ((f.getType() & Folder.HOLDS_MESSAGES) == 0) {
                    continue;
                }
                try {
                    pastas.add(new Pasta(nomeLegivel(f.getName()), f.getFullName(),
                            papel(f.getFullName()), f.getMessageCount(), f.getUnreadMessageCount()));
                } catch (Exception e) {
                    // Uma pasta que não abre não deve esconder as outras.
                    pastas.add(new Pasta(nomeLegivel(f.getName()), f.getFullName(),
                            papel(f.getFullName()), 0, 0));
                }
            }
            pastas.sort(Comparator.comparingInt(p -> ordemDoPapel(p.papel())));
            return pastas;
        });
    }

    // ==== Lista =============================================================

    /**
     * Uma página de mensagens, já com a identidade de quem escreveu.
     *
     * @param entidade quando preenchido, só o correio de/para esse endereço
     * @param apenasEntidades só correio de quem está registado no sistema
     * @param procura texto no assunto ou no remetente
     */
    public Pagina listar(
            String orgId, String pasta, int pagina, int tamanho,
            boolean apenasPorLer, boolean apenasComAnexos, boolean apenasEntidades,
            String entidade, String procura) {

        Map<String, Entidade> indice = directory.indice(orgId);
        String alvo = EntityDirectory.normalizar(entidade);
        String texto = procura == null || procura.isBlank()
                ? null : procura.trim().toLowerCase(Locale.ROOT);

        return comLigacao(orgId, store -> {
            Folder f = abrir(store, pasta, Folder.READ_ONLY);
            try {
                int total = f.getMessageCount();
                if (total == 0) {
                    return new Pagina(f.getFullName(), pagina, tamanho, 0, List.of());
                }
                int inicio = Math.max(1, total - JANELA + 1);
                Message[] janela = f.getMessages(inicio, total);

                // Uma ida ao servidor para tudo o que a lista precisa. Sem isto,
                // o JavaMail vai buscar cada cabeçalho de cada vez e cinquenta
                // linhas tornam-se cinquenta viagens.
                FetchProfile perfil = new FetchProfile();
                perfil.add(FetchProfile.Item.ENVELOPE);
                perfil.add(FetchProfile.Item.FLAGS);
                perfil.add(FetchProfile.Item.CONTENT_INFO);
                perfil.add(jakarta.mail.UIDFolder.FetchProfileItem.UID);
                f.fetch(janela, perfil);

                List<Resumo> todos = new ArrayList<>();
                jakarta.mail.UIDFolder uids = (jakarta.mail.UIDFolder) f;
                for (int i = janela.length - 1; i >= 0; i--) {
                    Message m = janela[i];
                    Resumo r = resumir(m, uids.getUID(m), indice);
                    if (r == null || !passa(r, apenasPorLer, apenasComAnexos, apenasEntidades,
                            alvo, texto)) {
                        continue;
                    }
                    todos.add(r);
                }

                int desde = Math.max(0, pagina) * tamanho;
                List<Resumo> daPagina = desde >= todos.size()
                        ? List.of()
                        : todos.subList(desde, Math.min(desde + tamanho, todos.size()));
                return new Pagina(f.getFullName(), pagina, tamanho, todos.size(),
                        List.copyOf(daPagina));
            } finally {
                fechar(f);
            }
        });
    }

    private static boolean passa(
            Resumo r, boolean porLer, boolean comAnexos, boolean soEntidades,
            String alvo, String texto) {
        if (porLer && r.lida()) {
            return false;
        }
        if (comAnexos && !r.temAnexos()) {
            return false;
        }
        boolean deEntidade = r.de() != null && r.de().entidade() != null;
        boolean paraEntidade = r.para().stream().anyMatch(p -> p.entidade() != null);
        if (soEntidades && !deEntidade && !paraEntidade) {
            return false;
        }
        if (alvo != null) {
            boolean bate = (r.de() != null && alvo.equals(r.de().email()))
                    || r.para().stream().anyMatch(p -> alvo.equals(p.email()));
            if (!bate) {
                return false;
            }
        }
        if (texto != null) {
            String onde = (r.assunto() == null ? "" : r.assunto()) + " "
                    + (r.de() == null ? "" : r.de().nome() + " " + r.de().email());
            if (!onde.toLowerCase(Locale.ROOT).contains(texto)) {
                return false;
            }
        }
        return true;
    }

    // ==== Uma mensagem ======================================================

    public Mensagem abrirMensagem(String orgId, String pasta, long uid, boolean marcarLida) {
        Map<String, Entidade> indice = directory.indice(orgId);
        return comLigacao(orgId, store -> {
            Folder f = abrir(store, pasta, marcarLida ? Folder.READ_WRITE : Folder.READ_ONLY);
            try {
                Message m = porUid(f, uid);
                Corpo corpo = new Corpo();
                List<Anexo> anexos = new ArrayList<>();
                percorrer(m, corpo, anexos);
                if (marcarLida && !m.isSet(Flags.Flag.SEEN)) {
                    m.setFlag(Flags.Flag.SEEN, true);
                }
                return new Mensagem(
                        uid, f.getFullName(), m.getSubject(),
                        primeira(m.getFrom(), indice),
                        pessoas(m.getRecipients(Message.RecipientType.TO), indice),
                        pessoas(m.getRecipients(Message.RecipientType.CC), indice),
                        quando(m),
                        true,
                        corpo.html == null ? null : limpar(corpo.html),
                        corpo.texto,
                        anexos);
            } finally {
                fechar(f);
            }
        });
    }

    /** O conteúdo de um anexo, para descarregar ou pré-visualizar. */
    public record Ficheiro(String nome, String tipo, byte[] bytes) {}

    public Ficheiro anexo(String orgId, String pasta, long uid, String anexoId) {
        return comLigacao(orgId, store -> {
            Folder f = abrir(store, pasta, Folder.READ_ONLY);
            try {
                Message m = porUid(f, uid);
                Corpo corpo = new Corpo();
                List<Anexo> anexos = new ArrayList<>();
                List<Part> partes = new ArrayList<>();
                percorrer(m, corpo, anexos, partes);
                for (int i = 0; i < anexos.size(); i++) {
                    if (anexos.get(i).id().equals(anexoId)) {
                        Part p = partes.get(i);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        p.getInputStream().transferTo(out);
                        return new Ficheiro(anexos.get(i).nome(), anexos.get(i).tipo(),
                                out.toByteArray());
                    }
                }
                throw ApiException.notFound("Anexo não encontrado.");
            } finally {
                fechar(f);
            }
        });
    }

    public void marcar(String orgId, String pasta, long uid, boolean lida) {
        comLigacao(orgId, store -> {
            Folder f = abrir(store, pasta, Folder.READ_WRITE);
            try {
                porUid(f, uid).setFlag(Flags.Flag.SEEN, lida);
                return null;
            } finally {
                fechar(f);
            }
        });
    }

    /**
     * Apagar é mover para o lixo.
     *
     * <p>Marcar como apagada e expurgar faria desaparecer para sempre com um
     * clique. Numa caixa partilhada por várias pessoas, isso é inaceitável: o
     * que um apaga por engano tem de poder ser recuperado por outro.
     */
    public void apagar(String orgId, String pasta, long uid) {
        comLigacao(orgId, store -> {
            Folder origem = abrir(store, pasta, Folder.READ_WRITE);
            try {
                Message m = porUid(origem, uid);
                Folder lixo = pastaComPapel(store, Pasta.LIXO);
                if (lixo != null && !lixo.getFullName().equals(origem.getFullName())) {
                    if (!lixo.exists()) {
                        lixo.create(Folder.HOLDS_MESSAGES);
                    }
                    origem.copyMessages(new Message[] {m}, lixo);
                }
                m.setFlag(Flags.Flag.DELETED, true);
                origem.expunge();
                return null;
            } finally {
                fechar(origem);
            }
        });
    }

    // ==== Enviar ============================================================

    /**
     * Envia e guarda nos enviados.
     *
     * <p>A cópia em «Enviados» não é um extra: sem ela, quem abrir a caixa pelo
     * telemóvel não vê o que foi enviado daqui, e a conversa parte-se ao meio.
     */
    @Transactional
    public void enviar(String orgId, String userId, Envio pedido, List<MultipartFile> anexos) {
        IntegrationSettings s = integrations.forOrganization(orgId);
        if (!s.hasSmtp()) {
            throw ApiException.conflict(
                    "Não há servidor de envio configurado. Configure-o em Configurações.");
        }
        long total = anexos == null ? 0 : anexos.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_ANEXO) {
            throw ApiException.badRequest(
                    "Os anexos somam mais de 15 MB. A maior parte dos servidores recusa — "
                            + "envie um link em vez do ficheiro.");
        }

        try {
            JavaMailSenderImpl remetente = integrations.build(s);
            MimeMessage msg = remetente.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(s.getSmtpFrom(),
                    s.getSmtpFromName() != null ? s.getSmtpFromName() : "IMBONDEIRO OS");
            h.setTo(enderecos(pedido.para()));
            if (preenchido(pedido.cc())) {
                h.setCc(enderecos(pedido.cc()));
            }
            if (preenchido(pedido.bcc())) {
                h.setBcc(enderecos(pedido.bcc()));
            }
            h.setSubject(pedido.assunto());
            h.setText(pedido.corpo() == null ? "" : pedido.corpo(), true);
            if (anexos != null) {
                for (MultipartFile ficheiro : anexos) {
                    if (ficheiro == null || ficheiro.isEmpty()) {
                        continue;
                    }
                    h.addAttachment(nomeSeguro(ficheiro.getOriginalFilename()),
                            new org.springframework.core.io.ByteArrayResource(ficheiro.getBytes()),
                            ficheiro.getContentType() != null
                                    ? ficheiro.getContentType() : "application/octet-stream");
                }
            }
            remetente.send(msg);

            audit.record(orgId, userId, "mailbox.send", "Organization", orgId,
                    pedido.assunto() + " → " + pedido.para());
            guardarNosEnviados(orgId, msg);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível enviar: " + e.getMessage());
        }
    }

    /** A cópia em «Enviados». Falhar aqui não desfaz o envio — já saiu. */
    private void guardarNosEnviados(String orgId, MimeMessage msg) {
        IntegrationSettings s = integrations.forOrganization(orgId);
        if (!s.hasImap()) {
            return;
        }
        try {
            comLigacao(orgId, store -> {
                Folder enviados = pastaComPapel(store, Pasta.ENVIADOS);
                if (enviados == null) {
                    return null;
                }
                if (!enviados.exists()) {
                    enviados.create(Folder.HOLDS_MESSAGES);
                }
                enviados.open(Folder.READ_WRITE);
                try {
                    msg.setFlag(Flags.Flag.SEEN, true);
                    enviados.appendMessages(new Message[] {msg});
                } finally {
                    fechar(enviados);
                }
                return null;
            });
        } catch (RuntimeException e) {
            // A mensagem seguiu; não ter ficado a cópia é um incómodo, não um erro.
            audit.record(orgId, null, "mailbox.sent_copy_failed", "Organization", orgId,
                    e.getMessage());
        }
    }

    // ==== Ligação ===========================================================

    private interface ComStore<T> {
        T executar(Store store) throws Exception;
    }

    private <T> T comLigacao(String orgId, ComStore<T> trabalho) {
        IntegrationSettings s = integrations.forOrganization(orgId);
        if (!s.hasImap()) {
            throw ApiException.conflict(
                    "A receção de email não está configurada. Indique o servidor IMAP em "
                            + "Configurações → Credenciais.");
        }
        String utilizador = s.mailboxUser();
        String senha = cofre.decrypt(s.mailboxPasswordEnc());
        if (utilizador == null || senha == null) {
            throw ApiException.conflict(
                    "Faltam as credenciais da caixa de correio, ou já não é possível lê-las. "
                            + "Volte a guardá-las em Configurações → Credenciais.");
        }

        Properties p = new Properties();
        String seguranca = s.getImapSecurity() != null ? s.getImapSecurity() : "SSL";
        boolean ssl = "SSL".equals(seguranca);
        String protocolo = ssl ? "imaps" : "imap";
        p.put("mail.store.protocol", protocolo);
        p.put("mail." + protocolo + ".connectiontimeout", "15000");
        p.put("mail." + protocolo + ".timeout", "20000");
        p.put("mail." + protocolo + ".writetimeout", "20000");
        if ("STARTTLS".equals(seguranca)) {
            p.put("mail.imap.starttls.enable", "true");
            p.put("mail.imap.starttls.required", "true");
        }
        // Os servidores de email partilhados anunciam mecanismos que não
        // cumprem; pedir PLAIN primeiro é o que os clientes normais fazem.
        p.put("mail." + protocolo + ".auth.mechanisms", "PLAIN LOGIN");

        Session sessao = Session.getInstance(p);
        try (Store store = sessao.getStore(protocolo)) {
            store.connect(s.getImapHost(), porta(s), utilizador, senha);
            return trabalho.executar(store);
        } catch (ApiException e) {
            throw e;
        } catch (jakarta.mail.AuthenticationFailedException e) {
            throw ApiException.conflict(
                    "O servidor de receção recusou as credenciais da caixa de correio. "
                            + "Confirme-as em Configurações → Credenciais.");
        } catch (Exception e) {
            throw ApiException.badRequest(
                    "Não foi possível falar com o servidor de email: " + e.getMessage());
        }
    }

    private static int porta(IntegrationSettings s) {
        if (s.getImapPort() != null) {
            return s.getImapPort();
        }
        return "SSL".equals(s.getImapSecurity() != null ? s.getImapSecurity() : "SSL") ? 993 : 143;
    }

    private static Folder abrir(Store store, String caminho, int modo) throws Exception {
        String nome = caminho == null || caminho.isBlank() ? "INBOX" : caminho;
        Folder f = store.getFolder(nome);
        if (!f.exists()) {
            throw ApiException.notFound("A pasta «" + nome + "» não existe nesta caixa.");
        }
        f.open(modo);
        return f;
    }

    private static Message porUid(Folder f, long uid) throws Exception {
        Message m = ((jakarta.mail.UIDFolder) f).getMessageByUID(uid);
        if (m == null) {
            throw ApiException.notFound("A mensagem já não está nesta pasta.");
        }
        return m;
    }

    private static void fechar(Folder f) {
        try {
            if (f != null && f.isOpen()) {
                f.close(false);
            }
        } catch (Exception ignorado) {
            // fechar uma pasta que já caiu não é problema de ninguém
        }
    }

    private static Folder pastaComPapel(Store store, String papel) throws Exception {
        for (Folder f : store.getDefaultFolder().list("*")) {
            if (papel.equals(papel(f.getFullName()))) {
                return f;
            }
        }
        return null;
    }

    // ==== Leitura de mensagens =============================================

    private static final class Corpo {
        private String html;
        private String texto;
    }

    private static void percorrer(Part parte, Corpo corpo, List<Anexo> anexos) throws Exception {
        percorrer(parte, corpo, anexos, new ArrayList<>());
    }

    /**
     * Separa o corpo dos anexos numa mensagem MIME.
     *
     * <p>Uma mensagem pode ser texto simples, texto e HTML em alternativa, ou
     * uma árvore com várias camadas — reencaminhamentos trazem mensagens
     * inteiras dentro de mensagens. Percorre-se tudo: o primeiro HTML ganha, o
     * texto fica como alternativa, e o que tiver nome de ficheiro é anexo.
     */
    private static void percorrer(
            Part parte, Corpo corpo, List<Anexo> anexos, List<Part> partes) throws Exception {
        if (parte.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) parte.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                percorrer(mp.getBodyPart(i), corpo, anexos, partes);
            }
            return;
        }

        String nome = nomeDoAnexo(parte);
        boolean anexo = nome != null
                || Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition());

        if (!anexo && parte.isMimeType("text/html") && corpo.html == null) {
            corpo.html = String.valueOf(parte.getContent());
            return;
        }
        if (!anexo && parte.isMimeType("text/plain") && corpo.texto == null) {
            corpo.texto = String.valueOf(parte.getContent());
            return;
        }
        if (anexo) {
            String tipo = parte.getContentType() == null
                    ? "application/octet-stream"
                    : parte.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
            anexos.add(new Anexo(
                    String.valueOf(anexos.size()),
                    nome != null ? nome : "anexo-" + (anexos.size() + 1),
                    tipo,
                    Math.max(parte.getSize(), 0),
                    tipo.startsWith("image/") || tipo.equals("application/pdf")));
            partes.add(parte);
        }
    }

    private static String nomeDoAnexo(Part parte) {
        try {
            String nome = parte.getFileName();
            return nome == null ? null : MimeUtility.decodeText(nome);
        } catch (Exception e) {
            return null;
        }
    }

    private Resumo resumir(Message m, long uid, Map<String, Entidade> indice) {
        try {
            boolean temAnexos = false;
            int quantos = 0;
            if (m.isMimeType("multipart/mixed")) {
                Object conteudo = null;
                try {
                    conteudo = m.getContent();
                } catch (Exception ignorado) {
                    // sem o corpo não se contam anexos; o cabeçalho basta
                }
                if (conteudo instanceof Multipart mp) {
                    for (int i = 0; i < mp.getCount(); i++) {
                        BodyPart bp = mp.getBodyPart(i);
                        if (nomeDoAnexo(bp) != null
                                || Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                            quantos++;
                        }
                    }
                }
                temAnexos = quantos > 0;
            }
            return new Resumo(
                    uid, m.getSubject(),
                    primeira(m.getFrom(), indice),
                    pessoas(m.getRecipients(Message.RecipientType.TO), indice),
                    quando(m),
                    m.isSet(Flags.Flag.SEEN),
                    m.isSet(Flags.Flag.ANSWERED),
                    temAnexos, quantos,
                    Math.max(m.getSize(), 0));
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant quando(Message m) throws Exception {
        java.util.Date d = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        return d == null ? null : d.toInstant();
    }

    private static Pessoa primeira(Address[] enderecos, Map<String, Entidade> indice) {
        List<Pessoa> todas = pessoas(enderecos, indice);
        return todas.isEmpty() ? null : todas.get(0);
    }

    private static List<Pessoa> pessoas(Address[] enderecos, Map<String, Entidade> indice) {
        if (enderecos == null) {
            return List.of();
        }
        List<Pessoa> lista = new ArrayList<>();
        for (Address a : enderecos) {
            String email;
            String nome;
            if (a instanceof InternetAddress ia) {
                email = EntityDirectory.normalizar(ia.getAddress());
                nome = ia.getPersonal() != null && !ia.getPersonal().isBlank()
                        ? ia.getPersonal() : ia.getAddress();
            } else {
                email = EntityDirectory.normalizar(a.toString());
                nome = a.toString();
            }
            Entidade entidade = email == null ? null : indice.get(email);
            // Quem está no sistema mostra-se pelo nome que lá tem, e não pelo
            // que escreveu no cabeçalho do email.
            lista.add(new Pessoa(entidade != null ? entidade.nome() : nome, email, entidade));
        }
        return lista;
    }

    // ==== Segurança do HTML =================================================

    /**
     * Tira do HTML o que pode correr.
     *
     * <p>Isto é a primeira de duas defesas; a segunda é o ecrã, que mostra a
     * mensagem dentro de uma moldura isolada e sem permissões. Uma mensagem de
     * email é conteúdo de um estranho: tratá-la como se fosse nossa é a forma
     * clássica de perder uma sessão.
     */
    static String limpar(String html) {
        if (html == null) {
            return null;
        }
        String limpo = html;
        limpo = limpo.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        limpo = limpo.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        limpo = limpo.replaceAll("(?is)<iframe[^>]*>.*?</iframe>", "");
        limpo = limpo.replaceAll("(?is)<object[^>]*>.*?</object>", "");
        limpo = limpo.replaceAll("(?is)<embed[^>]*>", "");
        limpo = limpo.replaceAll("(?is)<link[^>]*>", "");
        limpo = limpo.replaceAll("(?is)<meta[^>]*>", "");
        // Atributos de evento: onclick, onerror, onload…
        limpo = limpo.replaceAll("(?is)\\son[a-z]+\\s*=\\s*\"[^\"]*\"", "");
        limpo = limpo.replaceAll("(?is)\\son[a-z]+\\s*=\\s*'[^']*'", "");
        limpo = limpo.replaceAll("(?is)\\son[a-z]+\\s*=\\s*[^\\s>]+", "");
        limpo = limpo.replaceAll("(?i)javascript:", "");
        return limpo;
    }

    // ==== Utilitários =======================================================

    private static String[] enderecos(String lista) {
        return java.util.Arrays.stream(lista.split("[,;]"))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toArray(String[]::new);
    }

    private static boolean preenchido(String v) {
        return v != null && !v.isBlank();
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "anexo";
        }
        return nome.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** O papel de uma pasta, pelo nome — os servidores não concordam entre si. */
    static String papel(String caminho) {
        String n = caminho == null ? "" : caminho.toLowerCase(Locale.ROOT);
        int ponto = n.lastIndexOf('.');
        String folha = ponto >= 0 ? n.substring(ponto + 1) : n;
        if (folha.equals("inbox") || n.equals("inbox")) {
            return Pasta.ENTRADA;
        }
        if (folha.contains("sent") || folha.contains("enviad")) {
            return Pasta.ENVIADOS;
        }
        if (folha.contains("draft") || folha.contains("rascunho")) {
            return Pasta.RASCUNHOS;
        }
        if (folha.contains("trash") || folha.contains("lixo") || folha.contains("deleted")
                || folha.contains("eliminad")) {
            return Pasta.LIXO;
        }
        if (folha.contains("junk") || folha.contains("spam")) {
            return Pasta.SPAM;
        }
        return "OTHER";
    }

    private static int ordemDoPapel(String papel) {
        return switch (papel) {
            case Pasta.ENTRADA -> 0;
            case Pasta.ENVIADOS -> 1;
            case Pasta.RASCUNHOS -> 2;
            case Pasta.SPAM -> 4;
            case Pasta.LIXO -> 5;
            default -> 3;
        };
    }

    private static String nomeLegivel(String nome) {
        return switch (papel(nome)) {
            case Pasta.ENTRADA -> "Entrada";
            case Pasta.ENVIADOS -> "Enviados";
            case Pasta.RASCUNHOS -> "Rascunhos";
            case Pasta.LIXO -> "Lixo";
            case Pasta.SPAM -> "Spam";
            default -> nome;
        };
    }
}
