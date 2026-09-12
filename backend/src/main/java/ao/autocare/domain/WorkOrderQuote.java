package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Orcamento de uma oficina para uma ordem.
 *
 * <p>Varios por ordem, de propósito: comparar propostas antes de decidir e o
 * que uma empresa faz. Um orcamento unico embutido na ordem obrigaria a apagar
 * o anterior para registar o seguinte -- e a comparacao deixava de existir.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_quotes")
public class WorkOrderQuote extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    /** Nome livre, para orcamentos de quem ainda nao esta registado. */
    @Column(name = "supplier_label", length = 200)
    private String supplierLabel;

    @Column(name = "quote_number", length = 60)
    private String quoteNumber;

    @Column(name = "quoted_at")
    private Instant quotedAt;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "parts_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal partsAmount = BigDecimal.ZERO;

    @Column(name = "labor_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal laborAmount = BigDecimal.ZERO;

    @Column(name = "other_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal otherAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    /** O escolhido. So um por ordem -- o servico garante-o. */
    @Column(name = "is_selected", nullable = false)
    private boolean selected;

    @Column(name = "file_id", length = 36)
    private String fileId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    /** Soma das parcelas, menos desconto, mais imposto. */
    public BigDecimal computeTotal() {
        return partsAmount.add(laborAmount).add(otherAmount)
                .subtract(discountAmount).add(taxAmount)
                .max(BigDecimal.ZERO);
    }

    public boolean isExpired(Instant moment) {
        return validUntil != null && moment != null && moment.isAfter(validUntil);
    }
}
