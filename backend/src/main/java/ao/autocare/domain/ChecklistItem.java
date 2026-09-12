package ao.autocare.domain;

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

/** Item de um modelo de checklist. */
@Getter
@Setter
@Entity
@Table(name = "checklist_items")
public class ChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    @Column(name = "item_text", nullable = false, length = 300)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VerificationType verification = VerificationType.VERIFY;

    @Column(name = "is_critical", nullable = false)
    private boolean critical = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
