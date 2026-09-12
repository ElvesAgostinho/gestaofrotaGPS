package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
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
 * Uma infracao de conducao.
 *
 * <p>Uma linha por <b>episodio</b>, nunca uma por amostra: um travao brusco e
 * um acontecimento, nao trinta leituras seguidas. A alternativa enchia a lista
 * de ruido e tornava a pontuacao refem da frequencia de reporte do aparelho --
 * o mesmo travao valeria o dobro num rastreador que reporta de 5 em 5 segundos.
 *
 * <p>Guarda o valor medido <b>e</b> o limiar ultrapassado porque uma infracao
 * tem de poder ser contestada com numeros, e nao com a palavra do sistema.
 */
@Getter
@Setter
@Entity
@Table(name = "driving_events")
public class DrivingEvent extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** Nulo quando ninguem estava atribuido ao ativo naquele instante. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /** Alerta de telemetria de origem, nos excessos de velocidade. */
    @Column(name = "source_alert_id", length = 36)
    private String sourceAlertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DrivingEventKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity = AlertSeverity.WARNING;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    @Column(name = "threshold_value", precision = 12, scale = 3)
    private BigDecimal thresholdValue;

    @Column(length = 20)
    private String unit;

    @Column(name = "speed_kph", precision = 6, scale = 2)
    private BigDecimal speedKph;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "place_name", length = 200)
    private String placeName;

    @Column(name = "penalty_points", nullable = false, precision = 6, scale = 2)
    private BigDecimal penaltyPoints = BigDecimal.ZERO;

    @Column(length = 400)
    private String description;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "dismissed_by", length = 36)
    private String dismissedBy;

    @Column(name = "dismiss_reason", length = 400)
    private String dismissReason;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** Anulada por quem a analisou: deixa de contar para a pontuacao. */
    public boolean isDismissed() {
        return dismissedAt != null;
    }
}
