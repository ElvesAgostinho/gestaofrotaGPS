package ao.autocare.modules.checklist;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ExecutionView;
import ao.autocare.modules.checklist.dto.ChecklistDtos.RecordExecutionRequest;
import ao.autocare.modules.checklist.dto.ChecklistDtos.SaveTemplateRequest;
import ao.autocare.modules.checklist.dto.ChecklistDtos.TemplateView;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checklists")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class ChecklistController {

    private final ChecklistTemplateService templates;
    private final ChecklistExecutionService executionsService;
    private final OrgContext orgContext;

    public ChecklistController(
            ChecklistTemplateService templates,
            ChecklistExecutionService executionsService,
            OrgContext orgContext) {
        this.templates = templates;
        this.executionsService = executionsService;
        this.orgContext = orgContext;
    }

    // ---- Modelos ------------------------------------------------------
    @Operation(summary = "Listar modelos de checklist")
    @GetMapping("/api/v1/checklist-templates")
    public List<TemplateView> listTemplates(@AuthenticationPrincipal AuthPrincipal principal) {
        return templates.list(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Obter um modelo de checklist")
    @GetMapping("/api/v1/checklist-templates/{id}")
    public TemplateView getTemplate(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return templates.get(orgContext.requireOrganizationId(principal), id);
    }

    @Operation(summary = "Criar um modelo de checklist")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/checklist-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView createTemplate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SaveTemplateRequest req) {
        return templates.create(orgContext.requireOrganizationId(principal), principal.id(), req);
    }

    @Operation(summary = "Atualizar um modelo de checklist (substitui os itens)")
    @RequireRole(MembershipRole.MANAGER)
    @PutMapping("/api/v1/checklist-templates/{id}")
    public TemplateView updateTemplate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody SaveTemplateRequest req) {
        return templates.update(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }

    @Operation(summary = "Eliminar um modelo de checklist")
    @RequireRole(MembershipRole.MANAGER)
    @DeleteMapping("/api/v1/checklist-templates/{id}")
    public Map<String, String> deleteTemplate(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        templates.delete(orgContext.requireOrganizationId(principal), principal.id(), id);
        return Map.of("message", "Modelo eliminado.");
    }

    // ---- Execuções ---------------------------------------------------
    @Operation(summary = "Histórico de inspeções de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/checklist-executions")
    public PagedResponse<ExecutionView> history(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return executionsService.history(
                orgContext.requireOrganizationId(principal), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Registar uma inspeção")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/assets/{assetId}/checklist-executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionView record(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @Valid @RequestBody RecordExecutionRequest req) {
        return executionsService.record(
                orgContext.requireOrganizationId(principal), principal.id(), assetId, req);
    }

    @Operation(summary = "Obter uma inspeção")
    @GetMapping("/api/v1/checklist-executions/{id}")
    public ExecutionView getExecution(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return executionsService.get(orgContext.requireOrganizationId(principal), id);
    }
}
