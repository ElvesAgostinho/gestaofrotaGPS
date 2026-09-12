package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.PositionSource;
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

/**
 * Uma posição comunicada por um aparelho. É a tabela que mais cresce do sistema,
 * por isso não tem relações desnecessárias e é sempre consultada por
 * {@code (asset_id, recorded_at)}.
 */
@Getter
@Setter
@Entity
@Table(name = "gps_positions")
public class GpsPosition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private GpsDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    /** Viagem a que esta posição pertence, quando já foi agrupada. */
    @Column(name = "trip_id", length = 36)
    private String tripId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kph", precision = 6, scale = 2)
    private BigDecimal speedKph;

    @Column(precision = 5, scale = 1)
    private BigDecimal heading;

    @Column(name = "altitude_m", precision = 8, scale = 2)
    private BigDecimal altitudeM;

    @Column(name = "accuracy_m", precision = 8, scale = 2)
    private BigDecimal accuracyM;

    private Integer satellites;

    private Boolean ignition;

    private Boolean moving;

    @Column(name = "odometer_km", precision = 12, scale = 2)
    private BigDecimal odometerKm;

    @Column(name = "engine_hours", precision = 12, scale = 2)
    private BigDecimal engineHours;

    /** Id da posição no fornecedor (Traccar): a mesma não entra duas vezes. */
    @Column(name = "provider_position_id", length = 60)
    private String providerPositionId;

    /** Nível do depósito lido pelo sensor, em litros. */
    @Column(name = "fuel_level_liters", precision = 10, scale = 2)
    private BigDecimal fuelLevelLiters;

    @Column(name = "fuel_level_percent", precision = 5, scale = 2)
    private BigDecimal fuelLevelPercent;

    /** Distância total acumulada pelo aparelho, em km. */
    @Column(name = "total_distance_km", precision = 12, scale = 2)
    private BigDecimal totalDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionSource source = PositionSource.TELEMETRY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
