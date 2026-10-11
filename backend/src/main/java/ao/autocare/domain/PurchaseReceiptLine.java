package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * O que chegou de uma linha da encomenda.
 *
 * <p>O que chega mal conta-se a parte: nao entra no stock, mas tambem nao pode
 * desaparecer do registo -- e o que se devolve, e e o que faz a diferenca
 * entre um fornecedor que entrega e um que entrega bem.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_receipt_lines")
public class PurchaseReceiptLine extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private PurchaseReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_line_id", nullable = false)
    private PurchaseOrderLine orderLine;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "rejected_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;
}
