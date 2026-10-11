package ao.autocare.modules.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Pedidos de cotação e a comparação de propostas. */
public final class RfqDtos {

    private RfqDtos() {}

    // ==== Entrada ===========================================================

    public record LinhaRequest(
            String partId,
            @NotBlank(message = "Indique o que se está a pedir.") @Size(max = 300) String description,
            @Size(max = 300) String specification,
            @NotNull @Positive(message = "A quantidade tem de ser maior do que zero.")
            BigDecimal quantity,
            @Size(max = 20) String unit,
            String assetId) {}

    public record PedidoRequest(
            @NotBlank(message = "Dê um nome ao pedido.") @Size(max = 200) String title,
            LocalDate dueDate,
            @Size(max = 2000) String notes,
            List<String> supplierIds,
            List<LinhaRequest> lines) {}

    /** O preço que um fornecedor deu a uma linha. */
    public record PrecoRequest(
            @NotBlank String lineId,
            BigDecimal unitPrice,
            @Size(max = 80) String supplierPartNumber,
            Boolean unavailable,
            @Size(max = 300) String note) {}

    public record RespostaRequest(
            Integer leadTimeDays,
            LocalDate validUntil,
            @Size(max = 200) String paymentTerms,
            @Size(max = 500) String notes,
            Boolean declined,
            List<PrecoRequest> precos) {}

    public record AdjudicarRequest(
            @NotBlank(message = "Escolha o fornecedor.") String supplierId,
            @Size(max = 500) String motivo) {}

    // ==== Saída =============================================================

    public record LinhaView(
            String id,
            String partId,
            String description,
            String specification,
            BigDecimal quantity,
            String unit,
            String assetTag) {}

    public record PrecoView(
            String lineId,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String supplierPartNumber,
            boolean unavailable,
            String note,
            /** Quanto está acima do melhor preço desta linha, em percentagem. */
            Integer acimaDoMelhorPct,
            boolean melhorDaLinha) {}

    public record PropostaView(
            String id,
            String supplierId,
            String supplierName,
            Instant sentAt,
            String sentTo,
            Instant respondedAt,
            boolean declined,
            Integer leadTimeDays,
            LocalDate validUntil,
            String paymentTerms,
            String notes,
            BigDecimal total,
            String currency,
            /** Quantas das linhas pedidas é que ele cotou. */
            int linhasCotadas,
            boolean completa,
            boolean maisBarata,
            boolean maisRapida,
            List<PrecoView> precos) {}

    public record PedidoView(
            String id,
            String number,
            String title,
            String status,
            String statusLabel,
            LocalDate dueDate,
            String notes,
            String createdByLabel,
            Instant createdAt,
            String awardedOrderId,
            String awardedSupplierId,
            String awardedSupplierName,
            String awardedReason,
            Instant awardedAt,
            List<LinhaView> lines,
            List<PropostaView> propostas,
            /**
             * O que a comparação diz, em português.
             *
             * <p>«O mais barato é A, mas B entrega duas semanas antes» é a
             * frase que falta a quem decide — e é a que nunca aparece num
             * quadro de números.
             */
            String leitura,
            /** Quanto se poupa escolhendo o mais barato em vez do mais caro. */
            BigDecimal diferenca) {}
}
