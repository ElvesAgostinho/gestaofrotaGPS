package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseReceipt;
import ao.autocare.domain.PurchaseReceiptLine;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.PurchaseReceiptLineRepository;
import ao.autocare.repo.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que veio mal, e o que lhe aconteceu a seguir.
 *
 * <p>A quantidade recusada numa entrega já ficava registada e já contava na
 * qualidade do fornecedor. Mas não gerava nada: não havia lista de «a
 * devolver», ninguém sabia quanto é que o fornecedor devia, e a caixa com os
 * filtros molhados ficava a um canto do armazém até alguém tropeçar nela.
 *
 * <p>Uma devolução acaba sempre de uma de quatro maneiras, e todas têm de
 * caber: devolveu-se, ele substituiu, ele creditou, ou ficámos com aquilo à
 * mesma — às vezes com desconto, às vezes porque não compensa o frete de
 * volta. Obrigar a escolher entre «devolvido» e «não devolvido» faria com que
 * metade dos casos ficasse por registar.
 */
@Service
public class ReturnService {

    /** A partir de quantos dias uma devolução por resolver deixa de ser normal. */
    private static final int DIAS_A_ARRASTAR = 15;

    public record ResolverRequest(
            @NotBlank(message = "Diga como acabou a devolução.") String resolution,
            @Size(max = 500) String note,
            @Size(max = 80) String creditNote,
            BigDecimal creditAmount) {}

    /** Uma devolução, com tudo o que é preciso para a resolver. */
    public record Devolucao(
            String id,
            String orderId,
            String orderNumber,
            String supplierId,
            String supplierName,
            String receiptId,
            String receiptNumber,
            Instant receivedAt,
            String description,
            BigDecimal quantity,
            String unit,
            String reason,
            String resolution,
            String resolutionLabel,
            Instant resolvedAt,
            String resolutionNote,
            String creditNote,
            BigDecimal creditAmount,
            /** Quanto vale, ao preço da encomenda. */
            BigDecimal valor,
            String currency,
            int diasPorResolver,
            boolean aArrastar) {}

    private final PurchaseReceiptLineRepository linhas;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public ReturnService(
            PurchaseReceiptLineRepository linhas,
            UserRepository users,
            NotificationService notifications,
            AuditService audit) {
        this.linhas = linhas;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ==== Lista =============================================================

    /**
     * As devoluções da empresa.
     *
     * <p>Por omissão só as que estão por resolver, que são as únicas sobre as
     * quais há alguma coisa a fazer. As resolvidas ficam acessíveis porque a
     * pergunta «o que é que aconteceu àquela caixa?» aparece meses depois.
     */
    @Transactional(readOnly = true)
    public List<Devolucao> listar(String orgId, boolean apenasPorResolver) {
        return linhas.recusadasDaOrganizacao(orgId).stream()
                .filter(l -> !apenasPorResolver
                        || l.getResolution() == PurchaseReceiptLine.Resolution.PENDING)
                .map(ReturnService::ver)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Devolucao> doFornecedor(String orgId, String supplierId) {
        return linhas.recusadasDaOrganizacao(orgId).stream()
                .filter(l -> {
                    PurchaseOrder o = l.getReceipt().getOrder();
                    return o.getSupplier() != null && o.getSupplier().getId().equals(supplierId);
                })
                .map(ReturnService::ver)
                .toList();
    }

    // ==== Resolver ==========================================================

    @Transactional
    public Devolucao resolver(String orgId, String userId, String id, ResolverRequest req) {
        PurchaseReceiptLine l = linhas.findById(id)
                .filter(x -> x.getReceipt().getOrganization().getId().equals(orgId))
                .orElseThrow(() -> ApiException.notFound("Devolução não encontrada."));
        if (l.getRejectedQuantity() == null || l.getRejectedQuantity().signum() <= 0) {
            throw ApiException.badRequest("Esta linha não tem nada recusado.");
        }

        PurchaseReceiptLine.Resolution escolha;
        try {
            escolha = PurchaseReceiptLine.Resolution.valueOf(
                    req.resolution().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Desfecho desconhecido: " + req.resolution());
        }
        if (escolha == PurchaseReceiptLine.Resolution.CREDITED
                && (req.creditNote() == null || req.creditNote().isBlank())) {
            // Uma nota de crédito sem número não se concilia com nada: o
            // financeiro recebe-a e não sabe a que compra pertence.
            throw ApiException.badRequest(
                    "Indique o número da nota de crédito: sem ele não há como a conciliar.");
        }

        l.setResolution(escolha);
        l.setResolvedAt(escolha == PurchaseReceiptLine.Resolution.PENDING ? null : Instant.now());
        l.setResolvedBy(userId);
        l.setResolutionNote(limpo(req.note()));
        l.setCreditNote(limpo(req.creditNote()));
        l.setCreditAmount(req.creditAmount());

        PurchaseOrder o = l.getReceipt().getOrder();
        audit.record(orgId, userId, "purchase_return.resolve", "PurchaseOrder", o.getId(),
                o.getNumber() + " · " + l.getOrderLine().getDescription()
                        + " · " + escolha.label()
                        + (req.creditNote() != null ? " · nota " + req.creditNote() : ""));
        return ver(l);
    }

    // ==== Aviso =============================================================

    /**
     * Avisa das devoluções que se estão a arrastar.
     *
     * <p>Uma devolução esquecida é dinheiro perdido em silêncio: ninguém a
     * reclama, o fornecedor não a credita, e ao fim de uns meses já não se
     * sabe de que entrega era. Um aviso ao fim de duas semanas é o que a põe
     * outra vez em cima da mesa.
     */
    @Transactional
    public int notificarPendentes() {
        Instant limite = Instant.now().minus(DIAS_A_ARRASTAR, ChronoUnit.DAYS);
        int enviados = 0;
        for (PurchaseReceiptLine l : linhas.recusadasPendentesAntesDe(limite)) {
            Devolucao d = ver(l);
            enviados += notifications.notifyManagers(NotificationService.Draft.of(
                    l.getReceipt().getOrganization().getId(),
                    AlertCategory.EXPENSE, AlertSeverity.WARNING,
                    "Devolução por resolver há " + d.diasPorResolver() + " dias: "
                            + d.supplierName(),
                    d.quantity().stripTrailingZeros().toPlainString() + " × "
                            + d.description() + " da ordem " + d.orderNumber()
                            + (d.reason() != null ? " (" + d.reason() + ")" : "")
                            + ". Enquanto não se resolve, é dinheiro que ninguém reclama.",
                    "purchase_return", l.getId(), "/compras"));
        }
        return enviados;
    }

    // ==== Vista =============================================================

    private static Devolucao ver(PurchaseReceiptLine l) {
        PurchaseReceipt r = l.getReceipt();
        PurchaseOrder o = r.getOrder();
        BigDecimal valor = l.getOrderLine().getUnitPrice() == null
                ? null
                : l.getOrderLine().getUnitPrice().multiply(l.getRejectedQuantity());
        Instant desde = r.getReceivedAt() != null ? r.getReceivedAt() : r.getCreatedAt();
        int dias = l.getResolution() == PurchaseReceiptLine.Resolution.PENDING && desde != null
                ? (int) ChronoUnit.DAYS.between(desde, Instant.now())
                : 0;

        return new Devolucao(
                l.getId(), o.getId(), o.getNumber(),
                o.getSupplier() != null ? o.getSupplier().getId() : null,
                o.getSupplierName(),
                r.getId(), r.getNumber(), r.getReceivedAt(),
                l.getOrderLine().getDescription(),
                l.getRejectedQuantity(), l.getOrderLine().getUnit(),
                l.getRejectionReason(),
                l.getResolution().name(), l.getResolution().label(),
                l.getResolvedAt(), l.getResolutionNote(),
                l.getCreditNote(), l.getCreditAmount(),
                valor, o.getCurrency(),
                dias, dias >= DIAS_A_ARRASTAR);
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
