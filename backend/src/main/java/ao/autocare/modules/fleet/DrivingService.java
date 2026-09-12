package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverScore;
import ao.autocare.domain.DrivingEvent;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.TelemetryAlert;
import ao.autocare.domain.Trip;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import ao.autocare.domain.enums.Enums.ScoreBand;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fleet.dto.DrivingDtos.DriverScoreView;
import ao.autocare.modules.fleet.dto.DrivingDtos.DrivingEventView;
import ao.autocare.modules.telemetry.TripFinishedHook;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.DriverScoreRepository;
import ao.autocare.repo.DrivingEventRepository;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.TelemetryAlertRepository;
import ao.autocare.repo.TripRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Análise de condução: infrações e pontuação.
 *
 * <p>Corre quando uma viagem fecha, porque é aí que existe o conjunto completo
 * de leituras. Analisar em tempo real daria os mesmos números mais tarde, mas
 * obrigaria a guardar estado entre posições — e uma leitura perdida deixaria o
 * cálculo torto sem ninguém dar por isso.
 */
@Service
public class DrivingService implements TripFinishedHook {

    private static final Logger log = LoggerFactory.getLogger(DrivingService.class);

    /**
     * Distância mínima para uma pontuação significar alguma coisa.
     *
     * <p>Dar 100 a quem conduziu três quilómetros poria essa pessoa acima de
     * quem fez cinco mil com duas infrações. Abaixo disto o sistema diz que não
     * sabe, que é diferente de dizer que está tudo bem.
     */
    static final BigDecimal MIN_DISTANCE_FOR_SCORE_KM = new BigDecimal("50");

    private final DrivingEventRepository events;
    private final DriverScoreRepository scores;
    private final DriverRepository drivers;
    private final TripRepository trips;
    private final GpsPositionRepository positions;
    private final TelemetryAlertRepository alerts;
    private final DriverService driverService;
    private final DrivingAnalyzer analyzer;
    private final PlaceResolver places;
    private final AuditService audit;

    public DrivingService(
            DrivingEventRepository events,
            DriverScoreRepository scores,
            DriverRepository drivers,
            TripRepository trips,
            GpsPositionRepository positions,
            TelemetryAlertRepository alerts,
            DriverService driverService,
            DrivingAnalyzer analyzer,
            PlaceResolver places,
            AuditService audit) {
        this.events = events;
        this.scores = scores;
        this.drivers = drivers;
        this.trips = trips;
        this.positions = positions;
        this.alerts = alerts;
        this.driverService = driverService;
        this.analyzer = analyzer;
        this.places = places;
        this.audit = audit;
    }

    // ==== Fecho de viagem ==================================================
    @Override
    @Transactional
    public void onTripFinished(Trip trip) {
        try {
            enrich(trip);
        } catch (Exception e) {
            // Uma falha a analisar não pode impedir a viagem de fechar: perder
            // a viagem inteira seria pior do que perder a análise dela.
            log.warn("Falha ao analisar a viagem {}: {}", trip.getId(), e.toString());
        }
    }

    /**
     * Dá nome aos extremos da viagem, atribui-lhe um motorista e regista as
     * infrações encontradas.
     */
    @Transactional
    public void enrich(Trip trip) {
        String orgId = trip.getOrganization().getId();

        // ---- Quem conduzia -------------------------------------------------
        // Resolvido pelo INÍCIO da viagem: se houve troca de motorista a meio,
        // quem a começou é quem responde por ela.
        Optional<Driver> condutor =
                driverService.driverAt(trip.getAsset().getId(), trip.getStartedAt());
        condutor.ifPresent(trip::setDriver);

        // ---- Onde começou e acabou -----------------------------------------
        PlaceResolver.Place partida =
                places.resolve(orgId, trip.getStartLatitude(), trip.getStartLongitude());
        trip.setStartPlaceName(partida.name());
        trip.setStartPlaceKind(partida.kind());
        trip.setStartPlaceId(partida.id());

        PlaceResolver.Place chegada =
                places.resolve(orgId, trip.getEndLatitude(), trip.getEndLongitude());
        trip.setEndPlaceName(chegada.name());
        trip.setEndPlaceKind(chegada.kind());
        trip.setEndPlaceId(chegada.id());

        // ---- Infrações ------------------------------------------------------
        List<GpsPosition> leituras =
                positions.findByTripIdOrderByRecordedAtAsc(trip.getId());

        int travagens = 0;
        int aceleracoes = 0;
        int curvas = 0;
        int minutosRalenti = 0;
        int minutosNoite = 0;

        for (DrivingAnalyzer.Detected d : analyzer.analyse(leituras)) {
            DrivingEvent e = new DrivingEvent();
            e.setOrganization(trip.getOrganization());
            e.setAsset(trip.getAsset());
            e.setDriver(trip.getDriver());
            e.setTrip(trip);
            e.setKind(d.kind());
            e.setSeverity(d.severity());
            e.setOccurredAt(d.occurredAt());
            e.setEndedAt(d.endedAt());
            e.setMeasuredValue(d.measuredValue());
            e.setThresholdValue(d.thresholdValue());
            e.setUnit(d.unit());
            e.setSpeedKph(d.speedKph());
            e.setLatitude(d.latitude());
            e.setLongitude(d.longitude());
            e.setPenaltyPoints(BigDecimal.valueOf(d.penaltyPoints()));
            e.setDescription(d.description());
            e.setPlaceName(places.resolve(orgId, d.latitude(), d.longitude()).name());
            events.save(e);

            switch (d.kind()) {
                case HARSH_BRAKE -> travagens++;
                case HARSH_ACCELERATION -> aceleracoes++;
                case HARSH_CORNERING -> curvas++;
                case IDLING -> minutosRalenti += valueOf(d.measuredValue());
                case NIGHT_DRIVING -> minutosNoite += valueOf(d.measuredValue());
                default -> { }
            }
        }

        // ---- Excessos de velocidade já detetados pelos alertas --------------
        int excessos = importOverspeed(trip);

        trip.setHarshBrakeCount(travagens);
        trip.setHarshAccelCount(aceleracoes);
        trip.setHarshCornerCount(curvas);
        trip.setIdleMinutes(minutosRalenti);
        trip.setNightMinutes(minutosNoite);
        trip.setOverspeedCount(excessos);
        trip.setEventCount(travagens + aceleracoes + curvas + excessos
                + (minutosRalenti > 0 ? 1 : 0) + (minutosNoite > 0 ? 1 : 0));
    }

    /**
     * Traz para as infrações os excessos de velocidade que os alertas de
     * telemetria já detetaram durante a viagem.
     *
     * <p>Não se volta a detetar nada: os limites por zona, ativo e empresa já
     * estão aplicados ali, e detetar outra vez daria dois números diferentes
     * para o mesmo excesso. O índice único em {@code source_alert_id} garante
     * que o mesmo alerta não entra duas vezes.
     */
    private int importOverspeed(Trip trip) {
        if (trip.getEndedAt() == null) {
            return 0;
        }
        int contados = 0;
        List<TelemetryAlert> episodios = alerts.findByAssetIdAndKindBetween(
                trip.getAsset().getId(), TelemetryAlertKind.SPEEDING,
                trip.getStartedAt(), trip.getEndedAt());

        for (TelemetryAlert a : episodios) {
            if (events.existsBySourceAlertId(a.getId())) {
                continue;
            }
            DrivingEvent e = new DrivingEvent();
            e.setOrganization(trip.getOrganization());
            e.setAsset(trip.getAsset());
            e.setDriver(trip.getDriver());
            e.setTrip(trip);
            e.setSourceAlertId(a.getId());
            e.setKind(DrivingEventKind.OVERSPEED);
            e.setSeverity(ao.autocare.domain.enums.Enums.AlertSeverity.WARNING);
            e.setOccurredAt(a.getStartedAt());
            e.setEndedAt(a.getEndedAt());
            e.setMeasuredValue(a.getPeakValue());
            e.setThresholdValue(a.getLimitValue());
            e.setUnit("km/h");
            e.setSpeedKph(a.getPeakValue());
            e.setLatitude(a.getLatitude());
            e.setLongitude(a.getLongitude());
            e.setPenaltyPoints(BigDecimal.valueOf(
                    analyzer.overspeedPenalty(a.getPeakValue(), a.getLimitValue())));
            e.setDescription(a.getMessage());
            events.save(e);
            contados++;
        }
        return contados;
    }

    // ==== Pontuação ========================================================
    /**
     * Calcula a pontuação de um motorista num período e guarda-a.
     *
     * <p>A fórmula está aqui, inteira, e é simples de propósito:
     * <pre>
     *   pontuação = 100 − (penalizações ÷ km percorridos × 100)
     * </pre>
     * Normalizar por distância é o que impede que quem conduz mais pareça pior
     * só por conduzir mais. Um motorista tem o direito de refazer esta conta
     * com os números que o ecrã lhe mostra.
     */
    @Transactional
    public DriverScore computeScore(
            String orgId, String driverId, Instant inicioBruto, Instant fimBruto) {

        Driver driver = drivers.findByIdAndOrganizationId(driverId, orgId)
                .orElseThrow(() -> ApiException.notFound("Motorista não encontrado."));
        Instant inicio = normalise(inicioBruto);
        Instant fim = normalise(fimBruto);
        if (!fim.isAfter(inicio)) {
            throw ApiException.badRequest("O fim do período tem de ser posterior ao início.");
        }

        List<Trip> viagens = trips.findByDriverIdBetween(driverId, inicio, fim);
        BigDecimal distancia = viagens.stream()
                .map(Trip::getDistanceKm)
                .filter(d -> d != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int minutos = viagens.stream()
                .map(Trip::getDurationMinutes)
                .filter(m -> m != null)
                .reduce(0, Integer::sum);

        List<DrivingEvent> infracoes = events.countingForDriver(driverId, inicio, fim);
        BigDecimal penalizacao = infracoes.stream()
                .map(DrivingEvent::getPenaltyPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DriverScore s = scores
                .findByDriverIdAndPeriodStartAndPeriodEnd(driverId, inicio, fim)
                .orElseGet(DriverScore::new);
        s.setOrganization(driver.getOrganization());
        s.setDriver(driver);
        s.setPeriodStart(inicio);
        s.setPeriodEnd(fim);
        s.setDistanceKm(distancia);
        s.setDrivingMinutes(minutos);
        s.setTripCount(viagens.size());
        s.setOverspeedCount(count(infracoes, DrivingEventKind.OVERSPEED));
        s.setHarshBrakeCount(count(infracoes, DrivingEventKind.HARSH_BRAKE));
        s.setHarshAccelCount(count(infracoes, DrivingEventKind.HARSH_ACCELERATION));
        s.setHarshCornerCount(count(infracoes, DrivingEventKind.HARSH_CORNERING));
        s.setIdlingCount(count(infracoes, DrivingEventKind.IDLING));
        s.setNightCount(count(infracoes, DrivingEventKind.NIGHT_DRIVING));
        s.setTotalPenalty(penalizacao);
        s.setComputedAt(Instant.now());

        if (distancia.compareTo(MIN_DISTANCE_FOR_SCORE_KM) < 0) {
            s.setScore(null);
            s.setBand(null);
            s.setInsufficientData(true);
        } else {
            BigDecimal por100km = penalizacao
                    .multiply(new BigDecimal("100"))
                    .divide(distancia, 2, RoundingMode.HALF_UP);
            BigDecimal valor = new BigDecimal("100").subtract(por100km);
            if (valor.signum() < 0) {
                valor = BigDecimal.ZERO;
            }
            s.setScore(valor.setScale(2, RoundingMode.HALF_UP));
            s.setBand(band(valor));
            s.setInsufficientData(false);
        }
        return scores.save(s);
    }

    /** Recalcula toda a gente no período — usado pelo ecrã de ranking. */
    @Transactional
    public List<DriverScore> computeAll(String orgId, Instant inicioBruto, Instant fimBruto) {
        Instant inicio = normalise(inicioBruto);
        Instant fim = normalise(fimBruto);
        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            computeScore(orgId, d.getId(), inicio, fim);
        }
        return scores.ranking(orgId, inicio, fim);
    }

    /**
     * Corta o período ao segundo.
     *
     * <p>{@code Instant.now()} traz nanossegundos; a coluna de data guarda menos
     * precisão. Gravar com nanos e depois procurar por igualdade com os mesmos
     * nanos não encontra nada — a linha existe, mas com o valor truncado. Como
     * um período de pontuação se mede em dias, o segundo é precisão de sobra.
     */
    private static Instant normalise(Instant moment) {
        return moment.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    static ScoreBand band(BigDecimal score) {
        double v = score.doubleValue();
        if (v >= 90) {
            return ScoreBand.EXCELLENT;
        }
        if (v >= 75) {
            return ScoreBand.GOOD;
        }
        if (v >= 60) {
            return ScoreBand.NEEDS_IMPROVEMENT;
        }
        return ScoreBand.CRITICAL;
    }

    // ==== Infrações ========================================================
    /**
     * Anula uma infração.
     *
     * <p>Existe porque nem toda a travagem violenta é má condução: travar a
     * fundo para não atropelar alguém é exatamente o que se quer que aconteça.
     * Fica registado quem anulou e porquê — sem isso, seria uma forma silenciosa
     * de apagar o que não convém.
     */
    @Transactional
    public DrivingEventView dismiss(
            String orgId, String userId, String eventId, String reason) {
        DrivingEvent e = events.findByIdAndOrganizationId(eventId, orgId)
                .orElseThrow(() -> ApiException.notFound("Infração não encontrada."));
        if (reason == null || reason.trim().length() < 5) {
            throw ApiException.badRequest(
                    "Escreva a razão para anular esta infração. Fica registada.");
        }
        if (e.isDismissed()) {
            throw ApiException.conflict("Esta infração já tinha sido anulada.");
        }
        e.setDismissedAt(Instant.now());
        e.setDismissedBy(userId);
        e.setDismissReason(reason.trim());

        audit.record(orgId, userId, "driving_event.dismiss", "DrivingEvent", e.getId(),
                e.getKind() + " · " + e.getAsset().getTag() + " · " + reason.trim());
        return DrivingEventView.of(e);
    }

    // ==== Leitura ==========================================================
    // As vistas sao montadas AQUI, dentro da transacao. Devolver entidades ao
    // controlador rebentava com LazyInitializationException assim que o JSON
    // tocasse no ativo ou no motorista -- e so em execucao, nunca a compilar.
    @Transactional(readOnly = true)
    public PagedResponse<DrivingEventView> list(String orgId, Pageable pageable) {
        return PagedResponse.of(events
                .findByOrganizationIdOrderByOccurredAtDesc(orgId, pageable)
                .map(DrivingEventView::of));
    }

    @Transactional(readOnly = true)
    public List<DrivingEventView> forTrip(String orgId, String tripId) {
        return events.findByTripIdOrderByOccurredAtAsc(tripId).stream()
                .filter(e -> e.getOrganization().getId().equals(orgId))
                .map(DrivingEventView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DrivingEventView> countingBetween(String orgId, Instant inicio, Instant fim) {
        return events.countingForOrganization(orgId, inicio, fim).stream()
                .map(DrivingEventView::of).toList();
    }

    @Transactional
    public List<DriverScoreView> rankAll(String orgId, Instant inicio, Instant fim) {
        return computeAll(orgId, inicio, fim).stream().map(DriverScoreView::of).toList();
    }

    @Transactional
    public DriverScoreView scoreView(
            String orgId, String driverId, Instant inicio, Instant fim) {
        return DriverScoreView.of(computeScore(orgId, driverId, inicio, fim));
    }

    private static int count(List<DrivingEvent> lista, DrivingEventKind kind) {
        return (int) lista.stream().filter(e -> e.getKind() == kind).count();
    }

    private static int valueOf(BigDecimal value) {
        return value == null ? 0 : value.intValue();
    }
}
