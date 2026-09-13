package ao.autocare.modules.tyre;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.tyre.dto.TyreDtos.ReadingRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.RemoveRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.RotateRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.SaveTyreRequest;
import ao.autocare.modules.tyre.dto.TyreDtos.TyreDetail;
import ao.autocare.modules.tyre.dto.TyreDtos.TyreView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pneus. Ver é de quem vê o equipamento; montar, medir e desmontar é de quem
 * trabalha as ordens (o mecânico); os custos só saem para quem os pode ver.
 */
@Tag(name = "Pneus")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class TyreController {

    private final TyreService service;
    private final OrgContext orgContext;

    public TyreController(TyreService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    private static boolean money(AuthPrincipal p) {
        return p.has(Permission.COSTS_VIEW);
    }

    @Operation(summary = "Pneus de um ativo (montados primeiro)")
    @GetMapping("/api/v1/assets/{assetId}/tyres")
    public List<TyreView> forAsset(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return service.listForAsset(org(p), assetId, money(p));
    }

    @Operation(summary = "Todos os pneus da empresa")
    @GetMapping("/api/v1/tyres")
    public List<TyreView> all(@AuthenticationPrincipal AuthPrincipal p) {
        return service.listAll(org(p), money(p));
    }

    @Operation(summary = "Um pneu com o histórico de medições")
    @GetMapping("/api/v1/tyres/{id}")
    public TyreDetail get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.get(org(p), id, money(p));
    }

    @Operation(summary = "Montar um pneu numa posição do ativo")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/assets/{assetId}/tyres")
    @ResponseStatus(HttpStatus.CREATED)
    public TyreView install(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId,
            @Valid @RequestBody SaveTyreRequest req) {
        return service.install(org(p), p.id(), assetId, req, money(p));
    }

    @Operation(summary = "Alterar os dados de um pneu")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PatchMapping("/api/v1/tyres/{id}")
    public TyreView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveTyreRequest req) {
        return service.update(org(p), p.id(), id, req, money(p));
    }

    @Operation(summary = "Registar uma medição (pressão e/ou sulco)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/tyres/{id}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public TyreDetail reading(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ReadingRequest req) {
        return service.addReading(org(p), p.id(), id, req, money(p));
    }

    @Operation(summary = "Desmontar (fim de vida ou para stock)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/tyres/{id}/remove")
    public TyreView remove(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody RemoveRequest req) {
        return service.remove(org(p), p.id(), id, req, money(p));
    }

    @Operation(summary = "Rodar: trocar de posição com outro pneu da mesma viatura")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/tyres/{id}/rotate")
    public List<TyreView> rotate(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody RotateRequest req) {
        return service.rotate(org(p), p.id(), id, req.otherTyreId(), money(p));
    }

    @Operation(summary = "Apagar um registo de pneu errado")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @DeleteMapping("/api/v1/tyres/{id}")
    public Map<String, String> delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.delete(org(p), p.id(), id);
        return Map.of("message", "Pneu apagado.");
    }
}
