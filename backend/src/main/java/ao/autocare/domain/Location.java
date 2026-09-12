package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.LocationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Local físico ou organizacional: empresa → obra / parque de máquinas → ... */
@Getter
@Setter
@Entity
@Table(name = "locations")
public class Location extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Location parent;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationKind kind = LocationKind.SITE;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    // ---- Filial (kind = BRANCH) -------------------------------------------
    /** Centro de custo: e por aqui que a contabilidade da empresa reconcilia. */
    @Column(name = "cost_center", length = 40)
    private String costCenter;

    @Column(name = "manager_user_id", length = 36)
    private String managerUserId;

    @Column(length = 300)
    private String address;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String province;

    @Column(length = 40)
    private String phone;

    /**
     * Raio em metros para reconhecer que uma viatura esta neste local.
     *
     * <p>Evita obrigar a desenhar uma geocerca a mao para cada filial so para
     * responder a "a viatura estava mesmo aqui quando abasteceu?".
     */
    @Column(name = "radius_meters")
    private Integer radiusMeters;

    /** Tem coordenadas suficientes para se poder medir distancias a este local. */
    public boolean isLocatable() {
        return latitude != null && longitude != null;
    }
}
