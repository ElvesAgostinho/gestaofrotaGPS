package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
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

/** O "relógio" de uma tarefa de plano para um ativo específico. */
@Getter
@Setter
@Entity
@Table(name = "asset_plan_tasks")
public class AssetPlanTask extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_plan_id", nullable = false)
    private AssetPlan assetPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private PlanTask task;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "system_name", length = 80)
    private String systemName;

    @Column(name = "last_done_at")
    private Instant lastDoneAt;

    @Column(name = "last_done_meter", precision = 14, scale = 2)
    private BigDecimal lastDoneMeter;

    @Column(name = "next_due_at")
    private Instant nextDueAt;

    @Column(name = "next_due_meter", precision = 14, scale = 2)
    private BigDecimal nextDueMeter;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_due_meter_kind", length = 20)
    private MeterKind nextDueMeterKind;

    /** Unidades do medidor em falta até vencer (negativo = vencida). */
    @Column(name = "remaining_meter", precision = 14, scale = 2)
    private BigDecimal remainingMeter;

    /** Dias até vencer (negativo = vencida). */
    @Column(name = "remaining_days")
    private Integer remainingDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PlanTaskStatus status = PlanTaskStatus.OK;
}
