package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma linha de uma encomenda.
 *
 * <p>A descricao vai em texto e nao so por referencia ao armazem: compra-se
 * coisas que ainda nao estao la, e uma encomenda que so aceitasse artigos
 * registados obrigava a inventar um artigo para comprar um parafuso.
 *
 * <p>A referencia DELE tambem vai: e com ela que o armazem do fornecedor
 * encontra a peca, e e por nao ir que chega a peca errada.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(length = 300)
    private String specification;

    @Column(name = "supplier_part_number", length = 80)
    private String supplierPartNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    /** Para que equipamento e: liga a compra ao custo daquela viatura. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "asset_tag", length = 40)
    private String assetTag;

    @Column(name = "received_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Quanto falta receber desta linha. */
    public BigDecimal porReceber() {
        BigDecimal falta = quantity.subtract(
                receivedQuantity == null ? BigDecimal.ZERO : receivedQuantity);
        return falta.signum() < 0 ? BigDecimal.ZERO : falta;
    }

    void recalcular() {
        lineTotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
