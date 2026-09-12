package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.PredictiveResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Uma medição feita ao abrigo de um programa preditivo. */
@Getter
@Setter
@Entity
@Table(name = "predictive_readings")
public class PredictiveReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private PredictiveProgram program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PredictiveResult result = PredictiveResult.NORMAL;

    /** Valor tal como o laboratório ou o aparelho o dá. */
    @Column(length = 120)
    private String measurement;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "performed_by_label", length = 120)
    private String performedByLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    /** Relatório do laboratório, termograma, espectro de vibração. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    @Column(name = "work_order_id", length = 36)
    private String workOrderId;

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
