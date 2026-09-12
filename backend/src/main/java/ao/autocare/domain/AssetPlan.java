package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Atribuição de um plano de manutenção a um ativo. */
@Getter
@Setter
@Entity
@Table(name = "asset_plans")
public class AssetPlan extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MaintenancePlan plan;

    @Column(name = "plan_name", nullable = false, length = 160)
    private String planName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @OneToMany(mappedBy = "assetPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssetPlanTask> tasks = new ArrayList<>();

    public void addTask(AssetPlanTask task) {
        task.setAssetPlan(this);
        tasks.add(task);
    }
}
