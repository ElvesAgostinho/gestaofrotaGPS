package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
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

/**
 * Combustivel que nao bate certo.
 *
 * <p>Cada linha tem de responder a tres perguntas: o que esta mal, quanto vale
 * isso em litros e em dinheiro, e o que foi feito a respeito. Sem a segunda,
 * seria mais um ecra de avisos que ninguem abre -- e o dinheiro em risco e o
 * que poe uma direccao a olhar para o assunto.
 *
 * <p>Nenhuma destas regras prova furto sozinha. Um consumo alto tanto pode ser
 * mangueira na calha como filtro entupido, terreno pesado ou um horimetro mal
 * copiado. O texto pede verificacao; a acusacao e de quem investiga.
 */
@Getter
@Setter
@Entity
@Table(name = "fuel_anomalies")
public class FuelAnomaly extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_record_id")
    private FuelRecord fuelRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FuelAnomalyKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity = AlertSeverity.WARNING;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "expected_value", precision = 14, scale = 3)
    private BigDecimal expectedValue;

    @Column(name = "observed_value", precision = 14, scale = 3)
    private BigDecimal observedValue;

    @Column(length = 20)
    private String unit;

    /** Litros que a explicacao nao cobre. */
    @Column(name = "liters_at_risk", precision = 12, scale = 2)
    private BigDecimal litersAtRisk;

    /** O mesmo em dinheiro, ao preco praticado no abastecimento. */
    @Column(name = "cost_at_risk", precision = 16, scale = 2)
    private BigDecimal costAtRisk;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnomalyStatus status = AnomalyStatus.OPEN;

    @Column(length = 1000)
    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    public boolean isOpen() {
        return status == AnomalyStatus.OPEN;
    }
}
