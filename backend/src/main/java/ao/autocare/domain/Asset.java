package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.PositionSource;
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

/** Ativo: viatura, máquina ou gerador sob gestão de manutenção. */
@Getter
@Setter
@Entity
@Table(name = "assets")
public class Asset extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    /** Código interno curto (ex.: "RE-001"). Único na organização. */
    @Column(nullable = false, length = 40)
    private String tag;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 80)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(length = 20)
    private String plate;

    /** Responsável em texto livre quando não é um utilizador (ex.: "Departamento de Manutenção"). */
    @Column(name = "responsible_label", length = 120)
    private String responsibleLabel;

    @Column(name = "acquisition_date")
    private Instant acquisitionDate;

    @Column(name = "acquisition_value", precision = 16, scale = 2)
    private BigDecimal acquisitionValue;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(columnDefinition = "text")
    private String objective;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.OPERATIONAL;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    // Posição geográfica (para o mapa)
    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(name = "position_at")
    private java.time.Instant positionAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_source", length = 20)
    private PositionSource positionSource;

    /** Capacidade do depósito, em litros. Trava abastecimentos impossíveis. */
    @jakarta.persistence.Column(name = "tank_capacity_liters", precision = 10, scale = 2)
    private java.math.BigDecimal tankCapacityLiters;

    /** Último nível do depósito lido pelo sensor do GPS, em litros. */
    @Column(name = "fuel_level_liters", precision = 10, scale = 2)
    private java.math.BigDecimal fuelLevelLiters;

    @Column(name = "fuel_level_at")
    private java.time.Instant fuelLevelAt;

    /**
     * Quanto custa uma hora com este ativo parado.
     *
     * <p>Sem isto, "esteve 14 horas parado" é uma frase; com isto é um número
     * que entra na decisão de reparar ou substituir. Fica no ativo porque um
     * gerador parado custa o que a obra deixa de produzir — e isso não se
     * adivinha a partir de nada.
     */
    @Column(name = "downtime_cost_per_hour", precision = 14, scale = 2)
    private java.math.BigDecimal downtimeCostPerHour;

    /** Limite de velocidade deste ativo, em km/h. Vazio = usa o da empresa. */
    @jakarta.persistence.Column(name = "speed_limit_kph", precision = 6, scale = 2)
    private java.math.BigDecimal speedLimitKph;
}
