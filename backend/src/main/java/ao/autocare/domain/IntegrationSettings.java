package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Credenciais de integração de uma empresa: Traccar e servidor de email.
 *
 * <p>Antes disto só existiam variáveis de ambiente. Isso serve um servidor
 * próprio; não serve um produto vendido a várias empresas, cada uma com o seu
 * Traccar e o seu email — e obrigava a reiniciar a aplicação para mudar uma
 * palavra-passe.
 *
 * <p>As palavras-passe ficam cifradas (ver {@code SecretBox}) e nunca saem da
 * API: a resposta diz apenas se estão definidas.
 */
@Getter
@Setter
@Entity
@Table(name = "integration_settings")
public class IntegrationSettings extends TimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    // ---- Traccar ----------------------------------------------------------

    @Column(name = "traccar_url", length = 300)
    private String traccarUrl;

    @Column(name = "traccar_user", length = 190)
    private String traccarUser;

    @Column(name = "traccar_password_enc", length = 1000)
    private String traccarPasswordEnc;

    @Column(name = "traccar_token_enc", length = 1000)
    private String traccarTokenEnc;

    @Column(name = "traccar_checked_at")
    private Instant traccarCheckedAt;

    @Column(name = "traccar_ok")
    private Boolean traccarOk;

    @Column(name = "traccar_last_error", length = 500)
    private String traccarLastError;

    /** Sondar o Traccar de 20 em 20 s para trazer as posições. */
    @Column(name = "traccar_poll_enabled", nullable = false)
    private boolean traccarPollEnabled = true;

    @Column(name = "traccar_last_poll_at")
    private Instant traccarLastPollAt;

    /** Quando entrou a última posição vinda do Traccar (por qualquer via). */
    @Column(name = "traccar_last_position_at")
    private Instant traccarLastPositionAt;

    @Column(name = "traccar_poll_error", length = 500)
    private String traccarPollError;

    /** SHA-256 do segredo que o Traccar manda no encaminhamento. */
    @Column(name = "traccar_forward_secret_hash", length = 64)
    private String traccarForwardSecretHash;

    // ---- Email ------------------------------------------------------------

    @Column(name = "smtp_host", length = 190)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 190)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", length = 1000)
    private String smtpPasswordEnc;

    @Column(name = "smtp_from", length = 190)
    private String smtpFrom;

    @Column(name = "smtp_from_name", length = 120)
    private String smtpFromName;

    /** NONE | STARTTLS | SSL */
    @Column(name = "smtp_security", length = 20)
    private String smtpSecurity = "STARTTLS";

    @Column(name = "smtp_checked_at")
    private Instant smtpCheckedAt;

    @Column(name = "smtp_ok")
    private Boolean smtpOk;

    @Column(name = "smtp_last_error", length = 500)
    private String smtpLastError;

    // ---- Motor de rotas (OSRM) -----------------------------------------

    @Column(name = "routing_url", length = 300)
    private String routingUrl;

    @Column(name = "routing_checked_at")
    private Instant routingCheckedAt;

    @Column(name = "routing_ok")
    private Boolean routingOk;

    @Column(name = "routing_last_error", length = 500)
    private String routingLastError;

    /** Há Traccar configurado a ponto de se poder tentar ligar? */
    public boolean hasTraccar() {
        return traccarUrl != null && !traccarUrl.isBlank();
    }

    /** Há motor de rotas apontado? */
    public boolean hasRouting() {
        return routingUrl != null && !routingUrl.isBlank();
    }

    /** Há servidor de email suficiente para enviar? */
    public boolean hasSmtp() {
        return smtpHost != null && !smtpHost.isBlank()
                && smtpFrom != null && !smtpFrom.isBlank();
    }
}
