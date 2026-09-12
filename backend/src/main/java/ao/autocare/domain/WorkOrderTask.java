package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Tarefa a executar numa Ordem de Manutenção (de um plano ou ad-hoc). */
@Getter
@Setter
@Entity
@Table(name = "work_order_tasks")
public class WorkOrderTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    /** Tarefa de plano de origem (se preventiva) — ao concluir, repõe o relógio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_plan_task_id")
    private AssetPlanTask assetPlanTask;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "system_name", length = 80)
    private String systemName;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "is_done", nullable = false)
    private boolean done = false;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
