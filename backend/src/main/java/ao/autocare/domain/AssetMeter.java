package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.MeterKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Medidor de um ativo (horímetro ou hodómetro) com o valor corrente em cache. */
@Getter
@Setter
@Entity
@Table(name = "asset_meters")
public class AssetMeter extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterKind kind;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "current_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    /** Média de utilização diária (horas/dia ou km/dia) das leituras recentes. */
    @Column(name = "daily_average", precision = 12, scale = 3)
    private BigDecimal dailyAverage;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;
}
