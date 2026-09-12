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
import java.util.Base64;
import java.util.Properties;
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
    private final AuditService audit;

    public IntegrationService(
            IntegrationSettingsRepository repo,
            OrganizationRepository organizations,
            SecretBox cofre,
            AuditService audit,
            @org.springframework.context.annotation.Lazy ao.autocare.modules.telemetry.TraccarPositions traccarPositions) {
        this.traccarPositions = traccarPositions;
        this.repo = repo;
        this.organizations = organizations;
        this.cofre = cofre;
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
            } else if (r.statusCode() == 401) {
                erro = "O servidor respondeu, mas recusou as credenciais.";
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
        return new IntegrationDtos.TestResult(ok, ok ? detalhe : erro, Instant.now());
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
            s.setSmtpPasswordEnc(cofre.encrypt(vazioParaNulo(req.password())));
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

        String erro = null;
        boolean ok = false;

        try {
            JavaMailSenderImpl remetente = build(s);
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
        }

        s.setSmtpOk(ok);
        s.setSmtpCheckedAt(Instant.now());
        s.setSmtpLastError(erro);

        audit.record(orgId, userId, "integration.smtp_test", "Organization", orgId,
                ok ? "Email de teste enviado para " + destino
                        : "Teste de email falhou: " + erro);
        return new IntegrationDtos.TestResult(ok,
                ok ? "Mensagem entregue ao servidor. Verifique a caixa de " + destino + "."
                        : erro,
                Instant.now());
    }

    /** Constrói o remetente a partir do que a empresa configurou. */
    public JavaMailSenderImpl build(IntegrationSettings s) {
        JavaMailSenderImpl remetente = new JavaMailSenderImpl();
        remetente.setHost(s.getSmtpHost());
        remetente.setPort(s.getSmtpPort() != null ? s.getSmtpPort() : 587);
        remetente.setUsername(s.getSmtpUsername());
        remetente.setPassword(cofre.decrypt(s.getSmtpPasswordEnc()));
        remetente.setDefaultEncoding("UTF-8");

        Properties p = remetente.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(s.getSmtpUsername() != null));
        p.put("mail.smtp.connectiontimeout", "12000");
        p.put("mail.smtp.timeout", "12000");
        p.put("mail.smtp.writetimeout", "12000");

        String seguranca = s.getSmtpSecurity() != null ? s.getSmtpSecurity() : "STARTTLS";
        if ("SSL".equals(seguranca)) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(seguranca)) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        return remetente;
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
        if (!s.hasRouting()) {
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
                                    .uri(URI.create(s.getRoutingUrl()
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
        return new IntegrationDtos.TestResult(ok, ok ? detalhe : erro, Instant.now());
    }

    /** Traduz a falha para algo que o utilizador possa corrigir. */
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
