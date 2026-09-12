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

    /** Garantia que esta oficina costuma dar, em meses. */
    @Column(name = "default_warranty_months")
    private Integer defaultWarrantyMonths;

    @Column(length = 2000)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
