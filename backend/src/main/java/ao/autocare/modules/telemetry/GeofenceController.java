package ao.autocare.modules.telemetry;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.GeofenceEventView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.GeofenceView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.PresenceView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.SaveGeofenceRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Geocercas")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class GeofenceController {

    private final GeofenceService geofences;
    private final OrgContext orgContext;

    public GeofenceController(GeofenceService geofences, OrgContext orgContext) {
        this.geofences = geofences;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Listar áreas")
    @GetMapping("/api/v1/geofences")
    public List<GeofenceView> list(@AuthenticationPrincipal AuthPrincipal p) {
        return geofences.list(org(p));
    }

    @Operation(summary = "Obter uma área")
    @GetMapping("/api/v1/geofences/{id}")
    public GeofenceView get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return geofences.get(org(p), id);
    }

    @Operation(summary = "Criar uma área (círculo com centro e raio, ou polígono)")
    @RequirePermission(Permission.GPS_MANAGE)
    @PostMapping("/api/v1/geofences")
    @ResponseStatus(HttpStatus.CREATED)
    public GeofenceView create(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody SaveGeofenceRequest req) {
        return geofences.create(org(p), p.id(), req);
    }

    @Operation(summary = "Atualizar uma área")
    @RequirePermission(Permission.GPS_MANAGE)
    @PatchMapping("/api/v1/geofences/{id}")
    public GeofenceView update(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody SaveGeofenceRequest req) {
        return geofences.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Remover uma área")
    @RequirePermission(Permission.GPS_MANAGE)
    @DeleteMapping("/api/v1/geofences/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        geofences.delete(org(p), p.id(), id);
        return Map.of("message", "Área removida.");
    }

    @Operation(summary = "Ativos que estão dentro de uma área agora")
    @GetMapping("/api/v1/geofences/{id}/inside")
    public List<PresenceView> inside(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return geofences.inside(org(p), id);
    }

    @Operation(summary = "Entradas e saídas de toda a frota")
    @GetMapping("/api/v1/geofence-events")
    public PagedResponse<GeofenceEventView> events(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return geofences.listEvents(org(p),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Entradas e saídas de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/geofence-events")
    public PagedResponse<GeofenceEventView> assetEvents(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return geofences.listEventsForAsset(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Marcar um evento como visto")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/geofence-events/{id}/acknowledge")
    public GeofenceEventView acknowledge(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return geofences.acknowledge(org(p), p.id(), id);
    }
}
