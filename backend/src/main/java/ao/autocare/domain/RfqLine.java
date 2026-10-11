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

/** Uma linha de um pedido de cotacao: o que se quer que nos cotem. */
@Getter
@Setter
@Entity
@Table(name = "rfq_lines")
public class RfqLine extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(nullable = false, length = 300)
    private String description;

    /**
     * A especificacao vai no pedido de proposito.
     *
     * <p>Pedir «oleo do motor» a tres fornecedores e receber tres precos de
     * tres oleos diferentes nao e uma comparacao. Com a norma escrita, os tres
     * cotam a mesma coisa.
     */
    @Column(length = 300)
    private String specification;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(length = 20)
    private String unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "asset_tag", length = 40)
    private String assetTag;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
