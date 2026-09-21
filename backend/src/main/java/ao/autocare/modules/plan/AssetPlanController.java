package ao.autocare.modules.plan;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.plan.dto.AssetPlanDtos.AssetPlanView;
import ao.autocare.modules.plan.dto.AssetPlanDtos.AssignPlanRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.CompleteTaskRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.CompletionView;
import ao.autocare.modules.plan.dto.AssetPlanDtos.IntervalRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.TaskCompletionResult;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Manutenção do ativo")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
@RequestMapping("/api/v1/assets/{assetId}")
public class AssetPlanController {

    private final AssetPlanService service;
    private final OrgContext orgContext;

    private final PlanService planService;

    public AssetPlanController(AssetPlanService service, OrgContext orgContext,
            PlanService planService) {
        this.planService = planService;
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Planos de manutenção do ativo e estado de cada tarefa")
    @GetMapping("/maintenance-plans")
    public List<AssetPlanView> plans(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return service.listForAsset(orgContext.requireOrganizationId(principal), assetId);
    }

    @Operation(summary = "Atribuir um plano de manutenção ao ativo")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping("/maintenance-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetPlanView assign(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @Valid @RequestBody AssignPlanRequest req) {
        return service.assign(orgContext.requireOrganizationId(principal), principal.id(), assetId, req);
    }

    @Operation(summary = "Remover a atribuição de um plano")
    @RequirePermission(Permission.PLANS_MANAGE)
    @DeleteMapping("/maintenance-plans/{assetPlanId}")
    public Map<String, String> unassign(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String assetPlanId) {
        service.unassign(orgContext.requireOrganizationId(principal), principal.id(), assetId, assetPlanId);
        return Map.of("message", "Plano removido do ativo.");
    }

    @Operation(summary = "Marcar uma tarefa de plano como executada (repõe o relógio)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/plan-tasks/{taskStateId}/complete")
    public TaskCompletionResult complete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String taskStateId,
            @Valid @RequestBody CompleteTaskRequest req) {
        return service.completeTask(
                orgContext.requireOrganizationId(principal), principal.id(), assetId, taskStateId, req);
    }

    @Operation(summary = "Histórico de tarefas de plano executadas")
    @GetMapping("/plan-task-completions")
    public PagedResponse<CompletionView> completions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.completionHistory(
                orgContext.requireOrganizationId(principal), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Definir o limite de manutenção do ativo (a cada N km / h / dias)",
            description = "A forma simples de dizer «revisão a cada 5 000 km». Cria um plano "
                    + "de uma tarefa (ou reutiliza um igual) e atribui-o ao ativo; a partir "
                    + "daí o contador — manual ou pelo GPS — faz a tarefa vencer.")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping("/maintenance-interval")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetPlanView defineInterval(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @Valid @RequestBody IntervalRequest req) {
        return service.defineInterval(orgContext.requireOrganizationId(principal), principal.id(),
                assetId, req, planService);
    }

    @Operation(summary = "Recalcular o vencimento das tarefas do ativo")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/maintenance-plans/recompute")
    public List<AssetPlanView> recompute(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return service.recomputeForAssetInOrg(orgContext.requireOrganizationId(principal), assetId);
    }
}
