package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.Failure;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Repair;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderLabor;
import ao.autocare.domain.WorkOrderPart;
import ao.autocare.domain.WorkOrderTask;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.part.StockService;
import ao.autocare.modules.plan.AssetPlanService;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CompleteRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CreateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.ExternalServiceInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.InsightView;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.WorkOrderQuote;
import ao.autocare.domain.WorkOrderStatusHistory;
import ao.autocare.domain.enums.Enums.WorkOrderExecution;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.ApprovalRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.DiagnosisRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.QuoteRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FromDueRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.LaborInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.PartInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.StartRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.TaskInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.UpdateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderSummary;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FailureRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.RepairRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {

    private static final String WO_COUNTER = "work_order";

    private final WorkOrderRepository workOrders;
    private final AssetRepository assets;
    private final AssetPlanTaskRepository assetPlanTasks;
    private final FailureRepository failures;
    private final RepairRepository repairs;
    private final MembershipRepository memberships;
    private final CounterService counters;
    private final StockService stock;
    private final AssetPlanService assetPlans;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final WorkOrderIntelligence intelligence;
    private final ao.autocare.modules.meter.MeterService meterService;
    private final WorkOrderWorkflow workflow;
    private final ao.autocare.repo.SupplierRepository suppliers;
    private final ao.autocare.repo.LocationRepository locations;
    private final ao.autocare.repo.DriverRepository drivers;

    public WorkOrderService(
            WorkOrderRepository workOrders,
            AssetRepository assets,
            AssetPlanTaskRepository assetPlanTasks,
            FailureRepository failures,
            RepairRepository repairs,
            MembershipRepository memberships,
            CounterService counters,
            StockService stock,
            AssetPlanService assetPlans,
            OrganizationRepository organizations,
            UserRepository users,
            NotificationService notifications,
            AuditService audit,
            WorkOrderIntelligence intelligence,
            ao.autocare.repo.LocationRepository locations,
            ao.autocare.repo.DriverRepository drivers,
            WorkOrderWorkflow workflow,
            ao.autocare.repo.SupplierRepository suppliers,
            ao.autocare.modules.meter.MeterService meterService) {
        this.workOrders = workOrders;
        this.assets = assets;
        this.assetPlanTasks = assetPlanTasks;
        this.failures = failures;
        this.repairs = repairs;
        this.memberships = memberships;
        this.counters = counters;
        this.stock = stock;
        this.assetPlans = assetPlans;
        this.organizations = organizations;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.intelligence = intelligence;
        this.meterService = meterService;
        this.workflow = workflow;
        this.suppliers = suppliers;
        this.locations = locations;
        this.drivers = drivers;
    }

    // ==== Consultas =================================================
    @Transactional(readOnly = true)
    public PagedResponse<WorkOrderSummary> list(
            String orgId, String status, String assetId, String assignedToUserId, Pageable pageable) {
        if (assignedToUserId != null && !assignedToUserId.isBlank()) {
            return PagedResponse.of(workOrders
                    .findByOrganizationIdAndAssignedToIdOrderByOpenedAtDesc(
                            orgId, assignedToUserId, pageable)
                    .map(WorkOrderSummary::of));
        }
        if (assetId != null && !assetId.isBlank()) {
            requireAsset(orgId, assetId);
            return PagedResponse.of(workOrders.findByAssetIdOrderByOpenedAtDesc(assetId, pageable)
                    .map(WorkOrderSummary::of));
        }
        if (status != null && !status.isBlank()) {
            WorkOrderStatus s = parseStatus(status);
            return PagedResponse.of(workOrders
                    .findByOrganizationIdAndStatusOrderByOpenedAtDesc(orgId, s, pageable)
                    .map(WorkOrderSummary::of));
        }
        return PagedResponse.of(workOrders.findByOrganizationIdOrderByOpenedAtDesc(orgId, pageable)
                .map(WorkOrderSummary::of));
    }

    @Transactional(readOnly = true)
    public WorkOrderView get(String orgId, String id) {
        WorkOrder w = require(orgId, id);
        return view(w);
    }

    /**
     * A vista completa: avisos + estados para onde a ordem pode seguir.
     *
     * <p>Os proximos estados vao no JSON para o ecra nao ter de replicar a
     * maquina de estados. Duas copias da mesma regra acabam sempre por
     * discordar -- e a que o utilizador ve seria a errada.
     */
    private WorkOrderView view(WorkOrder w) {
        return WorkOrderView.of(w, insights(w),
                workflow.nextFrom(w.getStatus()).stream()
                        .map(java.lang.Enum::name).toList());
    }

    private List<InsightView> insights(WorkOrder w) {
        return intelligence.insights(w).stream()
                .map(i -> new InsightView(i.code(), i.severity(), i.title(), i.detail()))
                .toList();
    }

    // ==== Servicos externos ================================================
    /**
     * Acrescenta um servico feito por uma oficina de fora.
     *
     * <p>Em Angola grande parte da manutencao pesada vai para fora. Sem estas
     * linhas, o custo da ordem ficava sistematicamente abaixo do real -- e
     * decidia-se reparar ou substituir com o numero errado.
     */
    @Transactional
    public WorkOrderView addExternalService(
            String orgId, String userId, String id, ExternalServiceInput in) {

        WorkOrder w = require(orgId, id);
        if (w.getStatus() == WorkOrderStatus.CANCELLED) {
            throw ApiException.conflict("Esta ordem esta anulada.");
        }
        ao.autocare.domain.WorkOrderExternalService s =
                new ao.autocare.domain.WorkOrderExternalService();
        s.setSupplier(in.supplier().trim());
        s.setDescription(in.description().trim());
        s.setInvoiceNumber(blankToNull(in.invoiceNumber()));
        s.setCost(in.cost());
        if (in.currency() != null && !in.currency().isBlank()) {
            s.setCurrency(in.currency().trim().toUpperCase());
        } else {
            s.setCurrency(w.getCurrency());
        }
        s.setPerformedAt(in.performedAt());
        s.setWarrantyMonths(in.warrantyMonths());
        s.setNotes(blankToNull(in.notes()));
        // A data limite da garantia e calculada aqui, e nao so no @PrePersist:
        // a vista devolvida a quem acabou de registar o servico tem de ja a
        // mostrar, senao parece que a garantia nao ficou guardada.
        if (in.warrantyMonths() != null && in.warrantyMonths() > 0) {
            Instant base = in.performedAt() != null ? in.performedAt() : Instant.now();
            s.setWarrantyUntil(base.plus(java.time.Duration.ofDays(30L * in.warrantyMonths())));
        }
        w.addService(s);

        intelligence.recalculate(w);
        workOrders.flush();
        audit.record(orgId, userId, "work_order.external_service", "WorkOrder", w.getId(),
                w.getNumber() + " - " + s.getSupplier() + " - " + s.getCost()
                        + " " + s.getCurrency());
        return view(w);
    }

    @Transactional
    public WorkOrderView removeExternalService(
            String orgId, String userId, String id, String serviceId) {

        WorkOrder w = require(orgId, id);
        boolean removido = w.getServices().removeIf(s -> s.getId().equals(serviceId));
        if (!removido) {
            throw ApiException.notFound("Servico nao encontrado nesta ordem.");
        }
        intelligence.recalculate(w);
        audit.record(orgId, userId, "work_order.external_service_remove", "WorkOrder",
                w.getId(), w.getNumber());
        return view(w);
    }

    // ==== Criação ==================================================
    @Transactional
    public WorkOrderView create(String orgId, String userId, CreateWorkOrderRequest req) {
        Asset asset = requireAsset(orgId, req.assetId());

        WorkOrder w = newWorkOrder(orgId, userId, asset, req.type(), req.title().trim(),
                req.description(), req.priority(), req.scheduledFor(),
                req.assignedToUserId(), req.assignedToLabel());

        int order = 1;
        if (req.tasks() != null) {
            for (TaskInput ti : req.tasks()) {
                WorkOrderTask t = new WorkOrderTask();
                t.setTitle(ti.title().trim());
                t.setSystemName(blankToNull(ti.systemName()));
                t.setInstructions(blankToNull(ti.instructions()));
                t.setSortOrder(order++);
                w.addTask(t);
            }
        }
        applyFicha(orgId, w, req);
        workOrders.save(w);

        if (req.failure() != null) {
            recordFailure(orgId, asset, w, req.failure(), asset);
        }

        audit.record(orgId, userId, "work_order.create", "WorkOrder", w.getId(),
                w.getNumber() + " · " + asset.getTag() + " · " + w.getType());
        return view(w);
    }

    @Transactional
    public WorkOrderView fromDue(String orgId, String userId, FromDueRequest req) {
        Asset asset = requireAsset(orgId, req.assetId());
        List<PlanTaskStatus> wanted = new ArrayList<>();
        if (req.statuses() == null || req.statuses().isEmpty()) {
            wanted.add(PlanTaskStatus.OVERDUE);
            wanted.add(PlanTaskStatus.DUE_SOON);
        } else {
            for (String s : req.statuses()) {
                wanted.add(PlanTaskStatus.valueOf(s.toUpperCase(Locale.ROOT)));
            }
        }

        List<AssetPlanTask> due = assetPlanTasks.findForAsset(orgId, asset.getId()).stream()
                .filter(t -> wanted.contains(t.getStatus()))
                .toList();
        if (due.isEmpty()) {
            throw ApiException.badRequest("Este ativo não tem tarefas de plano nesse estado.");
        }

        WorkOrderPriority priority = req.priority() != null ? req.priority()
                : (due.stream().anyMatch(t -> t.getStatus() == PlanTaskStatus.OVERDUE)
                    ? WorkOrderPriority.HIGH : WorkOrderPriority.NORMAL);

        WorkOrder w = newWorkOrder(orgId, userId, asset, WorkOrderType.PREVENTIVE,
                "Manutenção preventiva — " + asset.getTag(),
                due.size() + " tarefa(s) de plano a executar.", priority,
                req.scheduledFor(), req.assignedToUserId(), null);

        int order = 1;
        for (AssetPlanTask apt : due) {
            WorkOrderTask t = new WorkOrderTask();
            t.setAssetPlanTask(apt);
            t.setTitle(apt.getTitle());
            t.setSystemName(apt.getSystemName());
            t.setSortOrder(order++);
            w.addTask(t);
        }
        workOrders.save(w);

        audit.record(orgId, userId, "work_order.from_due", "WorkOrder", w.getId(),
                w.getNumber() + " · " + asset.getTag() + " · " + due.size() + " tarefas");
        return view(w);
    }

    @Transactional
    public WorkOrderView update(String orgId, String userId, String id, UpdateWorkOrderRequest req) {
        WorkOrder w = require(orgId, id);
        ensureEditable(w);
        if (req.title() != null && !req.title().isBlank()) w.setTitle(req.title().trim());
        if (req.description() != null) w.setDescription(blankToNull(req.description()));
        if (req.priority() != null) w.setPriority(req.priority());
        if (req.scheduledFor() != null) {
            w.setScheduledFor(req.scheduledFor());
            if (w.getStatus() == WorkOrderStatus.OPEN) {
                transition(w, WorkOrderStatus.PLANNED, userId, "Agendada");
            }
        }
        if (req.assignedToUserId() != null || req.assignedToLabel() != null) {
            assign(w, orgId, req.assignedToUserId(), req.assignedToLabel());
        }
        audit.record(orgId, userId, "work_order.update", "WorkOrder", w.getId(), w.getNumber());
        return view(w);
    }

    // ==== Ciclo de vida ===========================================
    @Transactional
    public WorkOrderView start(String orgId, String userId, String id, StartRequest req) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus() != WorkOrderStatus.OPEN && w.getStatus() != WorkOrderStatus.PLANNED) {
            throw ApiException.conflict("A ordem já foi iniciada ou está fechada.");
        }
        Instant when = req.startedAt() != null ? req.startedAt() : Instant.now();
        // Passa pelo mesmo caminho validado das restantes: dois caminhos
        // dariam estados impossiveis sem historico que explicasse como la
        // chegaram.
        transition(w, WorkOrderStatus.IN_PROGRESS, userId, null);
        w.setStartedAt(when);
        if (Boolean.TRUE.equals(req.stopAsset()) || w.getType() == WorkOrderType.CORRECTIVE) {
            w.setDowntimeStart(when);
            Asset a = w.getAsset();
            a.setStatus(w.getType() == WorkOrderType.CORRECTIVE ? AssetStatus.DOWN : AssetStatus.MAINTENANCE);
        }
        audit.record(orgId, userId, "work_order.start", "WorkOrder", w.getId(), w.getNumber());
        return view(w);
    }

    @Transactional
    public WorkOrderView complete(String orgId, String userId, String id, CompleteRequest req) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus() == WorkOrderStatus.DONE || w.getStatus() == WorkOrderStatus.VERIFIED
                || w.getStatus() == WorkOrderStatus.CANCELLED) {
            throw ApiException.conflict("A ordem já está fechada.");
        }
        Instant when = req.completedAt() != null ? req.completedAt() : Instant.now();
        if (w.getStartedAt() == null) w.setStartedAt(when);

        transition(w, WorkOrderStatus.DONE, userId, null);
        w.setCompletedAt(when);
        w.setResolution(blankToNull(req.resolution()));
        if (req.meterValue() != null) {
            w.setMeterValue(req.meterValue());
            // O contador lido ao fechar é uma leitura a sério: atualiza a ficha
            // e faz andar os intervalos de manutenção.
            meterService.recordFromWorkOrder(w.getAsset(), req.meterValue(), when, userId,
                    "Leitura ao concluir a OM " + w.getNumber());
        }

        if (w.getDowntimeStart() != null && w.getDowntimeEnd() == null) {
            w.setDowntimeEnd(req.downtimeEnd() != null ? req.downtimeEnd() : when);
        }

        // 1) concluir as tarefas de plano associadas -> repõe o relógio
        for (WorkOrderTask t : w.getTasks()) {
            t.setDone(true);
            if (t.getDoneAt() == null) t.setDoneAt(when);
            if (t.getAssetPlanTask() != null) {
                assetPlans.markTaskCompleted(t.getAssetPlanTask(), when, w.getMeterValue(),
                        w.getAssignedToLabel(), "OM " + w.getNumber());
            }
        }

        // 2) consumir peças
        BigDecimal partsCost = BigDecimal.ZERO;
        for (WorkOrderPart p : w.getParts()) {
            if (!p.isConsumed() && p.getPart() != null && p.getWarehouse() != null) {
                stock.consumeForWorkOrder(orgId, userId, p.getPart().getId(), p.getWarehouse().getId(),
                        p.getQuantity(), w.getId(), "OM " + w.getNumber());
                p.setConsumed(true);
            }
            if (p.getUnitCost() != null) {
                partsCost = partsCost.add(p.getUnitCost().multiply(p.getQuantity()));
            }
        }
        w.setTotalPartsCost(partsCost);

        // 3) contas da ordem: mão de obra valorizada, peças, oficina externa,
        //    paragem. Sem isto o "custo de manutenção" do ativo ficava numa
        //    fração do que a empresa pagou.
        if (req.closingMeterValue() != null) {
            w.setClosingMeterValue(req.closingMeterValue());
        }
        if (req.rootCause() != null) {
            w.setRootCause(blankToNull(req.rootCause()));
        }
        if (req.correctiveAction() != null) {
            w.setCorrectiveAction(blankToNull(req.correctiveAction()));
        }
        if (req.warrantyRecovered() != null) {
            w.setWarrantyRecovered(req.warrantyRecovered());
        }
        intelligence.recalculate(w);
        intelligence.stampSla(w, when);
        BigDecimal laborHours = w.getTotalLaborHours();

        // 4) registar reparação (MTTR)
        Instant repairStart = w.getDowntimeStart() != null ? w.getDowntimeStart() : w.getStartedAt();
        // Em milissegundos: por minutos, o arredondamento da base de dados podia comer
        // um minuto inteiro em cada reparação e baixar o MTTR.
        double repairHours = Math.max(Duration.between(repairStart, when).toMillis() / 3_600_000.0, 0.0);
        Repair repair = new Repair();
        repair.setOrganization(organizations.getReferenceById(orgId));
        repair.setAsset(w.getAsset());
        repair.setWorkOrder(w);
        repair.setStartedAt(repairStart);
        repair.setFinishedAt(when);
        repair.setRepairHours(BigDecimal.valueOf(repairHours).setScale(2, java.math.RoundingMode.HALF_UP));
        failures.findFirstByWorkOrderId(w.getId()).ifPresent(repair::setFailure);
        repairs.save(repair);

        // 5) ativo volta a operacional
        if (req.assetBackToOperational() == null || req.assetBackToOperational()) {
            w.getAsset().setStatus(AssetStatus.OPERATIONAL);
        }

        // 6) recalcular o plano do ativo
        assetPlans.recomputeForAsset(w.getAsset().getId());

        audit.record(orgId, userId, "work_order.complete", "WorkOrder", w.getId(),
                w.getNumber() + " · " + laborHours + " h · " + w.getParts().size() + " peças");
        return view(w);
    }

    @Transactional
    public WorkOrderView verify(String orgId, String userId, String id) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus() != WorkOrderStatus.DONE) {
            throw ApiException.conflict("Só é possível verificar uma ordem concluída.");
        }
        transition(w, WorkOrderStatus.VERIFIED, userId, null);
        w.setVerifiedAt(Instant.now());
        w.setApprovedBy(users.getReferenceById(userId));
        w.setVerifiedBy(users.getReferenceById(userId));
        audit.record(orgId, userId, "work_order.verify", "WorkOrder", w.getId(), w.getNumber());
        return view(w);
    }

    @Transactional
    public WorkOrderView cancel(String orgId, String userId, String id, String reason) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus() == WorkOrderStatus.VERIFIED) {
            throw ApiException.conflict("Não é possível cancelar uma ordem já verificada.");
        }
        if (reason == null || reason.trim().length() < 5) {
            // Uma ordem anulada sem razao e indistinguivel de trabalho escondido.
            throw ApiException.badRequest(
                    "Explique porque e que a ordem foi anulada. Fica registado.");
        }
        transition(w, WorkOrderStatus.CANCELLED, userId, reason.trim());
        w.setCancellationReason(reason.trim());
        w.setResolution("Cancelada: " + reason.trim());
        if (w.getAsset().getStatus() == AssetStatus.MAINTENANCE || w.getAsset().getStatus() == AssetStatus.DOWN) {
            w.getAsset().setStatus(AssetStatus.OPERATIONAL);
        }
        audit.record(orgId, userId, "work_order.cancel", "WorkOrder", w.getId(), w.getNumber());
        return view(w);
    }

    // ==== Detalhes ================================================
    @Transactional
    public WorkOrderView addLabor(String orgId, String userId, String id, LaborInput in) {
        WorkOrder w = require(orgId, id);
        WorkOrderLabor l = new WorkOrderLabor();
        l.setTechnicianLabel(blankToNull(in.technicianLabel()));
        l.setHours(in.hours());
        l.setHourlyRate(in.hourlyRate());
        l.setWorkedOn(in.workedOn() != null ? in.workedOn() : Instant.now());
        l.setNotes(blankToNull(in.notes()));
        w.addLabor(l);
        audit.record(orgId, userId, "work_order.labor", "WorkOrder", w.getId(),
                w.getNumber() + " · +" + in.hours() + " h");
        // Sem isto, os totais so mudavam quando entrava um servico externo -- e
        // a ficha mostrava horas registadas com custo em branco.
        intelligence.recalculate(w);
        return view(w);
    }

    @Transactional
    public WorkOrderView addPart(String orgId, String userId, String id, PartInput in) {
        WorkOrder w = require(orgId, id);
        WorkOrderPart p = new WorkOrderPart();
        if (in.partId() != null && !in.partId().isBlank()) {
            var part = stock.requirePartInternal(orgId, in.partId());
            p.setPart(part);
            p.setPartName(part.getName());
            if (in.unitCost() == null) p.setUnitCost(part.getAverageCost());
        } else {
            if (in.partName() == null || in.partName().isBlank()) {
                throw ApiException.badRequest("Indique a peça ou o nome da peça.");
            }
            p.setPartName(in.partName().trim());
        }
        if (in.warehouseId() != null && !in.warehouseId().isBlank()) {
            p.setWarehouse(stock.requireWarehouseInternal(orgId, in.warehouseId()));
        }
        p.setQuantity(in.quantity());
        if (in.unitCost() != null) p.setUnitCost(in.unitCost());
        w.addPart(p);
        audit.record(orgId, userId, "work_order.part", "WorkOrder", w.getId(),
                w.getNumber() + " · " + in.quantity() + "× " + p.getPartName());
        intelligence.recalculate(w);
        return view(w);
    }

    @Transactional
    public WorkOrderView markTaskDone(String orgId, String userId, String id, String taskId,
                                      boolean done, String notes) {
        WorkOrder w = require(orgId, id);
        WorkOrderTask t = w.getTasks().stream().filter(x -> x.getId().equals(taskId))
                .findFirst().orElseThrow(() -> ApiException.notFound("Tarefa não encontrada."));
        t.setDone(done);
        t.setDoneAt(done ? Instant.now() : null);
        if (notes != null) t.setNotes(blankToNull(notes));
        return view(w);
    }

    // ==== Avarias =================================================
    /**
     * Gera automaticamente uma OM preventiva das tarefas vencidas de um ativo,
     * se ainda não existir uma OM preventiva aberta. Usado pelo agendador.
     */
    @Transactional
    public boolean autoGeneratePreventive(String assetId) {
        Asset asset = assets.findById(assetId).orElse(null);
        if (asset == null) return false;
        String orgId = asset.getOrganization().getId();

        boolean hasOpen = workOrders.existsByAssetIdAndTypeAndStatusIn(assetId, WorkOrderType.PREVENTIVE,
                List.of(WorkOrderStatus.OPEN, WorkOrderStatus.PLANNED, WorkOrderStatus.IN_PROGRESS));
        if (hasOpen) return false;

        List<AssetPlanTask> overdue = assetPlanTasks.findForAsset(orgId, assetId).stream()
                .filter(t -> t.getStatus() == PlanTaskStatus.OVERDUE)
                .toList();
        if (overdue.isEmpty()) return false;

        WorkOrder w = newWorkOrder(orgId, null, asset, WorkOrderType.PREVENTIVE,
                "Manutenção preventiva (automática) — " + asset.getTag(),
                overdue.size() + " tarefa(s) de plano vencida(s).",
                WorkOrderPriority.HIGH, null, null, null);
        int order = 1;
        for (AssetPlanTask apt : overdue) {
            WorkOrderTask t = new WorkOrderTask();
            t.setAssetPlanTask(apt);
            t.setTitle(apt.getTitle());
            t.setSystemName(apt.getSystemName());
            t.setSortOrder(order++);
            w.addTask(t);
        }
        workOrders.save(w);
        audit.record(w.getOrganization().getId(), null,
                "work_order.auto_generated", "WorkOrder", w.getId(),
                w.getNumber() + " · " + asset.getTag());
        return true;
    }

    @Transactional
    public ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureCreated recordStandaloneFailure(
            String orgId, String userId, String assetId, FailureInput in) {
        Asset asset = requireAsset(orgId, assetId);
        Failure f = recordFailure(orgId, asset, null, in, asset);
        audit.record(orgId, userId, "failure.record", "Asset", assetId, asset.getTag() + " · " + in.description());
        return new ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureCreated(f.getId());
    }

    // ------------------------------------------------------------------
    /**
     * Campos da ficha que vem no pedido de criacao.
     *
     * <p>Nada aqui e obrigatorio: uma avaria as tres da manha e aberta com o
     * titulo e pouco mais. O que nao se sabe fica por preencher, e os avisos da
     * ordem encarregam-se de lembrar o que falta.
     */
    // ==== Workflow (Fatia 17) ==============================================
    /**
     * Muda o estado da ordem, com a transicao validada e registada.
     *
     * <p>Todas as mudancas passam por aqui, incluindo as que ja existiam
     * (iniciar, concluir, verificar). Ter dois caminhos -- um validado e outro
     * nao -- acabaria com estados impossiveis na base de dados e sem historico
     * que explicasse como la chegaram.
     */
    @Transactional
    public WorkOrderView moveTo(
            String orgId, String userId, String id, WorkOrderStatus destino, String nota) {

        WorkOrder w = require(orgId, id);
        transition(w, destino, userId, nota);
        audit.record(orgId, userId, "work_order.status", "WorkOrder", w.getId(),
                w.getNumber() + " - " + destino.label()
                        + (nota != null && !nota.isBlank() ? " - " + nota.trim() : ""));
        return view(w);
    }

    /**
     * Aplica a transicao e grava a linha de historico.
     *
     * <p>Os minutos no estado anterior sao calculados agora e guardados: se
     * alguem corrigir uma data mais tarde, o tempo que a ordem realmente esteve
     * parada nao muda.
     */
    private void transition(WorkOrder w, WorkOrderStatus destino, String userId, String nota) {
        WorkOrderStatus origem = w.getStatus();
        workflow.require(origem, destino);

        Instant agora = Instant.now();
        Integer minutos = null;
        if (!w.getStatusHistory().isEmpty()) {
            Instant ultima = w.getStatusHistory()
                    .get(w.getStatusHistory().size() - 1).getChangedAt();
            minutos = (int) Duration.between(ultima, agora).toMinutes();
        } else if (w.getOpenedAt() != null) {
            minutos = (int) Duration.between(w.getOpenedAt(), agora).toMinutes();
        }

        WorkOrderStatusHistory h = new WorkOrderStatusHistory();
        h.setFromStatus(origem);
        h.setToStatus(destino);
        h.setChangedBy(userId);
        h.setChangedByLabel(nameOf(userId));
        h.setNote(blankToNull(nota));
        h.setMinutesInPrevious(minutos);
        h.setChangedAt(agora);
        w.addStatusChange(h);

        w.setStatus(destino);
    }

    private String nameOf(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return users.findById(userId).map(ao.autocare.domain.User::getName).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==== Diagnostico ======================================================
    /**
     * Regista o diagnostico em quatro campos separados.
     *
     * <p>Um unico campo de texto livre juntava o que o condutor sente, o que o
     * tecnico ve e o que se vai fazer -- e depois nao havia forma de responder
     * a "quantas vezes este problema foi causado pela mesma coisa?".
     */
    @Transactional
    public WorkOrderView diagnose(String orgId, String userId, String id, DiagnosisRequest req) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus().isTerminal()) {
            throw ApiException.conflict("Uma ordem "
                    + w.getStatus().label().toLowerCase() + " ja nao se altera.");
        }
        w.setSymptom(blankToNull(req.symptom()));
        w.setDiagnosis(blankToNull(req.diagnosis()));
        w.setProbableCause(blankToNull(req.probableCause()));
        w.setRecommendedAction(blankToNull(req.recommendedAction()));
        w.setDiagnosedBy(userId);
        w.setDiagnosedByLabel(blankToNull(req.technicianLabel()) != null
                ? req.technicianLabel().trim() : nameOf(userId));
        w.setDiagnosedAt(Instant.now());

        // Registar o diagnostico E deixar a ordem em "aberta" faria o quadro
        // mentir sobre o que esta a acontecer naquela viatura.
        if (w.getStatus() == WorkOrderStatus.OPEN || w.getStatus() == WorkOrderStatus.PLANNED) {
            transition(w, WorkOrderStatus.DIAGNOSIS, userId, "Diagnostico registado");
        }
        audit.record(orgId, userId, "work_order.diagnosis", "WorkOrder", w.getId(),
                w.getNumber() + " - " + (w.getDiagnosis() != null ? w.getDiagnosis() : ""));
        return view(w);
    }

    // ==== Orcamentos =======================================================
    @Transactional
    public WorkOrderView addQuote(String orgId, String userId, String id, QuoteRequest req) {
        WorkOrder w = require(orgId, id);
        if (w.getStatus().isTerminal()) {
            throw ApiException.conflict("Uma ordem "
                    + w.getStatus().label().toLowerCase() + " ja nao recebe orcamentos.");
        }
        WorkOrderQuote q = new WorkOrderQuote();
        q.setSupplierLabel(blankToNull(req.supplierLabel()));
        if (req.supplierId() != null && !req.supplierId().isBlank()) {
            Supplier f = suppliers.findByIdAndOrganizationId(req.supplierId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Fornecedor nao encontrado."));
            q.setSupplier(f);
            if (q.getSupplierLabel() == null) {
                q.setSupplierLabel(f.getName());
            }
        }
        if (q.getSupplierLabel() == null) {
            throw ApiException.badRequest(
                    "Indique de quem e o orcamento: escolha um fornecedor ou escreva o nome.");
        }
        q.setQuoteNumber(blankToNull(req.quoteNumber()));
        q.setQuotedAt(req.quotedAt() != null ? req.quotedAt() : Instant.now());
        q.setValidUntil(req.validUntil());
        q.setPartsAmount(orZero(req.partsAmount()));
        q.setLaborAmount(orZero(req.laborAmount()));
        q.setOtherAmount(orZero(req.otherAmount()));
        q.setDiscountAmount(orZero(req.discountAmount()));
        q.setTaxAmount(orZero(req.taxAmount()));
        q.setNotes(blankToNull(req.notes()));
        q.setFileId(blankToNull(req.fileId()));
        q.setCreatedBy(userId);
        q.setCurrency(w.getCurrency());
        // O total e sempre calculado, nunca aceite do pedido: um total que nao
        // bate com as parcelas e uma discussao com o fornecedor a espera.
        q.setTotalAmount(q.computeTotal());
        w.addQuote(q);

        if (w.getStatus() == WorkOrderStatus.OPEN || w.getStatus() == WorkOrderStatus.PLANNED
                || w.getStatus() == WorkOrderStatus.DIAGNOSIS) {
            transition(w, WorkOrderStatus.QUOTING, userId, "Orcamento recebido");
        }
        // Descarregar o contexto antes de montar a vista: o orcamento so recebe
        // id ao ser gravado, e sem id o ecra mostra-o mas nao consegue
        // seleciona-lo. E flush, nao save: save faz merge de uma entidade que ja
        // esta gerida e rebenta com os filhos ainda por gravar.
        workOrders.flush();
        audit.record(orgId, userId, "work_order.quote", "WorkOrder", w.getId(),
                w.getNumber() + " - " + q.getSupplierLabel() + " - "
                        + q.getTotalAmount() + " " + q.getCurrency());
        return view(w);
    }

    /** Escolhe o orcamento. So um fica escolhido: e o que vale para aprovacao. */
    @Transactional
    public WorkOrderView selectQuote(String orgId, String userId, String id, String quoteId) {
        WorkOrder w = require(orgId, id);
        WorkOrderQuote escolhido = w.getQuotes().stream()
                .filter(q -> q.getId().equals(quoteId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Orcamento nao encontrado nesta ordem."));

        w.getQuotes().forEach(q -> q.setSelected(false));
        escolhido.setSelected(true);
        w.setEstimatedCost(escolhido.getTotalAmount());
        if (escolhido.getSupplier() != null) {
            w.setSupplier(escolhido.getSupplier());
            w.setExecution(WorkOrderExecution.EXTERNAL);
        }
        audit.record(orgId, userId, "work_order.quote_select", "WorkOrder", w.getId(),
                w.getNumber() + " - " + escolhido.getSupplierLabel()
                        + " - " + escolhido.getTotalAmount());
        return view(w);
    }

    // ==== Aprovacao ========================================================
    /**
     * Pede aprovacao.
     *
     * <p>Abaixo do limite da empresa, aprova-se sozinha. Obrigar um dono a
     * aprovar a troca de uma lampada faz com que ninguem aprove nada e o
     * processo passe a ser contornado por toda a gente.
     */
    @Transactional
    public WorkOrderView requestApproval(String orgId, String userId, String id) {
        WorkOrder w = require(orgId, id);
        BigDecimal valor = w.selectedQuote()
                .map(WorkOrderQuote::getTotalAmount)
                .orElse(w.getEstimatedCost());

        if (valor == null) {
            throw ApiException.badRequest(
                    "Sem orcamento nem custo estimado nao ha o que aprovar. "
                            + "Registe um orcamento primeiro.");
        }
        transition(w, WorkOrderStatus.AWAITING_APPROVAL, userId, null);

        BigDecimal limite = w.getOrganization().getMaintenanceApprovalLimit();
        if (limite != null && valor.compareTo(limite) <= 0) {
            w.setApprovedAmount(valor);
            w.setApprovalNote("Abaixo do limite de " + limite + " " + w.getCurrency()
                    + ": aprovacao automatica.");
            transition(w, WorkOrderStatus.APPROVED, userId, w.getApprovalNote());
            audit.record(orgId, userId, "work_order.auto_approve", "WorkOrder", w.getId(),
                    w.getNumber() + " - " + valor + " " + w.getCurrency());
            return view(w);
        }

        notifyApprovers(w, valor);
        audit.record(orgId, userId, "work_order.request_approval", "WorkOrder", w.getId(),
                w.getNumber() + " - " + valor + " " + w.getCurrency());
        return view(w);
    }

    @Transactional
    public WorkOrderView approve(String orgId, String userId, String id, ApprovalRequest req) {
        WorkOrder w = require(orgId, id);
        BigDecimal valor = req != null && req.amount() != null
                ? req.amount()
                : w.selectedQuote().map(WorkOrderQuote::getTotalAmount)
                        .orElse(w.getEstimatedCost());

        transition(w, WorkOrderStatus.APPROVED, userId, req != null ? req.note() : null);
        w.setApprovedBy(users.getReferenceById(userId));
        w.setApprovedAt(Instant.now());
        w.setApprovedAmount(valor);
        w.setApprovalNote(req != null ? blankToNull(req.note()) : null);

        audit.record(orgId, userId, "work_order.approve", "WorkOrder", w.getId(),
                w.getNumber() + " - aprovado " + valor + " " + w.getCurrency());
        return view(w);
    }

    @Transactional
    public WorkOrderView reject(String orgId, String userId, String id, ApprovalRequest req) {
        WorkOrder w = require(orgId, id);
        if (req == null || req.note() == null || req.note().trim().length() < 5) {
            throw ApiException.badRequest(
                    "Explique porque e que o orcamento foi rejeitado. Quem pediu precisa de "
                            + "saber o que mudar.");
        }
        transition(w, WorkOrderStatus.REJECTED, userId, req.note());
        w.setRejectedBy(userId);
        w.setRejectedAt(Instant.now());
        w.setRejectionReason(req.note().trim());

        audit.record(orgId, userId, "work_order.reject", "WorkOrder", w.getId(),
                w.getNumber() + " - " + req.note().trim());
        return view(w);
    }

    // ==== Fecho ============================================================
    /**
     * Fecha a ordem em definitivo.
     *
     * <p>Depois disto nao se mexe mais: e o ponto a partir do qual os custos
     * entram nos indicadores do ano e deixam de poder mudar por baixo deles.
     */
    @Transactional
    public WorkOrderView close(String orgId, String userId, String id) {
        WorkOrder w = require(orgId, id);
        transition(w, WorkOrderStatus.CLOSED, userId, null);
        w.setClosedAt(Instant.now());
        w.setClosedBy(userId);
        intelligence.recalculate(w);
        audit.record(orgId, userId, "work_order.close", "WorkOrder", w.getId(),
                w.getNumber() + " - total " + w.getTotalCost() + " " + w.getCurrency());
        return view(w);
    }

    private void notifyApprovers(WorkOrder w, BigDecimal valor) {
        notifications.notifyManagers(
                ao.autocare.modules.notification.NotificationService.Draft.of(
                        w.getOrganization().getId(),
                        ao.autocare.domain.enums.Enums.AlertCategory.WORK_ORDER,
                        ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                        "Aprovacao pendente - " + w.getNumber(),
                        w.getAsset().getTag() + " - " + w.getTitle() + " - "
                                + valor + " " + w.getCurrency()
                                + ". Acima do limite da empresa, precisa de aprovacao.",
                        "work_order_approval", w.getId(),
                        "/ordens/" + w.getId())
                .forAsset(w.getAsset()));
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void applyFicha(String orgId, WorkOrder w, CreateWorkOrderRequest req) {
        if (req.dueAt() != null) {
            w.setDueAt(req.dueAt());
        }
        w.setEstimatedHours(req.estimatedHours());
        w.setEstimatedCost(req.estimatedCost());
        w.setSystemCode(blankToNull(req.systemCode()));
        w.setSafetyNotes(blankToNull(req.safetyNotes()));
        w.setWarrantyReference(blankToNull(req.warrantyReference()));
        w.setParentWorkOrderId(blankToNull(req.parentWorkOrderId()));
        if (req.requiresShutdown() != null) {
            w.setRequiresShutdown(req.requiresShutdown());
        }
        if (req.underWarranty() != null) {
            w.setUnderWarranty(req.underWarranty());
        }
        if (req.branchId() != null && !req.branchId().isBlank()) {
            w.setBranch(locations.findByIdAndOrganizationId(req.branchId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Filial nao encontrada.")));
        }
        if (req.driverId() != null && !req.driverId().isBlank()) {
            w.setDriver(drivers.findByIdAndOrganizationId(req.driverId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Motorista nao encontrado.")));
        }
    }

    private WorkOrder newWorkOrder(
            String orgId, String userId, Asset asset, WorkOrderType type, String title,
            String description, WorkOrderPriority priority, Instant scheduledFor,
            String assignedToUserId, String assignedToLabel) {

        // Numeracao por ANO: OM-2026-000001. Um contador unico daria
        // OM-2027-000998 no primeiro dia de 2027, e o numero deixava de dizer
        // nada sobre o volume do ano. A chave do contador leva o ano.
        int ano = java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Luanda")).getYear();
        long seq = counters.next(orgId, WO_COUNTER + ":" + ano);
        WorkOrder w = new WorkOrder();
        w.setOrganization(organizations.getReferenceById(orgId));
        w.setNumber(String.format("OM-%d-%06d", ano, seq));
        w.setOrderYear(ano);
        w.setAsset(asset);
        w.setType(type);
        w.setStatus(scheduledFor != null ? WorkOrderStatus.PLANNED : WorkOrderStatus.OPEN);
        w.setPriority(priority != null ? priority : WorkOrderPriority.NORMAL);
        w.setTitle(title);
        w.setDescription(blankToNull(description));
        w.setScheduledFor(scheduledFor);
        assign(w, orgId, assignedToUserId, assignedToLabel);
        w.setOpenedAt(Instant.now());
        if (userId != null) w.setRequestedBy(users.getReferenceById(userId));
        // Prazo por omissao a partir da prioridade. Sem prazo nenhum, toda a
        // ordem esta a horas e o indicador de cumprimento e sempre 100%.
        w.setDueAt(intelligence.defaultDueDate(w.getPriority(), w.getOpenedAt()));
        // A filial que suporta o custo e, por omissao, onde o ativo esta.
        if (asset.getLocation() != null) {
            w.setBranch(asset.getLocation());
        }
        return w;
    }

    /**
     * Atribui a ordem a um membro da equipa. O id manda: quando é indicado, o
     * nome mostrado passa a ser o do membro. Uma string vazia ({@code ""})
     * desatribui. Sem id, aceita-se apenas um nome livre — útil para prestadores
     * externos que não têm conta no sistema.
     */
    private void assign(WorkOrder w, String orgId, String assignedToUserId, String assignedToLabel) {
        if (assignedToUserId != null) {
            if (assignedToUserId.isBlank()) {
                w.setAssignedTo(null);
                w.setAssignedToLabel(blankToNull(assignedToLabel));
                return;
            }
            Membership member = memberships
                    .findByUserIdAndOrganizationId(assignedToUserId, orgId)
                    .orElseThrow(() -> ApiException.badRequest(
                            "Essa pessoa não faz parte da equipa desta empresa."));
            if (member.isSuspended()) {
                throw ApiException.conflict(
                        "Não é possível atribuir ordens a um membro suspenso.");
            }
            w.setAssignedTo(member.getUser());
            w.setAssignedToLabel(member.getUser().getName());
            notifyAssignee(w, member.getUser());
            return;
        }
        w.setAssignedToLabel(blankToNull(assignedToLabel));
    }

    /**
     * Avisa o técnico de que tem uma ordem para executar. O aviso só sai depois
     * de a ordem ter número — em ordens novas isso acontece antes de gravar, por
     * isso o número já está atribuído aqui.
     */
    private void notifyAssignee(WorkOrder w, ao.autocare.domain.User assignee) {
        notifications.notifyUser(assignee, NotificationService.Draft.of(
                        w.getOrganization().getId(),
                        ao.autocare.domain.enums.Enums.AlertCategory.WORK_ORDER,
                        ao.autocare.domain.enums.Enums.AlertSeverity.INFO,
                        "Ordem " + w.getNumber() + " atribuída a si",
                        w.getTitle() + " · " + w.getAsset().getTag(),
                        "work_order_assigned", w.getId(),
                        "/manutencao/ordens/" + w.getId())
                .forAsset(w.getAsset()));
    }

    private Failure recordFailure(String orgId, Asset asset, WorkOrder w, FailureInput in, Asset assetForMeter) {
        Failure f = new Failure();
        f.setOrganization(organizations.getReferenceById(orgId));
        f.setAsset(asset);
        f.setWorkOrder(w);
        f.setSystemCode(blankToNull(in.systemCode()));
        f.setDescription(in.description().trim());
        f.setCause(blankToNull(in.cause()));
        f.setDetectedAt(Instant.now());
        f.setCausedDowntime(in.causedDowntime() == null || in.causedDowntime());
        return failures.save(f);
    }

    private void ensureEditable(WorkOrder w) {
        if (w.getStatus() == WorkOrderStatus.VERIFIED || w.getStatus() == WorkOrderStatus.CANCELLED) {
            throw ApiException.conflict("Esta ordem já está fechada e não pode ser alterada.");
        }
    }

    private WorkOrder require(String orgId, String id) {
        return workOrders.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem de manutenção não encontrada."));
    }

    private Asset requireAsset(String orgId, String id) {
        return assets.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private WorkOrderStatus parseStatus(String s) {
        try {
            return WorkOrderStatus.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado inválido: " + s);
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
