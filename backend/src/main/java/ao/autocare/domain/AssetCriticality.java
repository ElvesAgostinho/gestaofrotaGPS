package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.CriticalityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Matriz de criticidade do ativo (bloco "CRITICIDADE DO EQUIPAMENTO" do
 * documento de referência). Impactos de 1 a 5.
 */
@Getter
@Setter
@Entity
@Table(name = "asset_criticality")
public class AssetCriticality extends TimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, unique = true)
    private Asset asset;

    @Column(name = "production_impact", nullable = false)
    private int productionImpact = 1;

    @Column(name = "safety_impact", nullable = false)
    private int safetyImpact = 1;

    @Column(name = "financial_impact", nullable = false)
    private int financialImpact = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CriticalityLevel overall = CriticalityLevel.LOW;

    /** Quando verdadeiro, {@code overall} foi definido à mão e não é recalculado. */
    @Column(name = "overall_manual", nullable = false)
    private boolean overallManual = false;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by_user_id")
    private User assessedBy;

    @Column(length = 500)
    private String notes;

    /**
     * Regra de cálculo (documentada): a criticidade geral é o pior dos três
     * impactos — 5 → CRÍTICA, 4 → ALTA, 3 → MÉDIA, 1–2 → BAIXA. Um único
     * impacto catastrófico torna o ativo crítico.
     */
    public static CriticalityLevel computeOverall(int production, int safety, int financial) {
        int worst = Math.max(production, Math.max(safety, financial));
        return switch (worst) {
            case 5 -> CriticalityLevel.CRITICAL;
            case 4 -> CriticalityLevel.HIGH;
            case 3 -> CriticalityLevel.MEDIUM;
            default -> CriticalityLevel.LOW;
        };
    }
}
