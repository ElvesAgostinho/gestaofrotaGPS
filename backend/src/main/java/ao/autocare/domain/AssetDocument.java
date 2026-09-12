package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.DocumentKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Documento associado a um ativo: manual, apólice, livrete, certificado de
 * inspeção.
 *
 * <p>O que o distingue de uma fotografia é a <b>validade</b>. Um seguro caducado
 * imobiliza a viatura e uma inspeção fora de prazo é uma multa à espera de
 * acontecer, por isso o sistema vigia estas datas e avisa. Documentos sem data
 * de validade (um manual, uma fatura) simplesmente não caducam.
 */
@Getter
@Setter
@Entity
@Table(name = "asset_documents")
public class AssetDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentKind kind = DocumentKind.OTHER;

    @Column(nullable = false, length = 200)
    private String title;

    /** Número da apólice, do certificado, da licença. */
    @Column(length = 120)
    private String reference;

    @Column(length = 160)
    private String issuer;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private StoredFile file;

    @Column(length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Um documento sem data de validade nunca caduca. */
    public boolean tracksExpiry() {
        return expiresAt != null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
