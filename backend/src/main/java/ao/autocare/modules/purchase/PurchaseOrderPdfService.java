package ao.autocare.modules.purchase;

import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseOrderLine;
import ao.autocare.domain.PurchaseSettings;
import ao.autocare.modules.org.DocumentSealService;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A ordem de compra em papel.
 *
 * <p>É o documento que vai para fora da empresa: é por ele que o fornecedor
 * prepara a encomenda e que o financeiro paga. Por isso leva o timbre, o número
 * sequencial e o selo de verificação — quem o recebe consegue confirmar que foi
 * mesmo emitido por esta empresa e que não foi alterado pelo caminho.
 *
 * <p>Não é documento fiscal: quem factura é o fornecedor. Isso simplifica a
 * vida — não há certificação a tratar — mas não dispensa que o documento diga
 * tudo o que é preciso para o pagamento sair certo, e é por isso que a
 * retenção na fonte aparece separada do total. Transferir o total quando há
 * retenção é pagar a mais e ir pedir o dinheiro de volta.
 */
@Service
public class PurchaseOrderPdfService {

    /** O que o modelo precisa de ver. */
    public record Doc(
            String number,
            String statusLabel,
            String data,
            String expectedAt,
            String paymentTerms,
            String supplierName,
            String supplierTaxId,
            String supplierEmail,
            String supplierIban,
            String supplierBank,
            String deliveryAddress,
            List<Linha> lines,
            String subtotal,
            String discountAmount,
            String taxAmount,
            String withholdingAmount,
            String total,
            String payable,
            String currency,
            boolean temDesconto,
            boolean temImposto,
            boolean temRetencao,
            String notes,
            String terms,
            String createdByLabel,
            String approvedByLabel,
            String approvedAt) {}

    public record Linha(
            String description,
            String specification,
            String supplierPartNumber,
            String quantity,
            String unit,
            String unitPrice,
            String lineTotal,
            String assetTag) {}

    private final PurchaseOrderService orders;
    private final PurchaseSettingsLookup configuracoes;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public PurchaseOrderPdfService(
            PurchaseOrderService orders,
            PurchaseSettingsLookup configuracoes,
            Letterhead letterhead,
            PdfRenderer renderer) {
        this.orders = orders;
        this.configuracoes = configuracoes;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    /** As condições gerais de compra, que vivem na configuração da empresa. */
    @org.springframework.stereotype.Component
    public static class PurchaseSettingsLookup {
        private final ao.autocare.repo.PurchaseSettingsRepository repo;

        public PurchaseSettingsLookup(ao.autocare.repo.PurchaseSettingsRepository repo) {
            this.repo = repo;
        }

        PurchaseSettings de(String orgId) {
            return repo.findById(orgId).orElseGet(PurchaseSettings::new);
        }
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String orderId, DocumentSealService.Selo selo) {
        PurchaseOrder o = orders.entidade(orgId, orderId);
        PurchaseSettings cfg = configuracoes.de(orgId);

        List<Linha> linhas = o.getLines().stream()
                .map(PurchaseOrderPdfService::linha)
                .toList();

        Doc doc = new Doc(
                o.getNumber(),
                o.getStatus().label(),
                o.getCreatedAt() != null ? PdfRenderer.data(o.getCreatedAt()) : null,
                data(o.getExpectedAt()),
                o.getPaymentTerms(),
                o.getSupplierName(), o.getSupplierTaxId(), o.getSupplierEmail(),
                o.getSupplierIban(), o.getSupplierBank(),
                o.getDeliveryAddress(),
                linhas,
                numero(o.getSubtotal()), numero(o.getDiscountAmount()), numero(o.getTaxAmount()),
                numero(o.getWithholdingAmount()), numero(o.getTotal()), numero(o.getPayable()),
                o.getCurrency(),
                positivo(o.getDiscountAmount()), positivo(o.getTaxAmount()),
                positivo(o.getWithholdingAmount()),
                o.getNotes(), cfg.getTerms(),
                o.getCreatedByLabel(), o.getApprovedByLabel(),
                o.getApprovedAt() != null ? PdfRenderer.data(o.getApprovedAt()) : null);

        return renderer.render("purchase-order", letterhead.of(o.getOrganization()), "o", doc, selo);
    }

    private static Linha linha(PurchaseOrderLine l) {
        return new Linha(
                l.getDescription(), l.getSpecification(), l.getSupplierPartNumber(),
                numero(l.getQuantity()), l.getUnit(),
                numero(l.getUnitPrice()), numero(l.getLineTotal()), l.getAssetTag());
    }

    private static String numero(BigDecimal v) {
        return v == null ? "0,00" : PdfRenderer.numero(v, 2);
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static String data(LocalDate d) {
        return d == null ? null : d.toString();
    }
}
