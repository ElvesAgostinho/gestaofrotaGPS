package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.SupplierKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Oficina ou fornecedor de pecas.
 *
 * <p>O NIF nao e um campo opcional de formulario: sem ele, uma factura da
 * oficina nao se reconcilia com a contabilidade da empresa, e o custo de
 * manutencao fica num sistema e a despesa noutro.
 */
@Getter
@Setter
@Entity
@Table(name = "suppliers")
public class Supplier extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String name;

    /** Numero de Identificacao Fiscal. */
    @Column(name = "tax_id", length = 40)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierKind kind = SupplierKind.WORKSHOP;

    @Column(length = 40)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(length = 400)
    private String address;

    @Column(length = 120)
    private String city;

    @Column(name = "contact_person", length = 160)
    private String contactPerson;

    @Column(name = "payment_terms", length = 200)
    private String paymentTerms;

    /** Regime de IVA: GERAL, SIMPLIFICADO, EXCLUSAO, ISENTO. */
    @Column(name = "vat_regime", length = 30)
    private String vatRegime;

    /**
      * Retencao na fonte, em percentagem.
      *
      * <p>Em Angola a prestacao de servicos leva 6,5 % retidos na fonte: quem
      * paga retem e entrega ao Estado. Nao e um detalhe de contabilista -- e a
      * diferenca entre o valor da factura e o valor a transferir, e sem ele a
      * ordem de compra diz um numero que ninguem vai pagar.
      */
    @Column(name = "withholding_tax", precision = 5, scale = 2)
    private java.math.BigDecimal withholdingTax;

    @Column(length = 60)
    private String iban;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    /** Moeda em que factura. Vazio = a da empresa. */
    @Column(length = 3)
    private String currency;

    /** Prazo medio REAL deste fornecedor, em dias -- nao o prometido. */
    @Column(name = "avg_lead_time_days")
    private Integer avgLeadTimeDays;

    /**
      * Importa, ou tem prateleira em Luanda?
      *
      * <p>Um filtro CIF por despachar nao e o mesmo artigo, em dias, que um
      * filtro no Rangel. Para uma maquina parada, a diferenca e de semanas.
      */
    @Column(nullable = false)
    private boolean importer = false;

    /** FOB, CIF, EXW… quando importa. */
    @Column(length = 20)
    private String incoterm;

    /** Dias de despacho aduaneiro a somar ao transito. */
    @Column(name = "customs_days")
    private Integer customsDays;

    /** Valor minimo de encomenda: abaixo disto nao vale a pena pedir. */
    @Column(name = "min_order_value", precision = 14, scale = 2)
    private java.math.BigDecimal minOrderValue;

    /** Marcas que representa, separadas por virgulas. */
    @Column(length = 500)
    private String brands;

    /** Familias que fornece (TRUCK_HEAVY, GENERATOR…), separadas por virgulas. */
    @Column(length = 300)
    private String families;

    /** Garantia que esta oficina costuma dar, em meses. */
    @Column(name = "default_warranty_months")
    private Integer defaultWarrantyMonths;

    @Column(length = 2000)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
