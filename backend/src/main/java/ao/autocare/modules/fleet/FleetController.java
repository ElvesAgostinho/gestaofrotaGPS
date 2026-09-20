package ao.autocare.modules.fleet;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.fleet.dto.FleetDtos.AssignDriverRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.AssignmentView;
import ao.autocare.modules.fleet.dto.FleetDtos.DriverSummary;
import ao.autocare.modules.fleet.dto.FleetDtos.DriverView;
import ao.autocare.modules.fleet.dto.FleetDtos.EndAssignmentRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.CalculateRouteRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.RouteCalculationView;
import ao.autocare.modules.fleet.dto.FleetDtos.RouteView;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveDriverRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveRouteRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Motoristas, quem conduz o quê, e rotas previstas. */
@Tag(name = "Frota")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class FleetController {

    private final DriverService driversService;
    private final RouteService routesService;
    private final RouteLiveService routesLiveService;
    private final DriverAccessService driverAccess;
    private final OrgContext orgContext;

    public FleetController(
            DriverAccessService driverAccess,
            DriverService driversService, RouteService routesService, OrgContext orgContext, RouteLiveService routesLiveService) {
        this.driversService = driversService;
        this.routesService = routesService;
        this.routesLiveService = routesLiveService;
        this.driverAccess = driverAccess;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== Motoristas =======================================================
    @Operation(summary = "Motoristas da empresa")
    @GetMapping("/api/v1/drivers")
    public PagedResponse<DriverView> drivers(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return driversService.list(org(p), search,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    // ==== Acesso do motorista à aplicação ==================================

    @Operation(summary = "Estado do acesso de um motorista à aplicação")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @GetMapping("/api/v1/drivers/{driverId}/access")
    public DriverAccessService.Acesso driverAccess(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String driverId) {
        return driverAccess.estado(org(p), driverId);
    }

    @Operation(summary = "Criar o acesso de um motorista",
            description = "Gera o identificador curto e a palavra-passe. A palavra-passe é "
                    + "devolvida uma única vez: depois fica cifrada e nem o gestor a vê.")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{driverId}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public DriverAccessService.Credenciais createDriverAccess(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String driverId) {
        return driverAccess.criar(org(p), p.id(), driverId);
    }

    @Operation(summary = "Repor a palavra-passe de um motorista")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{driverId}/access/password")
    public DriverAccessService.Credenciais resetDriverPassword(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String driverId) {
        return driverAccess.reporPalavraPasse(org(p), p.id(), driverId);
    }

    @Operation(summary = "Bloquear o acesso de um motorista")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{driverId}/access/block")
    public DriverAccessService.Acesso blockDriverAccess(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String driverId) {
        return driverAccess.bloquear(org(p), p.id(), driverId);
    }

    @Operation(summary = "Desbloquear o acesso de um motorista")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{driverId}/access/unblock")
    public DriverAccessService.Acesso unblockDriverAccess(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String driverId) {
        return driverAccess.desbloquear(org(p), p.id(), driverId);
    }

    @Operation(summary = "Resumo de motoristas para o painel")
    @GetMapping("/api/v1/drivers/summary")
    public DriverSummary summary(@AuthenticationPrincipal AuthPrincipal p) {
        return driversService.summary(org(p));
    }

    @Operation(summary = "Cartas de condução caducadas ou a caducar",
            description = "Um motorista a conduzir com a carta caducada é "
                    + "responsabilidade da empresa.")
    @GetMapping("/api/v1/drivers/licenses")
    public List<DriverView> licenses(@AuthenticationPrincipal AuthPrincipal p) {
        return driversService.licensesNeedingAttention(org(p));
    }

    @Operation(summary = "Ficha de um motorista")
    @GetMapping("/api/v1/drivers/{id}")
    public DriverView driver(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return driversService.get(org(p), id);
    }

    @Operation(summary = "Registar um motorista")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public DriverView create(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody SaveDriverRequest req) {
        return driversService.create(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar um motorista")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PutMapping("/api/v1/drivers/{id}")
    public DriverView update(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveDriverRequest req) {
        return driversService.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Eliminar um motorista sem histórico")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @DeleteMapping("/api/v1/drivers/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        driversService.delete(org(p), p.id(), id);
        return Map.of("message", "Motorista eliminado.");
    }

    @Operation(summary = "Ativos que este motorista conduziu")
    @GetMapping("/api/v1/drivers/{id}/assignments")
    public List<AssignmentView> driverHistory(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return driversService.historyForDriver(org(p), id);
    }

    // ==== Atribuições ======================================================
    @Operation(summary = "Pôr um motorista ao volante de um ativo")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/driver-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assign(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody AssignDriverRequest req) {
        return driversService.assign(org(p), p.id(), req);
    }

    @Operation(summary = "Encerrar uma atribuição")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PatchMapping("/api/v1/driver-assignments/{id}/end")
    public AssignmentView endAssignment(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody(required = false) EndAssignmentRequest req) {
        return driversService.endAssignment(org(p), p.id(), id, req);
    }

    @Operation(summary = "Quem conduziu este ativo, ao longo do tempo")
    @GetMapping("/api/v1/assets/{assetId}/drivers")
    public List<AssignmentView> assetHistory(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return driversService.historyForAsset(org(p), assetId);
    }

    // ==== Rotas ============================================================
    @Operation(summary = "Rotas previstas")
    @GetMapping("/api/v1/routes")
    public List<RouteView> routes(@AuthenticationPrincipal AuthPrincipal p) {
        return routesService.list(org(p));
    }

    @Operation(summary = "Uma rota")
    @GetMapping("/api/v1/routes/{id}")
    public RouteView route(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return routesService.get(org(p), id);
    }

    @Operation(summary = "Calcular o percurso entre pontos",
            description = "Usa o motor de rotas da empresa; sem ele, estima em linha "
                    + "reta e diz que o fez. Nao grava nada.")
    @PostMapping("/api/v1/routes/calculate")
    public RouteCalculationView calculateRoute(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody CalculateRouteRequest req) {
        return routesService.calculate(org(p), req);
    }

    @Operation(summary = "Viaturas atribuídas a uma rota")
    @GetMapping("/api/v1/routes/{id}/assignments")
    public java.util.List<ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView> routeAssignments(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return routesService.listAssignments(org(p), id);
    }

    @Operation(summary = "Atribuir uma viatura (e motorista) a uma rota")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/routes/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView assignRoute(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ao.autocare.modules.fleet.dto.FleetDtos.AssignRouteRequest req) {
        return routesService.assign(org(p), p.id(), id, req);
    }

    @Operation(summary = "Retirar uma atribuição (nunca a última da rota)")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @DeleteMapping("/api/v1/route-assignments/{id}")
    public java.util.Map<String, String> unassignRoute(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        routesService.unassign(org(p), p.id(), id);
        return java.util.Map.of("message", "Atribuição retirada.");
    }

    @Operation(summary = "Quem vai a caminho agora: progresso, hora prevista de chegada e desvio")
    @GetMapping("/api/v1/routes/live")
    public java.util.List<ao.autocare.modules.fleet.dto.FleetDtos.RouteLiveView> routesLive(
            @AuthenticationPrincipal AuthPrincipal p) {
        return routesLiveService.live(org(p));
    }

    @Operation(summary = "Viaturas a caminho nesta rota")
    @GetMapping("/api/v1/routes/{id}/live")
    public java.util.List<ao.autocare.modules.fleet.dto.FleetDtos.RouteLiveView> routeLive(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return routesLiveService.liveForRoute(org(p), id);
    }

    @Operation(summary = "Previsto contra o andado: traçado da rota e percurso real das últimas viagens")
    @GetMapping("/api/v1/routes/{id}/comparison")
    public java.util.List<ao.autocare.modules.fleet.dto.FleetDtos.RouteVsRealView> routeComparison(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestParam(defaultValue = "5") int limit) {
        return routesService.comparison(org(p), id, limit);
    }

    @Operation(summary = "Criar uma rota")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteView createRoute(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody SaveRouteRequest req) {
        return routesService.create(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar uma rota")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PutMapping("/api/v1/routes/{id}")
    public RouteView updateRoute(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveRouteRequest req) {
        return routesService.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Eliminar uma rota")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @DeleteMapping("/api/v1/routes/{id}")
    public Map<String, String> deleteRoute(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        routesService.delete(org(p), p.id(), id);
        return Map.of("message", "Rota eliminada.");
    }
}
