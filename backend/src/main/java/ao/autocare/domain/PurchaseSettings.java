package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Os limites de aprovacao de compras, por empresa.
 *
 * <p>Dois numeros, e e o que impede a aprovacao de ser inutil nos dois
 * extremos. Numa empresa de tres pessoas, obrigar a aprovar um filtro de 8 000
 * Kz faz com que ninguem use o sistema; numa de trezentas, deixar passar uma
 * encomenda de dez milhoes sem assinatura faz com que o sistema nao sirva para
 * nada.
 *
 * <p>Abaixo de {@code autoApproveBelow} quem cria aprova. Acima de
 * {@code ownerApprovalAbove} so o dono aprova. Entre os dois, basta quem gere.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_settings")
public class PurchaseSettings {

    @Id
    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Column(name = "auto_approve_below", precision = 14, scale = 2)
    private BigDecimal autoApproveBelow;

    @Column(name = "owner_approval_above", precision = 14, scale = 2)
    private BigDecimal ownerApprovalAbove;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(name = "delivery_address", length = 400)
    private String deliveryAddress;

    @Column(name = "delivery_contact", length = 200)
    private String deliveryContact;

    /** Condicoes gerais de compra, impressas no rodape da ordem. */
    @Column(length = 2000)
    private String terms;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (createdAt == null) {
            createdAt = agora;
        }
        updatedAt = agora;
    }

    @PreUpdate
    void aoActualizar() {
        updatedAt = Instant.now();
    }
}
