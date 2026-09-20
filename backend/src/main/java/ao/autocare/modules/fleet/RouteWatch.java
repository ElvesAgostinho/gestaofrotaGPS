package ao.autocare.modules.fleet;

import ao.autocare.domain.Asset;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.Route;
import ao.autocare.domain.RouteAssignment;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.RouteAssignmentRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A viatura saiu do caminho combinado.
 *
 * <p>Cada posição que chega é comparada com o traçado da rota que a viatura
 * tem atribuída. Enquanto anda dentro do corredor, ninguém é incomodado; mal
 * sai dele, quem gere recebe o aviso <b>na hora</b> — com a distância a que
 * está da estrada prevista e onde o desvio começou.
 *
 * <p>O corredor é largo de propósito (500 m por omissão): estradas com obras,
 * desvios de trânsito e o próprio erro do GPS não são desvios. O que se quer
 * apanhar é a viatura que foi a outro sítio — e isso vê-se a quilómetros, não
 * a metros. Quando a viatura regressa, o aviso desaparece sozinho.
 */
@Component
public class RouteWatch {

    private static final Logger log = LoggerFactory.getLogger(RouteWatch.class);
    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");

    private final RouteAssignmentRepository assignments;
    private final NotificationService notifications;

    public RouteWatch(RouteAssignmentRepository assignments, NotificationService notifications) {
        this.assignments = assignments;
        this.notifications = notifications;
    }

    /**
     * As rotas que esta viatura devia estar a fazer agora: as atribuições sem
     * dia marcado (recorrentes) e as marcadas para hoje.
     */
    public List<RouteAssignment> emCurso(String assetId) {
        LocalDate hoje = LocalDate.now(FUSO);
        List<RouteAssignment> out = new ArrayList<>();
        for (RouteAssignment a : assignments.forAsset(assetId)) {
            if (a.getPlannedFor() == null || a.getPlannedFor().equals(hoje)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Chamado a cada posição recebida. Nunca deixa rebentar a entrada de posições. */
    public void check(Asset asset, GpsPosition p) {
        if (asset == null || p == null || p.getLatitude() == null || p.getLongitude() == null) {
            return;
        }
        try {
            List<RouteAssignment> candidatas = emCurso(asset.getId());
            if (candidatas.isEmpty()) {
                return;
            }
            // Com várias rotas atribuídas, vale a mais próxima: é a que a
            // viatura está a fazer. Acusar desvio na outra seria um alarme falso.
            RouteAssignment melhor = null;
            RouteGeometry.Posicao melhorPos = null;
            for (RouteAssignment a : candidatas) {
                RouteGeometry g = RouteGeometry.of(a.getRoute().getPathGeojson());
                if (g == null) {
                    continue;
                }
                RouteGeometry.Posicao pos = g.onde(p.getLatitude().doubleValue(), p.getLongitude().doubleValue());
                if (melhorPos == null || pos.distanciaMetros() < melhorPos.distanciaMetros()) {
                    melhor = a;
                    melhorPos = pos;
                }
            }
            if (melhor == null) {
                return;   // nenhuma rota com traçado: não há corredor que se possa verificar
            }
            Route r = melhor.getRoute();
            int corredor = r.getCorridorMeters() != null ? r.getCorridorMeters() : 500;
            // A origem é a viatura: um desvio de cada vez por viatura, seja
            // qual for a rota. (E cabe nos 80 caracteres da coluna.)
            String origem = asset.getId();

            if (melhorPos.distanciaMetros() <= corredor) {
                // De volta ao caminho: o aviso deixa de fazer sentido.
                notifications.resolve("route_deviation", origem);
                return;
            }
            long fora = Math.round(melhorPos.distanciaMetros());
            String distancia = fora >= 1000
                    ? String.format(java.util.Locale.forLanguageTag("pt"), "%.1f km", fora / 1000.0)
                    : fora + " m";
            notifications.notifyManagers(NotificationService.Draft.of(
                            asset.getOrganization().getId(), AlertCategory.GPS,
                            fora >= corredor * 4L ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                            "Fora da rota — " + asset.getTag(),
                            asset.getTag() + " está a " + distancia + " da rota «" + r.getName()
                                    + "» (corredor de " + corredor + " m). "
                                    + "Percorreu " + Math.round(melhorPos.percorridoKm()) + " km dos "
                                    + Math.round(melhorPos.percorridoKm() + melhorPos.restanteKm()) + " km previstos.",
                            "route_deviation", origem, "/mapa")
                    .forAsset(asset));
        } catch (Exception e) {
            // Uma posição nunca se perde por causa de uma verificação de rota.
            log.warn("Falha a verificar o desvio de rota de {}: {}", asset.getTag(), e.toString());
        }
    }
}
