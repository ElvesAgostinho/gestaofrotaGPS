package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.ScoreBand;
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
 * Pontuacao de conducao de um motorista num periodo.
 *
 * <p>Guardada, e nao calculada na hora, por duas razoes. A conta percorre todas
 * as infracoes e toda a distancia do periodo, o que e caro de repetir a cada
 * abertura de ecra. E um numero que muda sozinho entre duas consultas nao serve
 * para uma conversa com o motorista -- que e para o que isto existe.
 */
@Getter
@Setter
@Entity
@Table(name = "driver_scores")
public class DriverScore extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "distance_km", nullable = false, precision = 12, scale = 2)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "driving_minutes", nullable = false)
    private int drivingMinutes;

    @Column(name = "trip_count", nullable = false)
    private int tripCount;

    @Column(name = "overspeed_count", nullable = false)
    private int overspeedCount;

    @Column(name = "harsh_brake_count", nullable = false)
    private int harshBrakeCount;

    @Column(name = "harsh_accel_count", nullable = false)
    private int harshAccelCount;

    @Column(name = "harsh_corner_count", nullable = false)
    private int harshCornerCount;

    @Column(name = "idling_count", nullable = false)
    private int idlingCount;

    @Column(name = "night_count", nullable = false)
    private int nightCount;

    @Column(name = "total_penalty", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPenalty = BigDecimal.ZERO;

    /**
     * Nulo quando nao houve distancia suficiente para a conta significar algo.
     *
     * <p>Dar 100 a quem conduziu tres quilometros poria essa pessoa acima de
     * quem fez cinco mil com duas infracoes -- e a tabela deixava de servir.
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ScoreBand band;

    @Column(name = "insufficient_data", nullable = false)
    private boolean insufficientData;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
