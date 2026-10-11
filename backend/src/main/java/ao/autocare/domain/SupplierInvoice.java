package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma factura do fornecedor, com o ficheiro digitalizado.
 *
 * <p>Fica ligada a ordem quando ha ordem, e ao fornecedor sempre. E o que
 * permite responder a duas perguntas que hoje vivem numa pasta de papel: «ja
 * pagamos esta?» e «quanto e que esta empresa nos facturou este ano?».
 *
 * <p>O valor a pagar nao e o da factura quando ha retencao na fonte: o que se
 * transfere e o liquido, e o resto entrega-se ao Estado. Por isso a retencao
 * vai num campo proprio em vez de se descontar em silencio.
 */
@Getter
@Setter
@Entity
@Table(name = "supplier_invoices")
public class SupplierInvoice extends TimestampedEntity {

    public enum Status {
        PENDING,
        PAID,
        /** Em disputa: o valor nao bate com a encomenda ou a mercadoria. */
        DISPUTED,
        CANCELLED;

        public String label() {
            return switch (this) {
                case PENDING -> "Por pagar";
                case PAID -> "Paga";
                case DISPUTED -> "Em disputa";
                case CANCELLED -> "Anulada";
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    @Column(nullable = false, length = 80)
    private String number;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "due_at")
    private LocalDate dueAt;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "withholding_amount", precision = 14, scale = 2)
    private BigDecimal withholdingAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "paid_at")
    private LocalDate paidAt;

    @Column(name = "file_id", length = 36)
    private String fileId;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    /** O que se transfere de facto: o total menos o que se retem. */
    public BigDecimal aPagar() {
        if (amount == null) {
            return null;
        }
        return withholdingAmount == null ? amount : amount.subtract(withholdingAmount);
    }

    /** Dias ate ao vencimento; negativo quando ja venceu. */
    public Integer diasAteVencer(LocalDate hoje) {
        if (dueAt == null || status != Status.PENDING) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, dueAt);
    }
}
