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

    /**
     * Como acabou a devolucao.
     *
     * <p>Uma devolucao acaba sempre de uma de quatro maneiras, e todas tem de
     * caber: devolveu-se, ele substituiu, ele creditou, ou ficamos com aquilo
     * a mesma -- as vezes com desconto, as vezes porque nao vale a pena o
     * frete de volta. Sem isto, a caixa com os filtros molhados fica a um
     * canto do armazem ate alguem tropecar nela.
     */
    public enum Resolution {
        PENDING,
        RETURNED,
        REPLACED,
        CREDITED,
        /** Ficou ca: nao compensa devolver, ou aceitou-se com desconto. */
        ACCEPTED;

        public String label() {
            return switch (this) {
                case PENDING -> "Por resolver";
                case RETURNED -> "Devolvida";
                case REPLACED -> "Substituida";
                case CREDITED -> "Creditada";
                case ACCEPTED -> "Aceite assim mesmo";
            };
        }
    }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Resolution resolution = Resolution.PENDING;

    @Column(name = "resolved_at")
    private java.time.Instant resolvedAt;

    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "credit_note", length = 80)
    private String creditNote;

    @Column(name = "credit_amount", precision = 14, scale = 2)
    private BigDecimal creditAmount;

    /** Ha alguma coisa recusada ainda por resolver nesta linha. */
    public boolean porResolver() {
        return rejectedQuantity != null && rejectedQuantity.signum() > 0
                && resolution == Resolution.PENDING;
    }
}
