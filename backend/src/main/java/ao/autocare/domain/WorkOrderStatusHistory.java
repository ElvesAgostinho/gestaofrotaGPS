package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma transicao de estado de uma ordem.
 *
 * <p>O registo de auditoria geral guarda "a ordem mudou". Isto guarda a
 * <b>sequencia</b> e quanto tempo esteve em cada estado -- que e o que permite
 * ver que uma reparacao de duas horas levou tres semanas porque a peca nao
 * chegava. Nenhuma media de tempo de reparacao conta essa historia sozinha.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_status_history")
public class WorkOrderStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private WorkOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private WorkOrderStatus toStatus;

    @Column(name = "changed_by", length = 36)
    private String changedBy;

    @Column(name = "changed_by_label", length = 160)
    private String changedByLabel;

    @Column(length = 1000)
    private String note;

    /**
     * Minutos no estado anterior, calculados na transicao.
     *
     * <p>Guardado em vez de calculado depois: se alguem corrigir uma data mais
     * tarde, o tempo que a ordem realmente esteve parada nao muda.
     */
    @Column(name = "minutes_in_previous")
    private Integer minutesInPrevious;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
