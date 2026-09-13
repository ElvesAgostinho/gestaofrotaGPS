package ao.autocare.modules.telemetry;

import ao.autocare.common.ApiException;
import ao.autocare.domain.GpsPosition;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.GpsPositionRepository;
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
 * O dia de uma viatura, para ver de novo: o percurso, as paragens (onde e
 * quanto tempo) e o ralenti — motor ligado sem andar, que é gasóleo a
 * queimar-se parado. O ralenti só se conta quando o aparelho reporta a
 * ignição; sem esse dado diz-se «desconhecido», não zero.
 */
@Service
public class DayHistoryService {

    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");
    /** Abaixo disto a viatura está parada (ruído do GPS). */
    private static final BigDecimal PARADO_KPH = new BigDecimal("3");
    /** Uma paragem só conta a partir daqui: semáforos e trânsito não são paragens. */
    private static final int PARAGEM_MINIMA_MIN = 3;

    public record Stop(int order, Instant startedAt, Instant endedAt, int minutes,
                       BigDecimal latitude, BigDecimal longitude,
                       /** Minutos com o motor ligado durante a paragem; nulo se o aparelho não reporta ignição. */
                       Integer idlingMinutes) {}

    public record Point(Instant at, BigDecimal latitude, BigDecimal longitude, BigDecimal speedKph,
                        BigDecimal heading, Boolean ignition) {}

    public record DayView(String assetId, LocalDate date, int points, BigDecimal distanceKm, BigDecimal maxSpeedKph,
                          Instant firstMovementAt, Instant lastMovementAt,
                          int movingMinutes, int stoppedMinutes,
                          /** Total de ralenti no dia; nulo se o aparelho não reporta ignição. */
                          Integer idlingMinutes,
                          boolean ignitionKnown,
                          List<Stop> stops, List<Point> track) {}

    private final AssetRepository assets;
    private final GpsPositionRepository positions;

    public DayHistoryService(AssetRepository assets, GpsPositionRepository positions) {
        this.assets = assets;
        this.positions = positions;
    }

    @Transactional(readOnly = true)
    public DayView day(String orgId, String assetId, LocalDate dia) {
        assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        Instant inicio = dia.atStartOfDay(FUSO).toInstant();
        Instant fim = dia.plusDays(1).atStartOfDay(FUSO).toInstant();
        List<GpsPosition> raw = positions.track(assetId, inicio, fim);

        List<Point> pontos = new ArrayList<>(raw.size());
        boolean ignicaoConhecida = false;
        BigDecimal maxVel = null;
        Instant primeiroMov = null;
        Instant ultimoMov = null;
        for (GpsPosition p : raw) {
            pontos.add(new Point(p.getRecordedAt(), p.getLatitude(), p.getLongitude(), p.getSpeedKph(), p.getHeading(), p.getIgnition()));
            if (p.getIgnition() != null) {
                ignicaoConhecida = true;
            }
            if (p.getSpeedKph() != null && (maxVel == null || p.getSpeedKph().compareTo(maxVel) > 0)) {
                maxVel = p.getSpeedKph();
            }
            if (aAndar(p)) {
                if (primeiroMov == null) {
                    primeiroMov = p.getRecordedAt();
                }
                ultimoMov = p.getRecordedAt();
            }
        }

        // Paragens: sequências de posições paradas com pelo menos 3 minutos entre a primeira e a última.
        List<Stop> paragens = new ArrayList<>();
        int aAndarMin = 0;
        int paradoMin = 0;
        int ralentiMin = 0;
        int inicioParagem = -1;
        for (int i = 1; i < raw.size(); i++) {
            GpsPosition antes = raw.get(i - 1);
            GpsPosition agora = raw.get(i);
            int minutos = (int) Math.min(Duration.between(antes.getRecordedAt(), agora.getRecordedAt()).toMinutes(), 60);
            boolean parado = !aAndar(antes);
            if (parado) {
                paradoMin += minutos;
                if (Boolean.TRUE.equals(antes.getIgnition())) {
                    ralentiMin += minutos;
                }
                if (inicioParagem < 0) {
                    inicioParagem = i - 1;
                }
            } else {
                aAndarMin += minutos;
                if (inicioParagem >= 0) {
                    // A paragem vai até à última amostra parada: o momento exato em que
                    // arrancou está entre ela e a seguinte, e não se inventa.
                    fecharParagem(raw, inicioParagem, i - 1, paragens);
                    inicioParagem = -1;
                }
            }
        }
        if (inicioParagem >= 0) {
            // Se a última amostra do dia já é a andar, a paragem acabou na anterior.
            int fimParagem = raw.size() - 1;
            if (aAndar(raw.get(fimParagem))) {
                fimParagem--;
            }
            fecharParagem(raw, inicioParagem, fimParagem, paragens);
        }

        return new DayView(assetId, dia, pontos.size(), distanciaKm(raw), maxVel, primeiroMov, ultimoMov,
                aAndarMin, paradoMin, ignicaoConhecida ? ralentiMin : null, ignicaoConhecida, paragens, pontos);
    }

    private static void fecharParagem(List<GpsPosition> raw, int de, int ate, List<Stop> out) {
        GpsPosition a = raw.get(de);
        GpsPosition b = raw.get(ate);
        int minutos = (int) Duration.between(a.getRecordedAt(), b.getRecordedAt()).toMinutes();
        if (minutos < PARAGEM_MINIMA_MIN) {
            return;
        }
        Integer ralenti = null;
        boolean conhecida = false;
        int r = 0;
        for (int i = de; i < ate; i++) {
            GpsPosition p = raw.get(i);
            if (p.getIgnition() != null) {
                conhecida = true;
                if (p.getIgnition()) {
                    r += (int) Math.min(Duration.between(p.getRecordedAt(), raw.get(i + 1).getRecordedAt()).toMinutes(), 60);
                }
            }
        }
        if (conhecida) {
            ralenti = r;
        }
        out.add(new Stop(out.size() + 1, a.getRecordedAt(), b.getRecordedAt(), minutos, a.getLatitude(), a.getLongitude(), ralenti));
    }

    private static boolean aAndar(GpsPosition p) {
        if (p.getSpeedKph() != null) {
            return p.getSpeedKph().compareTo(PARADO_KPH) > 0;
        }
        return Boolean.TRUE.equals(p.getMoving());
    }

    /** Soma das distâncias entre pontos consecutivos (haversine), em km. */
    static BigDecimal distanciaKm(List<GpsPosition> raw) {
        double total = 0;
        for (int i = 1; i < raw.size(); i++) {
            GpsPosition a = raw.get(i - 1);
            GpsPosition b = raw.get(i);
            if (a.getLatitude() == null || b.getLatitude() == null) {
                continue;
            }
            double lat1 = Math.toRadians(a.getLatitude().doubleValue());
            double lat2 = Math.toRadians(b.getLatitude().doubleValue());
            double dLat = lat2 - lat1;
            double dLon = Math.toRadians(b.getLongitude().doubleValue() - a.getLongitude().doubleValue());
            double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            total += 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        }
        return BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP);
    }
}
