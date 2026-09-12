package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    /**
     * Empresa a que esta linha diz respeito.
     *
     * <p>Nula quando não se conseguiu atribuir com certeza. A consulta filtra
     * sempre por empresa, por isso uma linha nula não aparece a ninguém — o que
     * é a falha segura: perde-se informação, nunca se mostra a empresa errada.
     */
    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(length = 400)
    private String summary;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
