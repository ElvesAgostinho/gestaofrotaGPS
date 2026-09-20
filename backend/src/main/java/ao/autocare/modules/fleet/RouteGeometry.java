package ao.autocare.modules.fleet;

import ao.autocare.modules.telemetry.Geo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Contas sobre o traçado de uma rota: a que distância a viatura está da
 * estrada prevista, quanto já andou dela e quanto falta.
 *
 * <p>Tudo se faz sobre a linha que o motor de rotas devolveu, em metros. Sem
 * traçado não há corredor nem ETA — e diz-se isso, em vez de inventar uma
 * linha reta entre a origem e o destino que ninguém percorre.
 */
public final class RouteGeometry {

    /** Ponto mais próximo da linha: a que distância está, e quanto falta até ao fim. */
    public record Posicao(double distanciaMetros, double percorridoKm, double restanteKm, double progresso) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<double[]> pontos;
    private final double[] acumulado;

    private RouteGeometry(List<double[]> pontos) {
        this.pontos = pontos;
        this.acumulado = new double[pontos.size()];
        for (int i = 1; i < pontos.size(); i++) {
            acumulado[i] = acumulado[i - 1] + metros(pontos.get(i - 1), pontos.get(i));
        }
    }

    /** {@code null} quando a rota não tem traçado utilizável. */
    public static RouteGeometry of(String pathGeojson) {
        if (pathGeojson == null || pathGeojson.isBlank()) {
            return null;
        }
        try {
            JsonNode g = JSON.readTree(pathGeojson);
            JsonNode geom = g.has("geometry") ? g.get("geometry")
                    : g.has("features") ? g.get("features").get(0).get("geometry") : g;
            JsonNode coords = geom.get("coordinates");
            if (coords == null || !coords.isArray()) {
                return null;
            }
            List<double[]> pontos = new ArrayList<>();
            if ("MultiLineString".equals(geom.path("type").asText())) {
                for (JsonNode linha : coords) {
                    for (JsonNode c : linha) {
                        pontos.add(new double[] {c.get(0).asDouble(), c.get(1).asDouble()});
                    }
                }
            } else {
                for (JsonNode c : coords) {
                    pontos.add(new double[] {c.get(0).asDouble(), c.get(1).asDouble()});
                }
            }
            return pontos.size() >= 2 ? new RouteGeometry(pontos) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public double comprimentoKm() {
        return acumulado[acumulado.length - 1] / 1000.0;
    }

    /**
     * Onde a viatura está em relação à linha: distância a ela, quanto já
     * percorreu e quanto falta. A projeção é feita segmento a segmento, para
     * que um ponto no meio de uma reta longa não seja arredondado ao vértice
     * mais próximo — a diferença dá quilómetros num percurso interurbano.
     */
    public Posicao onde(double lat, double lon) {
        double melhor = Double.MAX_VALUE;
        double melhorAo = 0;
        for (int i = 1; i < pontos.size(); i++) {
            double[] a = pontos.get(i - 1);
            double[] b = pontos.get(i);
            double t = fracaoProjetada(a, b, lon, lat);
            double projLon = a[0] + (b[0] - a[0]) * t;
            double projLat = a[1] + (b[1] - a[1]) * t;
            double d = Geo.distanceMeters(lat, lon, projLat, projLon);
            if (d < melhor) {
                melhor = d;
                melhorAo = acumulado[i - 1] + metros(a, new double[] {projLon, projLat});
            }
        }
        double total = acumulado[acumulado.length - 1];
        double restante = Math.max(0, total - melhorAo);
        return new Posicao(melhor, melhorAo / 1000.0, restante / 1000.0,
                total > 0 ? Math.min(1, melhorAo / total) : 0);
    }

    /** Fração [0,1] do segmento a→b onde cai a perpendicular do ponto. */
    private static double fracaoProjetada(double[] a, double[] b, double lon, double lat) {
        // Aproximação plana: a poucos quilómetros o erro é irrelevante, e os
        // segmentos de um traçado de estrada são curtos.
        double cos = Math.cos(Math.toRadians(a[1]));
        double ax = a[0] * cos;
        double bx = b[0] * cos;
        double px = lon * cos;
        double dx = bx - ax;
        double dy = b[1] - a[1];
        double comprimento = dx * dx + dy * dy;
        if (comprimento == 0) {
            return 0;
        }
        double t = ((px - ax) * dx + (lat - a[1]) * dy) / comprimento;
        return Math.max(0, Math.min(1, t));
    }

    private static double metros(double[] a, double[] b) {
        return Geo.distanceMeters(a[1], a[0], b[1], b[0]);
    }
}
