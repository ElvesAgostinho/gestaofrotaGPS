package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.MeterReadingSource;
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

/** Uma leitura de medidor registada num momento. */
@Getter
@Setter
@Entity
@Table(name = "meter_readings")
public class MeterReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_meter_id", nullable = false)
    private AssetMeter meter;

    @Column(name = "reading_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "reading_at", nullable = false)
    private Instant readingAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterReadingSource source = MeterReadingSource.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    /** Diferença face à leitura anterior (pode ser negativa se houve substituição de medidor). */
    @Column(precision = 14, scale = 2)
    private BigDecimal delta;

    @Column(nullable = false)
    private boolean flagged = false;

    @Column(name = "flag_reason", length = 200)
    private String flagReason;

    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
