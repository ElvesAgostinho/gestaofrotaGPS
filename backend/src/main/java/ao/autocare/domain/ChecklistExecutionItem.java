package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.ChecklistItemResult;
import ao.autocare.domain.enums.Enums.VerificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Resultado de um item numa execução de checklist (cópia do item do modelo). */
@Getter
@Setter
@Entity
@Table(name = "checklist_execution_items")
public class ChecklistExecutionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private ChecklistExecution execution;

    @Column(name = "item_text", nullable = false, length = 300)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VerificationType verification = VerificationType.VERIFY;

    @Column(name = "is_critical", nullable = false)
    private boolean critical = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChecklistItemResult result = ChecklistItemResult.OK;

    @Column(length = 300)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
