package ao.autocare.domain;

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
 * Uma pessoa dentro do fornecedor.
 *
 * <p>Um fornecedor nao tem um contacto: tem o comercial que recebe a
 * encomenda, o financeiro que quer a factura e o armazem onde se vai buscar a
 * peca. Mandar a ordem de compra para o numero errado atrasa-a uma semana, e
 * era o que um unico campo de contacto garantia.
 */
@Getter
@Setter
@Entity
@Table(name = "supplier_contacts")
public class SupplierContact extends TimestampedEntity {

    public enum Role {
        /** Quem recebe a encomenda. */
        SALES,
        /** Quem trata da factura e do pagamento. */
        FINANCE,
        /** Onde se vai buscar a peca. */
        WAREHOUSE,
        /** Quem responde a duvidas tecnicas de compatibilidade. */
        TECHNICAL,
        OTHER;

        public String label() {
            return switch (this) {
                case SALES -> "Comercial";
                case FINANCE -> "Financeiro";
                case WAREHOUSE -> "Armazem";
                case TECHNICAL -> "Tecnico";
                case OTHER -> "Outro";
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.SALES;

    @Column(length = 40)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(length = 300)
    private String note;

    /** O que recebe a ordem de compra por omissao. */
    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact = false;
}
