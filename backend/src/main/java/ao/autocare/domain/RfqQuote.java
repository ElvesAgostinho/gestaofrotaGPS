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

/** O preco que um fornecedor deu a uma linha do pedido. */
@Getter
@Setter
@Entity
@Table(name = "rfq_quotes")
public class RfqQuote extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_supplier_id", nullable = false)
    private RfqSupplier rfqSupplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_line_id", nullable = false)
    private RfqLine rfqLine;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "supplier_part_number", length = 80)
    private String supplierPartNumber;

    /**
     * Nao tem esta linha.
     *
     * <p>Nao o desclassifica das outras: ha fornecedores que tem os filtros e
     * nao tem os pneus, e obrigar a escolher um so para tudo seria pagar mais
     * caro por metade da lista.
     */
    @Column(nullable = false)
    private boolean unavailable = false;

    @Column(length = 300)
    private String note;
}
