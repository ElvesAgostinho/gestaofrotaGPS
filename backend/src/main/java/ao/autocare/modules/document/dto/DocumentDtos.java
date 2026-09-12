package ao.autocare.modules.document.dto;

import ao.autocare.common.ExpiryCalculator;
import ao.autocare.domain.AssetDocument;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DocumentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Pedidos e respostas dos documentos do ativo. */
public final class DocumentDtos {

    private DocumentDtos() {}

    public record SaveDocumentRequest(
            DocumentKind kind,
            @NotBlank(message = "Dê um título ao documento.")
            @Size(max = 200) String title,
            @Size(max = 120) String reference,
            @Size(max = 160) String issuer,
            Instant issuedAt,
            /** Sem data de validade o documento não caduca (um manual, uma fatura). */
            Instant expiresAt,
            /** Ficheiro já carregado. Para carregar um novo, use o envio multipart. */
            String fileId,
            @Size(max = 2000) String notes) {}

    /**
     * Estado de validade.
     *
     * <ul>
     *   <li>{@code SEM_VALIDADE} — não caduca;</li>
     *   <li>{@code VALIDO} — falta mais de um mês;</li>
     *   <li>{@code A_CADUCAR} — falta um mês ou menos;</li>
     *   <li>{@code CADUCADO} — a data já passou.</li>
     * </ul>
     */
    public enum ExpiryState { SEM_VALIDADE, VALIDO, A_CADUCAR, CADUCADO }

    public record DocumentView(
            String id,
            String assetId,
            String assetTag,
            String assetName,
            DocumentKind kind,
            String kindLabel,
            String title,
            String reference,
            String issuer,
            Instant issuedAt,
            Instant expiresAt,
            ExpiryState state,
            String expiryLabel,
            Long daysRemaining,
            AlertSeverity severity,
            String fileUrl,
            String fileName,
            String notes,
            Instant createdAt) {

        public static DocumentView of(AssetDocument d, String fileUrl, Instant now) {
            ExpiryCalculator.ExpiryStatus status =
                    ExpiryCalculator.status(d.getExpiresAt(), now);
            return new DocumentView(
                    d.getId(), d.getAsset().getId(), d.getAsset().getTag(), d.getAsset().getName(),
                    d.getKind(), d.getKind().label(), d.getTitle(), d.getReference(),
                    d.getIssuer(), d.getIssuedAt(), d.getExpiresAt(),
                    state(status), status != null ? status.label() : null,
                    status != null ? status.daysRemaining() : null,
                    status != null ? status.severity() : null,
                    fileUrl,
                    d.getFile() != null ? d.getFile().getOriginalName() : null,
                    d.getNotes(), d.getCreatedAt());
        }

        private static ExpiryState state(ExpiryCalculator.ExpiryStatus status) {
            if (status == null) {
                return ExpiryState.SEM_VALIDADE;
            }
            if (status.expired()) {
                return ExpiryState.CADUCADO;
            }
            return status.daysRemaining() <= 30 ? ExpiryState.A_CADUCAR : ExpiryState.VALIDO;
        }
    }

    /** Tipo de documento disponível, para preencher listas na interface. */
    public record KindView(DocumentKind code, String label) {}
}
