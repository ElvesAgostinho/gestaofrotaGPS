package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Um pedido de cotacao.
 *
 * <p>Numa obra, «pediram-se tres precos» e uma frase que se diz e que ninguem
 * consegue provar. Os precos chegam por WhatsApp, por email e por telefone, e
 * a comparacao faz-se de cabeca -- quando se faz. O resultado e escolher pelo
 * que se lembra em vez de pelo que custa.
 *
 * <p>Um pedido termina sempre numa de duas coisas: uma encomenda, ou uma
 * anulacao com motivo. Se ficasse aberto para sempre, voltava a ser papel.
 */
@Getter
@Setter
@Entity
@Table(name = "rfqs")
public class Rfq extends TimestampedEntity {

    public enum Status {
        OPEN,
        AWARDED,
        CANCELLED;

        public String label() {
            return switch (this) {
                case OPEN -> "Em curso";
                case AWARDED -> "Adjudicado";
                case CANCELLED -> "Anulado";
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** PC-2026-0001. */
    @Column(nullable = false, length = 40)
    private String number;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    /** Ate quando se aceitam respostas. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 2000)
    private String notes;

    @Column(name = "awarded_order_id", length = 36)
    private String awardedOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awarded_supplier_id")
    private Supplier awardedSupplier;

    /**
     * Porque se escolheu este.
     *
     * <p>Obrigatorio quando nao se escolhe o mais barato -- e e esse o caso que
     * interessa registar. Escolher o mais caro pode ser a decisao certa (chega
     * numa semana em vez de tres), mas tem de ficar escrito porque.
     */
    @Column(name = "awarded_reason", length = 500)
    private String awardedReason;

    @Column(name = "awarded_at")
    private Instant awardedAt;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_by_label", length = 160)
    private String createdByLabel;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<RfqLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RfqSupplier> suppliers = new ArrayList<>();

    public void addLine(RfqLine l) {
        l.setRfq(this);
        lines.add(l);
    }

    public void addSupplier(RfqSupplier s) {
        s.setRfq(this);
        suppliers.add(s);
    }
}
