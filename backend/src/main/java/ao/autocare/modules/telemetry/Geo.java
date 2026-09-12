package ao.autocare.modules.telemetry;

import java.math.BigDecimal;
import java.util.List;

/**
 * Matemática geoespacial usada pela telemetria. Puro e sem dependências, para
 * ser testável isoladamente.
 *
 * <p>Trabalha em graus decimais WGS-84. As distâncias usam a fórmula de
 * Haversine, que assume a Terra esférica: o erro é inferior a 0,5 % — muito
 * abaixo da precisão de um GPS de frota, e sobra para geocercas e para somar
 * quilómetros de viagem.
 */
public final class Geo {

    /** Raio médio da Terra, em metros. */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private Geo() {}

    /** Distância em metros entre dois pontos. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double distanceMeters(
            BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        return distanceMeters(
                lat1.doubleValue(), lon1.doubleValue(), lat2.doubleValue(), lon2.doubleValue());
    }

    /**
     * Rumo inicial em graus (0 = norte, 90 = este). Serve para orientar o ícone
     * do ativo no mapa quando o aparelho não envia o rumo.
     */
    public static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return (degrees + 360) % 360;
    }

    /** Um ponto do polígono de uma geocerca. */
    public record Point(double lat, double lon) {}

    /**
     * Ponto dentro de um polígono, pelo algoritmo do raio (ray casting): conta
     * quantas arestas um raio para leste atravessa — ímpar significa dentro.
     *
     * <p>Os polígonos de geocerca são pequenos (uma obra, um parque), por isso
     * tratar as coordenadas como planas é suficiente e não distorce o resultado.
     */
    public static boolean insidePolygon(double lat, double lon, List<Point> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Point a = polygon.get(i);
            Point b = polygon.get(j);
            boolean straddles = (a.lat() > lat) != (b.lat() > lat);
            if (!straddles) {
                continue;
            }
            double lonAtLat = (b.lon() - a.lon()) * (lat - a.lat()) / (b.lat() - a.lat()) + a.lon();
            if (lon < lonAtLat) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Ponto dentro de um círculo de raio em metros. */
    public static boolean insideCircle(
            double lat, double lon, double centerLat, double centerLon, double radiusM) {
        return distanceMeters(lat, lon, centerLat, centerLon) <= radiusM;
    }

    /** Coordenadas plausíveis (evita gravar lixo enviado por um aparelho avariado). */
    public static boolean isValid(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return false;
        }
        // (0,0) fica no Atlântico ao largo da Guiné: é o valor que os aparelhos
        // enviam quando ainda não têm sinal, não uma posição real.
        return !(lat == 0.0 && lon == 0.0);
    }
}
