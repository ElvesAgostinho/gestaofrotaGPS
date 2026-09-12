package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PredictiveTechnique;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Monitorização de condição de um ativo por uma técnica (vibração, termografia,
 * análise de óleo). Ao contrário do plano preventivo, que conta horas de
 * trabalho, aqui a agenda é sempre de calendário: o óleo envelhece com o tempo e
 * a termografia procura defeitos que aparecem sem aviso.
 */
@Getter
@Setter
@Entity
@Table(name = "predictive_programs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "technique"}))
public class PredictiveProgram extends BaseEntity {

    /** Antecedência mínima com que se avisa que está a chegar a altura. */
    private static final int MIN_WARNING_DAYS = 3;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PredictiveTechnique technique;

    @Column(name = "frequency_months", nullable = false)
    private int frequencyMonths;

    @Column(length = 300)
    private String components;

    @Column(length = 300)
    private String goal;

    @Column(name = "responsible_label", length = 120)
    private String responsibleLabel;

    @Column(name = "last_done_at")
    private Instant lastDoneAt;

    @Column(name = "next_due_at")
    private Instant nextDueAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Estado face à data prevista, calculado à leitura.
     *
     * <p>A antecedência do aviso é proporcional: 10 % do intervalo, nunca menos
     * de três dias. Avisar com sete dias uma análise mensal seria cedo demais;
     * avisar com sete dias uma análise anual seria tarde demais.
     */
    public PlanTaskStatus statusAt(Instant now) {
        if (nextDueAt == null) {
            return PlanTaskStatus.OK;
        }
        if (!nextDueAt.isAfter(now)) {
            return PlanTaskStatus.OVERDUE;
        }
        long remainingDays = Duration.between(now, nextDueAt).toDays();
        long warningDays = Math.max(MIN_WARNING_DAYS, Math.round(frequencyMonths * 30 * 0.10));
        return remainingDays <= warningDays ? PlanTaskStatus.DUE_SOON : PlanTaskStatus.OK;
    }

    /** Dias que faltam (negativo se já passou). */
    public Long remainingDays(Instant now) {
        return nextDueAt == null ? null : Duration.between(now, nextDueAt).toDays();
    }

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
