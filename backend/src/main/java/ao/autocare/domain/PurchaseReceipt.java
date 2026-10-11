package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma entrega: o que chegou de uma encomenda, e quando.
 *
 * <p>Recebe-se em partes porque e assim que chega -- o fornecedor manda metade
 * e promete o resto. Uma recepcao que so aceitasse tudo de uma vez obrigava a
 * mentir ou a nao registar nada, e nos dois casos o stock fica errado.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_receipts")
public class PurchaseReceipt extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    /** Para onde entrou a mercadoria. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** A guia de remessa que veio com a mercadoria. */
    @Column(name = "delivery_note", length = 80)
    private String deliveryNote;

    @Column(name = "received_by", length = 36)
    private String receivedBy;

    @Column(name = "received_by_label", length = 160)
    private String receivedByLabel;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReceiptLine> lines = new ArrayList<>();

    public void addLine(PurchaseReceiptLine l) {
        l.setReceipt(this);
        lines.add(l);
    }
}
