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
    private final DailyInspectionService daily;
    private final OperatorSheetPdfService operatorSheet;
    private final ao.autocare.modules.org.DocumentSealService seals;

    public ChecklistController(
            ChecklistTemplateService templates,
            ChecklistExecutionService executionsService,
            OrgContext orgContext,
            DailyInspectionService daily,
            OperatorSheetPdfService operatorSheet,
            ao.autocare.modules.org.DocumentSealService seals) {
        this.templates = templates;
        this.executionsService = executionsService;
        this.orgContext = orgContext;
        this.daily = daily;
        this.operatorSheet = operatorSheet;
        this.seals = seals;
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

    @Operation(summary = "A inspeção diária deste equipamento: o modelo da empresa ou o sugerido para a família")
    @GetMapping("/api/v1/assets/{assetId}/daily-inspection")
    public DailyInspectionService.Ficha dailyInspection(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return daily.forAsset(orgContext.requireOrganizationId(principal), assetId);
    }

    @Operation(summary = "Criar na empresa a inspeção diária sugerida para esta família")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/assets/{assetId}/daily-inspection")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView adoptDailyInspection(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return daily.adopt(orgContext.requireOrganizationId(principal), principal.id(), assetId);
    }

    @Operation(summary = "Ficha do posto em PDF: inspeção diária, lubrificação e materiais, para pendurar na cabina")
    @GetMapping(value = "/api/v1/assets/{assetId}/operator-sheet.pdf",
            produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> operatorSheet(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        String orgId = orgContext.requireOrganizationId(principal);
        byte[] pdf = seals.emitir(orgId, principal.id(), "OPERATOR_SHEET", assetId, assetId,
                selo -> operatorSheet.render(orgId, assetId, selo));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"ficha-do-posto.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
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
