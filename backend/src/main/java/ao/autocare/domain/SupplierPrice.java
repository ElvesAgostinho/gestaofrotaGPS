package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Quanto custa esta peca neste fornecedor, e em quantos dias chega.
 *
 * <p>E o elo que faltava. A lista de compras sabia que faltavam quatro filtros;
 * nao sabia quanto custavam nem a quem pedir, e a ordem de compra nao tinha
 * preco nenhum para levar. Com isto, a mesma linha passa a ler-se «4 filtros --
 * Auto Pecas 8 500 Kz/21 dias, Casa do Camiao 9 200 Kz/5 dias», que e a
 * decisao que o gestor tem de tomar.
 *
 * <p>O prazo esta aqui e nao so no artigo de proposito: o prazo e DO
 * FORNECEDOR. O mesmo filtro chega em cinco dias no Rangel e em tres semanas
 * se vier de fora -- e e esse numero que decide quando se encomenda.
 */
@Getter
@Setter
@Entity
@Table(name = "supplier_prices")
public class SupplierPrice extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    /** A referencia dele, que raramente e a nossa: vai escrita na encomenda. */
    @Column(name = "supplier_part_number", length = 80)
    private String supplierPartNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    /** Pedir 2 quando o minimo dele e 10 e um pedido que volta. */
    @Column(name = "min_order_quantity", precision = 12, scale = 2)
    private BigDecimal minOrderQuantity;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "quoted_at")
    private LocalDate quotedAt;

    /** Passada esta data o preco e historico, e o painel diz isso. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(length = 300)
    private String note;

    /** Uma cotacao fora de validade nao se apresenta como preco firme. */
    public boolean expirada(LocalDate hoje) {
        return validUntil != null && validUntil.isBefore(hoje);
    }
}
