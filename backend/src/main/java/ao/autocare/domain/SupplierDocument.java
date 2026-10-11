package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Um documento do fornecedor, com validade.
 *
 * <p>Alvara, certidao de nao divida, declaracao de inicio de actividade. Nao
 * esta aqui por burocracia: comprar a quem tem a certidao caducada e um
 * problema que so aparece meses depois, na contabilidade, e ja nao tem como se
 * desfazer. O sistema ja sabe avisar de validades que vao expirar -- passa a
 * fazer o mesmo por estes.
 */
@Getter
@Setter
@Entity
@Table(name = "supplier_documents")
public class SupplierDocument extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** ALVARA, CERTIDAO_NAO_DIVIDA, DECLARACAO_INICIO, NIF, SEGURO, OUTRO. */
    @Column(nullable = false, length = 40)
    private String kind;

    @Column(length = 80)
    private String number;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** Ficheiro digitalizado, quando existe. */
    @Column(name = "file_id", length = 36)
    private String fileId;

    @Column(length = 300)
    private String note;
}
