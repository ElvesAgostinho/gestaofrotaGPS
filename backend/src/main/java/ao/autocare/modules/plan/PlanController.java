package ao.autocare.modules.plan;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.plan.dto.PlanDtos.ApprovePlanRequest;
import ao.autocare.modules.plan.dto.PlanDtos.PlanView;
import ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Planos de manutenção")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/maintenance-plans")
public class PlanController {

    private final PlanService service;
    private final CatalogApplyService catalogApply;
    private final OrgContext orgContext;

    public PlanController(PlanService service, OrgContext orgContext,
            CatalogApplyService catalogApply) {
        this.service = service;
        this.catalogApply = catalogApply;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Listar planos de manutenção")
    @GetMapping
    public List<PlanView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Obter um plano (com tarefas e gatilhos)")
    @GetMapping("/{id}")
    public PlanView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return service.get(orgContext.requireOrganizationId(principal), id);
    }

    @Operation(summary = "Aprovar o plano",
            description = "Um plano por aprovar aplica-se na mesma, mas fica assinalado: "
                    + "numa auditoria, a pergunta é quem decidiu estes intervalos.")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping("/{id}/approve")
    public PlanView approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) ApprovePlanRequest req) {
        return service.approve(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }

    @Operation(summary = "Modelos de plano prontos",
            description = "Planos completos por tipo de equipamento, para não se escrever "
                    + "trinta tarefas à mão em cada máquina.")
    @GetMapping("/catalog")
    public java.util.List<PlanCatalog.Modelo> catalog() {
        return PlanCatalog.disponiveis();
    }

    @Operation(summary = "Aplicar um modelo por inteiro",
            description = "Cria o plano de horas, a inspeção diária, os programas de "
                    + "monitorização preditiva e as peças de armazém. Indicando o ativo, "
                    + "o plano fica-lhe atribuído e a preditiva é criada para ele.")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping("/from-catalog/{code}/apply")
    public CatalogApplyService.Resultado applyCatalog(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String code,
            @RequestParam(required = false) String assetTypeId,
            @RequestParam(required = false) String assetId,
            @RequestParam(defaultValue = "true") boolean checklist,
            @RequestParam(defaultValue = "true") boolean predictive,
            @RequestParam(defaultValue = "true") boolean parts) {
        return catalogApply.apply(orgContext.requireOrganizationId(principal), principal.id(),
                code, assetTypeId, assetId, checklist, predictive, parts);
    }

    @Operation(summary = "Criar um plano a partir de um modelo",
            description = "Cria o plano completo com tarefas, intervalos, ferramentas e peças. "
                    + "Fica editável: o manual do fabricante manda sempre.")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping("/from-catalog/{code}")
    public PlanView fromCatalog(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String code,
            @RequestParam(required = false) String assetTypeId) {
        return service.create(orgContext.requireOrganizationId(principal), principal.id(),
                PlanCatalog.build(code, assetTypeId));
    }

    @Operation(summary = "Criar um plano de manutenção")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanView create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SavePlanRequest req) {
        return service.create(orgContext.requireOrganizationId(principal), principal.id(), req);
    }

    @Operation(summary = "Atualizar um plano (substitui as tarefas)")
    @RequirePermission(Permission.PLANS_MANAGE)
    @PutMapping("/{id}")
    public PlanView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody SavePlanRequest req) {
        return service.update(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }

    @Operation(summary = "Eliminar um plano de manutenção")
    @RequirePermission(Permission.PLANS_MANAGE)
    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        service.delete(orgContext.requireOrganizationId(principal), principal.id(), id);
        return Map.of("message", "Plano eliminado.");
    }
}
