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

    public RouteService(
            RouteRepository routes,
            LocationRepository locations,
            OrganizationRepository organizations,
            AuditService audit,
            RoutingEngine engine,
            IntegrationService integrations) {
        this.routes = routes;
        this.locations = locations;
        this.organizations = organizations;
        this.audit = audit;
        this.engine = engine;
        this.integrations = integrations;
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
        return routes.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(r -> RouteView.of(r, waypointViews(r)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteView get(String orgId, String routeId) {
        Route r = require(orgId, routeId);
        return RouteView.of(r, waypointViews(r));
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

        audit.record(orgId, userId, "route.create", "Route", r.getId(),
                r.getName() + " · " + describe(r));
        return RouteView.of(r, waypointViews(r));
    }

    @Transactional
    public RouteView update(String orgId, String userId, String routeId, SaveRouteRequest req) {
        Route r = require(orgId, routeId);
        apply(orgId, r, req);
        audit.record(orgId, userId, "route.update", "Route", r.getId(), r.getName());
        return RouteView.of(r, waypointViews(r));
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
