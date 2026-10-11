package ao.autocare.modules.purchase.dto;

import ao.autocare.domain.SupplierContact;
import ao.autocare.domain.SupplierDocument;
import ao.autocare.domain.SupplierPrice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contactos, documentos e preços de fornecedor. */
public final class PurchaseDtos {

    private PurchaseDtos() {}

    // ==== Contactos =========================================================

    public record SaveContactRequest(
            @NotBlank(message = "Indique o nome do contacto.") @Size(max = 160) String name,
            String role,
            @Size(max = 40) String phone,
            @Size(max = 190) String email,
            @Size(max = 300) String note,
            Boolean primaryContact) {}

    public record ContactView(
            String id, String name, String role, String roleLabel,
            String phone, String email, String note, boolean primaryContact) {

        public static ContactView of(SupplierContact c) {
            return new ContactView(c.getId(), c.getName(), c.getRole().name(), c.getRole().label(),
                    c.getPhone(), c.getEmail(), c.getNote(), c.isPrimaryContact());
        }
    }

    // ==== Documentos ========================================================

    /** Os documentos que uma empresa angolana pede a um fornecedor. */
    public record SaveDocumentRequest(
            @NotBlank(message = "Indique o tipo de documento.") @Size(max = 40) String kind,
            @Size(max = 80) String number,
            LocalDate issuedAt,
            LocalDate expiresAt,
            @Size(max = 36) String fileId,
            @Size(max = 300) String note) {}

    public record DocumentView(
            String id, String kind, String kindLabel, String number,
            LocalDate issuedAt, LocalDate expiresAt, String fileId, String note,
            /** Dias até expirar; negativo quando já expirou. */
            Integer diasAteExpirar,
            /** VALIDO · A_EXPIRAR (≤ 30 dias) · EXPIRADO · SEM_VALIDADE. */
            String estado) {

        public static DocumentView of(SupplierDocument d, LocalDate hoje) {
            Integer dias = d.getExpiresAt() == null
                    ? null
                    : (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, d.getExpiresAt());
            String estado = dias == null ? "SEM_VALIDADE"
                    : dias < 0 ? "EXPIRADO"
                            : dias <= 30 ? "A_EXPIRAR" : "VALIDO";
            return new DocumentView(d.getId(), d.getKind(), etiqueta(d.getKind()), d.getNumber(),
                    d.getIssuedAt(), d.getExpiresAt(), d.getFileId(), d.getNote(), dias, estado);
        }

        private static String etiqueta(String kind) {
            return switch (kind == null ? "" : kind.toUpperCase()) {
                case "ALVARA" -> "Alvará comercial";
                case "CERTIDAO_NAO_DIVIDA" -> "Certidão de não dívida";
                case "DECLARACAO_INICIO" -> "Declaração de início de actividade";
                case "NIF" -> "Cartão de contribuinte";
                case "SEGURO" -> "Seguro";
                case "CONTRATO" -> "Contrato de fornecimento";
                default -> "Outro documento";
            };
        }
    }

    // ==== Preços ============================================================

    public record SavePriceRequest(
            @NotBlank(message = "Indique a peça.") String partId,
            @Size(max = 80) String supplierPartNumber,
            @NotNull(message = "Indique o preço.") @Positive(message = "O preço tem de ser maior do que zero.")
            BigDecimal price,
            @Size(max = 3) String currency,
            BigDecimal minOrderQuantity,
            Integer leadTimeDays,
            LocalDate quotedAt,
            LocalDate validUntil,
            @Size(max = 300) String note) {}

    public record PriceView(
            String id,
            String supplierId,
            String supplierName,
            String partId,
            String partName,
            String partNumber,
            String supplierPartNumber,
            BigDecimal price,
            String currency,
            BigDecimal minOrderQuantity,
            /** O prazo desta cotação, ou o prazo médio do fornecedor. */
            Integer leadTimeDays,
            LocalDate quotedAt,
            LocalDate validUntil,
            boolean expirada,
            String note) {

        public static PriceView of(SupplierPrice p, LocalDate hoje) {
            Integer prazo = p.getLeadTimeDays();
            if (prazo == null) {
                Integer medio = p.getSupplier().getAvgLeadTimeDays();
                Integer alfandega = p.getSupplier().getCustomsDays();
                if (medio != null) {
                    prazo = alfandega != null ? medio + alfandega : medio;
                }
            }
            return new PriceView(
                    p.getId(), p.getSupplier().getId(), p.getSupplier().getName(),
                    p.getPart().getId(), p.getPart().getName(), p.getPart().getPartNumber(),
                    p.getSupplierPartNumber(), p.getPrice(), p.getCurrency(),
                    p.getMinOrderQuantity(), prazo, p.getQuotedAt(), p.getValidUntil(),
                    p.expirada(hoje), p.getNote());
        }
    }

    /**
     * Os preços de uma peça, lado a lado.
     *
     * <p>O mais barato não é sempre o melhor: um filtro 8 % mais caro que chega
     * em cinco dias em vez de vinte e um pode ser a diferença entre a máquina
     * trabalhar na segunda ou ficar parada três semanas. Por isso vão os dois
     * destaques — o mais barato e o mais rápido — e a decisão fica com quem a
     * tem de tomar.
     */
    public record ComparacaoPreco(
            String partId,
            String partName,
            List<PriceView> precos,
            String maisBaratoId,
            String maisRapidoId) {}
}
