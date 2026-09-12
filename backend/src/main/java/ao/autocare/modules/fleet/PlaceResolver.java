package ao.autocare.modules.fleet;

import ao.autocare.domain.Geofence;
import ao.autocare.domain.Location;
import ao.autocare.domain.enums.Enums.GeofenceKind;
import ao.autocare.domain.enums.Enums.LocationKind;
import ao.autocare.domain.enums.Enums.PlaceKind;
import ao.autocare.modules.telemetry.Geo;
import ao.autocare.repo.GeofenceRepository;
import ao.autocare.repo.LocationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dá nome a um ponto do mapa.
 *
 * <p>Resolve contra o que a <b>empresa registou</b> — filiais, locais e
 * geocercas — e não contra um serviço de mapas. A escolha é deliberada: um
 * geocodificador devolveria "Rua Amílcar Cabral, Luanda" para um ponto no meio
 * do estaleiro, e quem gere a frota não reconheceria o sítio. Pior: pagaria-se
 * por cada chamada e dependeria-se de uma ligação à Internet que a instalação
 * pode não ter.
 *
 * <p>Quando nada bate certo devolve {@link PlaceKind#UNKNOWN} e o ecrã mostra as
 * coordenadas. Inventar um nome aproximado seria pior do que não ter nenhum.
 */
@Component
public class PlaceResolver {

    /**
     * Raio assumido para um local sem raio definido.
     *
     * <p>200 m cobre um parque ou uma obra sem apanhar o quarteirão ao lado.
     */
    private static final int DEFAULT_RADIUS_M = 200;

    private final LocationRepository locations;
    private final GeofenceRepository geofences;
    private final ObjectMapper json;

    public PlaceResolver(
            LocationRepository locations, GeofenceRepository geofences, ObjectMapper json) {
        this.locations = locations;
        this.geofences = geofences;
        this.json = json;
    }

    /** Nome, tipo e id do sítio; nunca nulo. */
    public record Place(String name, PlaceKind kind, String id) {

        public static Place unknown() {
            return new Place(null, PlaceKind.UNKNOWN, null);
        }

        public boolean isKnown() {
            return kind != PlaceKind.UNKNOWN;
        }
    }

    /**
     * O sítio mais próximo que contém este ponto.
     *
     * <p>Precedência: filial, depois outros locais, depois geocercas. Uma filial
     * ganha a uma geocerca que a envolva porque é o nome que a empresa usa para
     * falar daquele sítio — "Filial de Benguela" diz mais do que "Zona 3".
     */
    public Place resolve(String organizationId, BigDecimal latitude, BigDecimal longitude) {
        if (!Geo.isValid(latitude, longitude)) {
            return Place.unknown();
        }
        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();

        Place local = nearestLocation(organizationId, lat, lon);
        if (local.isKnown()) {
            return local;
        }
        return enclosingGeofence(organizationId, lat, lon);
    }

    private Place nearestLocation(String organizationId, double lat, double lon) {
        Location melhor = null;
        double melhorDistancia = Double.MAX_VALUE;
        boolean melhorEFilial = false;

        for (Location l : locations.findByOrganizationIdOrderByNameAsc(organizationId)) {
            if (!l.isActive() || !l.isLocatable()) {
                continue;
            }
            double distancia = Geo.distanceMeters(
                    lat, lon, l.getLatitude().doubleValue(), l.getLongitude().doubleValue());
            int raio = l.getRadiusMeters() != null ? l.getRadiusMeters() : DEFAULT_RADIUS_M;
            if (distancia > raio) {
                continue;
            }
            boolean eFilial = l.getKind() == LocationKind.BRANCH;

            // Filial ganha sempre a nao-filial; entre iguais, ganha a mais perto.
            boolean melhorCandidato = melhor == null
                    || (eFilial && !melhorEFilial)
                    || (eFilial == melhorEFilial && distancia < melhorDistancia);
            if (melhorCandidato) {
                melhor = l;
                melhorDistancia = distancia;
                melhorEFilial = eFilial;
            }
        }
        return melhor == null
                ? Place.unknown()
                : new Place(melhor.getName(),
                        melhorEFilial ? PlaceKind.BRANCH : PlaceKind.LOCATION, melhor.getId());
    }

    private Place enclosingGeofence(String organizationId, double lat, double lon) {
        for (Geofence g : geofences.findByOrganizationIdOrderByNameAsc(organizationId)) {
            if (!g.isActive()) {
                continue;
            }
            if (contains(g, lat, lon)) {
                return new Place(g.getName(), PlaceKind.GEOFENCE, g.getId());
            }
        }
        return Place.unknown();
    }

    private boolean contains(Geofence g, double lat, double lon) {
        try {
            if (g.getKind() == GeofenceKind.CIRCLE) {
                if (g.getCenterLatitude() == null || g.getRadiusM() == null) {
                    return false;
                }
                return Geo.insideCircle(lat, lon,
                        g.getCenterLatitude().doubleValue(),
                        g.getCenterLongitude().doubleValue(),
                        g.getRadiusM().doubleValue());
            }
            if (g.getPolygon() == null || g.getPolygon().isBlank()) {
                return false;
            }
            List<Geo.Point> pontos = new ArrayList<>();
            for (var no : json.readTree(g.getPolygon())) {
                pontos.add(new Geo.Point(
                        no.path("lat").asDouble(), no.path("lon").asDouble()));
            }
            return pontos.size() >= 3 && Geo.insidePolygon(lat, lon, pontos);
        } catch (Exception e) {
            // Uma geocerca mal formada nao pode impedir o resto de ser nomeado.
            return false;
        }
    }
}
