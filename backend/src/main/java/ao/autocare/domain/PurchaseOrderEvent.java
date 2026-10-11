package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * O que aconteceu a uma encomenda, e por obra de quem.
 *
 * <p>Sem isto, «quem autorizou isto?» nao tem resposta -- e essa e a primeira
 * pergunta que alguem faz quando chega uma factura que ninguem esperava.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_order_events")
public class PurchaseOrderEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    /** CREATED, SUBMITTED, APPROVED, REJECTED, SENT, CONFIRMED, CANCELLED, NOTE. */
    @Column(nullable = false, length = 30)
    private String kind;

    @Column(length = 500)
    private String note;

    @Column(name = "by_user", length = 36)
    private String byUser;

    @Column(name = "by_label", length = 160)
    private String byLabel;

    @Column(nullable = false)
    private Instant at = Instant.now();
}
