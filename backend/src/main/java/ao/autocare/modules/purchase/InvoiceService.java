package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.StoredFile;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.SupplierInvoice;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PurchaseOrderRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.SupplierInvoiceRepository;
import ao.autocare.repo.SupplierRepository;
import ao.autocare.storage.StorageProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * As facturas do fornecedor, com o papel digitalizado em anexo.
 *
 * <p>Hoje vivem numa pasta. Duas perguntas que essa pasta não responde: «já
 * pagámos esta?» e «quanto é que esta empresa nos facturou este ano?». A
 * primeira faz pagar duas vezes; a segunda faz negociar às cegas.
 *
 * <p>Quando a factura está ligada a uma ordem de compra, compara-se o valor: se
 * não bate, diz-se. Uma factura que não bate com a encomenda é a forma mais
 * comum de pagar a mais, e descobre-se sempre tarde de mais — quando já foi
 * transferida.
 *
 * <p>O que se transfere não é o valor da factura quando há retenção na fonte.
 * A retenção vai em campo próprio e desconta-se do que se paga, em vez de
 * desaparecer num cálculo que ninguém vê.
 */
@Service
public class InvoiceService {

    /** Até quantos dias de diferença se considera que a factura bate com a ordem. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    public record FacturaRequest(
            @NotBlank(message = "Indique o número da factura.") @Size(max = 80) String number,
            String orderId,
            LocalDate issuedAt,
            LocalDate dueAt,
            BigDecimal amount,
            BigDecimal taxAmount,
            BigDecimal withholdingAmount,
            @Size(max = 3) String currency,
            @Size(max = 1000) String notes) {}

    public record FacturaView(
            String id,
            String supplierId,
            String supplierName,
            String orderId,
            String orderNumber,
            String number,
            LocalDate issuedAt,
            LocalDate dueAt,
            BigDecimal amount,
            BigDecimal taxAmount,
            BigDecimal withholdingAmount,
            BigDecimal aPagar,
            String currency,
            String status,
            String statusLabel,
            LocalDate paidAt,
            String fileId,
            String fileName,
            String notes,
            Integer diasAteVencer,
            /** O que a factura diz contra o que a ordem dizia. */
            String conferencia) {}

    private final SupplierInvoiceRepository invoices;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository orders;
    private final OrganizationRepository organizations;
    private final StoredFileRepository files;
    private final StorageProvider storage;
    private final NotificationService notifications;
    private final AuditService audit;

    public InvoiceService(
            SupplierInvoiceRepository invoices,
            SupplierRepository suppliers,
            PurchaseOrderRepository orders,
            OrganizationRepository organizations,
            StoredFileRepository files,
            StorageProvider storage,
            NotificationService notifications,
            AuditService audit) {
        this.invoices = invoices;
        this.suppliers = suppliers;
        this.orders = orders;
        this.organizations = organizations;
        this.files = files;
        this.storage = storage;
        this.notifications = notifications;
        this.audit = audit;
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    // ==== Registar ==========================================================

    @Transactional
    public FacturaView registar(
            String orgId, String userId, String supplierId,
            FacturaRequest req, MultipartFile ficheiro) {
        Supplier s = suppliers.findByIdAndOrganizationId(supplierId, orgId)
                .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
        String numero = req.number().trim();
        if (invoices.existsBySupplierIdAndNumberIgnoreCase(supplierId, numero)) {
            throw ApiException.conflict(
                    "Já há uma factura " + numero + " deste fornecedor. "
                            + "Registar duas vezes é o caminho para pagar duas vezes.");
        }

        SupplierInvoice f = new SupplierInvoice();
        f.setOrganization(organizations.getReferenceById(orgId));
        f.setSupplier(s);
        f.setNumber(numero);
        f.setIssuedAt(req.issuedAt());
        f.setDueAt(req.dueAt());
        f.setAmount(req.amount());
        f.setTaxAmount(req.taxAmount());
        f.setWithholdingAmount(req.withholdingAmount());
        f.setCurrency(req.currency() != null && !req.currency().isBlank()
                ? req.currency().trim().toUpperCase(Locale.ROOT)
                : (s.getCurrency() != null ? s.getCurrency() : "AOA"));
        f.setNotes(limpo(req.notes()));
        f.setCreatedBy(userId);

        if (req.orderId() != null && !req.orderId().isBlank()) {
            PurchaseOrder o = orders.findByIdAndOrganizationId(req.orderId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Ordem de compra não encontrada."));
            if (o.getSupplier() != null && !o.getSupplier().getId().equals(supplierId)) {
                throw ApiException.badRequest(
                        "Essa ordem de compra é de outro fornecedor.");
            }
            f.setOrder(o);
            // Se a retenção não foi indicada, usa-se a da ordem: é a mesma
            // regra, e esquecê-la é transferir a mais.
            if (f.getWithholdingAmount() == null
                    && o.getWithholdingAmount() != null && o.getWithholdingAmount().signum() > 0) {
                f.setWithholdingAmount(o.getWithholdingAmount());
            }
        }

        if (ficheiro != null && !ficheiro.isEmpty()) {
            guardarFicheiro(orgId, f, ficheiro);
        }
        invoices.save(f);

        audit.record(orgId, userId, "supplier_invoice.create", "Supplier", supplierId,
                s.getName() + " · factura " + numero
                        + (f.getAmount() != null ? " · " + f.getAmount() + " " + f.getCurrency() : ""));
        avisarSeNaoBate(f);
        return ver(f);
    }

    /** Anexar ou substituir o papel digitalizado. */
    @Transactional
    public FacturaView anexar(String orgId, String userId, String id, MultipartFile ficheiro) {
        SupplierInvoice f = exigir(orgId, id);
        if (ficheiro == null || ficheiro.isEmpty()) {
            throw ApiException.badRequest("Escolha o ficheiro da factura.");
        }
        guardarFicheiro(orgId, f, ficheiro);
        audit.record(orgId, userId, "supplier_invoice.attach", "Supplier",
                f.getSupplier().getId(), f.getNumber() + " · " + f.getFileName());
        return ver(f);
    }

    private void guardarFicheiro(String orgId, SupplierInvoice f, MultipartFile ficheiro) {
        try {
            String chave = storage.store(ficheiro.getBytes(), ficheiro.getContentType(),
                    ficheiro.getOriginalFilename());
            StoredFile registo = new StoredFile();
            registo.setOrganization(organizations.getReferenceById(orgId));
            registo.setStorageKey(chave);
            registo.setOriginalName(ficheiro.getOriginalFilename());
            registo.setContentType(ficheiro.getContentType());
            registo.setSizeBytes(ficheiro.getSize());
            files.save(registo);
            f.setFileId(registo.getId());
            f.setFileName(ficheiro.getOriginalFilename());
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro enviado.");
        }
    }

    @Transactional
    public FacturaView marcarPaga(String orgId, String userId, String id, LocalDate quando) {
        SupplierInvoice f = exigir(orgId, id);
        f.setStatus(SupplierInvoice.Status.PAID);
        f.setPaidAt(quando != null ? quando : hoje());
        audit.record(orgId, userId, "supplier_invoice.paid", "Supplier",
                f.getSupplier().getId(), f.getNumber() + " paga em " + f.getPaidAt());
        return ver(f);
    }

    @Transactional
    public FacturaView contestar(String orgId, String userId, String id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Diga o que está errado na factura.");
        }
        SupplierInvoice f = exigir(orgId, id);
        f.setStatus(SupplierInvoice.Status.DISPUTED);
        f.setNotes((f.getNotes() == null ? "" : f.getNotes() + "\n") + "Contestada: " + motivo.trim());
        audit.record(orgId, userId, "supplier_invoice.dispute", "Supplier",
                f.getSupplier().getId(), f.getNumber() + " · " + motivo.trim());
        return ver(f);
    }

    @Transactional
    public void apagar(String orgId, String userId, String id) {
        SupplierInvoice f = exigir(orgId, id);
        if (f.getStatus() == SupplierInvoice.Status.PAID) {
            throw ApiException.conflict(
                    "Uma factura já paga não se apaga: o pagamento aconteceu. "
                            + "Anule-a se foi registada por engano.");
        }
        String rotulo = f.getSupplier().getName() + " · " + f.getNumber();
        String fornecedor = f.getSupplier().getId();
        invoices.delete(f);
        audit.record(orgId, userId, "supplier_invoice.remove", "Supplier", fornecedor, rotulo);
    }

    // ==== Leitura ===========================================================

    @Transactional(readOnly = true)
    public List<FacturaView> doFornecedor(String orgId, String supplierId) {
        suppliers.findByIdAndOrganizationId(supplierId, orgId)
                .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
        return invoices.findBySupplierIdOrderByIssuedAtDesc(supplierId).stream()
                .map(InvoiceService::ver).toList();
    }

    @Transactional(readOnly = true)
    public List<FacturaView> daOrdem(String orgId, String orderId) {
        return invoices.findByOrderIdOrderByIssuedAtDesc(orderId).stream()
                .map(InvoiceService::ver).toList();
    }

    @Transactional(readOnly = true)
    public List<FacturaView> listar(String orgId, String estado) {
        List<SupplierInvoice> lista = estado == null || estado.isBlank()
                ? invoices.todas(orgId)
                : invoices.porEstado(orgId, SupplierInvoice.Status.valueOf(estado));
        return lista.stream().map(InvoiceService::ver).toList();
    }

    private static FacturaView ver(SupplierInvoice f) {
        return new FacturaView(
                f.getId(), f.getSupplier().getId(), f.getSupplier().getName(),
                f.getOrder() != null ? f.getOrder().getId() : null,
                f.getOrder() != null ? f.getOrder().getNumber() : null,
                f.getNumber(), f.getIssuedAt(), f.getDueAt(),
                f.getAmount(), f.getTaxAmount(), f.getWithholdingAmount(), f.aPagar(),
                f.getCurrency(), f.getStatus().name(), f.getStatus().label(), f.getPaidAt(),
                f.getFileId(), f.getFileName(), f.getNotes(),
                f.diasAteVencer(hoje()), conferencia(f));
    }

    /**
     * A factura bate com a encomenda?
     *
     * <p>Uma factura que não bate com a ordem é a forma mais comum de pagar a
     * mais, e descobre-se sempre tarde de mais — quando já foi transferida.
     */
    private static String conferencia(SupplierInvoice f) {
        if (f.getOrder() == null || f.getAmount() == null || f.getOrder().getTotal() == null) {
            return null;
        }
        BigDecimal diferenca = f.getAmount().subtract(f.getOrder().getTotal());
        if (diferenca.abs().compareTo(TOLERANCIA) <= 0) {
            return "Bate com a ordem " + f.getOrder().getNumber() + ".";
        }
        boolean aMais = diferenca.signum() > 0;
        return "A factura está " + diferenca.abs().toPlainString() + " " + f.getCurrency()
                + (aMais ? " ACIMA" : " abaixo") + " da ordem " + f.getOrder().getNumber()
                + " (" + f.getOrder().getTotal().toPlainString() + ")."
                + (aMais ? " Confirme antes de pagar." : "");
    }

    private void avisarSeNaoBate(SupplierInvoice f) {
        String conferencia = conferencia(f);
        if (conferencia == null || !conferencia.contains("ACIMA")) {
            return;
        }
        notifications.notifyManagers(NotificationService.Draft.of(
                f.getOrganization().getId(),
                AlertCategory.EXPENSE, AlertSeverity.WARNING,
                "Factura acima da encomenda: " + f.getSupplier().getName(),
                "Factura " + f.getNumber() + ". " + conferencia,
                "supplier_invoice", f.getId(), "/fornecedores/" + f.getSupplier().getId()));
    }

    private SupplierInvoice exigir(String orgId, String id) {
        return invoices.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Factura não encontrada."));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
