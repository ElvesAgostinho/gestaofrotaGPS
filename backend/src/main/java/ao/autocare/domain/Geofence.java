package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.GeofenceKind;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Área no mapa que dispara alertas quando um ativo entra ou sai — obra, parque,
 * oficina. Círculo (centro + raio) ou polígono guardado como JSON em TEXT.
 */
@Getter
@Setter
@Entity
@Table(name = "geofences")
public class Geofence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GeofenceKind kind = GeofenceKind.CIRCLE;

    @Column(name = "center_latitude", precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @Column(name = "radius_m", precision = 10, scale = 2)
    private BigDecimal radiusM;

    /** Vértices como JSON {@code [[lat,lon],...]}. */
    @Column(columnDefinition = "TEXT")
    private String polygon;

    @Column(length = 20)
    private String color;

    /** Limite de velocidade dentro desta zona, em km/h. Vazio = zona sem limite próprio. */
    @Column(name = "speed_limit_kph", precision = 6, scale = 2)
    private BigDecimal speedLimitKph;

    @Column(name = "alert_on_enter", nullable = false)
    private boolean alertOnEnter = true;

    @Column(name = "alert_on_exit", nullable = false)
    private boolean alertOnExit = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    /** Sem ativos associados, a geocerca aplica-se a toda a frota. */
    @OneToMany(mappedBy = "geofence", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GeofenceAsset> assets = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
