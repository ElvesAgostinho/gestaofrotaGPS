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

    public record TestResult(boolean ok, String message, Instant checkedAt) {}

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

            String routingUrl,
            boolean routingConfigured,
            Boolean routingOk,
            Instant routingCheckedAt,
            String routingLastError) {

        public static SettingsView of(IntegrationSettings s) {
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

                    s.getRoutingUrl(),
                    s.hasRouting(),
                    s.getRoutingOk(),
                    s.getRoutingCheckedAt(),
                    s.getRoutingLastError());
        }
    }
}
