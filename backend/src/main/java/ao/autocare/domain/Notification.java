package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.EmailState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Um aviso dirigido a uma pessoa.
 *
 * <p>{@code sourceKind} + {@code sourceId} identificam o que deu origem ao
 * aviso (uma tarefa vencida, um alerta de velocidade). O índice único sobre
 * utilizador + origem é o que impede o agendador de repetir a mesma
 * notificação a cada passagem.
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertCategory category = AlertCategory.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity = AlertSeverity.INFO;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "source_kind", length = 40)
    private String sourceKind;

    /** Chave lógica da origem, não uma chave estrangeira: pode ser composta. */
    @Column(name = "source_id", length = 80)
    private String sourceId;

    @Column(length = 300)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_state", nullable = false, length = 20)
    private EmailState emailState = EmailState.NOT_REQUESTED;

    @Column(name = "email_at")
    private Instant emailAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isRead() {
        return readAt != null;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
