package ao.autocare.modules.consumables;

import ao.autocare.modules.consumables.dto.ConsumableDtos.Painel;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O painel de pneus, óleos e filtros.
 *
 * <p>Ler é de quem vê o equipamento. Os custos só saem para quem os pode ver —
 * um encarregado de oficina vê que faltam três filtros e quando, mas não vê o
 * preço nem o custo por quilómetro dos pneus.
 */
@Tag(name = "Consumíveis")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class ConsumablesController {

    private final ConsumablesService service;
    private final OrgContext orgContext;

    public ConsumablesController(ConsumablesService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Pneus, óleos e filtros a vencer",
            description = "O que tem de ser trocado no horizonte pedido, em que equipamento, "
                    + "com que peça — e se está em armazém. Inclui a taxa de desgaste dos "
                    + "pneus medida a partir do sulco e a lista de compras com prazo de entrega.")
    @GetMapping("/api/v1/consumables")
    public Painel painel(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) Integer dias) {
        return service.painel(orgContext.requireOrganizationId(p), dias, p.has(Permission.COSTS_VIEW));
    }

    @Operation(summary = "Ligar as peças dos planos ao armazém",
            description = "Faz corresponder, pelo nome, as peças escritas nos planos aos "
                    + "artigos do armazém. Sem esta ligação o painel mostra a peça mas não o stock.")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/consumables/link-parts")
    public Map<String, Integer> ligar(@AuthenticationPrincipal AuthPrincipal p) {
        return Map.of("linked", service.ligarAoArmazem(orgContext.requireOrganizationId(p)));
    }
}
