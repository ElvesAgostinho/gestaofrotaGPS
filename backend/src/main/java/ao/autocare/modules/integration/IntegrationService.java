package ao.autocare.modules.integration;

import ao.autocare.common.ApiException;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.domain.Organization;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.IntegrationSettingsRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.security.SecretBox;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credenciais de Traccar e de email, por empresa.
 *
 * <p>Os testes de ligação são ligações a sério ao servidor indicado. Um botão
 * de «testar» que devolvesse sempre verde seria pior do que não existir: daria
 * ao cliente a certeza de que está configurado, e o problema só aparecia no dia
 * em que fosse preciso bloquear uma viatura.
 */
@Service
public class IntegrationService {

    private final ao.autocare.modules.telemetry.TraccarPositions traccarPositions;

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(12);

    /** O que se mostra no lugar de um segredo guardado. */
    public static final String MASCARA = "••••••••";

    private final IntegrationSettingsRepository repo;
    private final OrganizationRepository organizations;
    private final SecretBox cofre;
    private final ao.autocare.modules.fleet.RoutingEngine routingEngine;
    private final AuditService audit;

    public IntegrationService(
            IntegrationSettingsRepository repo,
            OrganizationRepository organizations,
            SecretBox cofre,
            ao.autocare.modules.fleet.RoutingEngine routingEngine,
            AuditService audit,
            @org.springframework.context.annotation.Lazy ao.autocare.modules.telemetry.TraccarPositions traccarPositions) {
        this.traccarPositions = traccarPositions;
        this.repo = repo;
        this.organizations = organizations;
        this.cofre = cofre;
        this.routingEngine = routingEngine;
        this.audit = audit;
    }

    @Transactional
    public IntegrationSettings forOrganization(String orgId) {
        return repo.findByOrganizationId(orgId).orElseGet(() -> {
            Organization o = organizations.findById(orgId)
                    .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
            IntegrationSettings s = new IntegrationSettings();
            s.setOrganization(o);
            return repo.save(s);
        });
    }

    // ==== Traccar ==========================================================

    @Transactional
    public IntegrationSettings saveTraccar(
            String orgId, String userId, IntegrationDtos.TraccarRequest req) {

        IntegrationSettings s = forOrganization(orgId);
        s.setTraccarUrl(normalizarUrl(req.url()));
        s.setTraccarUser(vazioParaNulo(req.user()));

        // A máscara significa «não mexi neste campo». Sem isto, gravar o
        // formulário sem reescrever a palavra-passe apagava-a.
        if (req.password() != null && !MASCARA.equals(req.password())) {
            s.setTraccarPasswordEnc(cofre.encrypt(vazioParaNulo(req.password())));
        }
        if (req.token() != null && !MASCARA.equals(req.token())) {
            s.setTraccarTokenEnc(cofre.encrypt(vazioParaNulo(req.token())));
        }

        if (s.hasTraccar() && s.getTraccarUser() == null && s.getTraccarTokenEnc() == null) {
            throw ApiException.badRequest(
                    "Indique um utilizador com palavra-passe, ou um token de acesso.");
        }

        // A configuração mudou: o que foi verificado antes já não vale.
        s.setTraccarOk(null);
        s.setTraccarCheckedAt(null);
        s.setTraccarLastError(null);

        audit.record(orgId, userId, "integration.traccar", "Organization", orgId,
                s.hasTraccar() ? "Traccar apontado a " + s.getTraccarUrl()
                        : "Traccar desconfigurado");
        return s;
    }

    /**
     * Liga-se mesmo ao Traccar e pergunta quem somos.
     *
     * <p>{@code /api/devices} é o ponto que valida credenciais sem alterar nada
     * no servidor do cliente. Não {@code /api/session}: num Traccar 6.6 real,
     * com token, esse responde 404 — e o teste dava falso negativo a uma
     * ligação que funcionava. Descoberto na primeira instalação a sério.
     */
    @Transactional
    public IntegrationDtos.TestResult testTraccar(String orgId, String userId) {
        IntegrationSettings s = forOrganization(orgId);
        if (!s.hasTraccar()) {
            throw ApiException.badRequest("Defina primeiro o endereço do servidor Traccar.");
        }

        String erro = null;
        boolean ok = false;
        String detalhe = null;

        try {
            HttpRequest.Builder pedido = HttpRequest.newBuilder()
                    .uri(URI.create(s.getTraccarUrl() + "/api/devices"))
                    .timeout(TEMPO_LIMITE)
                    .header("Accept", "application/json")
                    .GET();

            String token = cofre.decrypt(s.getTraccarTokenEnc());
            String senha = cofre.decrypt(s.getTraccarPasswordEnc());
            if (token != null) {
                pedido.header("Authorization", "Bearer " + token);
            } else if (s.getTraccarUser() != null && senha != null) {
                String par = s.getTraccarUser() + ":" + senha;
                pedido.header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(par.getBytes(StandardCharsets.UTF_8)));
            } else {
                throw new IllegalStateException("Sem credenciais guardadas.");
            }

            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(TEMPO_LIMITE)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(pedido.build(), HttpResponse.BodyHandlers.ofString());

            if (r.statusCode() == 200) {
                ok = true;
                detalhe = "Credenciais aceites pelo servidor Traccar.";
            } else if (r.statusCode() == 401 || r.statusCode() == 403 || r.statusCode() == 400) {
                // O Traccar 6 responde 400 a um token mal formado e 401 a um
                // token revogado ou a uma palavra-passe errada: para quem está a
                // configurar é a mesma coisa — as credenciais não servem.
                erro = "O servidor respondeu, mas recusou as credenciais (token ou palavra-passe errados).";
            } else {
                erro = "O servidor respondeu " + r.statusCode() + ".";
            }

        } catch (java.net.ConnectException e) {
            erro = "Não foi possível contactar o servidor no endereço indicado.";
        } catch (java.net.http.HttpTimeoutException e) {
            erro = "O servidor não respondeu dentro de " + TEMPO_LIMITE.toSeconds() + " segundos.";
        } catch (Exception e) {
            // A mensagem técnica fica no log; o utilizador recebe uma frase.
            log.warn("Teste do Traccar falhou para a empresa {}: {}", orgId, e.toString());
            erro = "Não foi possível concluir o teste. Verifique o endereço e as credenciais.";
        }

        s.setTraccarOk(ok);
        s.setTraccarCheckedAt(Instant.now());
        s.setTraccarLastError(erro);

        audit.record(orgId, userId, "integration.traccar_test", "Organization", orgId,
                ok ? "Ligação ao Traccar confirmada" : "Teste ao Traccar falhou: " + erro);
        return IntegrationDtos.TestResult.of(ok, ok ? detalhe : erro);
    }

    // ==== Email ============================================================

    @Transactional
    public IntegrationSettings saveSmtp(
            String orgId, String userId, IntegrationDtos.SmtpRequest req) {

        IntegrationSettings s = forOrganization(orgId);
        s.setSmtpHost(vazioParaNulo(req.host()));
        s.setSmtpPort(req.port());
        s.setSmtpUsername(vazioParaNulo(req.username()));
        s.setSmtpFrom(vazioParaNulo(req.from()));
        s.setSmtpFromName(vazioParaNulo(req.fromName()));
        if (req.security() != null && !req.security().isBlank()) {
            s.setSmtpSecurity(req.security().trim().toUpperCase());
        }
        if (req.password() != null && !MASCARA.equals(req.password())) {
            s.setSmtpPasswordEnc(cofre.encrypt(semMascara(req.password())));
        }

        if (s.getSmtpHost() != null && s.getSmtpFrom() == null) {
            throw ApiException.badRequest(
                    "Indique o endereço remetente: sem ele nenhum servidor aceita o envio.");
        }
        if (s.getSmtpHost() != null && s.getSmtpPort() == null) {
            // Os portos habituais, para o utilizador não ter de os saber.
            s.setSmtpPort("SSL".equals(s.getSmtpSecurity()) ? 465 : 587);
        }

        s.setSmtpOk(null);
        s.setSmtpCheckedAt(null);
        s.setSmtpLastError(null);

        audit.record(orgId, userId, "integration.smtp", "Organization", orgId,
                s.hasSmtp() ? "Email por " + s.getSmtpHost() : "Email desconfigurado");
        return s;
    }

    /**
     * Envia mesmo uma mensagem para o endereço indicado.
     *
     * <p>Validar só a ligação diria que está bom em casos em que o envio falha
     * na mesma — autenticação aceite mas remetente recusado, por exemplo. Quem
     * carrega no botão quer saber se o email chega.
     */
    @Transactional
    public IntegrationDtos.TestResult testSmtp(String orgId, String userId, String destino) {
        IntegrationSettings s = forOrganization(orgId);
        if (!s.hasSmtp()) {
            throw ApiException.badRequest(
                    "Defina primeiro o servidor de email e o endereço remetente.");
        }
        if (destino == null || !destino.contains("@")) {
            throw ApiException.badRequest("Indique o endereço para onde enviar o teste.");
        }

        // Utilizador sem palavra-passe nenhuma: o servidor vai pedir autenticação
        // e nós não temos o que dar. Dá 535, igual a uma palavra-passe errada —
        // e é o que acontece quando o campo foi guardado com a máscara e nunca
        // chegou a receber uma palavra-passe a sério.
        if (s.getSmtpUsername() != null && !s.getSmtpUsername().isBlank()
                && (s.getSmtpPasswordEnc() == null || s.getSmtpPasswordEnc().isBlank())) {
            String aviso = "Há utilizador mas não há nenhuma palavra-passe guardada.";
            s.setSmtpOk(false);
            s.setSmtpCheckedAt(Instant.now());
            s.setSmtpLastError(aviso);
            audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                    "Teste de email falhou: " + aviso);
            return new IntegrationDtos.TestResult(false, aviso, Instant.now(),
                    "utilizador: " + s.getSmtpUsername() + " | palavra-passe guardada: nenhuma",
                    "Escreva a palavra-passe no campo e guarde. Se o campo mostra pontos, "
                            + "apague-os primeiro: enquanto lá estiverem tal como vieram, o "
                            + "sistema entende que não a quer mudar.", null);
        }

        // A palavra-passe está guardada cifrada. Se a chave de cifra mudou, o
        // decrypt devolve null em silêncio e o envio segue com palavra-passe
        // vazia — e o servidor responde «credenciais recusadas», que manda
        // procurar onde não está o problema. Isto distingue as duas coisas.
        if (s.getSmtpPasswordEnc() != null && !s.getSmtpPasswordEnc().isBlank()
                && cofre.decrypt(s.getSmtpPasswordEnc()) == null) {
            String aviso = "A palavra-passe está guardada mas o sistema já não a consegue ler.";
            s.setSmtpOk(false);
            s.setSmtpCheckedAt(Instant.now());
            s.setSmtpLastError(aviso);
            audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                    "Teste de email falhou: " + aviso);
            return new IntegrationDtos.TestResult(false, aviso, Instant.now(),
                    "A decifragem do segredo guardado falhou.",
                    "Escreva a palavra-passe outra vez neste ecrã e guarde. "
                            + "Acontece quando a chave de cifra do servidor muda: os segredos "
                            + "antigos deixam de ser legíveis, de propósito.", null);
        }

        // Primeiro o que se sabe sem rede nenhuma. O porto e a segurança têm de
        // combinar: pedir STARTTLS no 465 dá um silêncio até ao fim do tempo
        // de espera, e esse erro não se parece nada com o problema que é.
        String incoerente = incoerencia(s);
        if (incoerente != null) {
            s.setSmtpOk(false);
            s.setSmtpCheckedAt(Instant.now());
            s.setSmtpLastError(incoerente);
            audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                    "Teste de email falhou: " + incoerente);
            return new IntegrationDtos.TestResult(false,
                    "A configuração não é coerente, e o envio falharia sempre.",
                    Instant.now(), null, incoerente, null);
        }

        // Antes de falar SMTP, ver se se chega lá. É a falha mais comum e a
        // que o erro de SMTP descreve pior: num servidor de alojamento, a
        // saída pelos portos de email vem bloqueada de origem, e a mensagem
        // que se recebia era «não foi possível enviar, verifique as
        // credenciais» — a mandar procurar onde não estava o problema.
        String alcance = alcancavel(s.getSmtpHost(), s.getSmtpPort());
        if (alcance != null) {
            s.setSmtpOk(false);
            s.setSmtpCheckedAt(Instant.now());
            s.setSmtpLastError(alcance);
            audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                    "Teste de email falhou: " + alcance);
            return new IntegrationDtos.TestResult(false, alcance, Instant.now(),
                    "Não foi possível abrir ligação TCP a " + s.getSmtpHost() + ":" + s.getSmtpPort(),
                    "Quase sempre é o alojamento a bloquear a saída de email. Peça ao suporte "
                            + "para libertar os portos 587 e 465, ou experimente o porto 465 "
                            + "com segurança SSL.", null);
        }

        String erro = null;
        String detalhe = null;
        String sugestao = null;
        String dialogo = null;
        boolean ok = false;

        java.io.ByteArrayOutputStream conversa = new java.io.ByteArrayOutputStream();
        try {
            JavaMailSenderImpl remetente = build(s);
            // Guardar o diálogo com o servidor. É a única forma de ver qual o
            // método de autenticação escolhido e o que o servidor respondeu a
            // cada passo — e, sem isso, um «535» é um beco sem saída.
            jakarta.mail.Session sessao = remetente.getSession();
            sessao.setDebug(true);
            sessao.setDebugOut(new java.io.PrintStream(
                    conversa, true, java.nio.charset.StandardCharsets.UTF_8));
            MimeMessage msg = remetente.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(s.getSmtpFrom(),
                    s.getSmtpFromName() != null ? s.getSmtpFromName() : "IMBONDEIRO OS");
            h.setTo(destino.trim());
            h.setSubject("IMBONDEIRO OS — teste de configuração de email");
            h.setText("""
                    Este email confirma que o IMBONDEIRO OS consegue enviar mensagens
                    através do servidor que configurou.

                    Se o recebeu, os avisos de manutenção, validades de documentos
                    e alertas da frota já podem sair por email.
                    """);
            remetente.send(msg);
            ok = true;

        } catch (Exception e) {
            log.warn("Teste de email falhou para a empresa {}: {}", orgId, e.toString());
            erro = explicar(e);
            detalhe = cru(e);
            sugestao = sugerir(s, detalhe);
            // Quando o servidor recusa as credenciais, o que falta saber é o
            // que foi mesmo enviado. Não se mostra a palavra-passe — mostra-se
            // a forma dela, que chega para ver que está truncada, com pontos
            // da máscara ou com um espaço no fim.
            String forma = formaDasCredenciais(s);
            if (forma != null) {
                detalhe = detalhe + " | " + forma;
            }
            dialogo = redigir(conversa.toString(java.nio.charset.StandardCharsets.UTF_8));
        }

        s.setSmtpOk(ok);
        s.setSmtpCheckedAt(Instant.now());
        // O detalhe fica junto da frase: quando alguém voltar ao ecrã dias
        // depois, a resposta do servidor ainda está lá para ser lida.
        s.setSmtpLastError(erro == null ? null
                : (detalhe == null ? erro : erro + " · " + detalhe));

        audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                ok ? "Email de teste enviado para " + destino
                        : "Teste de email falhou: " + erro);
        return new IntegrationDtos.TestResult(ok,
                ok ? "Mensagem entregue ao servidor. Verifique a caixa de " + destino + "."
                        : erro,
                Instant.now(), detalhe, sugestao, dialogo);
    }

    /** Constrói o remetente a partir do que a empresa configurou. */
    public JavaMailSenderImpl build(IntegrationSettings s) {
        return SmtpFactory.build(s, cofre);
    }

    @Transactional
    public IntegrationSettings setTraccarPoll(String orgId, String userId, boolean enabled) {
        IntegrationSettings s = forOrganization(orgId);
        s.setTraccarPollEnabled(enabled);
        audit.record(orgId, userId, "integration.traccar_poll", "Organization", orgId,
                enabled ? "Sondagem do Traccar ligada" : "Sondagem do Traccar desligada");
        return s;
    }

    @Transactional
    public String newForwardSecret(String orgId, String userId) {
        IntegrationSettings s = forOrganization(orgId);
        String segredo = traccarPositions.novoSegredoDeEncaminhamento(s);
        audit.record(orgId, userId, "integration.traccar_forward", "Organization", orgId,
                "Segredo de encaminhamento gerado");
        return segredo;
    }

    // ===== Motor de rotas ==================================================

    /**
     * Aponta a empresa a um motor de rotas.
     *
     * <p>Endereço vazio desliga: o sistema volta a estimar em linha reta e
     * a dizê-lo. Preferível a manter um endereço morto que falha em silêncio.
     */
    @Transactional
    public IntegrationSettings saveRouting(String orgId, String userId, String url) {
        IntegrationSettings s = forOrganization(orgId);
        s.setRoutingUrl(normalizarUrl(url));
        if (!s.hasRouting()) {
            s.setRoutingOk(null);
            s.setRoutingCheckedAt(null);
            s.setRoutingLastError(null);
        }
        audit.record(orgId, userId, "integration.routing", "Organization", orgId,
                s.hasRouting() ? "Motor de rotas apontado a " + s.getRoutingUrl()
                        : "Motor de rotas desligado");
        return s;
    }

    /**
     * Pede ao motor um percurso curto e conhecido.
     *
     * <p>Dois pontos separados por uns quarteirões em Luanda: se o motor
     * estiver a correr com um mapa carregado, responde {@code Ok}. Um motor de
     * pé mas sem mapa responde, e é exatamente a avaria que um teste de
     * «o servidor está vivo?» deixaria passar.
     */
    @Transactional
    public IntegrationDtos.TestResult testRouting(String orgId, String userId) {
        IntegrationSettings s = forOrganization(orgId);
        String motor = routingEngine.motorPara(s);
        if (motor == null) {
            throw ApiException.badRequest("Defina primeiro o endereço do motor de rotas.");
        }

        String erro = null;
        boolean ok = false;
        String detalhe = null;

        try {
            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(TEMPO_LIMITE)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(motor
                                            + "/route/v1/driving/"
                                            + "13.2344,-8.8383;13.2600,-8.8200"
                                            + "?overview=false"))
                                    .timeout(TEMPO_LIMITE)
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            if (r.statusCode() == 200 && r.body().contains("\"code\":\"Ok\"")) {
                ok = true;
                detalhe = "O motor respondeu com um percurso.";
            } else if (r.statusCode() == 200) {
                erro = "O motor respondeu, mas não encontrou caminho — o mapa "
                        + "carregado pode não cobrir Angola.";
            } else {
                erro = "O motor respondeu " + r.statusCode() + ".";
            }

        } catch (java.net.ConnectException e) {
            erro = "Não foi possível contactar o motor no endereço indicado.";
        } catch (java.net.http.HttpTimeoutException e) {
            erro = "O motor não respondeu dentro de " + TEMPO_LIMITE.toSeconds() + " segundos.";
        } catch (Exception e) {
            log.warn("Teste do motor de rotas falhou para a empresa {}: {}", orgId, e.toString());
            erro = "Não foi possível concluir o teste. Verifique o endereço.";
        }

        s.setRoutingOk(ok);
        s.setRoutingCheckedAt(Instant.now());
        s.setRoutingLastError(erro);

        audit.record(orgId, userId, "integration.routing_test", "Organization", orgId,
                ok ? "Motor de rotas confirmado" : "Teste ao motor de rotas falhou: " + erro);
        return IntegrationDtos.TestResult.of(ok, ok ? detalhe : erro);
    }

    /**
     * O diálogo com o servidor, sem as credenciais lá dentro.
     *
     * <p>O registo do JavaMail traz o que foi enviado e o que foi respondido,
     * linha a linha — incluindo a linha com o utilizador e a palavra-passe em
     * base64, que é trivial de desfazer. Essa é substituída; tudo o resto fica,
     * porque é tudo o que interessa: que método de autenticação o servidor
     * ofereceu, qual foi escolhido, e em que passo recusou.
     */
    private static String redigir(String registo) {
        if (registo == null || registo.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String linha : registo.split("\n")) {
            String limpa = linha.strip();
            if (limpa.isEmpty()) {
                continue;
            }
            String maiusculas = limpa.toUpperCase(Locale.ROOT);

            // «AUTH PLAIN <base64>» leva as credenciais na própria linha.
            if (maiusculas.startsWith("AUTH PLAIN ") || maiusculas.startsWith("AUTH LOGIN ")) {
                String[] partes = limpa.split(" ");
                sb.append(partes[0]).append(' ').append(partes[1])
                        .append(" <credenciais ocultas>\n");
                continue;
            }
            // E o AUTH LOGIN manda-as nas linhas seguintes, uma em cada.
            if (pareceBase64(limpa)) {
                sb.append("<credenciais ocultas>\n");
                continue;
            }
            sb.append(limpa).append('\n');
        }
        String texto = sb.toString();
        return texto.length() > 4000 ? texto.substring(texto.length() - 4000) : texto;
    }

    /** Uma linha que só tem caracteres de base64 e é comprida. */
    private static boolean pareceBase64(String linha) {
        if (linha.length() < 8) {
            return false;
        }
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            boolean valido = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!valido) {
                return false;
            }
        }
        return true;
    }

    /**
     * A forma das credenciais enviadas, sem revelar nenhuma.
     *
     * <p>Quando o servidor diz «recusado» e quem configurou jura que a
     * palavra-passe está certa, só há uma forma de avançar: ver o que foi
     * realmente enviado. Mostrar a palavra-passe seria inaceitável — mas o
     * comprimento, se tem acentos, se traz pontos da máscara ou um espaço no
     * fim resolve o caso em segundos e não revela nada a ninguém.
     */
    private String formaDasCredenciais(IntegrationSettings s) {
        String senha = cofre.decrypt(s.getSmtpPasswordEnc());
        if (senha == null) {
            return "palavra-passe guardada: nenhuma (o envio seguiu sem palavra-passe)";
        }
        StringBuilder sb = new StringBuilder("utilizador: ")
                .append(s.getSmtpUsername() == null ? "(vazio)" : s.getSmtpUsername())
                .append(" | palavra-passe guardada: ").append(senha.length()).append(" caracteres");
        if (senha.indexOf('\u2022') >= 0) {
            sb.append(", COM PONTOS DA MÁSCARA — foi escrita por cima dos pontos do campo");
        }
        if (!senha.equals(senha.trim())) {
            sb.append(", com espaço no início ou no fim");
        }
        boolean soAscii = senha.chars().allMatch(c -> c >= 32 && c < 127);
        if (!soAscii) {
            sb.append(", com caracteres fora do ASCII");
        }
        return sb.toString();
    }

    /**
     * Tira a máscara que o ecrã mostra, quando ela vem colada ao que foi escrito.
     *
     * <p>O campo da palavra-passe mostra pontos quando já há uma guardada. Quem
     * clica no meio e escreve a seguir manda «••••••••palavra-passe» — que não
     * é igual à máscara, portanto passava e era guardada com os pontos à frente.
     * Resultado: autenticação recusada para sempre, com a palavra-passe certa
     * escrita no ecrã. Não é erro de quem escreve; é do campo.
     */
    private static String semMascara(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor;
        while (v.startsWith("•")) {
            v = v.substring(1);
        }
        // Não se corta mais nada. Um segredo guarda-se tal e qual foi escrito:
        // há palavras-passe que acabam em espaço, e cortá-lo é inventar uma
        // palavra-passe diferente da que o utilizador tem — que depois é
        // recusada sem ninguém perceber porquê.
        return v.isEmpty() ? null : v;
    }

    /** O domínio do endereço remetente, para sugerir o utilizador completo. */
    private static String dominioDe(String endereco) {
        if (endereco == null || !endereco.contains("@")) {
            return "o-seu-dominio.ao";
        }
        return endereco.substring(endereco.indexOf('@') + 1);
    }

    /**
     * Incoerências que se apanham sem sair da aplicação.
     *
     * <p>São as duas que custam mais tempo a quem configura, porque o erro que
     * o servidor devolve não aponta para elas: o porto 465 fala TLS desde o
     * primeiro byte e o 587 começa em claro e sobe com STARTTLS. Trocar os dois
     * dá um tempo de espera esgotado, que se parece com «o servidor está em
     * baixo» e não com «a opção está trocada».
     */
    private static String incoerencia(IntegrationSettings s) {
        int porto = s.getSmtpPort() != null ? s.getSmtpPort() : 587;
        String seguranca = s.getSmtpSecurity() != null ? s.getSmtpSecurity() : "STARTTLS";
        if (porto == 465 && !"SSL".equals(seguranca)) {
            return "O porto 465 fala SSL desde o início. Mude a segurança para SSL, "
                    + "ou use o porto 587 com STARTTLS.";
        }
        if (porto == 587 && "SSL".equals(seguranca)) {
            return "O porto 587 usa STARTTLS, não SSL directo. Mude a segurança para STARTTLS, "
                    + "ou use o porto 465 com SSL.";
        }
        if (porto == 25) {
            return "O porto 25 está bloqueado em praticamente todos os alojamentos. "
                    + "Use 587 com STARTTLS ou 465 com SSL.";
        }
        return null;
    }

    /**
     * Chega-se ao servidor, antes de se tentar falar com ele?
     *
     * @return {@code null} quando a ligação abre; a frase do problema quando não
     */
    private static String alcancavel(String host, Integer porto) {
        if (host == null || host.isBlank()) {
            return null;
        }
        int p = porto != null ? porto : 587;
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, p), 8000);
            return null;
        } catch (java.net.UnknownHostException e) {
            return "O nome «" + host + "» não existe. Confirme o endereço do servidor de email.";
        } catch (java.io.IOException e) {
            return "O servidor da aplicação não consegue abrir ligação a " + host + ":" + p + ".";
        }
    }

    /** A resposta do servidor, em cru e curta — é isto que resolve o problema. */
    private static String cru(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        int voltas = 0;
        while (t != null && voltas++ < 4) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ← ");
                }
                sb.append(t.getMessage().replaceAll("\\s+", " ").trim());
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        String texto = sb.toString();
        return texto.isBlank() ? e.getClass().getSimpleName()
                : (texto.length() > 400 ? texto.substring(0, 400) + "…" : texto);
    }

    /**
     * O que fazer a seguir, quando o erro é um dos conhecidos.
     *
     * <p>Isto é conhecimento que não está no erro do servidor e que faz perder
     * tardes: o Gmail não aceita a palavra-passe da conta desde 2022 — quer
     * uma palavra-passe de aplicação; a Microsoft desliga o SMTP autenticado
     * por omissão em cada caixa; e o porto 465 fala SSL desde o primeiro byte,
     * portanto pedir-lhe STARTTLS dá um silêncio até ao fim do tempo de
     * espera, que não se parece nada com o problema que é.
     */
    private static String sugerir(IntegrationSettings s, String detalhe) {
        String d = detalhe == null ? "" : detalhe.toLowerCase();
        String host = s.getSmtpHost() == null ? "" : s.getSmtpHost().toLowerCase();
        int porto = s.getSmtpPort() != null ? s.getSmtpPort() : 587;
        String seguranca = s.getSmtpSecurity() != null ? s.getSmtpSecurity() : "STARTTLS";

        if (porto == 465 && !"SSL".equals(seguranca)) {
            return "O porto 465 fala SSL desde o início. Mude a segurança para SSL, "
                    + "ou use o porto 587 com STARTTLS.";
        }
        if (porto == 587 && "SSL".equals(seguranca)) {
            return "O porto 587 usa STARTTLS, não SSL directo. Mude a segurança para STARTTLS, "
                    + "ou use o porto 465 com SSL.";
        }
        if (porto == 25) {
            return "O porto 25 está bloqueado em praticamente todos os alojamentos. "
                    + "Use 587 com STARTTLS ou 465 com SSL.";
        }
        boolean autenticacao = d.contains("535") || d.contains("534") || d.contains("authentication")
                || d.contains("username and password") || d.contains("not accepted")
                || d.contains("invalid credentials") || d.contains("login");
        if (autenticacao) {
            if (host.contains("gmail") || host.contains("google")) {
                return "O Gmail já não aceita a palavra-passe da conta. Ligue a verificação em "
                        + "dois passos e crie uma «palavra-passe de aplicação» de 16 letras em "
                        + "myaccount.google.com/apppasswords — é essa que se põe aqui, sem espaços.";
            }
            if (host.contains("office365") || host.contains("outlook") || host.contains("microsoft")) {
                return "A Microsoft desliga o «SMTP autenticado» em cada caixa de correio por "
                        + "omissão. Tem de o ligar no centro de administração (Utilizadores → "
                        + "a conta → Correio → Gerir aplicações de email → SMTP autenticado) e, "
                        + "se a conta tiver verificação em dois passos, usar uma palavra-passe "
                        + "de aplicação.";
            }
            String utilizador = s.getSmtpUsername();
            if (utilizador == null || utilizador.isBlank()) {
                return "Está a enviar sem utilizador e o servidor exige autenticação. "
                        + "Preencha o utilizador (normalmente o endereço de email completo) "
                        + "e a palavra-passe da caixa de correio.";
            }
            if (!utilizador.contains("@")) {
                return "O utilizador está como «" + utilizador + "» e a maior parte dos "
                        + "servidores exige o endereço completo — por exemplo «"
                        + utilizador + "@" + dominioDe(s.getSmtpFrom()) + "». Experimente assim.";
            }
            return "O servidor aceitou a ligação e recusou estas credenciais para «" + utilizador
                    + "». As três causas, por ordem de frequência: (1) a palavra-passe é a do "
                    + "painel do alojamento e não a da caixa de correio — são diferentes, e a da "
                    + "caixa define-se onde se cria a conta de email; (2) a caixa «" + utilizador
                    + "» ainda não existe no servidor, só o domínio; (3) a palavra-passe foi "
                    + "mudada depois de ter sido guardada aqui. Reponha a palavra-passe da caixa "
                    + "no painel do alojamento e escreva-a de novo neste ecrã.";
        }
        if (d.contains("relay") || d.contains("sender") || d.contains("553") || d.contains("550")) {
            String utilizador = s.getSmtpUsername() == null ? "o utilizador" : s.getSmtpUsername();
            return "O servidor recusou o remetente. A maior parte dos servidores só deixa enviar "
                    + "em nome da própria conta: ponha no «endereço remetente» o mesmo que em "
                    + "utilizador (" + utilizador + ").";
        }
        if (d.contains("timed out") || d.contains("timeout")) {
            return "O servidor aceitou a ligação mas não respondeu. É o sintoma típico de "
                    + "segurança trocada: confirme 587 com STARTTLS ou 465 com SSL.";
        }
        if (d.contains("certificate") || d.contains("ssl") || d.contains("handshake")) {
            return "A ligação segura falhou. Experimente a outra opção de segurança; se o "
                    + "servidor é do próprio alojamento, confirme o nome exacto com o suporte.";
        }
        return null;
    }

    private static String explicar(Exception e) {
        String t = String.valueOf(e.getMessage()).toLowerCase();
        if (t.contains("authentication") || t.contains("535") || t.contains("password")) {
            return "O servidor recusou o utilizador ou a palavra-passe.";
        }
        if (t.contains("connect") || t.contains("unknownhost")) {
            return "Não foi possível contactar o servidor no endereço e porto indicados.";
        }
        if (t.contains("timed out") || t.contains("timeout")) {
            return "O servidor não respondeu a tempo. Confirme o porto e a segurança.";
        }
        if (t.contains("ssl") || t.contains("starttls") || t.contains("certificate")) {
            return "Falha de segurança na ligação. Experimente outra opção de segurança.";
        }
        if (t.contains("relay") || t.contains("sender") || t.contains("from")) {
            return "O servidor recusou o endereço remetente.";
        }
        return "Não foi possível enviar. Verifique o servidor, o porto e as credenciais.";
    }

    /** Sem barra no fim: o caminho é sempre acrescentado a seguir. */
    private static String normalizarUrl(String url) {
        String v = vazioParaNulo(url);
        if (v == null) {
            return null;
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            throw ApiException.badRequest("O endereço tem de começar por http:// ou https://.");
        }
        return v;
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
