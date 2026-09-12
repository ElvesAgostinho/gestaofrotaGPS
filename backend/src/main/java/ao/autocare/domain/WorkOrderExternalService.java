package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Servico feito por uma oficina de fora.
 *
 * <p>Em Angola grande parte da manutencao pesada vai para fora. Sem estas
 * linhas, o custo de uma ordem ficava sistematicamente abaixo do real -- e as
 * decisoes de reparar ou substituir um ativo eram tomadas com o numero errado.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_services")
public class WorkOrderExternalService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Column(nullable = false, length = 200)
    private String supplier;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "performed_at")
    private Instant performedAt;

    /**
     * Garantia dada pela oficina sobre este servico.
     *
     * <p>Uma avaria que volte dentro do prazo nao se paga duas vezes -- desde
     * que alguem se lembre de a invocar. E por isso que a data fica calculada
     * e guardada, em vez de ficar na cabeca de quem tratou do assunto.
     */
    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "warranty_until")
    private Instant warrantyUntil;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isUnderWarranty(Instant moment) {
        return warrantyUntil != null && moment != null && moment.isBefore(warrantyUntil);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (warrantyUntil == null && warrantyMonths != null && warrantyMonths > 0) {
            Instant base = performedAt != null ? performedAt : createdAt;
            warrantyUntil = base.plus(java.time.Duration.ofDays(30L * warrantyMonths));
        }
    }
}
