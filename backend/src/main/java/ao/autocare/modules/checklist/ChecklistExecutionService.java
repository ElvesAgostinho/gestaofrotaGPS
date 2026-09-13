package ao.autocare.modules.checklist;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.ChecklistExecution;
import ao.autocare.domain.ChecklistExecutionItem;
import ao.autocare.domain.ChecklistItem;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.enums.Enums.ChecklistItemResult;
import ao.autocare.domain.enums.Enums.ChecklistOutcome;
import ao.autocare.domain.enums.Enums.VerificationType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ExecutionView;
import ao.autocare.modules.checklist.dto.ChecklistDtos.RecordExecutionRequest;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ResultInput;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.ChecklistExecutionRepository;
import ao.autocare.repo.ChecklistTemplateRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistExecutionService {

    private final AssetRepository assets;
    private final ChecklistExecutionRepository executions;
    private final ChecklistTemplateRepository templates;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuditService audit;
    private final ao.autocare.modules.notification.NotificationService notifications;
    private final ao.autocare.modules.meter.MeterService meters;

    public ChecklistExecutionService(
            AssetRepository assets,
            ChecklistExecutionRepository executions,
            ChecklistTemplateRepository templates,
            OrganizationRepository organizations,
            UserRepository users,
            AuditService audit,
            ao.autocare.modules.notification.NotificationService notifications,
            ao.autocare.modules.meter.MeterService meters) {
        this.assets = assets;
        this.executions = executions;
        this.templates = templates;
        this.organizations = organizations;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.meters = meters;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExecutionView> history(String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                executions.findByAssetIdOrderByPerformedAtDesc(assetId, pageable)
                        .map(ExecutionView::summary));
    }

    @Transactional(readOnly = true)
    public ExecutionView get(String orgId, String id) {
        return ExecutionView.of(executions.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Execução de checklist não encontrada.")));
    }

    @Transactional
    public ExecutionView record(String orgId, String userId, String assetId, RecordExecutionRequest req) {
        Asset asset = requireAsset(orgId, assetId);

        ChecklistTemplate template = null;
        if (req.templateId() != null && !req.templateId().isBlank()) {
            template = templates.findByIdAndOrganizationId(req.templateId(), orgId)
                    .orElseThrow(() -> ApiException.badRequest("Modelo de checklist inválido."));
        }

        List<ResultInput> itemInputs = req.items();
        if ((itemInputs == null || itemInputs.isEmpty()) && template != null) {
            // Sem resultados enviados: parte do modelo com tudo OK.
            itemInputs = template.getItems().stream()
                    .map(i -> new ResultInput(i.getText(), i.getVerification(), i.isCritical(),
                            ChecklistItemResult.OK, null))
                    .toList();
        }
        if (itemInputs == null || itemInputs.isEmpty()) {
            throw ApiException.badRequest("A checklist não tem itens para registar.");
        }

        ChecklistExecution exec = new ChecklistExecution();
        exec.setOrganization(organizations.getReferenceById(orgId));
        exec.setAsset(asset);
        exec.setTemplate(template);
        exec.setTemplateName(resolveName(req, template));
        exec.setPerformedAt(req.performedAt() != null ? req.performedAt() : Instant.now());
        exec.setMeterValue(req.meterValue());
        exec.setNotes(blankToNull(req.notes()));
        if (userId != null) {
            exec.setPerformedBy(users.getReferenceById(userId));
        }
        exec.setPerformedByLabel(blankToNull(req.performedByLabel()));

        boolean anyNotOk = false;
        java.util.List<String> criticosMal = new java.util.ArrayList<>();
        int order = 1;
        for (ResultInput in : itemInputs) {
            ChecklistExecutionItem item = new ChecklistExecutionItem();
            item.setText(in.text().trim());
            item.setVerification(in.verification() != null ? in.verification() : VerificationType.VERIFY);
            item.setCritical(Boolean.TRUE.equals(in.critical()));
            item.setResult(in.result() != null ? in.result() : ChecklistItemResult.OK);
            item.setNote(blankToNull(in.note()));
            item.setSortOrder(order++);
            exec.addItem(item);
            if (item.getResult() == ChecklistItemResult.NOT_OK) {
                anyNotOk = true;
                if (item.isCritical()) {
                    criticosMal.add(item.getText());
                }
            }
        }
        exec.setOutcome(anyNotOk ? ChecklistOutcome.ISSUES : ChecklistOutcome.OK);
        executions.save(exec);

        if (req.meterValue() != null) {
            meters.recordFromInspection(asset, req.meterValue(), exec.getPerformedAt(), userId,
                    "Inspeção: " + exec.getTemplateName());
        }
        // Um ponto crítico reprovado (travões, direção, fuga…) não pode ficar só
        // na lista: quem gere tem de saber antes de a viatura sair.
        if (!criticosMal.isEmpty()) {
            String quem = exec.getPerformedByLabel() != null ? exec.getPerformedByLabel()
                    : users.findById(userId).map(ao.autocare.domain.User::getName).orElse("alguém");
            notifications.notifyManagers(ao.autocare.modules.notification.NotificationService.Draft.of(
                    orgId, ao.autocare.domain.enums.Enums.AlertCategory.INSPECTION,
                    ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                    "Inspeção reprovada: " + asset.getTag(),
                    quem + " — " + exec.getTemplateName() + ". Pontos críticos reprovados: "
                            + String.join(", ", criticosMal) + ".",
                    "checklist_issue", exec.getId(), "/ativos/" + asset.getId()).forAsset(asset));
        }

        audit.record(orgId, userId, "checklist.execute", "Asset", assetId,
                asset.getTag() + " · " + exec.getTemplateName() + " → " + exec.getOutcome());
        return ExecutionView.of(exec);
    }

    // ------------------------------------------------------------------
    private String resolveName(RecordExecutionRequest req, ChecklistTemplate template) {
        if (req.templateName() != null && !req.templateName().isBlank()) return req.templateName().trim();
        if (template != null) return template.getName();
        return "Inspeção";
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
