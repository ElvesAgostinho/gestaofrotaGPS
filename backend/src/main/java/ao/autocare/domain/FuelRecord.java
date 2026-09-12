package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.FuelSource;
import ao.autocare.domain.enums.Enums.FuelType;
import ao.autocare.domain.enums.Enums.MeterKind;
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
 * Um abastecimento.
 *
 * <p>O consumo é calculado no momento do registo e guardado aqui, em vez de ser
 * recalculado a cada leitura. Assim o histórico não muda quando alguém corrige
 * um abastecimento antigo — o que já foi analisado continua a dizer o mesmo.
 */
@Getter
@Setter
@Entity
@Table(name = "fuel_records")
public class FuelRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "filled_at", nullable = false)
    private Instant filledAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal liters;

    @Column(name = "price_per_liter", precision = 14, scale = 2)
    private BigDecimal pricePerLiter;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_kind", length = 20)
    private MeterKind meterKind;

    /**
     * Depósito cheio. Só entre dois enchimentos completos se sabe exatamente
     * quanto foi gasto no percurso — um abastecimento parcial não fecha a conta.
     */
    @Column(name = "full_tank", nullable = false)
    private boolean fullTank = true;

    @Column(length = 160)
    private String station;

    // ---- quando o lançamento vem do sensor ---------------------------------

    /** A posição em que o sensor viu o depósito subir. */
    @Column(name = "sensor_position_id", length = 36)
    private String sensorPositionId;

    @Column(name = "sensor_level_before", precision = 10, scale = 2)
    private BigDecimal sensorLevelBefore;

    @Column(name = "sensor_level_after", precision = 10, scale = 2)
    private BigDecimal sensorLevelAfter;

    @Column(name = "driver_label", length = 120)
    private String driverLabel;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_file_id")
    private StoredFile receipt;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FuelSource source = FuelSource.MANUAL;

    /** Litros por 100 km, ou litros por hora, conforme o medidor do ativo. */
    @Column(precision = 10, scale = 3)
    private BigDecimal consumption;

    @Column(name = "consumption_unit", length = 20)
    private String consumptionUnit;

    /** Quilómetros ou horas percorridos desde o enchimento completo anterior. */
    @Column(name = "distance_or_hours", precision = 12, scale = 2)
    private BigDecimal distanceOrHours;

    // ---- Controlo de consumo (Fatia 15) -----------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Location branch;

    /** Onde o abastecimento diz ter acontecido. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Resultado do cruzamento com a posicao real da viatura.
     *
     * <p>NULO significa "nao foi possivel verificar" -- sem aparelho, ou sem
     * posicao proxima daquele momento. E diferente de "verificado e esta bem",
     * e a diferenca tem de chegar ao ecra: mostrar tudo a verde quando nao se
     * verificou nada e pior do que nao ter verificacao nenhuma.
     */
    @Column(name = "gps_verified")
    private Boolean gpsVerified;

    @Column(name = "gps_distance_m", precision = 12, scale = 2)
    private BigDecimal gpsDistanceM;

    @Column(name = "gps_checked_at")
    private Instant gpsCheckedAt;

    /** Distancia que o GPS viu desde o abastecimento anterior. */
    @Column(name = "gps_distance_km", precision = 12, scale = 3)
    private BigDecimal gpsDistanceKm;

    @Column(name = "card_number", length = 40)
    private String cardNumber;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", length = 20)
    private FuelType fuelType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

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
