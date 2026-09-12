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
            if (startFromNow) {
                apt.setLastDoneAt(now);
                apt.setLastDoneMeter(currentMeter);
            }
            ap.addTask(apt);
        }
        assetPlans.save(ap);

        recompute(ap.getTasks(), primary, now);
        audit.record(orgId, userId, "asset_plan.assign", "Asset", assetId,
                asset.getTag() + " ← " + plan.getName());
        return AssetPlanView.of(ap, ap.getTasks());
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
