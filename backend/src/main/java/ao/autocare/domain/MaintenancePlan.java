package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Modelo de plano de manutenção preventiva (reutilizável por vários ativos). */
@Getter
@Setter
@Entity
@Table(name = "maintenance_plans")
public class MaintenancePlan extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlanTask> tasks = new ArrayList<>();

    public void addTask(PlanTask task) {
        task.setPlan(this);
        tasks.add(task);
    }

    /**
     * Quem elaborou e quem aprovou.
     *
     * <p>O documento de manutenção preventiva de um fabricante termina sempre
     * com «Elaborado por / Aprovado por / Data». Não é formalidade: um plano
     * que ninguém aprovou é uma sugestão, e numa auditoria de segurança a
     * pergunta é sempre a mesma — quem decidiu que esta máquina se revê às 250
     * horas e não às 500?
     */
    @Column(name = "prepared_by_label", length = 150)
    private String preparedByLabel;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "approved_by_label", length = 150)
    private String approvedByLabel;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Manual do fabricante, norma ou versão de onde o plano saiu. */
    @Column(name = "source_reference", length = 300)
    private String sourceReference;

    /** Objetivo do plano, separado da descrição: é o que a auditoria lê primeiro. */
    @Column(length = 2000)
    private String objective;

    /** Um plano por aprovar aplica-se na mesma, mas fica assinalado. */
    public boolean isApproved() {
        return approvedAt != null;
    }
}
