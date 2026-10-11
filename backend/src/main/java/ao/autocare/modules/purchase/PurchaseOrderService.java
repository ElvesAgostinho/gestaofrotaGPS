package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Budget;
import ao.autocare.domain.Part;
import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseOrderEvent;
import ao.autocare.domain.PurchaseOrderLine;
import ao.autocare.domain.PurchaseSettings;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.budget.BudgetService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.purchase.dto.OrderDtos.ConfiguracaoRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.ConfiguracaoView;
import ao.autocare.modules.purchase.dto.OrderDtos.EventoView;
import ao.autocare.modules.purchase.dto.OrderDtos.LinhaRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.LinhaView;
import ao.autocare.modules.purchase.dto.OrderDtos.OrdemRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.OrdemView;
import ao.autocare.modules.purchase.dto.OrderDtos.Podem;
import ao.autocare.modules.workorder.CounterService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.PurchaseOrderEventRepository;
import ao.autocare.repo.PurchaseOrderRepository;
import ao.autocare.repo.PurchaseSettingsRepository;
import ao.autocare.repo.SupplierRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordens de compra: o que se encomendou, a quem, por quanto e com que autorização.
 *
 * <p>A aprovação é a parte que é fácil fazer mal. Obrigar a aprovar um filtro
 * de oito mil kwanzas faz com que, numa empresa de três pessoas, ninguém use o
 * sistema e as compras voltem ao telefone. Deixar passar uma encomenda de dez
 * milhões sem assinatura faz com que, numa de trezentas, o sistema não sirva
 * para nada. Por isso são dois números e três degraus: abaixo do primeiro quem
 * cria aprova; acima do segundo só o dono; no meio, basta quem gere.
 *
 * <p>Ao aprovar, confronta-se com o orçamento do ano. Não trava — travar uma
 * compra urgente porque o orçamento está no limite é como se param obras —,
 * mas diz-se, e fica escrito. Quem assina tem de saber o que está a assinar.
 *
 * <p>Os totais nunca vêm do ecrã. Somam-se sempre a partir das linhas: um
 * cliente que enviasse um total à escolha conseguiria aprovar uma ordem de dez
 * milhões com linhas de dez mil.
 */
@Service
public class PurchaseOrderService {

    private static final List<PurchaseOrder.Status> EM_CURSO = List.of(
            PurchaseOrder.Status.APPROVED,
            PurchaseOrder.Status.SENT,
            PurchaseOrder.Status.CONFIRMED,
            PurchaseOrder.Status.PARTIALLY_RECEIVED);

    private final PurchaseOrderRepository orders;
    private final PurchaseOrderEventRepository events;
    private final ao.autocare.repo.PurchaseOrderLineRepository linhas;
    private final PurchaseSettingsRepository settings;
    private final SupplierRepository suppliers;
    private final PartRepository parts;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final CounterService counters;
    private final BudgetService budgets;
    private final NotificationService notifications;
    private final AuditService audit;

    public PurchaseOrderService(
            PurchaseOrderRepository orders,
            PurchaseOrderEventRepository events,
            ao.autocare.repo.PurchaseOrderLineRepository linhas,
            PurchaseSettingsRepository settings,
            SupplierRepository suppliers,
            PartRepository parts,
            AssetRepository assets,
            OrganizationRepository organizations,
            MembershipRepository memberships,
            UserRepository users,
            CounterService counters,
            BudgetService budgets,
            NotificationService notifications,
            AuditService audit) {
        this.orders = orders;
        this.events = events;
        this.linhas = linhas;
        this.settings = settings;
        this.suppliers = suppliers;
        this.parts = parts;
        this.assets = assets;
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
        this.counters = counters;
        this.budgets = budgets;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ==== Configuração ======================================================

    @Transactional
    public PurchaseSettings configuracao(String orgId) {
        return settings.findById(orgId).orElseGet(() -> {
            PurchaseSettings s = new PurchaseSettings();
            s.setOrganizationId(orgId);
            return settings.save(s);
        });
    }

    @Transactional(readOnly = true)
    public ConfiguracaoView verConfiguracao(String orgId) {
        PurchaseSettings s = settings.findById(orgId).orElseGet(PurchaseSettings::new);
        return new ConfiguracaoView(s.getAutoApproveBelow(), s.getOwnerApprovalAbove(),
                s.getCurrency(), s.getDeliveryAddress(), s.getDeliveryContact(), s.getTerms());
    }

    @Transactional
    public ConfiguracaoView guardarConfiguracao(
            String orgId, String userId, ConfiguracaoRequest req) {
        if (req.autoApproveBelow() != null && req.ownerApprovalAbove() != null
                && req.autoApproveBelow().compareTo(req.ownerApprovalAbove()) > 0) {
            throw ApiException.badRequest(
                    "O limite de aprovação automática não pode ser maior do que o limite "
                            + "a partir do qual só o dono aprova.");
        }
        PurchaseSettings s = configuracao(orgId);
        s.setAutoApproveBelow(req.autoApproveBelow());
        s.setOwnerApprovalAbove(req.ownerApprovalAbove());
        if (req.currency() != null && !req.currency().isBlank()) {
            s.setCurrency(req.currency().trim().toUpperCase());
        }
        s.setDeliveryAddress(limpo(req.deliveryAddress()));
        s.setDeliveryContact(limpo(req.deliveryContact()));
        s.setTerms(limpo(req.terms()));
        settings.save(s);
        audit.record(orgId, userId, "purchase.settings", "Organization", orgId,
                "Limites de aprovação actualizados");
        return verConfiguracao(orgId);
    }

    // ==== Criar e alterar ===================================================

    @Transactional
    public OrdemView criar(String orgId, String userId, OrdemRequest req) {
        Supplier s = suppliers.findByIdAndOrganizationId(req.supplierId(), orgId)
                .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
        if (!s.isActive()) {
            throw ApiException.badRequest(
                    "O fornecedor «" + s.getName() + "» está inactivo.");
        }
        PurchaseSettings cfg = configuracao(orgId);

        PurchaseOrder o = new PurchaseOrder();
        o.setOrganization(organizations.getReferenceById(orgId));
        o.setNumber(proximoNumero(orgId));
        copiarFornecedor(o, s);
        o.setCurrency(s.getCurrency() != null ? s.getCurrency() : cfg.getCurrency());
        o.setDeliveryAddress(req.deliveryAddress() != null && !req.deliveryAddress().isBlank()
                ? req.deliveryAddress().trim()
                : cfg.getDeliveryAddress());
        o.setCreatedBy(userId);
        o.setCreatedByLabel(nomeDe(userId));
        aplicar(orgId, o, req);
        orders.save(o);

        registar(o, "CREATED", "Ordem criada com " + o.getLines().size() + " linha(s)", userId);
        audit.record(orgId, userId, "purchase_order.create", "PurchaseOrder", o.getId(),
                o.getNumber() + " · " + o.getSupplierName());
        return ver(orgId, userId, o);
    }

    @Transactional
    public OrdemView alterar(String orgId, String userId, String id, OrdemRequest req) {
        PurchaseOrder o = exigir(orgId, id);
        if (!o.getStatus().editavel()) {
            throw ApiException.conflict(
                    "Uma ordem " + o.getStatus().label().toLowerCase() + " já não se altera. "
                            + "Anule-a e faça outra.");
        }
        if (!o.getSupplierName().isBlank() && req.supplierId() != null
                && (o.getSupplier() == null || !o.getSupplier().getId().equals(req.supplierId()))) {
            Supplier s = suppliers.findByIdAndOrganizationId(req.supplierId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
            copiarFornecedor(o, s);
        }
        // Uma ordem que foi rejeitada e se corrige volta a rascunho: quem a
        // rejeitou tem de a ver outra vez.
        if (o.getStatus() == PurchaseOrder.Status.REJECTED) {
            o.setStatus(PurchaseOrder.Status.DRAFT);
            o.setRejectedReason(null);
        }
        aplicar(orgId, o, req);
        audit.record(orgId, userId, "purchase_order.update", "PurchaseOrder", o.getId(),
                o.getNumber());
        return ver(orgId, userId, o);
    }

    private void aplicar(String orgId, PurchaseOrder o, OrdemRequest req) {
        o.setExpectedAt(req.expectedAt());
        o.setNotes(limpo(req.notes()));
        o.setDiscountAmount(req.discountAmount() != null ? req.discountAmount() : BigDecimal.ZERO);
        o.setTaxAmount(req.taxAmount() != null ? req.taxAmount() : BigDecimal.ZERO);

        o.getLines().clear();
        int ordem = 0;
        for (LinhaRequest l : req.lines() == null ? List.<LinhaRequest>of() : req.lines()) {
            PurchaseOrderLine linha = new PurchaseOrderLine();
            linha.setDescription(l.description().trim());
            linha.setSpecification(limpo(l.specification()));
            linha.setSupplierPartNumber(limpo(l.supplierPartNumber()));
            linha.setQuantity(l.quantity());
            linha.setUnit(limpo(l.unit()));
            linha.setUnitPrice(l.unitPrice() != null ? l.unitPrice() : BigDecimal.ZERO);
            linha.setSortOrder(ordem++);
            if (l.partId() != null && !l.partId().isBlank()) {
                Part p = parts.findByIdAndOrganizationId(l.partId(), orgId)
                        .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
                linha.setPart(p);
                if (linha.getUnit() == null) {
                    linha.setUnit(p.getUnit());
                }
                if (linha.getSpecification() == null) {
                    linha.setSpecification(p.getSpecification());
                }
            }
            if (l.assetId() != null && !l.assetId().isBlank()) {
                Asset a = assets.findByIdAndOrganizationId(l.assetId(), orgId)
                        .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
                linha.setAsset(a);
                linha.setAssetTag(a.getTag());
            }
            o.addLine(linha);
        }
        if (o.getLines().isEmpty()) {
            throw ApiException.badRequest("Uma ordem de compra sem linhas não é uma ordem.");
        }
        o.recalcular(retencaoDe(o));
    }

    /** A retenção na fonte deste fornecedor, em percentagem. */
    private static BigDecimal retencaoDe(PurchaseOrder o) {
        return o.getSupplier() != null ? o.getSupplier().getWithholdingTax() : null;
    }

    private static void copiarFornecedor(PurchaseOrder o, Supplier s) {
        o.setSupplier(s);
        o.setSupplierName(s.getName());
        o.setSupplierTaxId(s.getTaxId());
        o.setSupplierEmail(s.getEmail());
        o.setSupplierIban(s.getIban());
        o.setSupplierBank(s.getBankName());
        o.setPaymentTerms(s.getPaymentTerms());
    }

    /** OC-2026-0001: sequencial por empresa e por ano. */
    private String proximoNumero(String orgId) {
        int ano = LocalDate.now(ZoneOffset.UTC).getYear();
        long n = counters.next(orgId, "purchase_order_" + ano);
        return String.format("OC-%d-%04d", ano, n);
    }

    /** Liga a ordem ao pedido de cotação de que nasceu. */
    @Transactional
    public void ligarAoPedido(String orgId, String orderId, String rfqId) {
        exigir(orgId, orderId).setRfqId(rfqId);
    }

    // ==== O percurso ========================================================

    @Transactional
    public OrdemView submeter(String orgId, String userId, String id) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() != PurchaseOrder.Status.DRAFT
                && o.getStatus() != PurchaseOrder.Status.REJECTED) {
            throw ApiException.conflict("Esta ordem já foi submetida.");
        }
        o.recalcular(retencaoDe(o));

        // Abaixo do limite, quem cria aprova. É isto que impede a aprovação de
        // se tornar uma chatice que ninguém usa.
        PurchaseSettings cfg = configuracao(orgId);
        if (cfg.getAutoApproveBelow() != null
                && o.getTotal().compareTo(cfg.getAutoApproveBelow()) < 0) {
            aprovarInterno(o, userId, "Abaixo do limite de aprovação automática");
            audit.record(orgId, userId, "purchase_order.auto_approve", "PurchaseOrder", o.getId(),
                    o.getNumber() + " · " + o.getTotal() + " " + o.getCurrency());
            return ver(orgId, userId, o);
        }

        o.setStatus(PurchaseOrder.Status.PENDING_APPROVAL);
        registar(o, "SUBMITTED", "Enviada para aprovação", userId);
        avisarAprovadores(o);
        audit.record(orgId, userId, "purchase_order.submit", "PurchaseOrder", o.getId(),
                o.getNumber());
        return ver(orgId, userId, o);
    }

    @Transactional
    public OrdemView aprovar(String orgId, String userId, String id, String nota) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() != PurchaseOrder.Status.PENDING_APPROVAL) {
            throw ApiException.conflict("Esta ordem não está à espera de aprovação.");
        }
        String impedimento = porqueNaoAprova(orgId, userId, o);
        if (impedimento != null) {
            throw ApiException.forbidden(impedimento);
        }
        aprovarInterno(o, userId, nota);
        audit.record(orgId, userId, "purchase_order.approve", "PurchaseOrder", o.getId(),
                o.getNumber() + " · " + o.getTotal() + " " + o.getCurrency());
        return ver(orgId, userId, o);
    }

    private void aprovarInterno(PurchaseOrder o, String userId, String nota) {
        o.setStatus(PurchaseOrder.Status.APPROVED);
        o.setApprovedBy(userId);
        o.setApprovedByLabel(nomeDe(userId));
        o.setApprovedAt(Instant.now());
        o.setRejectedReason(null);
        registar(o, "APPROVED", nota, userId);
    }

    @Transactional
    public OrdemView rejeitar(String orgId, String userId, String id, String motivo) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() != PurchaseOrder.Status.PENDING_APPROVAL) {
            throw ApiException.conflict("Esta ordem não está à espera de aprovação.");
        }
        if (motivo == null || motivo.isBlank()) {
            // Rejeitar sem dizer porquê devolve a ordem a quem a fez sem lhe
            // dizer o que corrigir -- e ela volta igual.
            throw ApiException.badRequest("Diga porque está a rejeitar: quem a fez tem de saber "
                    + "o que corrigir.");
        }
        o.setStatus(PurchaseOrder.Status.REJECTED);
        o.setRejectedReason(motivo.trim());
        registar(o, "REJECTED", motivo.trim(), userId);
        avisarCriador(o, "Ordem de compra rejeitada",
                o.getNumber() + " foi rejeitada: " + motivo.trim());
        audit.record(orgId, userId, "purchase_order.reject", "PurchaseOrder", o.getId(),
                o.getNumber() + " · " + motivo.trim());
        return ver(orgId, userId, o);
    }

    @Transactional
    public OrdemView marcarEnviada(String orgId, String userId, String id, String para) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() != PurchaseOrder.Status.APPROVED && o.getStatus() != PurchaseOrder.Status.SENT) {
            throw ApiException.conflict(
                    "Só se envia uma ordem aprovada. Esta está «" + o.getStatus().label() + "».");
        }
        o.setStatus(PurchaseOrder.Status.SENT);
        o.setSentAt(Instant.now());
        o.setSentTo(para);
        registar(o, "SENT", "Enviada para " + para, userId);
        return ver(orgId, userId, o);
    }

    @Transactional
    public OrdemView confirmar(String orgId, String userId, String id, String nota) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() != PurchaseOrder.Status.SENT) {
            throw ApiException.conflict("Só se confirma uma ordem que já foi enviada.");
        }
        o.setStatus(PurchaseOrder.Status.CONFIRMED);
        o.setConfirmedAt(Instant.now());
        registar(o, "CONFIRMED", nota, userId);
        audit.record(orgId, userId, "purchase_order.confirm", "PurchaseOrder", o.getId(),
                o.getNumber());
        return ver(orgId, userId, o);
    }

    @Transactional
    public OrdemView anular(String orgId, String userId, String id, String motivo) {
        PurchaseOrder o = exigir(orgId, id);
        if (o.getStatus() == PurchaseOrder.Status.RECEIVED
                || o.getStatus() == PurchaseOrder.Status.CLOSED) {
            throw ApiException.conflict("Uma ordem já recebida não se anula.");
        }
        o.setStatus(PurchaseOrder.Status.CANCELLED);
        o.setClosedAt(Instant.now());
        registar(o, "CANCELLED", motivo, userId);
        audit.record(orgId, userId, "purchase_order.cancel", "PurchaseOrder", o.getId(),
                o.getNumber() + (motivo != null ? " · " + motivo : ""));
        return ver(orgId, userId, o);
    }

    // ==== Quem aprova =======================================================

    /**
     * Porque é que esta pessoa não pode aprovar esta ordem.
     *
     * @return {@code null} quando pode
     */
    private String porqueNaoAprova(String orgId, String userId, PurchaseOrder o) {
        PurchaseSettings cfg = configuracao(orgId);
        MembershipRole papel = papelDe(orgId, userId);
        if (papel == null) {
            return "Não pertence a esta empresa.";
        }
        boolean dono = papel == MembershipRole.OWNER;
        if (cfg.getOwnerApprovalAbove() != null
                && o.getTotal().compareTo(cfg.getOwnerApprovalAbove()) > 0 && !dono) {
            return "Acima de " + dinheiro(cfg.getOwnerApprovalAbove(), o.getCurrency())
                    + " só o dono aprova. Esta ordem é de "
                    + dinheiro(o.getTotal(), o.getCurrency()) + ".";
        }
        if (papel != MembershipRole.OWNER && papel != MembershipRole.MANAGER) {
            return "Aprovar ordens de compra é de quem gere.";
        }
        return null;
    }

    /** Quem tem de aprovar uma ordem deste valor, dito em português. */
    private String quemAprova(String orgId, PurchaseOrder o) {
        PurchaseSettings cfg = configuracao(orgId);
        if (cfg.getAutoApproveBelow() != null
                && o.getTotal().compareTo(cfg.getAutoApproveBelow()) < 0) {
            return "Abaixo de " + dinheiro(cfg.getAutoApproveBelow(), o.getCurrency())
                    + ": aprova-se sozinha.";
        }
        if (cfg.getOwnerApprovalAbove() != null
                && o.getTotal().compareTo(cfg.getOwnerApprovalAbove()) > 0) {
            return "Acima de " + dinheiro(cfg.getOwnerApprovalAbove(), o.getCurrency())
                    + ": tem de ser o dono.";
        }
        return "Aprova quem gere.";
    }

    /**
     * O que esta ordem faz ao orçamento do ano.
     *
     * <p>Não trava nada: travar uma compra urgente porque o orçamento está no
     * limite é como se param obras. Mas quem assina tem de saber o que está a
     * assinar, e por isso diz-se — e fica no histórico.
     */
    private String impactoNoOrcamento(String orgId, PurchaseOrder o) {
        try {
            int ano = LocalDate.now(ZoneOffset.UTC).getYear();
            for (var b : budgets.list(orgId, ano)) {
                boolean geral = "ORG".equals(b.scope())
                        && (Budget.Category.MAINTENANCE.name().equals(b.category())
                                || Budget.Category.TOTAL.name().equals(b.category()));
                if (!geral || b.amount() == null || b.amount().signum() <= 0) {
                    continue;
                }
                BigDecimal depois = (b.actual() == null ? BigDecimal.ZERO : b.actual())
                        .add(o.getTotal());
                int pct = depois.multiply(new BigDecimal("100"))
                        .divide(b.amount(), 0, java.math.RoundingMode.HALF_UP).intValue();
                String aviso = pct > 100
                        ? " — ultrapassa o orçamento do ano"
                        : pct >= 90 ? " — perto do limite do ano" : "";
                return "Com esta ordem, a rubrica «" + b.categoryLabel() + "» fica a "
                        + pct + "% do orçamento de " + ano + aviso + ".";
            }
        } catch (RuntimeException e) {
            // Sem orçamento definido não há nada a dizer, e isso não é um erro.
            return null;
        }
        return null;
    }

    // ==== Leitura ===========================================================

    @Transactional(readOnly = true)
    public List<OrdemView> listar(String orgId, String userId, String estado) {
        List<PurchaseOrder> lista = estado == null || estado.isBlank()
                ? orders.findAllForOrg(orgId)
                : orders.findByEstados(orgId, List.of(PurchaseOrder.Status.valueOf(estado)));
        List<OrdemView> vistas = new ArrayList<>();
        for (PurchaseOrder o : lista) {
            vistas.add(ver(orgId, userId, o));
        }
        return vistas;
    }

    @Transactional(readOnly = true)
    public OrdemView obter(String orgId, String userId, String id) {
        return ver(orgId, userId, exigir(orgId, id));
    }

    private OrdemView ver(String orgId, String userId, PurchaseOrder o) {
        List<LinhaView> linhas = o.getLines().stream()
                .map(l -> new LinhaView(
                        l.getId(),
                        l.getPart() != null ? l.getPart().getId() : null,
                        l.getDescription(), l.getSpecification(), l.getSupplierPartNumber(),
                        l.getQuantity(), l.getUnit(), l.getUnitPrice(), l.getLineTotal(),
                        l.getAsset() != null ? l.getAsset().getId() : null, l.getAssetTag(),
                        l.getReceivedQuantity(), l.porReceber()))
                .toList();

        List<EventoView> historico = events.findByOrderIdOrderByAtAsc(o.getId()).stream()
                .map(e -> new EventoView(e.getKind(), rotuloDoEvento(e.getKind()), e.getNote(),
                        e.getByLabel(), e.getAt()))
                .toList();

        String impedimento = o.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL
                ? porqueNaoAprova(orgId, userId, o) : null;
        MembershipRole papel = papelDe(orgId, userId);
        boolean gere = papel == MembershipRole.OWNER || papel == MembershipRole.MANAGER;

        Podem podem = new Podem(
                o.getStatus().editavel() && gere,
                (o.getStatus() == PurchaseOrder.Status.DRAFT
                        || o.getStatus() == PurchaseOrder.Status.REJECTED) && gere,
                o.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL && impedimento == null,
                o.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL && gere,
                (o.getStatus() == PurchaseOrder.Status.APPROVED
                        || o.getStatus() == PurchaseOrder.Status.SENT) && gere,
                o.getStatus() == PurchaseOrder.Status.SENT && gere,
                o.getStatus() != PurchaseOrder.Status.RECEIVED
                        && o.getStatus() != PurchaseOrder.Status.CLOSED
                        && o.getStatus() != PurchaseOrder.Status.CANCELLED && gere,
                impedimento);

        return new OrdemView(
                o.getId(), o.getNumber(),
                o.getSupplier() != null ? o.getSupplier().getId() : null,
                o.getSupplierName(), o.getSupplierTaxId(), o.getSupplierEmail(),
                o.getSupplierIban(), o.getSupplierBank(), o.getPaymentTerms(),
                o.getStatus().name(), o.getStatus().label(), o.getCurrency(),
                o.getSubtotal(), o.getDiscountAmount(), o.getTaxAmount(),
                o.getWithholdingAmount(), o.getTotal(), o.getPayable(),
                o.getExpectedAt(), o.getDeliveryAddress(), o.getNotes(),
                o.getCreatedByLabel(), o.getCreatedAt(),
                o.getApprovedByLabel(), o.getApprovedAt(), o.getRejectedReason(),
                o.getSentAt(), o.getSentTo(), o.getConfirmedAt(),
                linhas, historico, podem,
                quemAprova(orgId, o),
                o.getStatus() == PurchaseOrder.Status.PENDING_APPROVAL
                        ? impactoNoOrcamento(orgId, o) : null);
    }

    /**
     * Quanto está encomendado e ainda não chegou, por artigo.
     *
     * <p>É isto que faz a lista de compras deixar de mandar comprar outra vez
     * o que já vai a caminho — e é o erro que custa dinheiro duas vezes: no
     * dobro das peças e no espaço que elas ocupam no armazém durante um ano.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, BigDecimal> porReceberPorPeca(String orgId) {
        java.util.Map<String, BigDecimal> mapa = new java.util.HashMap<>();
        for (Object[] r : linhas.porReceberPorPeca(orgId, EM_CURSO)) {
            mapa.put((String) r[0], (BigDecimal) r[1]);
        }
        return mapa;
    }

    /** Usado pelo PDF e pelo envio, que precisam da entidade e não da vista. */
    @Transactional(readOnly = true)
    public PurchaseOrder entidade(String orgId, String id) {
        PurchaseOrder o = exigir(orgId, id);
        o.getLines().size();
        return o;
    }

    // ==== Avisos ============================================================

    private void avisarAprovadores(PurchaseOrder o) {
        notifications.notifyManagers(NotificationService.Draft.of(
                o.getOrganization().getId(),
                AlertCategory.EXPENSE,
                AlertSeverity.WARNING,
                "Ordem de compra à espera de aprovação: " + o.getNumber(),
                o.getSupplierName() + " · " + dinheiro(o.getTotal(), o.getCurrency())
                        + ". " + quemAprova(o.getOrganization().getId(), o),
                "purchase_order", o.getId(), "/compras/" + o.getId()));
    }

    private void avisarCriador(PurchaseOrder o, String titulo, String corpo) {
        if (o.getCreatedBy() == null) {
            return;
        }
        users.findById(o.getCreatedBy()).ifPresent(u ->
                notifications.notifyUser(u, NotificationService.Draft.of(
                        o.getOrganization().getId(),
                        AlertCategory.EXPENSE, AlertSeverity.WARNING,
                        titulo, corpo, "purchase_order", o.getId(), "/compras/" + o.getId())));
    }

    // ==== Utilitários =======================================================

    private PurchaseOrder exigir(String orgId, String id) {
        return orders.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem de compra não encontrada."));
    }

    private void registar(PurchaseOrder o, String kind, String nota, String userId) {
        PurchaseOrderEvent e = new PurchaseOrderEvent();
        e.setOrder(o);
        e.setKind(kind);
        e.setNote(nota);
        e.setByUser(userId);
        e.setByLabel(nomeDe(userId));
        e.setAt(Instant.now());
        events.save(e);
    }

    private static String rotuloDoEvento(String kind) {
        return switch (kind) {
            case "CREATED" -> "Criada";
            case "SUBMITTED" -> "Submetida";
            case "APPROVED" -> "Aprovada";
            case "REJECTED" -> "Rejeitada";
            case "SENT" -> "Enviada";
            case "CONFIRMED" -> "Confirmada pelo fornecedor";
            case "CANCELLED" -> "Anulada";
            default -> "Nota";
        };
    }

    private String nomeDe(String userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(u -> u.getName()).orElse(null);
    }

    private MembershipRole papelDe(String orgId, String userId) {
        if (userId == null) {
            return null;
        }
        return memberships.findByUserIdAndOrganizationId(userId, orgId)
                .map(m -> m.getRole()).orElse(null);
    }

    static String dinheiro(BigDecimal v, String moeda) {
        if (v == null) {
            return "—";
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " " + moeda;
    }

    private static String limpo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Os estados que contam como «já encomendado». */
    public static List<PurchaseOrder.Status> estadosEmCurso() {
        return EM_CURSO;
    }
}
