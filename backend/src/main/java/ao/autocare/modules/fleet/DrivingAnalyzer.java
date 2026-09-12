package ao.autocare.modules.fleet;

import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deteta infrações de condução a partir das posições de uma viagem.
 *
 * <p><b>Os limiares estão todos aqui em cima e explicados.</b> Uma pontuação de
 * condução que afeta a vida de alguém não pode depender de números escondidos
 * no meio do código — um motorista tem o direito de perguntar porque é que
 * aquilo contou como travagem brusca, e a resposta tem de existir.
 *
 * <p>O que este analisador <b>não</b> faz, e importa dizer:
 * <ul>
 *   <li>Não deteta excesso de velocidade. Isso já é feito pelos alertas de
 *       telemetria, que aplicam os limites por zona, ativo e empresa. Duplicar
 *       daria dois números diferentes para o mesmo excesso.</li>
 *   <li>Não usa acelerómetro, porque não há. Trabalha com a variação da
 *       velocidade entre leituras de GPS, que é menos preciso — e por isso
 *       ignora intervalos longos de mais, onde a média esconderia o evento.</li>
 * </ul>
 */
@Component
public class DrivingAnalyzer {

    /**
     * Travagem brusca: queda de velocidade igual ou superior a 12 km/h por
     * segundo (cerca de -0,34 g). É o limiar corrente na indústria para
     * distinguir uma travagem firme de uma travagem de emergência.
     */
    static final double HARSH_BRAKE_KPH_PER_S = 12.0;

    /** Aceleração brusca: 10 km/h por segundo. Menos exigente do que a travagem
     *  porque acelerar com força gasta combustível mas raramente causa acidentes. */
    static final double HARSH_ACCEL_KPH_PER_S = 10.0;

    /** Curva brusca: mudar mais de 45° em pouco tempo, com velocidade a sério. */
    static final double HARSH_CORNER_DEGREES = 45.0;
    static final double HARSH_CORNER_MIN_SPEED_KPH = 25.0;
    static final int HARSH_CORNER_MAX_SECONDS = 6;

    /**
     * Intervalo máximo entre duas leituras para se poder concluir alguma coisa.
     *
     * <p>Acima disto a média esconde o acontecimento: entre duas leituras a 60
     * segundos de distância, uma travagem de emergência e um abrandamento suave
     * são indistinguíveis. Nesse caso o analisador cala-se, em vez de inventar
     * uma infração que talvez não tenha existido.
     */
    static final int MAX_SAMPLE_GAP_SECONDS = 30;

    /** Ralenti: motor ligado e parado durante mais de 5 minutos seguidos. */
    static final int IDLE_MIN_MINUTES = 5;
    static final double STOPPED_SPEED_KPH = 1.0;

    /** Condução noturna: entre as 22h e as 5h, hora local. */
    static final int NIGHT_FROM_HOUR = 22;
    static final int NIGHT_TO_HOUR = 5;

    /** Fuso de Angola. Sem isto a noite seria contada em UTC — uma hora a menos. */
    static final ZoneId ZONE = ZoneId.of("Africa/Luanda");

    /** Penalizações por episódio. Somadas e depois normalizadas por 100 km. */
    static final double PENALTY_HARSH_BRAKE = 3.0;
    static final double PENALTY_HARSH_ACCEL = 2.0;
    static final double PENALTY_HARSH_CORNER = 2.0;
    static final double PENALTY_IDLING = 1.0;
    static final double PENALTY_NIGHT = 2.0;
    static final double PENALTY_OVERSPEED = 5.0;
    static final double PENALTY_OVERSPEED_SEVERE = 10.0;

    /** Acima do limite por mais do que isto, o excesso conta a dobrar. */
    static final double SEVERE_OVERSPEED_MARGIN_KPH = 30.0;

    /** Uma infração detetada, antes de virar linha na base de dados. */
    public record Detected(
            DrivingEventKind kind,
            AlertSeverity severity,
            Instant occurredAt,
            Instant endedAt,
            BigDecimal measuredValue,
            BigDecimal thresholdValue,
            String unit,
            BigDecimal speedKph,
            BigDecimal latitude,
            BigDecimal longitude,
            double penaltyPoints,
            String description) {}

    /**
     * Percorre as posições de uma viagem, por ordem crescente de tempo.
     *
     * @param positions leituras da viagem, da mais antiga para a mais recente
     */
    public List<Detected> analyse(List<GpsPosition> positions) {
        List<Detected> encontradas = new ArrayList<>();
        if (positions == null || positions.size() < 2) {
            return encontradas;
        }

        Instant inicioRalenti = null;
        Instant inicioNoite = null;

        for (int i = 1; i < positions.size(); i++) {
            GpsPosition anterior = positions.get(i - 1);
            GpsPosition atual = positions.get(i);

            long segundos = Duration.between(
                    anterior.getRecordedAt(), atual.getRecordedAt()).getSeconds();

            // ---- Ralenti: motor ligado, parado, tempo a acumular -----------
            if (isIdling(atual)) {
                if (inicioRalenti == null) {
                    inicioRalenti = atual.getRecordedAt();
                }
            } else if (inicioRalenti != null) {
                adicionarRalenti(encontradas, inicioRalenti, anterior);
                inicioRalenti = null;
            }

            // ---- Condução noturna ------------------------------------------
            if (isMoving(atual) && isNight(atual.getRecordedAt())) {
                if (inicioNoite == null) {
                    inicioNoite = atual.getRecordedAt();
                }
            } else if (inicioNoite != null) {
                adicionarNoite(encontradas, inicioNoite, anterior);
                inicioNoite = null;
            }

            // ---- Travagem, aceleração e curva ------------------------------
            // Fora da janela útil não se conclui nada: nem intervalos nulos
            // (que dariam divisão por zero e acelerações infinitas), nem
            // intervalos longos, onde a média esconde o acontecimento.
            if (segundos <= 0 || segundos > MAX_SAMPLE_GAP_SECONDS) {
                continue;
            }
            if (anterior.getSpeedKph() == null || atual.getSpeedKph() == null) {
                continue;
            }

            double v0 = anterior.getSpeedKph().doubleValue();
            double v1 = atual.getSpeedKph().doubleValue();
            double variacao = (v1 - v0) / segundos;

            if (variacao <= -HARSH_BRAKE_KPH_PER_S) {
                encontradas.add(new Detected(
                        DrivingEventKind.HARSH_BRAKE, AlertSeverity.WARNING,
                        atual.getRecordedAt(), null,
                        round(Math.abs(variacao)), round(HARSH_BRAKE_KPH_PER_S), "km/h por segundo",
                        atual.getSpeedKph(), atual.getLatitude(), atual.getLongitude(),
                        PENALTY_HARSH_BRAKE,
                        "Travagem de " + fmt(v0) + " para " + fmt(v1)
                                + " km/h em " + segundos + " s."));
            } else if (variacao >= HARSH_ACCEL_KPH_PER_S) {
                encontradas.add(new Detected(
                        DrivingEventKind.HARSH_ACCELERATION, AlertSeverity.INFO,
                        atual.getRecordedAt(), null,
                        round(variacao), round(HARSH_ACCEL_KPH_PER_S), "km/h por segundo",
                        atual.getSpeedKph(), atual.getLatitude(), atual.getLongitude(),
                        PENALTY_HARSH_ACCEL,
                        "Aceleração de " + fmt(v0) + " para " + fmt(v1)
                                + " km/h em " + segundos + " s."));
            }

            if (segundos <= HARSH_CORNER_MAX_SECONDS
                    && v1 >= HARSH_CORNER_MIN_SPEED_KPH
                    && anterior.getHeading() != null && atual.getHeading() != null) {

                double giro = headingDelta(
                        anterior.getHeading().doubleValue(), atual.getHeading().doubleValue());
                if (giro >= HARSH_CORNER_DEGREES) {
                    encontradas.add(new Detected(
                            DrivingEventKind.HARSH_CORNERING, AlertSeverity.WARNING,
                            atual.getRecordedAt(), null,
                            round(giro), round(HARSH_CORNER_DEGREES), "graus",
                            atual.getSpeedKph(), atual.getLatitude(), atual.getLongitude(),
                            PENALTY_HARSH_CORNER,
                            "Mudança de direção de " + fmt(giro) + "° a " + fmt(v1)
                                    + " km/h."));
                }
            }
        }

        // Episódios ainda abertos no fim da viagem contam na mesma.
        GpsPosition ultima = positions.get(positions.size() - 1);
        if (inicioRalenti != null) {
            adicionarRalenti(encontradas, inicioRalenti, ultima);
        }
        if (inicioNoite != null) {
            adicionarNoite(encontradas, inicioNoite, ultima);
        }
        return encontradas;
    }

    /** Penalização de um excesso de velocidade já detetado pelos alertas. */
    public double overspeedPenalty(BigDecimal pico, BigDecimal limite) {
        if (pico == null || limite == null) {
            return PENALTY_OVERSPEED;
        }
        double margem = pico.doubleValue() - limite.doubleValue();
        return margem > SEVERE_OVERSPEED_MARGIN_KPH
                ? PENALTY_OVERSPEED_SEVERE : PENALTY_OVERSPEED;
    }

    // ==== Auxiliares =======================================================
    private void adicionarRalenti(List<Detected> saida, Instant inicio, GpsPosition fim) {
        long minutos = Duration.between(inicio, fim.getRecordedAt()).toMinutes();
        if (minutos < IDLE_MIN_MINUTES) {
            return;
        }
        saida.add(new Detected(
                DrivingEventKind.IDLING, AlertSeverity.INFO,
                inicio, fim.getRecordedAt(),
                BigDecimal.valueOf(minutos), BigDecimal.valueOf(IDLE_MIN_MINUTES), "minutos",
                BigDecimal.ZERO, fim.getLatitude(), fim.getLongitude(),
                PENALTY_IDLING,
                "Motor ligado e parado durante " + minutos + " minutos."));
    }

    private void adicionarNoite(List<Detected> saida, Instant inicio, GpsPosition fim) {
        long minutos = Duration.between(inicio, fim.getRecordedAt()).toMinutes();
        if (minutos < 1) {
            return;
        }
        saida.add(new Detected(
                DrivingEventKind.NIGHT_DRIVING, AlertSeverity.INFO,
                inicio, fim.getRecordedAt(),
                BigDecimal.valueOf(minutos), null, "minutos",
                fim.getSpeedKph(), fim.getLatitude(), fim.getLongitude(),
                PENALTY_NIGHT,
                "Condução entre as " + NIGHT_FROM_HOUR + "h e as " + NIGHT_TO_HOUR
                        + "h durante " + minutos + " minutos."));
    }

    private boolean isIdling(GpsPosition p) {
        // Sem informação de ignição não se sabe se o motor está ligado, e um
        // veículo estacionado com o motor desligado não está ao ralenti.
        if (!Boolean.TRUE.equals(p.getIgnition())) {
            return false;
        }
        return p.getSpeedKph() != null && p.getSpeedKph().doubleValue() <= STOPPED_SPEED_KPH;
    }

    private boolean isMoving(GpsPosition p) {
        if (p.getSpeedKph() != null) {
            return p.getSpeedKph().doubleValue() > STOPPED_SPEED_KPH;
        }
        return Boolean.TRUE.equals(p.getMoving());
    }

    boolean isNight(Instant moment) {
        LocalTime hora = moment.atZone(ZONE).toLocalTime();
        return hora.getHour() >= NIGHT_FROM_HOUR || hora.getHour() < NIGHT_TO_HOUR;
    }

    /** Menor ângulo entre dois rumos: 350° para 10° são 20°, não 340°. */
    static double headingDelta(double from, double to) {
        double bruto = Math.abs(to - from) % 360;
        return bruto > 180 ? 360 - bruto : bruto;
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(Math.round(value * 1000d) / 1000d);
    }

    private static String fmt(double value) {
        return String.valueOf(Math.round(value));
    }
}
