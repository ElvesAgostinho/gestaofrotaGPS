package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Um pneu: uma peça com vida própria, que entra numa posição da viatura a um
 * contador, se mede (pressão, sulco) e sai a outro contador — e só aí se sabe
 * quanto custou por quilómetro.
 */
@Getter
@Setter
@Entity
@Table(name = "tyres")
public class Tyre extends TimestampedEntity {

    public enum Status {
        /** Montado numa viatura. */
        INSTALLED,
        /** Desmontado mas aproveitável (recauchutagem, reserva). */
        STOCK,
        /** Fim de vida. */
        RETIRED
    }

    public enum RemovalReason {
        WORN, DAMAGED, PUNCTURE, ROTATION, RETREAD, OTHER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    /** FE, FD, TE1, TD1, TE2, TD2 … ; nulo quando está em stock. */
    @Column(length = 12)
    private String position;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String model;

    @Column(length = 40)
    private String size;

    @Column(name = "serial_number", length = 80)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.INSTALLED;

    @Column(precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "installed_meter", precision = 14, scale = 2)
    private BigDecimal installedMeter;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_meter", precision = 14, scale = 2)
    private BigDecimal removedMeter;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 30)
    private RemovalReason removalReason;

    @Column(name = "target_pressure", precision = 5, scale = 2)
    private BigDecimal targetPressure;

    @Column(name = "last_pressure", precision = 5, scale = 2)
    private BigDecimal lastPressure;

    @Column(name = "last_tread_mm", precision = 5, scale = 2)
    private BigDecimal lastTreadMm;

    /** Sulco mínimo legal/da empresa; abaixo disto o pneu está a pedir substituição. */
    @Column(name = "min_tread_mm", nullable = false, precision = 5, scale = 2)
    private BigDecimal minTreadMm = new BigDecimal("3.0");

    @Column(name = "last_measured_at")
    private Instant lastMeasuredAt;

    @Column(length = 1000)
    private String notes;

    /** Quilómetros (ou horas) que o pneu já fez nesta montagem, dado o contador atual. */
    public BigDecimal distanceRun(BigDecimal currentMeter) {
        BigDecimal fim = removedMeter != null ? removedMeter : currentMeter;
        if (installedMeter == null || fim == null) {
            return null;
        }
        BigDecimal d = fim.subtract(installedMeter);
        return d.signum() < 0 ? BigDecimal.ZERO : d;
    }

    /** Custo por km/hora, quando se sabe o custo e a distância. */
    public BigDecimal costPerUnit(BigDecimal currentMeter) {
        BigDecimal d = distanceRun(currentMeter);
        if (cost == null || d == null || d.signum() <= 0) {
            return null;
        }
        return cost.divide(d, 4, RoundingMode.HALF_UP);
    }

    /** Abaixo do sulco mínimo, ou muito abaixo da pressão-alvo (−20 %). */
    public String alerta() {
        if (status != Status.INSTALLED) {
            return null;
        }
        if (lastTreadMm != null && minTreadMm != null && lastTreadMm.compareTo(minTreadMm) < 0) {
            return "Sulco abaixo do mínimo (" + lastTreadMm.stripTrailingZeros().toPlainString().replace('.', ',')
                    + " mm < " + minTreadMm.stripTrailingZeros().toPlainString().replace('.', ',') + " mm)";
        }
        if (lastPressure != null && targetPressure != null && targetPressure.signum() > 0
                && lastPressure.compareTo(targetPressure.multiply(new BigDecimal("0.80"))) < 0) {
            return "Pressão baixa (" + lastPressure.stripTrailingZeros().toPlainString().replace('.', ',')
                    + " bar, alvo " + targetPressure.stripTrailingZeros().toPlainString().replace('.', ',') + " bar)";
        }
        return null;
    }
}
