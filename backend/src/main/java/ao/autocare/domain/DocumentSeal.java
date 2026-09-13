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

/** O selo de um documento emitido: código, quem, quando, e o resumo do ficheiro. */
@Getter
@Setter
@Entity
@Table(name = "document_seals")
public class DocumentSeal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 20)
    private String code;

    /** WORK_ORDER, TRANSPORT_NOTE, ASSET_HISTORY, ASSET_SHEET … */
    @Column(nullable = false, length = 30)
    private String kind;

    @Column(name = "reference_id", length = 36)
    private String referenceId;

    /** O número humano do documento: OM-2026-000012, GT-2026-000003. */
    @Column(length = 80)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
}
