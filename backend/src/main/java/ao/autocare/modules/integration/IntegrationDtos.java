package ao.autocare.modules.integration;

import ao.autocare.domain.IntegrationSettings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Pedidos e vistas das integrações. */
public final class IntegrationDtos {

    private IntegrationDtos() {}

    public record TraccarRequest(
            @Size(max = 300) String url,
            @Size(max = 190) String user,
            @Size(max = 200) String password,
            @Size(max = 400) String token) {}

    public record SmtpRequest(
            @Size(max = 190) String host,
            @Min(1) @Max(65535) Integer port,
            @Size(max = 190) String username,
            @Size(max = 200) String password,
            @Email(message = "O remetente tem de ser um endereço de email válido.")
            @Size(max = 190) String from,
            @Size(max = 120) String fromName,
            @Size(max = 20) String security) {}

    /** Onde vive o motor de rotas desta empresa. */
    public record RoutingRequest(
            @jakarta.validation.constraints.Size(max = 300) String url) {}

    public record PollRequest(boolean enabled) {}

    /** O segredo em claro, uma unica vez, e o URL a por no traccar.xml. */
    public record ForwardSecret(String secret, String url) {}

    public record TestRequest(
            @Email(message = "Indique um endereço de email válido.") String to) {}

    /**
     * O resultado de um teste de ligação.
     *
     * <p>{@code message} é a frase para quem lê. {@code detail} é o que o
     * servidor respondeu, em cru — e é o campo que faz a diferença entre
     * «deu erro» e «535 5.7.8 Username and Password not accepted», que se
     * resolve numa tarde. {@code hint} é o que fazer a seguir, quando o erro
     * é um dos conhecidos.
     */
    /** O servidor de receção. As credenciais são, quase sempre, as do envio. */
    public record ImapRequest(
            @Size(max = 190) String host,
            Integer port,
            @Size(max = 20) String security,
            @Size(max = 190) String username,
            @Size(max = 300) String password,
            Boolean useSmtpLogin,
            @Size(max = 190) String sentFolder) {}

    public record TestResult(boolean ok, String message, Instant checkedAt,
                             String detail, String hint,
                             /** O diálogo com o servidor, sem as credenciais. */
                             String dialog) {

        public static TestResult of(boolean ok, String message) {
            return new TestResult(ok, message, Instant.now(), null, null, null);
        }
    }

    /**
     * O que a API devolve.
     *
     * <p>As palavras-passe e o token <b>não</b> saem daqui: só se diz se estão
     * definidos. Devolver o segredo cifrado ou em claro tornaria o ecrã de
     * configuração numa forma de os ler.
     */
    public record SettingsView(
            String traccarUrl,
            String traccarUser,
            boolean traccarPasswordSet,
            boolean traccarTokenSet,
            boolean traccarConfigured,
            Boolean traccarOk,
            Instant traccarCheckedAt,
            String traccarLastError,
            boolean traccarPollEnabled,
            Instant traccarLastPollAt,
            Instant traccarLastPositionAt,
            String traccarPollError,
            boolean traccarForwardSecretSet,

            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            boolean smtpPasswordSet,
            String smtpFrom,
            String smtpFromName,
            String smtpSecurity,
            boolean smtpConfigured,
            Boolean smtpOk,
            Instant smtpCheckedAt,
            String smtpLastError,

            String imapHost,
            Integer imapPort,
            String imapSecurity,
            String imapUsername,
            boolean imapPasswordSet,
            boolean imapUseSmtpLogin,
            String imapSentFolder,
            boolean imapConfigured,
            Boolean imapOk,
            Instant imapCheckedAt,
            String imapLastError,

            String routingUrl,
            boolean routingConfigured,
            Boolean routingOk,
            Instant routingCheckedAt,
            String routingLastError,
            /** Motor de rotas da plataforma, usado quando a empresa não tem o seu. */
            String routingPlatformUrl,
            /** Traccar da plataforma: a empresa pode pedir uma conta lá em vez de trazer a sua. */
            boolean traccarPlatformAvailable) {

        public static SettingsView of(IntegrationSettings s) {
            return of(s, null, false);
        }

        public static SettingsView of(IntegrationSettings s, String routingPlatformUrl,
                boolean traccarPlatformAvailable) {
            return new SettingsView(
                    s.getTraccarUrl(),
                    s.getTraccarUser(),
                    s.getTraccarPasswordEnc() != null,
                    s.getTraccarTokenEnc() != null,
                    s.hasTraccar(),
                    s.getTraccarOk(),
                    s.getTraccarCheckedAt(),
                    s.getTraccarLastError(),
                    s.isTraccarPollEnabled(),
                    s.getTraccarLastPollAt(),
                    s.getTraccarLastPositionAt(),
                    s.getTraccarPollError(),
                    s.getTraccarForwardSecretHash() != null,

                    s.getSmtpHost(),
                    s.getSmtpPort(),
                    s.getSmtpUsername(),
                    s.getSmtpPasswordEnc() != null,
                    s.getSmtpFrom(),
                    s.getSmtpFromName(),
                    s.getSmtpSecurity(),
                    s.hasSmtp(),
                    s.getSmtpOk(),
                    s.getSmtpCheckedAt(),
                    s.getSmtpLastError(),

                    s.getImapHost(),
                    s.getImapPort(),
                    s.getImapSecurity(),
                    s.getImapUsername(),
                    s.getImapPasswordEnc() != null,
                    s.isImapUseSmtpLogin(),
                    s.getImapSentFolder(),
                    s.hasImap(),
                    s.getImapOk(),
                    s.getImapCheckedAt(),
                    s.getImapLastError(),

                    s.getRoutingUrl(),
                    s.hasRouting(),
                    s.getRoutingOk(),
                    s.getRoutingCheckedAt(),
                    s.getRoutingLastError(),
                    routingPlatformUrl,
                    traccarPlatformAvailable);
        }
    }
}
