package ao.autocare.modules.checklist;

import ao.autocare.common.ApiException;
import ao.autocare.domain.ChecklistItem;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.enums.Enums.VerificationType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ItemInput;
import ao.autocare.modules.checklist.dto.ChecklistDtos.SaveTemplateRequest;
import ao.autocare.modules.checklist.dto.ChecklistDtos.TemplateView;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.ChecklistTemplateRepository;
import ao.autocare.repo.OrganizationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistTemplateService {

    private final ChecklistTemplateRepository templates;
    private final AssetTypeRepository assetTypes;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public ChecklistTemplateService(
            ChecklistTemplateRepository templates,
            AssetTypeRepository assetTypes,
            OrganizationRepository organizations,
            AuditService audit) {
        this.templates = templates;
        this.assetTypes = assetTypes;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(String orgId) {
        return templates.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(TemplateView::of).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(String orgId, String id) {
        return TemplateView.of(load(orgId, id));
    }

    @Transactional
    public TemplateView create(String orgId, String userId, SaveTemplateRequest req) {
        ChecklistTemplate t = new ChecklistTemplate();
        t.setOrganization(organizations.getReferenceById(orgId));
        apply(orgId, t, req);
        templates.save(t);
        audit.record(orgId, userId, "checklist_template.create", "ChecklistTemplate", t.getId(), t.getName());
        return TemplateView.of(t);
    }

    @Transactional
    public TemplateView update(String orgId, String userId, String id, SaveTemplateRequest req) {
        ChecklistTemplate t = load(orgId, id);
        t.getItems().clear();
        apply(orgId, t, req);
        audit.record(orgId, userId, "checklist_template.update", "ChecklistTemplate", t.getId(), t.getName());
        return TemplateView.of(t);
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        ChecklistTemplate t = load(orgId, id);
        templates.delete(t);
        audit.record(orgId, userId, "checklist_template.delete", "ChecklistTemplate", id, t.getName());
    }

    // ------------------------------------------------------------------
    private void apply(String orgId, ChecklistTemplate t, SaveTemplateRequest req) {
        t.setName(req.name().trim());
        t.setDescription(blankToNull(req.description()));
        t.setEstimatedMinutes(req.estimatedMinutes());
        if (req.assetTypeId() != null && !req.assetTypeId().isBlank()) {
            t.setAssetType(assetTypes.findByIdAndOrganizationId(req.assetTypeId(), orgId)
                    .orElseThrow(() -> ApiException.badRequest("Tipo de ativo inválido.")));
        } else {
            t.setAssetType(null);
        }
        int order = 1;
        for (ItemInput in : req.items()) {
            ChecklistItem item = new ChecklistItem();
            item.setText(in.text().trim());
            item.setVerification(in.verification() != null ? in.verification() : VerificationType.VERIFY);
            item.setCritical(Boolean.TRUE.equals(in.critical()));
            item.setSortOrder(order++);
            t.addItem(item);
        }
    }

    private ChecklistTemplate load(String orgId, String id) {
        return templates.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Modelo de checklist não encontrado."));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
