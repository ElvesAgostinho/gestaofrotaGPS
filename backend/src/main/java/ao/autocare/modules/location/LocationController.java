package ao.autocare.modules.location;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.location.dto.LocationDtos.CreateLocationRequest;
import ao.autocare.modules.location.dto.LocationDtos.LocationNode;
import ao.autocare.modules.location.dto.LocationDtos.LocationView;
import ao.autocare.modules.location.dto.LocationDtos.UpdateLocationRequest;
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

@Tag(name = "Locais")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService service;
    private final OrgContext orgContext;

    public LocationController(LocationService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Listar locais da empresa")
    @GetMapping
    public List<LocationView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Árvore de locais (hierarquia)")
    @GetMapping("/tree")
    public List<LocationNode> tree(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.tree(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Obter um local")
    @GetMapping("/{id}")
    public LocationView get(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return service.get(orgContext.requireOrganizationId(principal), id);
    }

    @Operation(summary = "Criar um local")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationView create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateLocationRequest req) {
        return service.create(orgContext.requireOrganizationId(principal), principal.id(), req);
    }

    @Operation(summary = "Atualizar um local")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PatchMapping("/{id}")
    public LocationView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateLocationRequest req) {
        return service.update(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }

    @Operation(summary = "Eliminar um local")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        service.delete(orgContext.requireOrganizationId(principal), principal.id(), id);
        return Map.of("message", "Local eliminado.");
    }
}
