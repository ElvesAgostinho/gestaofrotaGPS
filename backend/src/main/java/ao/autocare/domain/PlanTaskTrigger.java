package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Gatilho de uma tarefa: por medidor (cada N h/km) ou por calendário (cada N dias).
 * Uma tarefa pode ter vários — vale o que ocorrer primeiro.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_task_triggers")
public class PlanTaskTrigger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private PlanTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private PlanTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_kind", length = 20)
    private MeterKind meterKind;

    @Column(name = "interval_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal intervalValue;

    @Column(name = "tolerance_value", precision = 12, scale = 2)
    private BigDecimal toleranceValue;
}
