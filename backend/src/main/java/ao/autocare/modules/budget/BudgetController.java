package ao.autocare.modules.budget;

import ao.autocare.modules.budget.dto.BudgetDtos.BudgetView;
import ao.autocare.modules.budget.dto.BudgetDtos.SaveBudgetRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Orçamentos anuais. Só quem vê custos entra aqui; só quem gere definições os define. */
@Tag(name = "Orçamento")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequirePermission(Permission.COSTS_VIEW)
public class BudgetController {

    private final BudgetService service;
    private final OrgContext orgContext;

    public BudgetController(BudgetService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Orçamentos de um ano com o gasto real até hoje")
    @GetMapping("/api/v1/budgets")
    public Map<String, Object> list(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) Integer year) {
        int ano = year != null ? year : LocalDate.now(ZoneId.of("Africa/Luanda")).getYear();
        List<BudgetView> itens = service.list(org(p), ano);
        return Map.of("year", ano, "items", itens, "years", service.years(org(p)));
    }

    @Operation(summary = "Definir um orçamento")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PostMapping("/api/v1/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetView create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SaveBudgetRequest req) {
        return service.create(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar o valor ou as notas")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PatchMapping("/api/v1/budgets/{id}")
    public BudgetView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveBudgetRequest req) {
        return service.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Apagar um orçamento")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @DeleteMapping("/api/v1/budgets/{id}")
    public Map<String, String> delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.delete(org(p), p.id(), id);
        return Map.of("message", "Orçamento apagado.");
    }
}
