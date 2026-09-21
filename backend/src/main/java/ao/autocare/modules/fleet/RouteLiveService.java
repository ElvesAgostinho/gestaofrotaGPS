package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.Route;
import ao.autocare.domain.RouteAssignment;
import ao.autocare.modules.fleet.dto.FleetDtos.RouteLiveView;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.RouteAssignmentRepository;
import ao.autocare.repo.RouteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem vai a caminho, agora: onde está no percurso, quanto falta, a que horas
 * chega e se saiu do corredor.
 *
 * <p>A hora de chegada sai da velocidade a que a viatura vem (a média das
 * últimas posições, não a instantânea — um camião a subir uma serra a 15 km/h
 * não chega amanhã). Sem velocidade utilizável, cai-se no ritmo previsto da
 * própria rota; e quando nem isso existe, não se dá hora nenhuma em vez de
 * inventar uma.
 */
@Service
public class RouteLiveService {

    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");
    /** Uma posição com mais do que isto não diz onde a viatura está agora. */
    private static final Duration FRESCA = Duration.ofHours(2);
    /** Abaixo disto considera-se parada: a média não conta paragens. */
    private static final BigDecimal A_ANDAR_KPH = new BigDecimal("5");

    private final RouteRepository routes;
    private final RouteAssignmentRepository assignments;
    private final GpsPositionRepository positions;

    public RouteLiveService(RouteRepository routes, RouteAssignmentRepository assignments,
            GpsPositionRepository positions) {
        this.routes = routes;
        this.assignments = assignments;
        this.positions = positions;
    }

    /** Todas as viaturas a caminho na empresa. */
    @Transactional(readOnly = true)
    public List<RouteLiveView> live(String orgId) {
        List<RouteLiveView> out = new ArrayList<>();
        LocalDate hoje = LocalDate.now(FUSO);
        for (RouteAssignment a : assignments.forOrganization(orgId)) {
            if (a.getPlannedFor() != null && !a.getPlannedFor().equals(hoje)) {
                continue;   // marcada para outro dia: não está a caminho agora
            }
            RouteLiveView v = calcular(a);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    /** As viaturas a caminho numa rota. */
    @Transactional(readOnly = true)
    public List<RouteLiveView> liveForRoute(String orgId, String routeId) {
        Route r = routes.findByIdAndOrganizationId(routeId, orgId)
                .orElseThrow(() -> ApiException.notFound("Rota não encontrada."));
        LocalDate hoje = LocalDate.now(FUSO);
        List<RouteLiveView> out = new ArrayList<>();
        for (RouteAssignment a : assignments.forRoute(r.getId())) {
            if (a.getPlannedFor() != null && !a.getPlannedFor().equals(hoje)) {
                continue;
            }
            RouteLiveView v = calcular(a);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private RouteLiveView calcular(RouteAssignment a) {
        Route r = a.getRoute();
        Asset asset = a.getAsset();
        RouteGeometry g = RouteGeometry.of(r.getPathGeojson());
        if (g == null) {
            return null;   // sem traçado não há progresso nem ETA que se sustente
        }
        GpsPosition p = positions.findFirstByAssetIdOrderByRecordedAtDesc(asset.getId()).orElse(null);
        if (p == null || p.getLatitude() == null || p.getRecordedAt() == null
                || p.getRecordedAt().isBefore(Instant.now().minus(FRESCA))) {
            return null;   // sem posição recente, dizer onde vai seria adivinhar
        }
        RouteGeometry.Posicao pos = g.onde(p.getLatitude().doubleValue(), p.getLongitude().doubleValue());
        int corredor = r.getCorridorMeters() != null ? r.getCorridorMeters() : 500;

        // Velocidade de marcha: média das posições em movimento da última hora.
        BigDecimal media = null;
        List<GpsPosition> recentes = positions.track(asset.getId(),
                p.getRecordedAt().minus(Duration.ofHours(1)), p.getRecordedAt().plusSeconds(1));
        BigDecimal soma = BigDecimal.ZERO;
        int n = 0;
        for (GpsPosition x : recentes) {
            if (x.getSpeedKph() != null && x.getSpeedKph().compareTo(A_ANDAR_KPH) > 0) {
                soma = soma.add(x.getSpeedKph());
                n++;
            }
        }
        if (n > 0) {
            media = soma.divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
        }

        Instant eta = null;
        String fonte = null;
        Integer atraso = null;
        if (media != null && media.signum() > 0) {
            long minutos = Math.round(pos.restanteKm() / media.doubleValue() * 60);
            eta = p.getRecordedAt().plus(Duration.ofMinutes(minutos));
            fonte = "GPS";
        } else if (r.getExpectedDurationMinutes() != null && g.comprimentoKm() > 0) {
            // Ao ritmo que a própria rota prevê.
            double minutosPorKm = r.getExpectedDurationMinutes() / g.comprimentoKm();
            eta = Instant.now().plus(Duration.ofMinutes(Math.round(pos.restanteKm() * minutosPorKm)));
            fonte = "PLANO";
        }
        if (eta != null && r.getExpectedDurationMinutes() != null) {
            // Atraso: o tempo total que esta viagem vai levar contra o previsto,
            // contado desde o início do percurso (a fração já feita da rota).
            double feitoFracao = pos.progresso();
            long previstoRestante = Math.round(r.getExpectedDurationMinutes() * (1 - feitoFracao));
            long faltaMesmo = Duration.between(Instant.now(), eta).toMinutes();
            atraso = (int) (faltaMesmo - previstoRestante);
        }

        return new RouteLiveView(r.getId(), r.getName(), asset.getId(), asset.getTag(), asset.getName(),
                ao.autocare.modules.plan.PlanCatalog.codigoPara(
                        asset.getAssetType() != null && asset.getAssetType().getCategory() != null
                                ? asset.getAssetType().getCategory().name() : null,
                        asset.getAssetType() != null ? asset.getAssetType().getName() : null),
                a.getDriver() != null ? a.getDriver().getName() : null,
                p.getRecordedAt(), p.getLatitude(), p.getLongitude(), p.getSpeedKph(),
                BigDecimal.valueOf(pos.progresso()).setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(pos.percorridoKm()).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(pos.restanteKm()).setScale(1, RoundingMode.HALF_UP),
                (int) Math.round(pos.distanciaMetros()),
                pos.distanciaMetros() > corredor, corredor, eta, atraso, fonte);
    }
}
