package ao.autocare.modules.plan;

import ao.autocare.common.ApiException;
import ao.autocare.domain.MaintenancePlan;
import ao.autocare.domain.PlanTask;
import ao.autocare.domain.PlanTaskPart;
import ao.autocare.domain.PlanTaskTrigger;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.plan.dto.PlanDtos.PartInput;
import ao.autocare.modules.plan.dto.PlanDtos.PlanView;
import ao.autocare.modules.plan.dto.PlanDtos.ApprovePlanRequest;
import ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest;
import ao.autocare.modules.plan.dto.PlanDtos.TaskInput;
import ao.autocare.modules.plan.dto.PlanDtos.TriggerInput;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.MaintenancePlanRepository;
import ao.autocare.repo.OrganizationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final MaintenancePlanRepository plans;
    private final AssetTypeRepository assetTypes;
    private final OrganizationRepository organizations;
    private final AuditService audit;
    private final ao.autocare.repo.UserRepository users;

    public PlanService(
            MaintenancePlanRepository plans,
            AssetTypeRepository assetTypes,
            OrganizationRepository organizations,
            AuditService audit,
            ao.autocare.repo.UserRepository users) {
        this.plans = plans;
        this.assetTypes = assetTypes;
        this.organizations = organizations;
        this.audit = audit;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<PlanView> list(String orgId) {
        return plans.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(PlanView::summary).toList();
    }

    @Transactional(readOnly = true)
    public PlanView get(String orgId, String id) {
        return PlanView.of(load(orgId, id));
    }

    @Transactional
    public PlanView create(String orgId, String userId, SavePlanRequest req) {
        MaintenancePlan p = new MaintenancePlan();
        p.setOrganization(organizations.getReferenceById(orgId));
        apply(orgId, p, req);
        plans.save(p);
        audit.record(orgId, userId, "plan.create", "MaintenancePlan", p.getId(),
                p.getName() + " · " + p.getTasks().size() + " tarefas");
        return PlanView.of(p);
    }

    @Transactional
    public PlanView update(String orgId, String userId, String id, SavePlanRequest req) {
        MaintenancePlan p = load(orgId, id);
        p.getTasks().clear();
        apply(orgId, p, req);
        audit.record(orgId, userId, "plan.update", "MaintenancePlan", p.getId(), p.getName());
        return PlanView.of(p);
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        MaintenancePlan p = load(orgId, id);
        plans.delete(p);
        audit.record(orgId, userId, "plan.delete", "MaintenancePlan", id, p.getName());
    }

    // ------------------------------------------------------------------
    private void apply(String orgId, MaintenancePlan p, SavePlanRequest req) {
        p.setName(req.name().trim());
        p.setDescription(blankToNull(req.description()));
        p.setNotes(blankToNull(req.notes()));
        p.setObjective(blankToNull(req.objective()));
        p.setSourceReference(blankToNull(req.sourceReference()));
        if (p.getPreparedAt() == null) {
            // Quem criou fica registado à primeira. Alterações posteriores não
            // reescrevem a autoria: quem elaborou foi quem elaborou.
            p.setPreparedByLabel(blankToNull(req.preparedByLabel()));
            p.setPreparedAt(java.time.Instant.now());
        }
        if (req.assetTypeId() != null && !req.assetTypeId().isBlank()) {
            p.setAssetType(assetTypes.findByIdAndOrganizationId(req.assetTypeId(), orgId)
                    .orElseThrow(() -> ApiException.badRequest("Tipo de ativo inválido.")));
        } else {
            p.setAssetType(null);
        }

        int order = 1;
        for (TaskInput ti : req.tasks()) {
            PlanTask task = new PlanTask();
            task.setSystemCode(blankToNull(ti.systemCode()));
            task.setSystemName(blankToNull(ti.systemName()));
            task.setTitle(ti.title().trim());
            task.setInstructions(blankToNull(ti.instructions()));
            task.setEstimatedMinutes(ti.estimatedMinutes());
            task.setTools(blankToNull(ti.tools()));
            task.setSortOrder(order++);

            for (TriggerInput tr : ti.triggers()) {
                validateTrigger(tr);
                PlanTaskTrigger trigger = new PlanTaskTrigger();
                trigger.setTriggerType(tr.type());
                trigger.setMeterKind(tr.type() == PlanTriggerType.METER_INTERVAL ? tr.meterKind() : null);
                trigger.setIntervalValue(tr.interval());
                trigger.setToleranceValue(tr.tolerance());
                task.addTrigger(trigger);
            }
            if (ti.parts() != null) {
                for (PartInput pi : ti.parts()) {
                    PlanTaskPart part = new PlanTaskPart();
                    part.setPartName(pi.name().trim());
                    part.setQuantity(pi.quantity() != null ? pi.quantity() : BigDecimal.ONE);
                    part.setUnit(blankToNull(pi.unit()));
                    task.addPart(part);
                }
            }
            p.addTask(task);
        }
    }

    private void validateTrigger(TriggerInput tr) {
        if (tr.type() == PlanTriggerType.METER_INTERVAL && tr.meterKind() == null) {
            throw ApiException.badRequest(
                    "Um gatilho por medidor precisa de indicar o tipo (horímetro ou hodómetro).");
        }
        if (tr.interval() == null || tr.interval().signum() <= 0) {
            throw ApiException.badRequest("O intervalo do gatilho tem de ser maior que zero.");
        }
    }

    private MaintenancePlan load(String orgId, String id) {
        return plans.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Plano de manutenção não encontrado."));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Aprova o plano.
     *
     * <p>Um plano por aprovar aplica-se na mesma — não se trava a manutenção à
     * espera de uma assinatura. Mas fica assinalado, porque numa auditoria de
     * segurança a pergunta é sempre quem decidiu que esta máquina se revê às
     * 250 horas e não às 500.
     *
     * <p>A data é do servidor. Uma data escrita por quem assina não prova nada.
     */
    @Transactional
    public PlanView approve(String orgId, String userId, String id, ApprovePlanRequest req) {
        MaintenancePlan p = plans.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Plano não encontrado."));
        if (p.isApproved()) {
            throw ApiException.conflict("Este plano já foi aprovado por "
                    + (p.getApprovedByLabel() != null ? p.getApprovedByLabel() : "alguém") + ".");
        }
        String quem = req != null && req.approvedByLabel() != null
                && !req.approvedByLabel().isBlank()
                ? req.approvedByLabel().trim()
                : users.findById(userId).map(u -> u.getName()).orElse(null);
        if (quem == null) {
            throw ApiException.badRequest("Indique quem aprova o plano.");
        }
        p.setApprovedByLabel(quem);
        p.setApprovedBy(userId);
        p.setApprovedAt(java.time.Instant.now());
        if (req != null && req.sourceReference() != null && !req.sourceReference().isBlank()) {
            p.setSourceReference(req.sourceReference().trim());
        }
        audit.record(orgId, userId, "maintenance_plan.approve", "MaintenancePlan", p.getId(),
                p.getName() + " aprovado por " + quem);
        return PlanView.of(p);
    }
}
