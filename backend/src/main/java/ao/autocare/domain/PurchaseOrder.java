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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma encomenda a um fornecedor.
 *
 * <p>Hoje a compra acontece por telefone, e o que foi pedido fica na memória de
 * quem ligou. Quando a peça não chega, não há a quem mostrar nada: nem o que se
 * encomendou, nem o preço combinado, nem quem autorizou.
 *
 * <p>Os dados do fornecedor são <b>copiados</b> para aqui no momento em que a
 * ordem é criada. É deliberado: se amanhã o IBAN dele mudar, a ordem que se
 * enviou tem de continuar a dizer o que dizia quando saiu — um documento que
 * muda sozinho depois de enviado não serve de prova nenhuma.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends TimestampedEntity {

    /**
     * O percurso de uma encomenda.
     *
     * <p>«Enviada» e «confirmada» são estados diferentes de propósito: mandar
     * um email não é o mesmo que o fornecedor dizer que o recebeu, e confundir
     * os dois é como se descobre, duas semanas depois, que ninguém leu nada.
     */
    public enum Status {
        /** Ainda a ser escrita. */
        DRAFT,
        /** À espera de quem a tem de autorizar. */
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        /** Saiu para o fornecedor. */
        SENT,
        /** O fornecedor respondeu que a aceita. */
        CONFIRMED,
        PARTIALLY_RECEIVED,
        RECEIVED,
        CLOSED,
        CANCELLED;

        public String label() {
            return switch (this) {
                case DRAFT -> "Rascunho";
                case PENDING_APPROVAL -> "Por aprovar";
                case APPROVED -> "Aprovada";
                case REJECTED -> "Rejeitada";
                case SENT -> "Enviada";
                case CONFIRMED -> "Confirmada";
                case PARTIALLY_RECEIVED -> "Recebida em parte";
                case RECEIVED -> "Recebida";
                case CLOSED -> "Fechada";
                case CANCELLED -> "Anulada";
            };
        }

        /** Está em curso: conta para «já encomendado» e não se pode apagar. */
        public boolean emCurso() {
            return this == APPROVED || this == SENT || this == CONFIRMED
                    || this == PARTIALLY_RECEIVED;
        }

        /** Ainda se pode mexer nas linhas. */
        public boolean editavel() {
            return this == DRAFT || this == PENDING_APPROVAL || this == REJECTED;
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** OC-2026-0001 — sequencial por empresa e por ano. */
    @Column(nullable = false, length = 40)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    // ---- a fotografia do fornecedor, à data ----------------------------

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "supplier_tax_id", length = 40)
    private String supplierTaxId;

    @Column(name = "supplier_email", length = 190)
    private String supplierEmail;

    @Column(name = "supplier_iban", length = 60)
    private String supplierIban;

    @Column(name = "supplier_bank", length = 120)
    private String supplierBank;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.DRAFT;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /**
     * Retenção na fonte.
     *
     * <p>O que se retém e entrega ao Estado — e que por isso <b>não</b> vai na
     * transferência para o fornecedor. Em Angola são 6,5 % nos serviços, e
     * esquecê-lo é transferir a mais e ir pedir o dinheiro de volta.
     */
    @Column(name = "withholding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal withholdingAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** O que se transfere de facto: total menos retenção. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal payable = BigDecimal.ZERO;

    @Column(name = "expected_at")
    private LocalDate expectedAt;

    @Column(name = "delivery_address", length = 400)
    private String deliveryAddress;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_by_label", length = 160)
    private String createdByLabel;

    @Column(name = "approved_by", length = 36)
    private String approvedBy;

    @Column(name = "approved_by_label", length = 160)
    private String approvedByLabel;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_to", length = 300)
    private String sentTo;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** O pedido de cotação de que esta ordem nasceu, quando houve um. */
    @Column(name = "rfq_id", length = 36)
    private String rfqId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    public void addLine(PurchaseOrderLine l) {
        l.setOrder(this);
        lines.add(l);
    }

    /**
     * Recalcula os totais a partir das linhas.
     *
     * <p>Os totais não se guardam vindos do ecrã: um cliente que envie um total
     * que não bate com as linhas conseguiria aprovar uma ordem de dez milhões
     * com linhas de dez mil. Somam-se aqui, sempre.
     */
    public void recalcular(BigDecimal percentagemRetencao) {
        BigDecimal soma = BigDecimal.ZERO;
        for (PurchaseOrderLine l : lines) {
            l.recalcular();
            soma = soma.add(l.getLineTotal());
        }
        subtotal = soma;
        BigDecimal base = soma.subtract(discountAmount == null ? BigDecimal.ZERO : discountAmount);
        if (base.signum() < 0) {
            base = BigDecimal.ZERO;
        }
        total = base.add(taxAmount == null ? BigDecimal.ZERO : taxAmount);
        withholdingAmount = percentagemRetencao == null || percentagemRetencao.signum() <= 0
                ? BigDecimal.ZERO
                : base.multiply(percentagemRetencao)
                        .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        payable = total.subtract(withholdingAmount);
    }
}
