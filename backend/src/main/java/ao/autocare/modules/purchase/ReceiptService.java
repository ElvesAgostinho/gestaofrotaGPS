package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseOrderLine;
import ao.autocare.domain.PurchaseReceipt;
import ao.autocare.domain.PurchaseReceiptLine;
import ao.autocare.domain.Warehouse;
import ao.autocare.domain.enums.Enums.StockMovementType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.part.StockService;
import ao.autocare.modules.part.dto.PartDtos.MovementRequest;
import ao.autocare.modules.workorder.CounterService;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PurchaseOrderRepository;
import ao.autocare.repo.PurchaseReceiptRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WarehouseRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receber a encomenda: o acto que torna tudo verdadeiro.
 *
 * <p>Uma ordem de compra que nunca é recebida é uma promessa. O stock continua
 * a dizer que não há, o painel continua a dizer que vem a caminho, e a peça
 * está na prateleira há três semanas. Receber faz entrar no armazém, actualiza
 * o custo médio com o preço que se pagou de facto, e o que faltava deixa de
 * faltar.
 *
 * <p>Recebe-se <b>em partes</b>, porque é assim que chega: o fornecedor manda
 * metade e promete o resto. Uma recepção que só aceitasse tudo de uma vez
 * obrigava a mentir ou a não registar nada — e nos dois casos o stock fica
 * errado.
 *
 * <p>O que chega mal conta-se à parte. Não entra no armazém, mas também não
 * pode desaparecer do registo: é o que se devolve, e é o que distingue um
 * fornecedor que entrega de um que entrega bem.
 */
@Service
public class ReceiptService {

    /** O que se recebeu de cada linha da encomenda. */
    public record LinhaRecebida(
            @NotBlank String orderLineId,
            BigDecimal quantity,
            BigDecimal rejectedQuantity,
            @Size(max = 300) String rejectionReason) {}

    public record RecepcaoRequest(
            String warehouseId,
            @Size(max = 80) String deliveryNote,
            Instant receivedAt,
            @Size(max = 1000) String notes,
            List<LinhaRecebida> lines) {}

    public record LinhaView(
            String orderLineId,
            String description,
            BigDecimal quantity,
            BigDecimal rejectedQuantity,
            String rejectionReason) {}

    public record RecepcaoView(
            String id,
            String number,
            String orderId,
            String orderNumber,
            Instant receivedAt,
            String warehouseId,
            String warehouseName,
            String deliveryNote,
            String receivedByLabel,
            String notes,
            List<LinhaView> lines) {}

    private final PurchaseReceiptRepository receipts;
    private final PurchaseOrderRepository orders;
    private final WarehouseRepository warehouses;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final CounterService counters;
    private final StockService stock;
    private final AuditService audit;

    public ReceiptService(
            PurchaseReceiptRepository receipts,
            PurchaseOrderRepository orders,
            WarehouseRepository warehouses,
            OrganizationRepository organizations,
            UserRepository users,
            CounterService counters,
            StockService stock,
            AuditService audit) {
        this.receipts = receipts;
        this.orders = orders;
        this.warehouses = warehouses;
        this.organizations = organizations;
        this.users = users;
        this.counters = counters;
        this.stock = stock;
        this.audit = audit;
    }

    @Transactional
    public RecepcaoView receber(String orgId, String userId, String orderId, RecepcaoRequest req) {
        PurchaseOrder o = orders.findByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem de compra não encontrada."));
        if (o.getStatus() == PurchaseOrder.Status.DRAFT
                || o.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL
                || o.getStatus() == PurchaseOrder.Status.REJECTED) {
            throw ApiException.conflict(
                    "Esta ordem ainda não foi aprovada. Não se recebe o que não foi encomendado.");
        }
        if (o.getStatus() == PurchaseOrder.Status.CANCELLED) {
            throw ApiException.conflict("Esta ordem foi anulada.");
        }

        Warehouse armazem = null;
        if (req.warehouseId() != null && !req.warehouseId().isBlank()) {
            armazem = warehouses.findById(req.warehouseId())
                    .filter(w -> w.getOrganization().getId().equals(orgId))
                    .orElseThrow(() -> ApiException.notFound("Armazém não encontrado."));
        }

        PurchaseReceipt r = new PurchaseReceipt();
        r.setOrganization(organizations.getReferenceById(orgId));
        r.setOrder(o);
        r.setNumber(proximoNumero(orgId));
        r.setReceivedAt(req.receivedAt() != null ? req.receivedAt() : Instant.now());
        r.setWarehouse(armazem);
        r.setDeliveryNote(limpo(req.deliveryNote()));
        r.setReceivedBy(userId);
        r.setReceivedByLabel(nomeDe(userId));
        r.setNotes(limpo(req.notes()));

        boolean entrouAlgo = false;
        for (LinhaRecebida l : req.lines() == null ? List.<LinhaRecebida>of() : req.lines()) {
            PurchaseOrderLine linhaDaOrdem = o.getLines().stream()
                    .filter(x -> x.getId().equals(l.orderLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest(
                            "Linha desconhecida nesta ordem."));

            BigDecimal boa = l.quantity() != null ? l.quantity() : BigDecimal.ZERO;
            BigDecimal ma = l.rejectedQuantity() != null ? l.rejectedQuantity() : BigDecimal.ZERO;
            if (boa.signum() <= 0 && ma.signum() <= 0) {
                continue;
            }
            // Receber mais do que foi encomendado é quase sempre um erro de
            // digitação, e passa despercebido até à factura não bater.
            BigDecimal falta = linhaDaOrdem.porReceber();
            if (boa.add(ma).compareTo(falta) > 0) {
                throw ApiException.badRequest(
                        "Está a receber " + boa.add(ma).toPlainString() + " de «"
                                + linhaDaOrdem.getDescription() + "» mas só faltam "
                                + falta.toPlainString() + ".");
            }

            PurchaseReceiptLine linha = new PurchaseReceiptLine();
            linha.setOrderLine(linhaDaOrdem);
            linha.setQuantity(boa);
            linha.setRejectedQuantity(ma);
            linha.setRejectionReason(limpo(l.rejectionReason()));
            r.addLine(linha);

            // Só o que chegou bem conta como recebido e entra no armazém.
            linhaDaOrdem.setReceivedQuantity(linhaDaOrdem.getReceivedQuantity().add(boa));
            if (boa.signum() > 0) {
                entrouAlgo = true;
                entrarNoArmazem(orgId, userId, linhaDaOrdem, armazem, boa, o.getNumber());
            }
        }
        if (r.getLines().isEmpty()) {
            throw ApiException.badRequest("Não indicou nada que tenha chegado.");
        }
        receipts.save(r);

        actualizarEstado(o);
        audit.record(orgId, userId, "purchase_receipt.create", "PurchaseOrder", o.getId(),
                o.getNumber() + " · " + r.getNumber()
                        + (entrouAlgo ? " · entrou no armazém" : " · sem entrada em armazém"));
        return ver(r);
    }

    /**
     * Entra no armazém, com o preço que se pagou.
     *
     * <p>O custo médio actualiza-se sozinho a partir daqui: é a única forma de
     * o custo por quilómetro de uma viatura reflectir o que as peças custam
     * hoje e não o que custavam quando o artigo foi criado.
     */
    private void entrarNoArmazem(
            String orgId, String userId, PurchaseOrderLine linha, Warehouse armazem,
            BigDecimal quantidade, String referencia) {
        if (linha.getPart() == null || armazem == null) {
            // Sem artigo de armazém ou sem armazém indicado, a recepção serve
            // para fechar a encomenda mas não mexe no stock. É um caso real:
            // compra-se um serviço, ou uma peça que vai directa para a viatura.
            return;
        }
        stock.recordMovement(orgId, userId, new MovementRequest(
                linha.getPart().getId(), armazem.getId(), StockMovementType.IN,
                quantidade, linha.getUnitPrice(),
                referencia, "Recebido da ordem " + referencia));
    }

    /**
     * O estado da ordem depois de uma entrega.
     *
     * <p>Fica «recebida» quando já não falta nada. A distinção entre recebida
     * em parte e recebida é o que permite ver, numa lista, as encomendas que
     * ficaram a meio — que são as que se esquecem.
     */
    private void actualizarEstado(PurchaseOrder o) {
        boolean faltaAlguma = o.getLines().stream()
                .anyMatch(l -> l.porReceber().signum() > 0);
        if (faltaAlguma) {
            o.setStatus(PurchaseOrder.Status.PARTIALLY_RECEIVED);
        } else {
            o.setStatus(PurchaseOrder.Status.RECEIVED);
            o.setClosedAt(Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public List<RecepcaoView> daOrdem(String orgId, String orderId) {
        orders.findByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem de compra não encontrada."));
        return receipts.findByOrderIdOrderByReceivedAtAsc(orderId).stream()
                .map(ReceiptService::ver).toList();
    }

    private static RecepcaoView ver(PurchaseReceipt r) {
        List<LinhaView> linhas = new ArrayList<>();
        for (PurchaseReceiptLine l : r.getLines()) {
            linhas.add(new LinhaView(
                    l.getOrderLine().getId(), l.getOrderLine().getDescription(),
                    l.getQuantity(), l.getRejectedQuantity(), l.getRejectionReason()));
        }
        return new RecepcaoView(
                r.getId(), r.getNumber(), r.getOrder().getId(), r.getOrder().getNumber(),
                r.getReceivedAt(),
                r.getWarehouse() != null ? r.getWarehouse().getId() : null,
                r.getWarehouse() != null ? r.getWarehouse().getName() : null,
                r.getDeliveryNote(), r.getReceivedByLabel(), r.getNotes(), linhas);
    }

    private String proximoNumero(String orgId) {
        int ano = LocalDate.now(ZoneOffset.UTC).getYear();
        return String.format("GR-%d-%04d", ano, counters.next(orgId, "purchase_receipt_" + ano));
    }

    private String nomeDe(String userId) {
        return userId == null ? null : users.findById(userId).map(u -> u.getName()).orElse(null);
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
