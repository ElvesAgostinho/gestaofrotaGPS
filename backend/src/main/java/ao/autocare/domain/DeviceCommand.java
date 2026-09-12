package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.CommandConfirmationSource;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import ao.autocare.domain.enums.Enums.DeviceCommandStatus;
import ao.autocare.domain.enums.Enums.LockReasonCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Um comando pedido a um aparelho — na prática, o bloqueio do motor.
 *
 * <p>Guarda o percurso completo do pedido: quem pediu, porquê, quem aprovou,
 * onde estava a viatura no momento do pedido e no momento do envio, e o que o
 * aparelho respondeu. Uma auditoria a um bloqueio tem de conseguir reconstruir
 * a decisão, não apenas ver que ela aconteceu.
 */
@Getter
@Setter
@Entity
@Table(name = "device_commands")
public class DeviceCommand extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private GpsDevice device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeviceCommandKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeviceCommandStatus status = DeviceCommandStatus.PENDING_APPROVAL;

    @Column(nullable = false, length = 500)
    private String reason;

    /** Categoria do motivo, além do texto livre. Permite filtrar e contar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", length = 30)
    private LockReasonCategory reasonCategory;

    /**
     * De onde veio a confirmação. {@code MANUAL} significa que alguém afirmou
     * sem prova do aparelho — e isso tem de ficar visível.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_source", length = 20)
    private CommandConfirmationSource confirmationSource;

    /**
     * O fornecedor aceitou mas o aparelho está offline: o comando ficou em fila
     * do lado dele e pode ser executado horas depois, noutro sítio.
     */
    @Column(name = "provider_queued", nullable = false)
    private boolean providerQueued;

    /** Última vez que se foi perguntar ao fornecedor se já há resposta. */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "previous_lock_state", length = 20)
    private String previousLockState;

    @Column(name = "resulting_lock_state", length = 20)
    private String resultingLockState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "request_latitude", precision = 10, scale = 7)
    private BigDecimal requestLatitude;

    @Column(name = "request_longitude", precision = 10, scale = 7)
    private BigDecimal requestLongitude;

    @Column(name = "request_speed_kph", precision = 6, scale = 2)
    private BigDecimal requestSpeedKph;

    @Column(name = "sent_latitude", precision = 10, scale = 7)
    private BigDecimal sentLatitude;

    @Column(name = "sent_longitude", precision = 10, scale = 7)
    private BigDecimal sentLongitude;

    @Column(name = "sent_speed_kph", precision = 6, scale = 2)
    private BigDecimal sentSpeedKph;

    @Column(name = "provider_command_id", length = 120)
    private String providerCommandId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Ainda pode vir a ser executado. */
    public boolean isPending() {
        return status == DeviceCommandStatus.PENDING_APPROVAL
                || status == DeviceCommandStatus.QUEUED;
    }

    public boolean isExpired(Instant now) {
        return isPending() && !expiresAt.isAfter(now);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
