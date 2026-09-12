package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.ChecklistOutcome;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Registo de uma execução de checklist contra um ativo (histórico imutável). */
@Getter
@Setter
@Entity
@Table(name = "checklist_executions")
public class ChecklistExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ChecklistTemplate template;

    @Column(name = "template_name", nullable = false, length = 160)
    private String templateName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedBy;

    @Column(name = "performed_by_label", length = 120)
    private String performedByLabel;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ChecklistOutcome outcome = ChecklistOutcome.OK;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ChecklistExecutionItem> items = new ArrayList<>();

    public void addItem(ChecklistExecutionItem item) {
        item.setExecution(this);
        items.add(item);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
