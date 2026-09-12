package ao.autocare.modules.telemetry;

import ao.autocare.domain.Asset;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.Trip;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.TripRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agrupa posições soltas em viagens.
 *
 * <p>Uma viagem abre quando o ativo arranca (ignição ligada ou movimento
 * detetado) e fecha por uma de três razões: a ignição desligar, o ativo ficar
 * parado mais do que {@link #STOP_TO_CLOSE}, ou o aparelho calar-se por mais do
 * que {@link GpsDevice#OFFLINE_AFTER}. Um semáforo ou uma paragem curta na obra
 * não partem a viagem em duas, e um aparelho que só comunica de dez em dez
 * minutos em andamento também não.
 *
 * <p>A distância é acumulada ponto a ponto, mas ao fechar prefere-se a
 * diferença do odómetro do aparelho quando existe — ver
 * {@code TelemetryService#distanceKm}, pela mesma razão: somar linhas rectas
 * inflaciona o total com o ruído do GPS.
 */
@Component
public class TripTracker {

    /** Parado mais do que isto fecha a viagem. */
    public static final Duration STOP_TO_CLOSE = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(TripTracker.class);

    /** Viagens abaixo desta distância são ruído de estacionamento e descartam-se. */
    private static final BigDecimal MIN_TRIP_KM = new BigDecimal("0.100");

    private final TripRepository trips;
    private final GpsPositionRepository positions;

    /**
     * Quem quer ser avisado quando uma viagem fecha.
     *
     * <p>Injetado como lista para a telemetria nao passar a depender do modulo
     * de frota: sem isto, fechar uma viagem e pontuar um motorista ficavam
     * presos um ao outro, e nenhum dos dois podia mudar sozinho.
     */
    private final List<TripFinishedHook> finishedHooks;

    public TripTracker(
            TripRepository trips,
            GpsPositionRepository positions,
            List<TripFinishedHook> finishedHooks) {
        this.trips = trips;
        this.positions = positions;
        this.finishedHooks = finishedHooks;
    }

    /**
     * Encaixa uma posição nova na viagem do ativo, abrindo ou fechando conforme
     * o estado. Devolve o id da viagem a que a posição ficou a pertencer, ou
     * {@code null} se o ativo está parado.
     */
    public String accept(Asset asset, GpsDevice device, GpsPosition p, GpsPosition previous) {
        if (asset == null) {
            return null;
        }
        Trip open = trips.findFirstByAssetIdAndEndedAtIsNullOrderByStartedAtDesc(asset.getId())
                .orElse(null);
        boolean running = isRunning(p);

        if (open != null && shouldClose(open, p, running)) {
            close(open, previous != null ? previous : p);
            open = null;
        }
        if (!running) {
            return null;
        }
        if (open == null) {
            open = start(asset, device, p);
        } else {
            extend(open, p, previous);
        }
        p.setTripId(open.getId());
        return open.getId();
    }

    /**
     * Fecha viagens de ativos que deixaram de comunicar. Sem isto, um aparelho
     * que se cala deixa a viagem aberta para sempre e os totais nunca fecham.
     */
    public int closeStale(String orgId, Instant now) {
        int closed = 0;
        for (Trip trip : trips.findByOrganizationIdAndEndedAtIsNull(orgId)) {
            GpsPosition last = positions.findByTripIdOrderByRecordedAtAsc(trip.getId()).stream()
                    .reduce((a, b) -> b).orElse(null);
            Instant reference = last != null ? last.getRecordedAt() : trip.getStartedAt();
            // Silêncio, não paragem: usa-se o limiar de "aparelho offline". Um
            // ativo em marcha que comunica de 10 em 10 minutos não é abandonado.
            if (Duration.between(reference, now).compareTo(GpsDevice.OFFLINE_AFTER) > 0) {
                close(trip, last);
                closed++;
            }
        }
        return closed;
    }

    // ------------------------------------------------------------------
    /** Em marcha: a ignição manda; sem ignição, vale o movimento detetado. */
    private boolean isRunning(GpsPosition p) {
        if (p.getIgnition() != null) {
            return p.getIgnition();
        }
        return Boolean.TRUE.equals(p.getMoving());
    }

    /**
     * Decide se a posição nova fecha a viagem aberta.
     *
     * <p>O intervalo entre relatórios <em>não</em> é tempo parado: um camião em
     * marcha que comunica de 10 em 10 minutos continua na mesma viagem. Só o
     * fecham três coisas: a ignição desligar, o ativo estar parado há mais de
     * {@link #STOP_TO_CLOSE}, ou o aparelho ter estado calado tempo suficiente
     * para se considerar offline — nesse caso não se sabe o que aconteceu pelo
     * meio e continuar a mesma viagem seria inventar percurso.
     */
    private boolean shouldClose(Trip open, GpsPosition p, boolean running) {
        if (!running && Boolean.FALSE.equals(p.getIgnition())) {
            return true;
        }
        List<GpsPosition> pontos = positions.findByTripIdOrderByRecordedAtAsc(open.getId());
        Instant last = pontos.isEmpty()
                ? open.getStartedAt()
                : pontos.get(pontos.size() - 1).getRecordedAt();
        Duration gap = Duration.between(last, p.getRecordedAt());

        if (gap.compareTo(GpsDevice.OFFLINE_AFTER) > 0) {
            return true;
        }
        return !running && gap.compareTo(STOP_TO_CLOSE) > 0;
    }

    private Trip start(Asset asset, GpsDevice device, GpsPosition p) {
        Trip trip = new Trip();
        trip.setOrganization(asset.getOrganization());
        trip.setAsset(asset);
        trip.setDevice(device);
        trip.setStartedAt(p.getRecordedAt());
        trip.setStartLatitude(p.getLatitude());
        trip.setStartLongitude(p.getLongitude());
        trip.setEndLatitude(p.getLatitude());
        trip.setEndLongitude(p.getLongitude());
        trip.setDistanceKm(BigDecimal.ZERO);
        trip.setMaxSpeedKph(p.getSpeedKph());
        trip.setPositionCount(1);
        return trips.save(trip);
    }

    private void extend(Trip trip, GpsPosition p, GpsPosition previous) {
        if (previous != null) {
            double meters = Geo.distanceMeters(
                    previous.getLatitude(), previous.getLongitude(),
                    p.getLatitude(), p.getLongitude());
            trip.setDistanceKm(trip.getDistanceKm()
                    .add(BigDecimal.valueOf(meters / 1000.0))
                    .setScale(3, RoundingMode.HALF_UP));
        }
        trip.setEndLatitude(p.getLatitude());
        trip.setEndLongitude(p.getLongitude());
        trip.setPositionCount(trip.getPositionCount() + 1);
        if (p.getSpeedKph() != null
                && (trip.getMaxSpeedKph() == null
                    || p.getSpeedKph().compareTo(trip.getMaxSpeedKph()) > 0)) {
            trip.setMaxSpeedKph(p.getSpeedKph());
        }
    }

    private void close(Trip trip, GpsPosition last) {
        Instant end = last != null ? last.getRecordedAt() : trip.getStartedAt();
        trip.setEndedAt(end);
        if (last != null) {
            trip.setEndLatitude(last.getLatitude());
            trip.setEndLongitude(last.getLongitude());
        }
        trip.setDurationMinutes((int) Duration.between(trip.getStartedAt(), end).toMinutes());

        BigDecimal fromOdometer = odometerDelta(trip);
        if (fromOdometer != null) {
            trip.setDistanceKm(fromOdometer);
        }
        if (trip.getDistanceKm().compareTo(MIN_TRIP_KM) < 0) {
            // Deriva do GPS com o ativo estacionado — não é uma viagem.
            positions.findByTripIdOrderByRecordedAtAsc(trip.getId())
                    .forEach(p -> p.setTripId(null));
            trips.delete(trip);
            return;
        }

        // A viagem existe mesmo: quem quiser analisá-la analisa-a agora, com o
        // conjunto completo de leituras já gravado.
        for (TripFinishedHook hook : finishedHooks) {
            try {
                hook.onTripFinished(trip);
            } catch (Exception e) {
                log.warn("Falha ao processar o fecho da viagem {}: {}",
                        trip.getId(), e.toString());
            }
        }
    }

    /** Diferença do odómetro do aparelho entre o primeiro e o último ponto. */
    private BigDecimal odometerDelta(Trip trip) {
        BigDecimal first = null;
        BigDecimal last = null;
        for (GpsPosition p : positions.findByTripIdOrderByRecordedAtAsc(trip.getId())) {
            if (p.getOdometerKm() == null) {
                continue;
            }
            if (first == null) {
                first = p.getOdometerKm();
            }
            last = p.getOdometerKm();
        }
        if (first == null || last == null) {
            return null;
        }
        BigDecimal delta = last.subtract(first);
        return delta.signum() > 0 ? delta.setScale(3, RoundingMode.HALF_UP) : null;
    }
}
