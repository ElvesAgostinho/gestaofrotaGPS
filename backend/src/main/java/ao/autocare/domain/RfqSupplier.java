package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Um fornecedor a quem se pediu preco, e o que ele respondeu.
 *
 * <p>Quem nao responde tambem e uma resposta. Fica registado que lhe foi
 * pedido e que nao disse nada -- e ao fim de tres pedidos sem resposta, isso
 * aparece na classificacao dele e deixa de ser uma impressao.
 */
@Getter
@Setter
@Entity
@Table(name = "rfq_suppliers")
public class RfqSupplier extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_to", length = 300)
    private String sentTo;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** Respondeu que nao fornece. */
    @Column(nullable = false)
    private boolean declined = false;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Column(length = 500)
    private String notes;

    @Column(precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    /** O ficheiro da proposta, tal como ele a mandou. */
    @Column(name = "file_id", length = 36)
    private String fileId;

    @Column(name = "file_name", length = 300)
    private String fileName;

    /**
      * De que mensagem do correio veio esta resposta.
      *
      * <p>Nao e decoracao: e o que permite voltar ao original e ler o que ele
      * escreveu a volta do numero -- «este preco e para pagamento a pronto» e
      * uma frase que nao cabe num campo de preco e que muda tudo.
      */
    @Column(name = "source_folder", length = 190)
    private String sourceFolder;

    @Column(name = "source_uid")
    private Long sourceUid;
}
