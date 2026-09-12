package ao.autocare.modules.assettype;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.CreateAssetTypeRequest;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.UpdateAssetTypeRequest;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.AssetTypeView;
import ao.autocare.modules.org.OrgContext;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tipos de ativo")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/asset-types")
public class AssetTypeController {

    private final AssetTypeService service;
    private final OrgContext orgContext;

    public AssetTypeController(AssetTypeService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Listar tipos de ativo")
    @GetMapping
    public List<AssetTypeView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Obter um tipo de ativo")
    @GetMapping("/{id}")
    public AssetTypeView get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return service.get(orgContext.requireOrganizationId(principal), id);
    }

    @Operation(summary = "Criar um tipo de ativo (com os 8 sistemas padrão por omissão)")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetTypeView create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAssetTypeRequest req) {
        return service.create(orgContext.requireOrganizationId(principal), principal.id(), req);
    }

    @Operation(summary = "Atualizar um tipo de ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PatchMapping("/{id}")
    public AssetTypeView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateAssetTypeRequest req) {
        return service.update(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }

    @Operation(summary = "Eliminar um tipo de ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        service.delete(orgContext.requireOrganizationId(principal), principal.id(), id);
        return Map.of("message", "Tipo de ativo eliminado.");
    }
}
