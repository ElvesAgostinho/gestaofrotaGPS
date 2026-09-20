package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Location;
import ao.autocare.domain.Route;
import ao.autocare.domain.RouteWaypoint;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fleet.dto.FleetDtos.CalculateRouteRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.RouteCalculationView;
import ao.autocare.modules.fleet.dto.FleetDtos.RoutePointRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.RouteView;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveRouteRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.WaypointRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.WaypointView;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.RouteRepository;
import ao.autocare.modules.integration.IntegrationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotas previstas.
 *
 * <p>Uma rota é o termo de comparação que falta a quase todas as frotas: sem
 * um previsto credível, "esta viagem gastou muito" é uma opinião. Com ele,
 * passa a ser uma diferença que se pode pôr em cima da mesa — e explicar.
 */
@Service
public class RouteService {

    private final RouteRepository routes;
    private final LocationRepository locations;
    private final OrganizationRepository organizations;
    private final AuditService audit;
    private final RoutingEngine engine;
    private final IntegrationService integrations;
    private final ao.autocare.repo.RouteAssignmentRepository assignments;
    private final ao.autocare.repo.AssetRepository assets;
    private final ao.autocare.repo.DriverRepository drivers;
    private final ao.autocare.repo.TripRepository trips;
    private final ao.autocare.repo.GpsPositionRepository positions;

    public RouteService(
            RouteRepository routes,
            LocationRepository locations,
            OrganizationRepository organizations,
            AuditService audit,
            RoutingEngine engine,
            IntegrationService integrations,
            ao.autocare.repo.RouteAssignmentRepository assignments,
            ao.autocare.repo.AssetRepository assets,
            ao.autocare.repo.DriverRepository drivers,
            ao.autocare.repo.TripRepository trips,
            ao.autocare.repo.GpsPositionRepository positions) {
        this.routes = routes;
        this.locations = locations;
        this.organizations = organizations;
        this.audit = audit;
        this.engine = engine;
        this.integrations = integrations;
        this.assignments = assignments;
        this.assets = assets;
        this.drivers = drivers;
        this.trips = trips;
        this.positions = positions;
    }

    /**
     * Calcula o percurso sem guardar nada.
     *
     * <p>Serve o botão «calcular» do formulário: a pessoa vê o que sai, e só
     * depois decide se aceita. Um cálculo que se gravasse sozinho tirava-lhe a
     * hipótese de olhar para o número antes de o assumir.
     */
    @Transactional(readOnly = true)
    public RouteCalculationView calculate(String orgId, CalculateRouteRequest req) {
        List<RoutingEngine.Ponto> pontos = new ArrayList<>();
        for (RoutePointRequest p : req.points()) {
            pontos.add(toPonto(orgId, p));
        }

        RoutingEngine.Trajeto t =
                engine.calcular(integrations.forOrganization(orgId), pontos);

        // O combustível não vem do motor de rotas: vem do consumo da própria
        // viatura. Um motor de mapas não sabe o que uma máquina destas bebe em
        // estrada de terra batida, e fingir que sabe seria inventar um número.
        BigDecimal litros = null;
        if (req.litersPer100Km() != null && req.litersPer100Km().signum() > 0
                && t.distanceKm() != null) {
            litros = t.distanceKm()
                    .multiply(req.litersPer100Km())
                    .divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP);
        }

        return new RouteCalculationView(
                t.distanceKm(), t.durationMinutes(), litros,
                t.geojson(), t.fonte().name(), t.aviso());
    }

    /** Um ponto do pedido, vindo de um local registado ou marcado no mapa. */
    private RoutingEngine.Ponto toPonto(String orgId, RoutePointRequest p) {
        if (p.locationId() != null && !p.locationId().isBlank()) {
            Location l = requireLocation(orgId, p.locationId());
            if (l.getLatitude() == null || l.getLongitude() == null) {
                throw ApiException.badRequest(
                        "O local «" + l.getName() + "» ainda não tem ponto no mapa. "
                                + "Abra Filiais e centros de custo e marque-o.");
            }
            return new RoutingEngine.Ponto(l.getLatitude(), l.getLongitude());
        }
        return new RoutingEngine.Ponto(p.latitude(), p.longitude());
    }

    @Transactional(readOnly = true)
    public List<RouteView> list(String orgId) {
        java.util.Map<String, List<ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView>> porRota =
                new java.util.HashMap<>();
        for (ao.autocare.domain.RouteAssignment a : assignments.forOrganization(orgId)) {
            porRota.computeIfAbsent(a.getRoute().getId(), k -> new java.util.ArrayList<>())
                    .add(ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView.of(a));
        }
        return routes.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(r -> RouteView.of(r, waypointViews(r), porRota.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteView get(String orgId, String routeId) {
        Route r = require(orgId, routeId);
        return RouteView.of(r, waypointViews(r), assignmentViews(r.getId()));
    }

    private List<ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView> assignmentViews(String routeId) {
        return assignments.forRoute(routeId).stream()
                .map(ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView::of).toList();
    }

    // ==== Atribuições ====================================================
    @Transactional(readOnly = true)
    public List<ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView> listAssignments(
            String orgId, String routeId) {
        require(orgId, routeId);
        return assignmentViews(routeId);
    }

    @Transactional
    public ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView assign(
            String orgId, String userId, String routeId,
            ao.autocare.modules.fleet.dto.FleetDtos.AssignRouteRequest req) {
        Route r = require(orgId, routeId);
        ao.autocare.domain.RouteAssignment a = criarAtribuicao(orgId, r, req.assetId(), req.driverId(),
                req.plannedFor(), req.notes());
        audit.record(orgId, userId, "route.assign", "Route", r.getId(),
                r.getName() + " · " + a.getAsset().getTag()
                        + (a.getDriver() != null ? " · " + a.getDriver().getName() : "")
                        + (a.getPlannedFor() != null ? " · " + a.getPlannedFor() : ""));
        return ao.autocare.modules.fleet.dto.FleetDtos.RouteAssignmentView.of(a);
    }

    @Transactional
    public void unassign(String orgId, String userId, String assignmentId) {
        ao.autocare.domain.RouteAssignment a = assignments.findByIdAndOrganizationId(assignmentId, orgId)
                .orElseThrow(() -> ApiException.notFound("Atribuição não encontrada."));
        if (assignments.countByRouteIdAndActiveTrue(a.getRoute().getId()) <= 1) {
            throw ApiException.conflict(
                    "Esta é a única viatura atribuída a «" + a.getRoute().getName()
                            + "». Atribua outra antes de retirar esta, ou desative a rota.");
        }
        a.setActive(false);
        audit.record(orgId, userId, "route.unassign", "Route", a.getRoute().getId(),
                a.getRoute().getName() + " · " + a.getAsset().getTag());
    }

    private ao.autocare.domain.RouteAssignment criarAtribuicao(String orgId, Route r, String assetId,
            String driverId, java.time.LocalDate plannedFor, String notes) {
        if (assetId == null || assetId.isBlank()) {
            throw ApiException.badRequest(
                    "Indique a viatura que vai fazer esta rota. Uma rota sem viatura é um percurso "
                            + "de que ninguém é responsável.");
        }
        ao.autocare.domain.Asset asset = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.badRequest("Viatura não encontrada."));
        ao.autocare.domain.RouteAssignment a = new ao.autocare.domain.RouteAssignment();
        a.setOrganization(organizations.getReferenceById(orgId));
        a.setRoute(r);
        a.setAsset(asset);
        if (driverId != null && !driverId.isBlank()) {
            a.setDriver(drivers.findByIdAndOrganizationId(driverId, orgId)
                    .orElseThrow(() -> ApiException.badRequest("Motorista não encontrado.")));
        }
        a.setPlannedFor(plannedFor);
        a.setNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        return assignments.save(a);
    }

    // ==== Previsto contra o andado =======================================
    /**
     * O traçado da rota e o percurso real das viagens que a fizeram, com a
     * diferença em km e minutos. É a comparação que transforma «gastou muito»
     * numa diferença que se põe em cima da mesa.
     */
    @Transactional(readOnly = true)
    public List<ao.autocare.modules.fleet.dto.FleetDtos.RouteVsRealView> comparison(
            String orgId, String routeId, int limite) {
        Route r = require(orgId, routeId);
        List<ao.autocare.modules.fleet.dto.FleetDtos.RouteVsRealView> out = new java.util.ArrayList<>();
        for (ao.autocare.domain.Trip t : trips.findByRouteIdOrderByStartedAtDesc(routeId,
                org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(1, limite), 20)))) {
            List<double[]> track = new java.util.ArrayList<>();
            java.time.Instant fim = t.getEndedAt() != null ? t.getEndedAt() : java.time.Instant.now();
            for (ao.autocare.domain.GpsPosition p : positions.track(t.getAsset().getId(), t.getStartedAt(), fim)) {
                if (p.getLatitude() != null && p.getLongitude() != null) {
                    track.add(new double[] {p.getLongitude().doubleValue(), p.getLatitude().doubleValue()});
                }
            }
            java.math.BigDecimal deltaKm = r.getExpectedDistanceKm() != null && t.getDistanceKm() != null
                    ? t.getDistanceKm().subtract(r.getExpectedDistanceKm()) : null;
            Integer deltaMin = r.getExpectedDurationMinutes() != null && t.getDurationMinutes() != null
                    ? t.getDurationMinutes() - r.getExpectedDurationMinutes() : null;
            Boolean fora = null;
            if (deltaKm != null && r.getExpectedDistanceKm() != null && r.getExpectedDistanceKm().signum() > 0) {
                java.math.BigDecimal tolerancia = r.getExpectedDistanceKm()
                        .multiply(r.getTolerancePercent() != null ? r.getTolerancePercent() : java.math.BigDecimal.ZERO)
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                fora = deltaKm.abs().compareTo(tolerancia) > 0;
            }
            out.add(new ao.autocare.modules.fleet.dto.FleetDtos.RouteVsRealView(
                    r.getId(), r.getName(), r.getPathGeojson(),
                    r.getExpectedDistanceKm(), r.getExpectedDurationMinutes(),
                    t.getId(), t.getAsset().getId(), t.getAsset().getTag(),
                    t.getStartedAt(), t.getEndedAt(), t.getDistanceKm(), t.getDurationMinutes(),
                    deltaKm, deltaMin, fora, track));
        }
        return out;
    }

    @Transactional
    public RouteView create(String orgId, String userId, SaveRouteRequest req) {
        if (req.code() != null && !req.code().isBlank()
                && routes.existsByOrganizationIdAndCode(orgId, req.code().trim())) {
            throw ApiException.conflict("Já existe uma rota com o código "
                    + req.code().trim() + ".");
        }
        Route r = new Route();
        r.setOrganization(organizations.getReferenceById(orgId));
        apply(orgId, r, req);
        routes.save(r);
        // A viatura é obrigatória na criação: o percurso nasce com um responsável.
        ao.autocare.domain.RouteAssignment atribuicao =
                criarAtribuicao(orgId, r, req.assetId(), req.driverId(), req.plannedFor(), null);

        audit.record(orgId, userId, "route.create", "Route", r.getId(),
                r.getName() + " · " + describe(r) + " · " + atribuicao.getAsset().getTag());
        return RouteView.of(r, waypointViews(r), assignmentViews(r.getId()));
    }

    @Transactional
    public RouteView update(String orgId, String userId, String routeId, SaveRouteRequest req) {
        Route r = require(orgId, routeId);
        apply(orgId, r, req);
        // Ao alterar, uma viatura nova acrescenta-se; as que lá estão mantêm-se.
        if (req.assetId() != null && !req.assetId().isBlank()) {
            criarAtribuicao(orgId, r, req.assetId(), req.driverId(), req.plannedFor(), null);
        }
        audit.record(orgId, userId, "route.update", "Route", r.getId(), r.getName());
        return RouteView.of(r, waypointViews(r), assignmentViews(r.getId()));
    }

    @Transactional
    public void delete(String orgId, String userId, String routeId) {
        Route r = require(orgId, routeId);
        String nome = r.getName();
        routes.delete(r);
        audit.record(orgId, userId, "route.delete", "Route", routeId, nome);
    }

    private void apply(String orgId, Route r, SaveRouteRequest req) {
        verificarVersao(req.version(), r.getVersion());
        r.setName(req.name().trim());
        r.setCode(trim(req.code()));
        r.setOriginLabel(trim(req.originLabel()));
        r.setDestinationLabel(trim(req.destinationLabel()));
        r.setExpectedDistanceKm(req.expectedDistanceKm());
        r.setExpectedDurationMinutes(req.expectedDurationMinutes());
        r.setExpectedFuelLiters(req.expectedFuelLiters());
        r.setNotes(trim(req.notes()));
        if (req.tolerancePercent() != null) {
            if (req.tolerancePercent().signum() < 0
                    || req.tolerancePercent().compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.badRequest("A tolerância tem de estar entre 0 e 100%.");
            }
            r.setTolerancePercent(req.tolerancePercent());
        }
        if (req.corridorMeters() != null) {
            if (req.corridorMeters() < 50 || req.corridorMeters() > 20_000) {
                throw ApiException.badRequest(
                        "O corredor da rota tem de estar entre 50 m e 20 km. Abaixo de 50 m o erro do "
                                + "GPS sozinho dispararia avisos.");
            }
            r.setCorridorMeters(req.corridorMeters());
        }
        if (req.active() != null) {
            r.setActive(req.active());
        }
        r.setOriginLocation(resolve(orgId, req.originLocationId(), r.getOriginLocation()));
        r.setDestinationLocation(
                resolve(orgId, req.destinationLocationId(), r.getDestinationLocation()));

        if (r.originName() == null || r.destinationName() == null) {
            throw ApiException.badRequest(
                    "Uma rota precisa de origem e destino — escolha locais registados "
                            + "ou escreva os nomes.");
        }
        if (req.expectedDistanceKm() != null && req.expectedDistanceKm().signum() < 0) {
            throw ApiException.badRequest("A distância prevista não pode ser negativa.");
        }
        // Quem grava diz de onde veio o número. Sem isto, uma distância
        // calculada e uma distância escrita ficam iguais na tabela, e o
        // relatório de desvios não distingue a medição do palpite.
        if (req.distanceSource() != null && !req.distanceSource().isBlank()) {
            String fonte = req.distanceSource().trim().toUpperCase();
            if (!List.of("MANUAL", "ENGINE", "STRAIGHT").contains(fonte)) {
                throw ApiException.badRequest("Origem da distância desconhecida.");
            }
            r.setDistanceSource(fonte);
            r.setPathGeojson(trim(req.pathGeojson()));
            r.setComputedAt("MANUAL".equals(fonte) ? null : Instant.now());
        }

        if (req.waypoints() != null) {
            r.getWaypoints().clear();
            int ordem = 0;
            for (WaypointRequest w : req.waypoints()) {
                RouteWaypoint p = new RouteWaypoint();
                p.setLabel(w.label().trim());
                p.setLatitude(w.latitude());
                p.setLongitude(w.longitude());
                p.setSortOrder(ordem++);
                if (w.locationId() != null && !w.locationId().isBlank()) {
                    Location l = requireLocation(orgId, w.locationId());
                    p.setLocation(l);
                    if (p.getLatitude() == null) {
                        p.setLatitude(l.getLatitude());
                        p.setLongitude(l.getLongitude());
                    }
                }
                r.addWaypoint(p);
            }
        }
    }

    private Location resolve(String orgId, String id, Location current) {
        if (id == null) {
            return current;
        }
        return id.isBlank() ? null : requireLocation(orgId, id);
    }

    private Location requireLocation(String orgId, String id) {
        return locations.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Local não encontrado."));
    }

    private List<WaypointView> waypointViews(Route r) {
        return r.getWaypoints().stream().map(WaypointView::of).toList();
    }

    private String describe(Route r) {
        return r.originName() + " → " + r.destinationName();
    }

    private Route require(String orgId, String id) {
        return routes.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Rota não encontrada."));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * A versão que o ecrã leu tem de ser a que está na base de dados.
     *
     * <p>Sem isto, o {@code @Version} só apanha colisões entre transações
     * simultâneas. O caso real — duas pessoas com a mesma ficha aberta durante
     * minutos — só se apanha comparando a versão que o ecrã devolve.
     */
    private static void verificarVersao(Long lida, long atual) {
        if (lida != null && lida != atual) {
            throw ApiException.conflict(
                    ao.autocare.common.GlobalExceptionHandler.MENSAGEM_VERSAO);
        }
    }
}
