package ao.autocare.modules.plan;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetPlan;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.MaintenancePlan;
import ao.autocare.domain.PlanTask;
import ao.autocare.domain.PlanTaskCompletion;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.plan.PlanScheduleCalculator.Projection;
import ao.autocare.modules.plan.PlanScheduleCalculator.TriggerSpec;
import ao.autocare.modules.plan.dto.AssetPlanDtos.AssetPlanView;
import ao.autocare.modules.plan.dto.AssetPlanDtos.AssignPlanRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.CompleteTaskRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.CompletionView;
import ao.autocare.modules.plan.dto.AssetPlanDtos.IntervalRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.TaskCompletionResult;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPlanRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.MaintenancePlanRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PlanTaskCompletionRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetPlanService {

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final MaintenancePlanRepository plans;
    private final AssetPlanRepository assetPlans;
    private final AssetPlanTaskRepository assetPlanTasks;
    private final PlanTaskCompletionRepository completions;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuditService audit;

    public AssetPlanService(
            AssetRepository assets,
            AssetMeterRepository meters,
            MaintenancePlanRepository plans,
            AssetPlanRepository assetPlans,
            AssetPlanTaskRepository assetPlanTasks,
            PlanTaskCompletionRepository completions,
            OrganizationRepository organizations,
            UserRepository users,
            AuditService audit) {
        this.assets = assets;
        this.meters = meters;
        this.plans = plans;
        this.assetPlans = assetPlans;
        this.assetPlanTasks = assetPlanTasks;
        this.completions = completions;
        this.organizations = organizations;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssetPlanView> listForAsset(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        List<AssetPlan> aps = assetPlans.findByAssetId(assetId);
        List<AssetPlanView> out = new ArrayList<>();
        for (AssetPlan ap : aps) {
            out.add(AssetPlanView.of(ap, assetPlanTasks.findByAssetPlanId(ap.getId())));
        }
        return out;
    }

    @Transactional
    public AssetPlanView assign(String orgId, String userId, String assetId, AssignPlanRequest req) {
        Asset asset = requireAsset(orgId, assetId);
        MaintenancePlan plan = plans.findByIdAndOrganizationId(req.planId(), orgId)
                .orElseThrow(() -> ApiException.badRequest("Plano de manutenção inválido."));
        if (assetPlans.findByAssetIdAndPlanId(assetId, plan.getId()).isPresent()) {
            throw ApiException.conflict("Este plano já está atribuído a este ativo.");
        }

        AssetMeter primary = primaryMeter(assetId);
        BigDecimal currentMeter = primary != null ? primary.getCurrentValue() : null;
        boolean startFromNow = req.startFromNow() == null || req.startFromNow();
        Instant now = Instant.now();
        // «A última revisão foi aos 48 000 km em Junho»: o relógio arranca daí,
        // não de hoje — senão a próxima vencia 5 000 km depois do que devia.
        Instant baseAt = req.lastDoneAt() != null ? req.lastDoneAt() : now;
        BigDecimal baseMeter = req.lastDoneMeter() != null ? req.lastDoneMeter() : currentMeter;
        if (req.lastDoneAt() != null && req.lastDoneAt().isAfter(now)) {
            throw ApiException.badRequest("A data da última revisão não pode ser no futuro.");
        }
        if (req.lastDoneMeter() != null && currentMeter != null
                && req.lastDoneMeter().compareTo(currentMeter) > 0) {
            throw ApiException.badRequest("A leitura da última revisão (" + req.lastDoneMeter()
                    + ") é maior do que a leitura atual do contador (" + currentMeter + ").");
        }

        AssetPlan ap = new AssetPlan();
        ap.setOrganization(organizations.getReferenceById(orgId));
        ap.setAsset(asset);
        ap.setPlan(plan);
        ap.setPlanName(plan.getName());
        ap.setAssignedAt(now);

        for (PlanTask task : plan.getTasks()) {
            AssetPlanTask apt = new AssetPlanTask();
            apt.setTitle(task.getTitle());
            apt.setSystemName(task.getSystemName());
            apt.setTask(task);
            if (startFromNow || req.lastDoneAt() != null || req.lastDoneMeter() != null) {
                apt.setLastDoneAt(baseAt);
                apt.setLastDoneMeter(baseMeter);
            }
            ap.addTask(apt);
        }
        assetPlans.save(ap);

        recompute(ap.getTasks(), primary, now);
        audit.record(orgId, userId, "asset_plan.assign", "Asset", assetId,
                asset.getTag() + " ← " + plan.getName());
        return AssetPlanView.of(ap, ap.getTasks());
    }

    /**
     * Define o limite de manutenção do ativo («revisão a cada 5 000 km»).
     *
     * <p>Valida contra o contador principal do ativo: um intervalo em horas
     * num camião que conta por km nunca venceria, e o sistema ficaria calado
     * a fingir que vigiava. Por isso recusa, e diz qual é o contador.
     */
    @Transactional
    public AssetPlanView defineInterval(String orgId, String userId, String assetId,
            IntervalRequest req, PlanService planService) {
        Asset asset = requireAsset(orgId, assetId);
        AssetMeter primary = primaryMeter(assetId);
        boolean temKm = req.everyKm() != null && req.everyKm().signum() > 0;
        boolean temHoras = req.everyHours() != null && req.everyHours().signum() > 0;
        boolean temDias = req.everyDays() != null && req.everyDays() > 0;
        if (!temKm && !temHoras && !temDias) {
            throw ApiException.badRequest(
                    "Indique o intervalo: a cada quantos km, horas ou dias.");
        }
        if ((temKm || temHoras) && primary == null) {
            throw ApiException.badRequest("Este ativo ainda não tem contador. Registe a primeira "
                    + "leitura (km ou horas) na ficha, ou ligue-lhe um rastreador GPS.");
        }
        if (temKm && primary.getKind() != MeterKind.ODOMETER) {
            throw ApiException.badRequest("Este ativo conta por horas (horímetro), não por km. "
                    + "Indique o intervalo em horas.");
        }
        if (temHoras && primary.getKind() != MeterKind.HOURMETER) {
            throw ApiException.badRequest("Este ativo conta por km (odómetro), não por horas. "
                    + "Indique o intervalo em km.");
        }

        String titulo = req.title() != null && !req.title().isBlank()
                ? req.title().trim() : "Revisão geral";
        List<String> partes = new ArrayList<>();
        List<ao.autocare.modules.plan.dto.PlanDtos.TriggerInput> gatilhos = new ArrayList<>();
        if (temKm) {
            gatilhos.add(new ao.autocare.modules.plan.dto.PlanDtos.TriggerInput(
                    ao.autocare.domain.enums.Enums.PlanTriggerType.METER_INTERVAL,
                    MeterKind.ODOMETER, req.everyKm(), null));
            partes.add("cada " + req.everyKm().stripTrailingZeros().toPlainString() + " km");
        }
        if (temHoras) {
            gatilhos.add(new ao.autocare.modules.plan.dto.PlanDtos.TriggerInput(
                    ao.autocare.domain.enums.Enums.PlanTriggerType.METER_INTERVAL,
                    MeterKind.HOURMETER, req.everyHours(), null));
            partes.add("cada " + req.everyHours().stripTrailingZeros().toPlainString() + " h");
        }
        if (temDias) {
            gatilhos.add(new ao.autocare.modules.plan.dto.PlanDtos.TriggerInput(
                    ao.autocare.domain.enums.Enums.PlanTriggerType.CALENDAR_DAYS,
                    null, BigDecimal.valueOf(req.everyDays()), null));
            partes.add("cada " + req.everyDays() + " dias");
        }
        String nome = titulo + " — " + String.join(" ou ", partes);

        // O mesmo intervalo noutro ativo do mesmo tipo reutiliza o plano: a
        // lista de planos não se enche de cópias iguais.
        MaintenancePlan plano = plans.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(p -> p.getName().equalsIgnoreCase(nome))
                .filter(p -> p.getAssetType() == null
                        || p.getAssetType().getId().equals(asset.getAssetType().getId()))
                .findFirst()
                .orElse(null);
        String planId;
        if (plano != null) {
            planId = plano.getId();
        } else {
            var task = new ao.autocare.modules.plan.dto.PlanDtos.TaskInput(
                    null, null, titulo, blankToNull(req.notes()), null, null, gatilhos, List.of());
            var save = new ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest(
                    nome, asset.getAssetType().getId(), null, null,
                    "Manter " + asset.getAssetType().getName().toLowerCase() + " dentro do intervalo "
                            + "de manutenção definido pela empresa.",
                    null, null, List.of(task));
            planId = planService.create(orgId, userId, save).id();
        }

        // Já tinha este mesmo plano: substituir a atribuição em vez de duplicar.
        assetPlans.findByAssetIdAndPlanId(assetId, planId).ifPresent(assetPlans::delete);

        return assign(orgId, userId, assetId,
                new AssignPlanRequest(planId, true, req.lastDoneAt(), req.lastDoneMeter()));
    }

    @Transactional
    public void unassign(String orgId, String userId, String assetId, String assetPlanId) {
        requireAsset(orgId, assetId);
        AssetPlan ap = assetPlans.findByIdAndOrganizationId(assetPlanId, orgId)
                .orElseThrow(() -> ApiException.notFound("Atribuição não encontrada."));
        assetPlans.delete(ap);
        audit.record(orgId, userId, "asset_plan.unassign", "Asset", assetId, ap.getPlanName());
    }

    @Transactional
    public TaskCompletionResult completeTask(
            String orgId, String userId, String assetId, String taskStateId, CompleteTaskRequest req) {

        Asset asset = requireAsset(orgId, assetId);
        AssetPlanTask apt = assetPlanTasks.findByIdAndAssetPlanOrganizationId(taskStateId, orgId)
                .orElseThrow(() -> ApiException.notFound("Tarefa de plano não encontrada."));
        if (!apt.getAssetPlan().getAsset().getId().equals(assetId)) {
            throw ApiException.notFound("Tarefa de plano não encontrada.");
        }

        AssetMeter primary = primaryMeter(assetId);
        Instant when = req.completedAt() != null ? req.completedAt() : Instant.now();
        BigDecimal meterValue = req.meterValue() != null ? req.meterValue()
                : (primary != null ? primary.getCurrentValue() : null);

        PlanTaskCompletion c = new PlanTaskCompletion();
        c.setOrganization(organizations.getReferenceById(orgId));
        c.setAssetPlanTask(apt);
        c.setAsset(asset);
        c.setTitle(apt.getTitle());
        c.setCompletedAt(when);
        c.setMeterValue(meterValue);
        if (userId != null) c.setPerformedBy(users.getReferenceById(userId));
        c.setPerformedByLabel(blankToNull(req.performedByLabel()));
        c.setNotes(blankToNull(req.notes()));
        completions.save(c);

        apt.setLastDoneAt(when);
        apt.setLastDoneMeter(meterValue);
        recompute(List.of(apt), primary, Instant.now());

        audit.record(orgId, userId, "asset_plan.task_done", "Asset", assetId,
                asset.getTag() + " · " + apt.getTitle());
        return new TaskCompletionResult(
                AssetPlanView.of(apt.getAssetPlan(),
                        assetPlanTasks.findByAssetPlanId(apt.getAssetPlan().getId())),
                CompletionView.of(c));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CompletionView> completionHistory(String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                completions.findByAssetIdOrderByCompletedAtDesc(assetId, pageable).map(CompletionView::of));
    }

    /** Recalcula o relógio de todas as tarefas de plano de um ativo (chamado ao registar leituras). */
    @Transactional
    public void recomputeForAsset(String assetId) {
        List<AssetPlanTask> tasks = assetPlanTasks.findByAssetPlanAssetId(assetId);
        if (tasks.isEmpty()) return;
        recompute(tasks, primaryMeter(assetId), Instant.now());
    }

    @Transactional
    public List<AssetPlanView> recomputeForAssetInOrg(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        recomputeForAsset(assetId);
        return listForAsset(orgId, assetId);
    }

    /**
     * Marca uma tarefa de plano como executada por uma Ordem de Manutenção
     * (repõe o relógio e regista a execução). Chamado pelo módulo de OM.
     */
    @Transactional
    public void markTaskCompleted(
            AssetPlanTask apt, Instant when, BigDecimal meterValue, String byLabel, String reference) {
        String assetId = apt.getAssetPlan().getAsset().getId();
        AssetMeter primary = primaryMeter(assetId);
        BigDecimal meter = meterValue != null ? meterValue
                : (primary != null ? primary.getCurrentValue() : null);

        PlanTaskCompletion c = new PlanTaskCompletion();
        c.setOrganization(apt.getAssetPlan().getOrganization());
        c.setAssetPlanTask(apt);
        c.setAsset(apt.getAssetPlan().getAsset());
        c.setTitle(apt.getTitle());
        c.setCompletedAt(when);
        c.setMeterValue(meter);
        c.setPerformedByLabel(byLabel);
        c.setNotes(reference);
        completions.save(c);

        apt.setLastDoneAt(when);
        apt.setLastDoneMeter(meter);
        recompute(List.of(apt), primary, Instant.now());
    }

    // ------------------------------------------------------------------
    private void recompute(List<AssetPlanTask> tasks, AssetMeter primary, Instant now) {
        BigDecimal currentMeter = primary != null ? primary.getCurrentValue() : null;
        MeterKind kind = primary != null ? primary.getKind() : null;
        BigDecimal dailyAverage = primary != null ? primary.getDailyAverage() : null;

        for (AssetPlanTask apt : tasks) {
            List<TriggerSpec> specs = new ArrayList<>();
            if (apt.getTask() != null) {
                apt.getTask().getTriggers().forEach(tr -> specs.add(new TriggerSpec(
                        tr.getTriggerType(), tr.getMeterKind(),
                        tr.getIntervalValue(), tr.getToleranceValue())));
            }
            Projection p = PlanScheduleCalculator.project(
                    specs, apt.getLastDoneAt(), apt.getLastDoneMeter(),
                    currentMeter, kind, dailyAverage, now);
            apt.setNextDueAt(p.nextDueAt());
            apt.setNextDueMeter(p.nextDueMeter());
            apt.setNextDueMeterKind(p.nextDueMeterKind());
            apt.setRemainingMeter(p.remainingMeter());
            apt.setRemainingDays(p.remainingDays());
            apt.setStatus(p.status());
        }
    }

    private AssetMeter primaryMeter(String assetId) {
        return meters.findByAssetId(assetId).stream()
                .filter(AssetMeter::isPrimary)
                .findFirst()
                .orElse(meters.findByAssetId(assetId).stream().findFirst().orElse(null));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}
