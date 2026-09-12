package ao.autocare.domain;

import jakarta.persistence.Column;
import ao.autocare.domain.enums.Enums.PlaceKind;
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
 * Deslocação contínua de um ativo. Abre quando o ativo começa a mover-se e
 * fecha quando fica parado tempo suficiente (ou a ignição desliga).
 */
@Getter
@Setter
@Entity
@Table(name = "trips")
public class Trip extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private GpsDevice device;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_latitude", precision = 10, scale = 7)
    private BigDecimal startLatitude;

    @Column(name = "start_longitude", precision = 10, scale = 7)
    private BigDecimal startLongitude;

    @Column(name = "end_latitude", precision = 10, scale = 7)
    private BigDecimal endLatitude;

    @Column(name = "end_longitude", precision = 10, scale = 7)
    private BigDecimal endLongitude;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "max_speed_kph", precision = 6, scale = 2)
    private BigDecimal maxSpeedKph;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "position_count", nullable = false)
    private int positionCount;

    // ---- Quem conduzia, e por onde (Fatia 14) -----------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    /**
     * Nome do sitio de partida, resolvido contra o que a empresa registou.
     *
     * <p>Sem geocodificacao externa de proposito: um servico de mapas devolveria
     * "Rua X, Luanda" para um ponto no meio do estaleiro, e quem gere a frota
     * nao reconheceria o sitio. Quando nada bate certo fica nulo e mostram-se
     * as coordenadas -- o que e a verdade.
     */
    @Column(name = "start_place_name", length = 200)
    private String startPlaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_place_kind", length = 20)
    private PlaceKind startPlaceKind;

    @Column(name = "start_place_id", length = 36)
    private String startPlaceId;

    @Column(name = "end_place_name", length = 200)
    private String endPlaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_place_kind", length = 20)
    private PlaceKind endPlaceKind;

    @Column(name = "end_place_id", length = 36)
    private String endPlaceId;

    @Column(name = "idle_minutes", nullable = false)
    private int idleMinutes;

    @Column(name = "night_minutes", nullable = false)
    private int nightMinutes;

    @Column(name = "harsh_brake_count", nullable = false)
    private int harshBrakeCount;

    @Column(name = "harsh_accel_count", nullable = false)
    private int harshAccelCount;

    @Column(name = "harsh_corner_count", nullable = false)
    private int harshCornerCount;

    @Column(name = "overspeed_count", nullable = false)
    private int overspeedCount;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(length = 200)
    private String purpose;

    @Column(length = 2000)
    private String notes;

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
