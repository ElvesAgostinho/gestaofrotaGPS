package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
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
 * Um alerta de telemetria é um <b>episódio</b>, não uma amostra: enquanto o
 * excesso de velocidade dura, ou enquanto o aparelho continua calado, é o mesmo
 * registo que se mantém aberto e vai guardando o pico. Assim um camião em
 * excesso durante dez minutos gera um alerta, não duzentos.
 */
@Getter
@Setter
@Entity
@Table(name = "telemetry_alerts")
public class TelemetryAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private GpsDevice device;

    /** Zona cujo limite foi excedido, quando o limite veio de uma geocerca. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geofence_id")
    private Geofence geofence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TelemetryAlertKind kind;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "limit_value", precision = 10, scale = 2)
    private BigDecimal limitValue;

    @Column(name = "peak_value", precision = 10, scale = 2)
    private BigDecimal peakValue;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 400)
    private String message;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isOpen() {
        return endedAt == null;
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
