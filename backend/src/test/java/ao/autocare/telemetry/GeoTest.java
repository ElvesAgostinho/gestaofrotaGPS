package ao.autocare.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.modules.telemetry.Geo;
import ao.autocare.modules.telemetry.Geo.Point;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Matemática do mapa: distâncias, rumos e geocercas. */
class GeoTest {

    // Luanda: Marginal e Aeroporto 4 de Fevereiro.
    private static final double MARGINAL_LAT = -8.8156;
    private static final double MARGINAL_LON = 13.2306;

    @Test
    void measuresKnownDistances() {
        // Um grau de latitude ronda os 111 km em qualquer ponto do globo.
        assertThat(Geo.distanceMeters(0, 0, 1, 0)).isCloseTo(111_195, within(300.0));
        // O mesmo ponto dista zero de si próprio.
        assertThat(Geo.distanceMeters(MARGINAL_LAT, MARGINAL_LON, MARGINAL_LAT, MARGINAL_LON))
                .isZero();
        // Luanda -> Lubango: cerca de 700 km em linha reta.
        double luandaLubango = Geo.distanceMeters(-8.8383, 13.2344, -14.9177, 13.4925);
        assertThat(luandaLubango / 1000).isBetween(670.0, 700.0);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    @Test
    void computesBearing() {
        assertThat(Geo.bearingDegrees(0, 0, 1, 0)).isCloseTo(0, within(0.5));    // norte
        assertThat(Geo.bearingDegrees(0, 0, 0, 1)).isCloseTo(90, within(0.5));   // este
        assertThat(Geo.bearingDegrees(0, 0, -1, 0)).isCloseTo(180, within(0.5)); // sul
        assertThat(Geo.bearingDegrees(0, 0, 0, -1)).isCloseTo(270, within(0.5)); // oeste
    }

    @Test
    void detectsPointInsideAndOutsideACircle() {
        // Parque de máquinas com 500 m de raio.
        assertThat(Geo.insideCircle(MARGINAL_LAT, MARGINAL_LON,
                MARGINAL_LAT, MARGINAL_LON, 500)).isTrue();
        // 300 m a norte: ainda dentro.
        assertThat(Geo.insideCircle(MARGINAL_LAT + 0.0027, MARGINAL_LON,
                MARGINAL_LAT, MARGINAL_LON, 500)).isTrue();
        // 2 km a norte: fora.
        assertThat(Geo.insideCircle(MARGINAL_LAT + 0.018, MARGINAL_LON,
                MARGINAL_LAT, MARGINAL_LON, 500)).isFalse();
    }

    @Test
    void detectsPointInsideAndOutsideAPolygon() {
        List<Point> quadrado = List.of(
                new Point(-8.80, 13.20),
                new Point(-8.80, 13.25),
                new Point(-8.85, 13.25),
                new Point(-8.85, 13.20));

        assertThat(Geo.insidePolygon(-8.82, 13.22, quadrado)).isTrue();   // centro
        assertThat(Geo.insidePolygon(-8.90, 13.22, quadrado)).isFalse();  // a sul
        assertThat(Geo.insidePolygon(-8.82, 13.30, quadrado)).isFalse();  // a este
    }

    @Test
    void treatsDegeneratePolygonsAsEmpty() {
        assertThat(Geo.insidePolygon(0, 0, null)).isFalse();
        assertThat(Geo.insidePolygon(0, 0, List.of())).isFalse();
        assertThat(Geo.insidePolygon(0, 0, List.of(new Point(0, 0), new Point(1, 1)))).isFalse();
    }

    @Test
    void rejectsImpossibleAndNullIslandCoordinates() {
        assertThat(Geo.isValid(bd("-8.8156"), bd("13.2306"))).isTrue();
        assertThat(Geo.isValid(null, bd("13.2"))).isFalse();
        assertThat(Geo.isValid(bd("91"), bd("13.2"))).isFalse();
        assertThat(Geo.isValid(bd("-8.8"), bd("181"))).isFalse();
        // Aparelho ainda sem sinal envia (0,0) — não é uma posição real.
        assertThat(Geo.isValid(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
