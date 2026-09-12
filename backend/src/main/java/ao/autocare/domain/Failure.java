package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Registo de uma avaria de um ativo (alimenta o MTBF). */
@Getter
@Setter
@Entity
@Table(name = "failures")
public class Failure extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id")
    private WorkOrder workOrder;

    @Column(name = "system_code", length = 30)
    private String systemCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 500)
    private String cause;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Column(name = "caused_downtime", nullable = false)
    private boolean causedDowntime = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
