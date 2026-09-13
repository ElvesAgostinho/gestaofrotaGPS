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

/** Uma medição de um pneu: pressão e/ou sulco, a um contador, num dia. */
@Getter
@Setter
@Entity
@Table(name = "tyre_readings")
public class TyreReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tyre_id", nullable = false)
    private Tyre tyre;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Column(precision = 5, scale = 2)
    private BigDecimal pressure;

    @Column(name = "tread_mm", precision = 5, scale = 2)
    private BigDecimal treadMm;

    @Column(length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (measuredAt == null) measuredAt = createdAt;
    }
}
