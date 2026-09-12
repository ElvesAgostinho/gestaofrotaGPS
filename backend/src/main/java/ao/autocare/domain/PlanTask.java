package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Tarefa de um plano de manutenção, agrupada por sistema. */
@Getter
@Setter
@Entity
@Table(name = "plan_tasks")
public class PlanTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MaintenancePlan plan;

    /** Código do sistema (ENGINE, HYDRAULIC…) — livre, pode ser nulo. */
    @Column(name = "system_code", length = 30)
    private String systemCode;

    @Column(name = "system_name", length = 80)
    private String systemName;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    /** Ferramentas e materiais em texto livre. */
    @Column(columnDefinition = "text")
    private String tools;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanTaskTrigger> triggers = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanTaskPart> parts = new ArrayList<>();

    public void addTrigger(PlanTaskTrigger t) {
        t.setTask(this);
        triggers.add(t);
    }

    public void addPart(PlanTaskPart p) {
        p.setTask(this);
        parts.add(p);
    }
}
