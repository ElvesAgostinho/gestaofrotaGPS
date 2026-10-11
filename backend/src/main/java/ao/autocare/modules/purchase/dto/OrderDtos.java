package ao.autocare.modules.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ordens de compra. */
public final class OrderDtos {

    private OrderDtos() {}

    // ==== Entrada ===========================================================

    public record LinhaRequest(
            String partId,
            @NotBlank(message = "Indique o que se está a comprar.") @Size(max = 300) String description,
            @Size(max = 300) String specification,
            @Size(max = 80) String supplierPartNumber,
            @NotNull @Positive(message = "A quantidade tem de ser maior do que zero.")
            BigDecimal quantity,
            @Size(max = 20) String unit,
            BigDecimal unitPrice,
            String assetId) {}

    public record OrdemRequest(
            @NotBlank(message = "Escolha o fornecedor.") String supplierId,
            LocalDate expectedAt,
            @Size(max = 400) String deliveryAddress,
            @Size(max = 2000) String notes,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            List<LinhaRequest> lines) {}

    public record DecisaoRequest(@Size(max = 500) String motivo) {}

    public record EnvioRequest(
            @Size(max = 300) String para,
            @Size(max = 2000) String mensagem) {}

    public record ConfiguracaoRequest(
            BigDecimal autoApproveBelow,
            BigDecimal ownerApprovalAbove,
            @Size(max = 3) String currency,
            @Size(max = 400) String deliveryAddress,
            @Size(max = 200) String deliveryContact,
            @Size(max = 2000) String terms) {}

    // ==== Saída =============================================================

    public record LinhaView(
            String id,
            String partId,
            String description,
            String specification,
            String supplierPartNumber,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String assetId,
            String assetTag,
            BigDecimal receivedQuantity,
            BigDecimal porReceber) {}

    public record EventoView(String kind, String rotulo, String note, String byLabel, Instant at) {}

    /**
     * O que falta para a ordem avançar.
     *
     * <p>Em vez de o ecrã adivinhar que botões mostrar — e ficar
     * desactualizado à primeira regra nova —, o servidor diz o que esta pessoa
     * pode fazer com esta ordem, agora.
     */
    public record Podem(
            boolean editar,
            boolean submeter,
            boolean aprovar,
            boolean rejeitar,
            boolean enviar,
            boolean confirmar,
            boolean anular,
            /** Porque é que não pode aprovar, quando não pode. */
            String porqueNaoAprova) {}

    public record OrdemView(
            String id,
            String number,
            String supplierId,
            String supplierName,
            String supplierTaxId,
            String supplierEmail,
            String supplierIban,
            String supplierBank,
            String paymentTerms,
            String status,
            String statusLabel,
            String currency,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal withholdingAmount,
            BigDecimal total,
            BigDecimal payable,
            LocalDate expectedAt,
            String deliveryAddress,
            String notes,
            String createdByLabel,
            Instant createdAt,
            String approvedByLabel,
            Instant approvedAt,
            String rejectedReason,
            Instant sentAt,
            String sentTo,
            Instant confirmedAt,
            List<LinhaView> lines,
            List<EventoView> eventos,
            Podem podem,
            /** Quem tem de aprovar esta ordem, pelo valor que tem. */
            String quemAprova,
            /** O que esta ordem faz ao orçamento do ano, quando há orçamento. */
            String impactoNoOrcamento) {}

    public record ConfiguracaoView(
            BigDecimal autoApproveBelow,
            BigDecimal ownerApprovalAbove,
            String currency,
            String deliveryAddress,
            String deliveryContact,
            String terms) {}
}
