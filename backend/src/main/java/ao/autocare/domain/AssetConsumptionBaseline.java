package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Consumo normal de um ativo, medido nele proprio.
 *
 * <p>Um camiao de obra gasta o dobro de um ligeiro, e isso nao e anomalia
 * nenhuma. Comparar ativos entre si daria listas de "maiores consumidores" que
 * toda a gente ja sabe de cor e que nao dizem nada. So faz sentido comparar
 * cada ativo <b>consigo proprio</b> -- e e ai que aparece o que mudou.
 */
@Getter
@Setter
@Entity
@Table(name = "asset_consumption_baselines")
public class AssetConsumptionBaseline extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** L/100km para veiculos, L/h para maquinas e geradores. */
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal baseline;

    /**
     * Desvio padrao da amostra.
     *
     * <p>Distingue um ativo regular de um que sempre oscilou muito. Num ativo
     * instavel, 25% acima da media pode ser perfeitamente normal -- e acusar
     * nesse caso so ensina as pessoas a ignorar os avisos.
     */
    @Column(name = "std_deviation", precision = 10, scale = 3)
    private BigDecimal stdDeviation;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(precision = 10, scale = 3)
    private BigDecimal best;

    @Column(precision = 10, scale = 3)
    private BigDecimal worst;

    @Column(name = "first_sample_at")
    private Instant firstSampleAt;

    @Column(name = "last_sample_at")
    private Instant lastSampleAt;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
