package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Part;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.SupplierContact;
import ao.autocare.domain.SupplierDocument;
import ao.autocare.domain.SupplierPrice;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.purchase.dto.PurchaseDtos.ComparacaoPreco;
import ao.autocare.modules.purchase.dto.PurchaseDtos.ContactView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.DocumentView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.PriceView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SaveContactRequest;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SaveDocumentRequest;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SavePriceRequest;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.SupplierContactRepository;
import ao.autocare.repo.SupplierDocumentRepository;
import ao.autocare.repo.SupplierPriceRepository;
import ao.autocare.repo.SupplierRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O caderno do fornecedor: quem são as pessoas, que papéis tem em ordem, a
 * que preço vende e em quantos dias entrega.
 *
 * <p>Um fornecedor com um nome e um telefone serve para registar que a oficina
 * X arranjou a viatura. Não serve para comprar. Para comprar é preciso saber a
 * quem se manda a encomenda (e não é a mesma pessoa a quem se manda a
 * factura), se os papéis dele estão em ordem — comprar a quem tem a certidão
 * caducada é um problema que só aparece meses depois, na contabilidade — e,
 * sobretudo, <b>a quanto e em quantos dias</b>.
 *
 * <p>A lista de preços é o elo que faltava em todo o sistema. Sem ela a lista
 * de compras sabia que faltavam quatro filtros mas não sabia quanto custavam
 * nem a quem pedir, e uma ordem de compra não tinha preço para levar. Com ela,
 * a mesma linha passa a dizer «Auto Peças 8 500 Kz em 21 dias · Casa do Camião
 * 9 200 Kz em 5 dias» — que é exactamente a decisão que o gestor tem de tomar,
 * e que não é só escolher o mais barato.
 */
@Service
public class SupplierBookService {

    /** A partir de quantos dias se avisa que um documento vai expirar. */
    private static final int AVISO_DIAS = 30;

    private final SupplierRepository suppliers;
    private final SupplierContactRepository contacts;
    private final SupplierDocumentRepository documents;
    private final SupplierPriceRepository prices;
    private final PartRepository parts;
    private final OrganizationRepository organizations;
    private final NotificationService notifications;
    private final AuditService audit;

    public SupplierBookService(
            SupplierRepository suppliers,
            SupplierContactRepository contacts,
            SupplierDocumentRepository documents,
            SupplierPriceRepository prices,
            PartRepository parts,
            OrganizationRepository organizations,
            NotificationService notifications,
            AuditService audit) {
        this.suppliers = suppliers;
        this.contacts = contacts;
        this.documents = documents;
        this.prices = prices;
        this.parts = parts;
        this.organizations = organizations;
        this.notifications = notifications;
        this.audit = audit;
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    // ==== Contactos =========================================================

    @Transactional(readOnly = true)
    public List<ContactView> listarContactos(String orgId, String supplierId) {
        exigirFornecedor(orgId, supplierId);
        return contacts.findBySupplierIdOrderByPrimaryContactDescNameAsc(supplierId).stream()
                .map(ContactView::of).toList();
    }

    @Transactional
    public ContactView criarContacto(
            String orgId, String userId, String supplierId, SaveContactRequest req) {
        Supplier s = exigirFornecedor(orgId, supplierId);
        SupplierContact c = new SupplierContact();
        c.setOrganization(organizations.getReferenceById(orgId));
        c.setSupplier(s);
        aplicar(c, req);
        // Só um contacto principal por fornecedor: é dele que a ordem de compra
        // tira o destinatário, e dois principais é não ter nenhum.
        if (c.isPrimaryContact()) {
            despromoverOutros(supplierId, null);
        }
        contacts.save(c);
        audit.record(orgId, userId, "supplier.contact.add", "Supplier", supplierId,
                s.getName() + " → " + c.getName() + " (" + c.getRole().label() + ")");
        return ContactView.of(c);
    }

    @Transactional
    public ContactView actualizarContacto(
            String orgId, String userId, String id, SaveContactRequest req) {
        SupplierContact c = contacts.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Contacto não encontrado."));
        aplicar(c, req);
        if (c.isPrimaryContact()) {
            despromoverOutros(c.getSupplier().getId(), id);
        }
        audit.record(orgId, userId, "supplier.contact.update", "Supplier",
                c.getSupplier().getId(), c.getName());
        return ContactView.of(c);
    }

    @Transactional
    public void apagarContacto(String orgId, String userId, String id) {
        SupplierContact c = contacts.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Contacto não encontrado."));
        String nome = c.getName();
        String fornecedor = c.getSupplier().getId();
        contacts.delete(c);
        audit.record(orgId, userId, "supplier.contact.remove", "Supplier", fornecedor, nome);
    }

    private void despromoverOutros(String supplierId, String excepto) {
        for (SupplierContact outro : contacts.findBySupplierIdOrderByPrimaryContactDescNameAsc(supplierId)) {
            if (outro.isPrimaryContact() && !outro.getId().equals(excepto)) {
                outro.setPrimaryContact(false);
            }
        }
    }

    private void aplicar(SupplierContact c, SaveContactRequest req) {
        c.setName(req.name().trim());
        c.setPhone(limpo(req.phone()));
        c.setEmail(limpo(req.email()));
        c.setNote(limpo(req.note()));
        if (req.role() != null && !req.role().isBlank()) {
            try {
                c.setRole(SupplierContact.Role.valueOf(req.role().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Função desconhecida: " + req.role());
            }
        }
        if (req.primaryContact() != null) {
            c.setPrimaryContact(req.primaryContact());
        }
    }

    /** O contacto a quem a ordem de compra deve ir: o principal, ou o comercial. */
    @Transactional(readOnly = true)
    public SupplierContact destinatario(String supplierId) {
        List<SupplierContact> todos =
                contacts.findBySupplierIdOrderByPrimaryContactDescNameAsc(supplierId);
        return todos.stream()
                .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
                .findFirst()
                .orElse(todos.isEmpty() ? null : todos.get(0));
    }

    // ==== Documentos ========================================================

    @Transactional(readOnly = true)
    public List<DocumentView> listarDocumentos(String orgId, String supplierId) {
        exigirFornecedor(orgId, supplierId);
        LocalDate hoje = hoje();
        return documents.findBySupplierIdOrderByExpiresAtAsc(supplierId).stream()
                .map(d -> DocumentView.of(d, hoje)).toList();
    }

    @Transactional
    public DocumentView criarDocumento(
            String orgId, String userId, String supplierId, SaveDocumentRequest req) {
        Supplier s = exigirFornecedor(orgId, supplierId);
        if (req.issuedAt() != null && req.expiresAt() != null
                && req.expiresAt().isBefore(req.issuedAt())) {
            throw ApiException.badRequest("A validade não pode ser anterior à emissão.");
        }
        SupplierDocument d = new SupplierDocument();
        d.setOrganization(organizations.getReferenceById(orgId));
        d.setSupplier(s);
        d.setKind(req.kind().trim().toUpperCase(Locale.ROOT));
        d.setNumber(limpo(req.number()));
        d.setIssuedAt(req.issuedAt());
        d.setExpiresAt(req.expiresAt());
        d.setFileId(limpo(req.fileId()));
        d.setNote(limpo(req.note()));
        documents.save(d);
        audit.record(orgId, userId, "supplier.document.add", "Supplier", supplierId,
                s.getName() + " → " + d.getKind());
        return DocumentView.of(d, hoje());
    }

    @Transactional
    public void apagarDocumento(String orgId, String userId, String id) {
        SupplierDocument d = documents.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Documento não encontrado."));
        String rotulo = d.getSupplier().getName() + " → " + d.getKind();
        String fornecedor = d.getSupplier().getId();
        documents.delete(d);
        audit.record(orgId, userId, "supplier.document.remove", "Supplier", fornecedor, rotulo);
    }

    /**
     * Os papéis de um fornecedor que já não estão em ordem.
     *
     * <p>Usado para avisar e para pôr a frase na ordem de compra. Não trava a
     * compra: há casos em que se compra de urgência a quem tem a certidão
     * atrasada, e a decisão é de quem assina — mas não pode ser por
     * desconhecimento.
     */
    @Transactional(readOnly = true)
    public List<String> reparosDocumentais(String supplierId) {
        LocalDate hoje = hoje();
        return documents.findBySupplierIdOrderByExpiresAtAsc(supplierId).stream()
                .map(d -> DocumentView.of(d, hoje))
                .filter(v -> "EXPIRADO".equals(v.estado()) || "A_EXPIRAR".equals(v.estado()))
                .map(v -> "EXPIRADO".equals(v.estado())
                        ? v.kindLabel() + " expirou em " + v.expiresAt()
                        : v.kindLabel() + " expira em " + v.expiresAt()
                                + " (" + v.diasAteExpirar() + " dias)")
                .toList();
    }

    /** Avisa quem gere dos documentos de fornecedor a expirar. Chamado pelo agendador. */
    @Transactional
    public int notificarDocumentos() {
        LocalDate limite = hoje().plusDays(AVISO_DIAS);
        int enviados = 0;
        for (SupplierDocument d : documents.todosExpirandoAte(limite)) {
            DocumentView v = DocumentView.of(d, hoje());
            boolean expirado = "EXPIRADO".equals(v.estado());
            enviados += notifications.notifyManagers(NotificationService.Draft.of(
                    d.getOrganization().getId(),
                    AlertCategory.DOCUMENT,
                    expirado ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                    (expirado ? "Documento de fornecedor expirado: " : "Documento de fornecedor a expirar: ")
                            + d.getSupplier().getName(),
                    v.kindLabel() + (expirado ? " expirou em " : " expira em ") + d.getExpiresAt()
                            + ". Comprar a um fornecedor com os papéis fora de prazo cria um "
                            + "problema na contabilidade que já não se desfaz.",
                    "supplier_document", d.getId(), "/fornecedores"));
        }
        return enviados;
    }

    // ==== Preços ============================================================

    @Transactional(readOnly = true)
    public List<PriceView> listarPrecos(String orgId, String supplierId) {
        exigirFornecedor(orgId, supplierId);
        LocalDate hoje = hoje();
        return prices.findBySupplierIdOrderByCreatedAtDesc(supplierId).stream()
                .map(p -> PriceView.of(p, hoje)).toList();
    }

    @Transactional
    public PriceView guardarPreco(
            String orgId, String userId, String supplierId, SavePriceRequest req) {
        Supplier s = exigirFornecedor(orgId, supplierId);
        Part peca = parts.findByIdAndOrganizationId(req.partId(), orgId)
                .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
        if (req.quotedAt() != null && req.validUntil() != null
                && req.validUntil().isBefore(req.quotedAt())) {
            throw ApiException.badRequest("A validade da cotação não pode ser anterior à cotação.");
        }

        // Um fornecedor tem um preço por peça: cotar de novo substitui a cotação
        // anterior em vez de acumular dez linhas que ninguém sabe qual vale.
        SupplierPrice p = prices.findBySupplierIdAndPartId(supplierId, peca.getId())
                .orElseGet(() -> {
                    SupplierPrice nova = new SupplierPrice();
                    nova.setOrganization(organizations.getReferenceById(orgId));
                    nova.setSupplier(s);
                    nova.setPart(peca);
                    return nova;
                });
        p.setSupplierPartNumber(limpo(req.supplierPartNumber()));
        p.setPrice(req.price());
        p.setCurrency(req.currency() == null || req.currency().isBlank()
                ? (s.getCurrency() != null ? s.getCurrency() : "AOA")
                : req.currency().trim().toUpperCase(Locale.ROOT));
        p.setMinOrderQuantity(req.minOrderQuantity());
        p.setLeadTimeDays(req.leadTimeDays());
        p.setQuotedAt(req.quotedAt() != null ? req.quotedAt() : hoje());
        p.setValidUntil(req.validUntil());
        p.setNote(limpo(req.note()));
        prices.save(p);

        audit.record(orgId, userId, "supplier.price.save", "Supplier", supplierId,
                s.getName() + " · " + peca.getName() + " = " + p.getPrice() + " " + p.getCurrency());
        return PriceView.of(p, hoje());
    }

    @Transactional
    public void apagarPreco(String orgId, String userId, String id) {
        SupplierPrice p = prices.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Preço não encontrado."));
        String rotulo = p.getSupplier().getName() + " · " + p.getPart().getName();
        String fornecedor = p.getSupplier().getId();
        prices.delete(p);
        audit.record(orgId, userId, "supplier.price.remove", "Supplier", fornecedor, rotulo);
    }

    /**
     * Os preços de uma peça, de todos os fornecedores.
     *
     * <p>Marca o mais barato e o mais rápido. São quase sempre dois
     * fornecedores diferentes, e é essa a informação que falta a quem decide:
     * pagar 8 % mais e ter a máquina a trabalhar na segunda, ou poupar e
     * esperar três semanas.
     */
    @Transactional(readOnly = true)
    public ComparacaoPreco comparar(String orgId, String partId) {
        Part peca = parts.findByIdAndOrganizationId(partId, orgId)
                .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
        LocalDate hoje = hoje();
        List<PriceView> vistas = prices.findByPartIdComFornecedor(partId).stream()
                .map(p -> PriceView.of(p, hoje))
                .toList();

        // Cotações fora de validade não concorrem para «o mais barato»: um
        // preço de há dois anos ganharia sempre e mandaria comprar onde já não
        // é barato.
        List<PriceView> firmes = vistas.stream().filter(v -> !v.expirada()).toList();
        List<PriceView> base = firmes.isEmpty() ? vistas : firmes;

        String maisBarato = base.stream()
                .min(Comparator.comparing(PriceView::price))
                .map(PriceView::id).orElse(null);
        String maisRapido = base.stream()
                .filter(v -> v.leadTimeDays() != null)
                .min(Comparator.comparingInt(PriceView::leadTimeDays))
                .map(PriceView::id).orElse(null);
        return new ComparacaoPreco(peca.getId(), peca.getName(), vistas, maisBarato, maisRapido);
    }

    // ==== Utilitários =======================================================

    private Supplier exigirFornecedor(String orgId, String supplierId) {
        return suppliers.findByIdAndOrganizationId(supplierId, orgId)
                .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
