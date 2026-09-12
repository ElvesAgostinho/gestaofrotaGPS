package ao.autocare.modules.fleet;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Limiares da análise de condução.
 *
 * <p>Testados à parte, sem base de dados, porque são as decisões que afetam a
 * vida de um motorista: o que conta como travagem brusca, e — mais importante —
 * o que o sistema se recusa a concluir por não ter dados que cheguem.
 */
class DrivingAnalyzerTest {

    private final DrivingAnalyzer analyzer = new DrivingAnalyzer();

    private GpsPosition p(Instant at, double speed, Double heading, Boolean ignition) {
        GpsPosition pos = new GpsPosition();
        pos.setRecordedAt(at);
        pos.setSpeedKph(BigDecimal.valueOf(speed));
        pos.setLatitude(new BigDecimal("-8.8383"));
        pos.setLongitude(new BigDecimal("13.2344"));
        if (heading != null) {
            pos.setHeading(BigDecimal.valueOf(heading));
        }
        pos.setIgnition(ignition);
        return pos;
    }

    /** Sequência a horas do dia, para a condução noturna não interferir. */
    private Instant midday(int plusSeconds) {
        return ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, ZoneId.of("Africa/Luanda"))
                .toInstant().plusSeconds(plusSeconds);
    }

    private List<DrivingEventKind> kinds(List<DrivingAnalyzer.Detected> found) {
        List<DrivingEventKind> k = new ArrayList<>();
        found.forEach(d -> k.add(d.kind()));
        return k;
    }

    // ---- travagem e aceleração --------------------------------------------
    @Test
    void aSharpDropInSpeedIsAHarshBrake() {
        // 80 → 20 km/h em 4 s = 15 km/h/s, acima do limiar de 12.
        List<DrivingAnalyzer.Detected> found = analyzer.analyse(List.of(
                p(midday(0), 80, null, true),
                p(midday(4), 20, null, true)));

        assertThat(kinds(found)).containsExactly(DrivingEventKind.HARSH_BRAKE);
        DrivingAnalyzer.Detected d = found.get(0);
        // O valor medido e o limiar viajam com a infração: sem eles, ela não
        // pode ser contestada com números.
        assertThat(d.measuredValue().doubleValue()).isEqualTo(15.0);
        assertThat(d.thresholdValue().doubleValue()).isEqualTo(12.0);
        assertThat(d.unit()).isEqualTo("km/h por segundo");
    }

    @Test
    void aNormalSlowdownIsNotAnInfraction() {
        // 60 → 40 km/h em 10 s = 2 km/h/s. Travar a chegar a um semáforo.
        assertThat(analyzer.analyse(List.of(
                p(midday(0), 60, null, true),
                p(midday(10), 40, null, true)))).isEmpty();
    }

    @Test
    void aFastPullAwayIsAHarshAcceleration() {
        // 0 → 50 km/h em 4 s = 12,5 km/h/s.
        assertThat(kinds(analyzer.analyse(List.of(
                p(midday(0), 0, null, true),
                p(midday(4), 50, null, true)))))
                .containsExactly(DrivingEventKind.HARSH_ACCELERATION);
    }

    // ---- o que o analisador se recusa a concluir ---------------------------
    @Test
    void nothingIsConcludedWhenTheReadingsAreTooFarApart() {
        // 80 → 0 km/h com 60 s de intervalo. Pode ter sido uma travagem de
        // emergência ou um abrandamento suave — a média não distingue, e
        // inventar uma infração seria pior do que não dizer nada.
        assertThat(analyzer.analyse(List.of(
                p(midday(0), 80, null, true),
                p(midday(60), 0, null, true)))).isEmpty();
    }

    @Test
    void twoReadingsWithTheSameTimestampProduceNothing() {
        // Sem isto haveria divisão por zero e acelerações infinitas.
        assertThat(analyzer.analyse(List.of(
                p(midday(0), 0, null, true),
                p(midday(0), 90, null, true)))).isEmpty();
    }

    @Test
    void aReadingWithoutSpeedIsSkipped() {
        GpsPosition semVelocidade = p(midday(4), 0, null, true);
        semVelocidade.setSpeedKph(null);
        assertThat(analyzer.analyse(List.of(
                p(midday(0), 80, null, true), semVelocidade))).isEmpty();
    }

    // ---- curvas ------------------------------------------------------------
    @Test
    void aSharpTurnAtSpeedIsHarshCornering() {
        List<DrivingAnalyzer.Detected> found = analyzer.analyse(List.of(
                p(midday(0), 50, 10.0, true),
                p(midday(3), 48, 80.0, true)));
        assertThat(kinds(found)).contains(DrivingEventKind.HARSH_CORNERING);
    }

    @Test
    void aSharpTurnAtWalkingPaceIsNotAnInfraction() {
        // Manobrar num parque não é conduzir mal.
        assertThat(analyzer.analyse(List.of(
                p(midday(0), 8, 10.0, true),
                p(midday(3), 8, 120.0, true)))).isEmpty();
    }

    @Test
    void headingDifferenceTakesTheShortWayRound() {
        // 350° para 10° são 20 graus, não 340.
        assertThat(DrivingAnalyzer.headingDelta(350, 10)).isEqualTo(20.0);
        assertThat(DrivingAnalyzer.headingDelta(10, 350)).isEqualTo(20.0);
        assertThat(DrivingAnalyzer.headingDelta(0, 180)).isEqualTo(180.0);
    }

    // ---- ralenti -----------------------------------------------------------
    @Test
    void anEngineLeftRunningAndStoppedIsIdling() {
        List<GpsPosition> leituras = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            leituras.add(p(midday(i * 60), 0, null, true));
        }
        List<DrivingAnalyzer.Detected> found = analyzer.analyse(leituras);
        assertThat(kinds(found)).contains(DrivingEventKind.IDLING);
        assertThat(found.get(0).measuredValue().intValue()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void aShortStopIsNotIdling() {
        List<GpsPosition> leituras = new ArrayList<>();
        for (int i = 0; i <= 3; i++) {
            leituras.add(p(midday(i * 60), 0, null, true));
        }
        assertThat(analyzer.analyse(leituras)).isEmpty();
    }

    @Test
    void aParkedVehicleWithTheEngineOffIsNotIdling() {
        List<GpsPosition> leituras = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            leituras.add(p(midday(i * 60), 0, null, false));
        }
        assertThat(analyzer.analyse(leituras)).isEmpty();
    }

    // ---- noite -------------------------------------------------------------
    @Test
    void nightIsCountedInAngolanLocalTimeNotUtc() {
        // 23h em Luanda é 22h UTC. Contar em UTC daria uma hora de diferença e
        // condução noturna a aparecer (ou a faltar) na hora errada.
        Instant vinteETres = ZonedDateTime
                .of(2026, 3, 10, 23, 0, 0, 0, ZoneId.of("Africa/Luanda")).toInstant();
        Instant meioDia = ZonedDateTime
                .of(2026, 3, 10, 12, 0, 0, 0, ZoneId.of("Africa/Luanda")).toInstant();

        assertThat(analyzer.isNight(vinteETres)).isTrue();
        assertThat(analyzer.isNight(meioDia)).isFalse();
    }

    @Test
    void drivingAtNightIsRecorded() {
        Instant base = ZonedDateTime
                .of(2026, 3, 10, 23, 0, 0, 0, ZoneId.of("Africa/Luanda")).toInstant();
        List<GpsPosition> leituras = List.of(
                p(base, 60, null, true),
                p(base.plusSeconds(600), 60, null, true),
                p(base.plusSeconds(1200), 60, null, true));

        assertThat(kinds(analyzer.analyse(leituras)))
                .contains(DrivingEventKind.NIGHT_DRIVING);
    }

    // ---- penalização do excesso de velocidade ------------------------------
    @Test
    void aLargeOverspeedCostsDouble() {
        // 20 km/h acima do limite: infração normal.
        assertThat(analyzer.overspeedPenalty(
                new BigDecimal("80"), new BigDecimal("60"))).isEqualTo(5.0);
        // 40 km/h acima: pesa a dobrar.
        assertThat(analyzer.overspeedPenalty(
                new BigDecimal("100"), new BigDecimal("60"))).isEqualTo(10.0);
    }
}
