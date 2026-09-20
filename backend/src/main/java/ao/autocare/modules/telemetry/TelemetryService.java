package ao.autocare.modules.telemetry;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.GpsDeviceStatus;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PositionSource;
import ao.autocare.domain.enums.Enums.TelemetryProviderKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.domain.TelemetryAlert;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.AlertView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.DeviceCreated;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.DeviceView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestResult;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.LiveAssetView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.LivePositionEvent;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.ManualPositionRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.PositionInput;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.PositionView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.SaveDeviceRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.TrackPoint;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.TrackView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.TripView;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.TelemetryAlertRepository;
import ao.autocare.repo.TripRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aparelhos de GPS e entrada de posições.
 *
 * <p>Uma posição só é aceite se as coordenadas forem plausíveis. Fica sempre
 * guardada no histórico, mas a <em>posição atual</em> do ativo só recua se a
 * leitura for mais recente do que a que lá está — os aparelhos guardam pontos
 * quando ficam sem rede e enviam-nos depois fora de ordem.
 */
@Service
public class TelemetryService {

    private final ao.autocare.modules.fuel.FuelSensorWatch fuelSensor;
    private final ao.autocare.modules.meter.MeterService meterService;

    /** Acima disto considera-se que o ativo está em movimento. */
    private static final BigDecimal MOVING_SPEED_KPH = new BigDecimal("3");

    /** Posições mais antigas do que isto não são aceites (relógio do aparelho perdido). */
    private static final Duration MAX_BACKDATE = Duration.ofDays(30);

    /**
     * Com menos de 4 satélites o aparelho não tem fixação 3D e a posição pode
     * saltar centenas de metros — é a principal fonte de quilómetros fantasma.
     */
    private static final int MIN_SATELLITES = 4;

    /** Erro declarado acima disto (metros) não serve para trajeto nem geocerca. */
    private static final BigDecimal MAX_ACCURACY_M = new BigDecimal("100");

    /**
     * Velocidade implícita entre dois pontos acima da qual o salto é impossível
     * para uma viatura ou máquina — sinal de posição corrompida, não de deslocação.
     */
    private static final double MAX_PLAUSIBLE_KPH = 250;

    private final GpsDeviceRepository devices;
    private final GpsPositionRepository positions;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final TripRepository trips;
    private final TelemetryAlertRepository alerts;
    private final TripTracker tripTracker;
    private final GeofenceService geofences;
    private final TelemetryStream stream;
    private final SpeedWatch speedWatch;
    private final CommsWatch commsWatch;
    private final ao.autocare.modules.fleet.RouteWatch routeWatch;
    private final AuditService audit;
    private final ao.autocare.modules.integration.TraccarAccounts traccarAccounts;

    public TelemetryService(
            GpsDeviceRepository devices,
            GpsPositionRepository positions,
            AssetRepository assets,
            OrganizationRepository organizations,
            UserRepository users,
            TripRepository trips,
            TelemetryAlertRepository alerts,
            TripTracker tripTracker,
            GeofenceService geofences,
            TelemetryStream stream,
            SpeedWatch speedWatch,
            CommsWatch commsWatch,
            AuditService audit,
            ao.autocare.modules.fuel.FuelSensorWatch fuelSensor,
            @org.springframework.context.annotation.Lazy ao.autocare.modules.meter.MeterService meterService,
            ao.autocare.modules.integration.TraccarAccounts traccarAccounts,
            ao.autocare.modules.fleet.RouteWatch routeWatch) {
        this.meterService = meterService;
        this.fuelSensor = fuelSensor;
        this.devices = devices;
        this.positions = positions;
        this.assets = assets;
        this.organizations = organizations;
        this.users = users;
        this.trips = trips;
        this.alerts = alerts;
        this.tripTracker = tripTracker;
        this.geofences = geofences;
        this.stream = stream;
        this.speedWatch = speedWatch;
        this.commsWatch = commsWatch;
        this.routeWatch = routeWatch;
        this.audit = audit;
        this.traccarAccounts = traccarAccounts;
    }

    // ==== Aparelhos =====================================================
    @Transactional(readOnly = true)
    public List<DeviceView> listDevices(String orgId) {
        return devices.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(DeviceView::of).toList();
    }

    @Transactional(readOnly = true)
    public DeviceView getDevice(String orgId, String id) {
        return DeviceView.of(requireDevice(orgId, id));
    }

    @Transactional
    public DeviceCreated createDevice(String orgId, String userId, SaveDeviceRequest req) {
        String externalId = req.externalId().trim();
        devices.findByOrganizationIdAndExternalId(orgId, externalId).ifPresent(existing -> {
            throw ApiException.conflict("Já existe um aparelho com este identificador.");
        });

        GpsDevice d = new GpsDevice();
        d.setOrganization(organizations.getReferenceById(orgId));
        d.setExternalId(externalId);
        applyDevice(orgId, d, req);
        String key = newIngestKey(d);
        devices.save(d);

        audit.record(orgId, userId, "gps_device.create", "GpsDevice", d.getId(), externalId);

        // Registar também no Traccar da empresa, para que ninguém tenha de o
        // cadastrar duas vezes. A frase diz o que aconteceu — e o que falta, se falhou.
        String nota = traccarAccounts.registarAparelho(orgId, externalId,
                d.getName() != null ? d.getName()
                        : (d.getAsset() != null ? d.getAsset().getTag() : externalId));
        return new DeviceCreated(DeviceView.of(d), key, "/api/v1/telemetry/positions", nota);
    }

    @Transactional
    public DeviceView updateDevice(String orgId, String userId, String id, SaveDeviceRequest req) {
        GpsDevice d = requireDevice(orgId, id);
        if (req.externalId() != null && !req.externalId().isBlank()
                && !req.externalId().trim().equals(d.getExternalId())) {
            String wanted = req.externalId().trim();
            devices.findByOrganizationIdAndExternalId(orgId, wanted).ifPresent(other -> {
                throw ApiException.conflict("Já existe um aparelho com este identificador.");
            });
            d.setExternalId(wanted);
        }
        applyDevice(orgId, d, req);
        audit.record(orgId, userId, "gps_device.update", "GpsDevice", d.getId(), d.getExternalId());
        return DeviceView.of(d);
    }

    /** Gera uma chave nova; a anterior deixa de funcionar imediatamente. */
    @Transactional
    public DeviceCreated rotateKey(String orgId, String userId, String id) {
        GpsDevice d = requireDevice(orgId, id);
        String key = newIngestKey(d);
        audit.record(orgId, userId, "gps_device.rotate_key", "GpsDevice", d.getId(), d.getExternalId());
        return new DeviceCreated(DeviceView.of(d), key, "/api/v1/telemetry/positions");
    }

    @Transactional
    public void deleteDevice(String orgId, String userId, String id) {
        GpsDevice d = requireDevice(orgId, id);
        String externalId = d.getExternalId();
        devices.delete(d);
        traccarAccounts.apagarAparelho(orgId, externalId);
        audit.record(orgId, userId, "gps_device.delete", "GpsDevice", id, externalId);
    }

    // ==== Entrada de posições ===========================================
    /**
     * Publicação feita pelo próprio aparelho. Não há sessão de utilizador: o
     * aparelho identifica-se pelo id externo e pela chave de publicação.
     */
    @Transactional
    public IngestResult ingest(IngestRequest req) {
        List<GpsDevice> candidates = devices.findByExternalId(req.deviceId().trim());
        String keyHash = sha256(req.key());
        GpsDevice device = candidates.stream()
                .filter(d -> d.getIngestKeyHash() != null
                        && MessageDigest.isEqual(
                                d.getIngestKeyHash().getBytes(StandardCharsets.UTF_8),
                                keyHash.getBytes(StandardCharsets.UTF_8)))
                .findFirst()
                .orElseThrow(() -> ApiException.unauthorized(
                        "Aparelho ou chave de publicação inválidos."));

        return record(device, req.position());
    }

    /**
     * Guarda uma posição vinda de um aparelho. Devolve o resultado em vez de
     * lançar quando o ponto é apenas ignorado — um aparelho não deve entrar em
     * ciclo de repetição por enviar uma coordenada má.
     */
    @Transactional
    public IngestResult record(GpsDevice device, PositionInput in) {
        if (!Geo.isValid(in.latitude(), in.longitude())) {
            return new IngestResult(false, "Coordenadas inválidas (o aparelho pode estar sem sinal).",
                    null, null, null, 0);
        }
        Instant now = Instant.now();
        Instant recordedAt = in.recordedAt() != null ? in.recordedAt() : now;
        if (recordedAt.isAfter(now.plusSeconds(300))) {
            return new IngestResult(false, "A data da posição está no futuro.", null, null, null, 0);
        }
        if (recordedAt.isBefore(now.minus(MAX_BACKDATE))) {
            return new IngestResult(false, "A posição é demasiado antiga para ser aceite.",
                    null, null, null, 0);
        }

        if (in.satellites() != null && in.satellites() < MIN_SATELLITES) {
            return new IngestResult(false,
                    "Posição descartada: apenas " + in.satellites() + " satélites.",
                    null, null, null, 0);
        }
        if (in.accuracyM() != null && in.accuracyM().compareTo(MAX_ACCURACY_M) > 0) {
            return new IngestResult(false,
                    "Posição descartada: erro declarado de " + in.accuracyM() + " m.",
                    null, null, null, 0);
        }

        Asset asset = device.getAsset();
        GpsPosition previous = asset != null
                ? positions.findFirstByAssetIdOrderByRecordedAtDesc(asset.getId()).orElse(null)
                : null;

        String jump = impossibleJump(previous, in, recordedAt);
        if (jump != null) {
            return new IngestResult(false, jump, null, null, null, 0);
        }

        GpsPosition p = new GpsPosition();
        p.setOrganization(device.getOrganization());
        p.setDevice(device);
        p.setAsset(asset);
        p.setRecordedAt(recordedAt);
        p.setLatitude(in.latitude());
        p.setLongitude(in.longitude());
        p.setSpeedKph(in.speedKph());
        p.setAltitudeM(in.altitudeM());
        p.setAccuracyM(in.accuracyM());
        p.setSatellites(in.satellites());
        p.setIgnition(in.ignition());
        p.setOdometerKm(in.odometerKm());
        p.setEngineHours(in.engineHours());
        p.setProviderPositionId(in.providerPositionId());
        p.setFuelLevelLiters(in.fuelLevelLiters());
        p.setFuelLevelPercent(in.fuelLevelPercent());
        p.setTotalDistanceKm(in.totalDistanceKm());
        p.setSource(PositionSource.TELEMETRY);

        p.setHeading(resolveHeading(in, previous));
        p.setMoving(isMoving(in, previous, recordedAt));
        positions.save(p);
        // O sensor de combustível compara com a última posição que trouxe nível
        // — que pode não ser a imediatamente anterior, se o aparelho só o manda
        // de vez em quando.
        if (asset != null && p.getFuelLevelLiters() != null) {
            GpsPosition comNivel = previous != null && previous.getFuelLevelLiters() != null
                    ? previous
                    : positions.findFirstByAssetIdAndFuelLevelLitersIsNotNullOrderByRecordedAtDesc(
                            asset.getId()).filter(x -> !x.getId().equals(p.getId())).orElse(null);
            fuelSensor.aoReceber(asset, comNivel, p);
        }
        // Os contadores do aparelho alimentam os medidores: é o odómetro em
        // tempo real. Sem isto, as revisões venciam só quando alguém ia ler o painel.
        if (asset != null) {
            if (p.getOdometerKm() != null) {
                meterService.recordFromTelemetry(asset, MeterKind.ODOMETER, p.getOdometerKm(), recordedAt);
            }
            if (p.getEngineHours() != null) {
                meterService.recordFromTelemetry(asset, MeterKind.HOURMETER, p.getEngineHours(), recordedAt);
            }
        }
        String tripId = tripTracker.accept(asset, device, p, previous);
        GeofenceService.Evaluation fence = geofences.evaluate(asset, p);
        // Reaproveita as zonas já calculadas para aplicar o limite da zona.
        speedWatch.check(asset, device, p, fence.inside());
        // Saiu do caminho combinado? O aviso vale no momento, não no relatório.
        routeWatch.check(asset, p);
        commsWatch.deviceReported(device, recordedAt);
        int fenceEvents = fence.events().size();

        device.setLastSeenAt(recordedAt.isAfter(orNow(device.getLastSeenAt()))
                ? recordedAt : device.getLastSeenAt());
        if (in.batteryPercent() != null) {
            device.setBatteryPercent(in.batteryPercent());
        }
        device.setStatus(device.currentStatus(Boolean.TRUE.equals(p.getMoving())));

        if (asset != null) {
            updateAssetPosition(asset, p);
            stream.publishAfterCommit(device.getOrganization().getId(), "posicao",
                    liveEvent(asset, device, p));
        }
        return new IngestResult(true, null, p.getId(),
                asset != null ? asset.getId() : null, tripId, fenceEvents);
    }

    /** Posição introduzida à mão, para ativos sem aparelho instalado. */
    @Transactional
    public PositionView recordManual(
            String orgId, String userId, String assetId, ManualPositionRequest req) {

        Asset asset = requireAsset(orgId, assetId);
        if (!Geo.isValid(req.latitude(), req.longitude())) {
            throw ApiException.badRequest("As coordenadas indicadas não são válidas.");
        }
        GpsPosition p = new GpsPosition();
        p.setOrganization(asset.getOrganization());
        p.setAsset(asset);
        p.setRecordedAt(req.recordedAt() != null ? req.recordedAt() : Instant.now());
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
        p.setMoving(false);
        p.setSource(PositionSource.MANUAL);
        positions.save(p);

        updateAssetPosition(asset, p);
        geofences.evaluate(asset, p);
        stream.publishAfterCommit(orgId, "posicao", liveEvent(asset, null, p));
        audit.record(orgId, userId, "asset.position", "Asset", asset.getId(),
                asset.getTag() + " · " + req.latitude() + ", " + req.longitude());
        return PositionView.of(p);
    }

    // ==== Consultas =====================================================
    /** Onde está agora cada ativo da frota — a base do mapa ao vivo. */
    @Transactional(readOnly = true)
    public List<LiveAssetView> live(String orgId) {
        Map<String, GpsDevice> byAsset = devices.findByOrganizationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .filter(d -> d.getAsset() != null)
                .collect(Collectors.toMap(
                        d -> d.getAsset().getId(), Function.identity(), (a, b) -> a));

        Instant now = Instant.now();
        List<LiveAssetView> out = new ArrayList<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (a.isArchived() || a.getLatitude() == null || a.getLongitude() == null) {
                continue;
            }
            GpsPosition last = positions.findFirstByAssetIdOrderByRecordedAtDesc(a.getId())
                    .orElse(null);
            GpsDevice device = byAsset.get(a.getId());
            Instant at = a.getPositionAt();
            out.add(new LiveAssetView(
                    a.getId(), a.getTag(), a.getName(),
                    a.getAssetType() != null && a.getAssetType().getCategory() != null
                            ? a.getAssetType().getCategory().name() : null,
                    a.getStatus() != null ? a.getStatus().name() : null,
                    a.getLatitude(), a.getLongitude(), at,
                    last != null ? last.getSpeedKph() : null,
                    last != null ? last.getHeading() : null,
                    last != null ? last.getIgnition() : null,
                    last != null ? last.getMoving() : null,
                    at != null ? Duration.between(at, now).toSeconds() : null,
                    device != null
                            ? device.currentStatus(last != null && Boolean.TRUE.equals(last.getMoving()))
                            : GpsDeviceStatus.NEVER_SEEN,
                    device != null ? device.getId() : null,
                    a.getLocation() != null ? a.getLocation().getName() : null));
        }
        return out;
    }

    /** Trajeto de um ativo num intervalo, pronto a desenhar. */
    @Transactional(readOnly = true)
    public TrackView track(String orgId, String assetId, Instant from, Instant to) {
        requireAsset(orgId, assetId);
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        if (!start.isBefore(end)) {
            throw ApiException.badRequest("O início do período tem de ser anterior ao fim.");
        }

        List<GpsPosition> raw = positions.track(assetId, start, end);
        List<TrackPoint> track = raw.stream().map(TrackPoint::of).toList();

        BigDecimal maxSpeed = null;
        for (GpsPosition p : raw) {
            if (p.getSpeedKph() != null
                    && (maxSpeed == null || p.getSpeedKph().compareTo(maxSpeed) > 0)) {
                maxSpeed = p.getSpeedKph();
            }
        }
        return new TrackView(assetId, start, end, track.size(), distanceKm(raw), maxSpeed, track);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TripView> tripsForAsset(String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                trips.findByAssetIdOrderByStartedAtDesc(assetId, pageable).map(TripView::of));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TripView> tripsForOrg(String orgId, Pageable pageable) {
        return PagedResponse.of(
                trips.findByOrganizationIdOrderByStartedAtDesc(orgId, pageable).map(TripView::of));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AlertView> listAlerts(
            String orgId, String kind, boolean onlyOpen, Pageable pageable) {

        if (onlyOpen) {
            return PagedResponse.of(alerts
                    .findByOrganizationIdAndEndedAtIsNullOrderByStartedAtDesc(orgId, pageable)
                    .map(AlertView::of));
        }
        if (kind != null && !kind.isBlank()) {
            TelemetryAlertKind parsed;
            try {
                parsed = TelemetryAlertKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Tipo de alerta desconhecido: " + kind);
            }
            return PagedResponse.of(alerts
                    .findByOrganizationIdAndKindOrderByStartedAtDesc(orgId, parsed, pageable)
                    .map(AlertView::of));
        }
        return PagedResponse.of(
                alerts.findByOrganizationIdOrderByStartedAtDesc(orgId, pageable)
                        .map(AlertView::of));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AlertView> listAlertsForAsset(
            String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                alerts.findByAssetIdOrderByStartedAtDesc(assetId, pageable).map(AlertView::of));
    }

    @Transactional
    public AlertView acknowledgeAlert(String orgId, String userId, String alertId) {
        TelemetryAlert alert = alerts.findById(alertId)
                .filter(a -> a.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> ApiException.notFound("Alerta não encontrado."));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(Instant.now());
            alert.setAcknowledgedBy(users.getReferenceById(userId));
            audit.record(orgId, userId, "telemetry.alert_ack", "TelemetryAlert", alert.getId(),
                    alert.getKind() + " · " + alert.getMessage());
        }
        return AlertView.of(alert);
    }

    /** Fecha viagens de ativos que deixaram de comunicar (chamado pelo agendador). */
    @Transactional
    public int closeStaleTrips(String orgId) {
        return tripTracker.closeStale(orgId, Instant.now());
    }

    @Transactional(readOnly = true)
    public PagedResponse<PositionView> history(String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                positions.findByAssetIdOrderByRecordedAtDesc(assetId, pageable)
                        .map(PositionView::of));
    }

    // ==== Auxiliares ====================================================
    /**
     * Quilómetros de uma sequência de posições.
     *
     * <p>O odómetro do próprio aparelho tem prioridade: conta as voltas da roda
     * e não sofre do ruído do GPS. Somar linhas rectas entre pontos consecutivos
     * inflaciona o total (o erro de posição de um veículo parado acumula como se
     * fosse deslocação), por isso só se recorre a isso quando o aparelho não
     * comunica odómetro.
     */
    private BigDecimal distanceKm(List<GpsPosition> ordered) {
        BigDecimal fromOdometer = odometerDelta(ordered);
        if (fromOdometer != null) {
            return fromOdometer.setScale(3, RoundingMode.HALF_UP);
        }
        double meters = 0;
        for (int i = 1; i < ordered.size(); i++) {
            GpsPosition a = ordered.get(i - 1);
            GpsPosition b = ordered.get(i);
            meters += Geo.distanceMeters(
                    a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return BigDecimal.valueOf(meters / 1000.0).setScale(3, RoundingMode.HALF_UP);
    }

    /** Diferença entre o primeiro e o último odómetro comunicados, se existirem. */
    private BigDecimal odometerDelta(List<GpsPosition> ordered) {
        BigDecimal first = null;
        BigDecimal last = null;
        for (GpsPosition p : ordered) {
            if (p.getOdometerKm() == null) {
                continue;
            }
            if (first == null) {
                first = p.getOdometerKm();
            }
            last = p.getOdometerKm();
        }
        if (first == null || last == null || first.compareTo(last) == 0) {
            return null;
        }
        BigDecimal delta = last.subtract(first);
        // Odómetro a recuar significa aparelho substituído ou contador reposto:
        // nesse caso não dá para confiar e volta-se ao cálculo geométrico.
        return delta.signum() > 0 ? delta : null;
    }

    private LivePositionEvent liveEvent(Asset asset, GpsDevice device, GpsPosition p) {
        return new LivePositionEvent(
                asset.getId(), asset.getTag(), asset.getName(),
                p.getLatitude(), p.getLongitude(), p.getSpeedKph(), p.getHeading(),
                p.getIgnition(), p.getMoving(), p.getRecordedAt(), p.getTripId(),
                device != null ? device.getId() : null);
    }

    /** A posição atual do ativo só avança; pontos atrasados ficam só no histórico. */
    private void updateAssetPosition(Asset asset, GpsPosition p) {
        if (asset.getPositionAt() != null && p.getRecordedAt().isBefore(asset.getPositionAt())) {
            return;
        }
        asset.setLatitude(p.getLatitude());
        asset.setLongitude(p.getLongitude());
        asset.setPositionAt(p.getRecordedAt());
        asset.setPositionSource(p.getSource());
    }

    /**
     * Rejeita saltos que implicariam uma velocidade impossível. Compara-se com o
     * ponto anterior: se o aparelho esteve calado horas e reaparece longe, a
     * velocidade implícita é baixa e o ponto passa — é um salto genuíno de
     * cobertura, não ruído.
     *
     * @return a razão da rejeição, ou {@code null} se o ponto é plausível
     */
    private String impossibleJump(GpsPosition previous, PositionInput in, Instant recordedAt) {
        if (previous == null) {
            return null;
        }
        double seconds = Duration.between(previous.getRecordedAt(), recordedAt).toMillis() / 1000.0;
        if (seconds <= 0) {
            return null; // ponto fora de ordem: fica no histórico, não é ruído
        }
        double meters = Geo.distanceMeters(
                previous.getLatitude(), previous.getLongitude(), in.latitude(), in.longitude());
        double kph = meters / seconds * 3.6;
        if (kph > MAX_PLAUSIBLE_KPH) {
            return "Posição descartada: implicaria " + Math.round(kph)
                    + " km/h desde a leitura anterior.";
        }
        return null;
    }

    private BigDecimal resolveHeading(PositionInput in, GpsPosition previous) {
        if (in.heading() != null) {
            return in.heading();
        }
        if (previous == null) {
            return null;
        }
        double bearing = Geo.bearingDegrees(
                previous.getLatitude().doubleValue(), previous.getLongitude().doubleValue(),
                in.latitude().doubleValue(), in.longitude().doubleValue());
        return BigDecimal.valueOf(bearing).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Em movimento se a velocidade o disser; sem velocidade, deduz-se do que o
     * ativo andou desde o ponto anterior (mais de 30 m em menos de 10 minutos).
     */
    private Boolean isMoving(PositionInput in, GpsPosition previous, Instant recordedAt) {
        if (in.speedKph() != null) {
            return in.speedKph().compareTo(MOVING_SPEED_KPH) > 0;
        }
        if (previous == null) {
            return null;
        }
        Duration gap = Duration.between(previous.getRecordedAt(), recordedAt);
        if (gap.isNegative() || gap.toMinutes() > 10) {
            return null;
        }
        double meters = Geo.distanceMeters(
                previous.getLatitude(), previous.getLongitude(), in.latitude(), in.longitude());
        return meters > 30;
    }

    private void applyDevice(String orgId, GpsDevice d, SaveDeviceRequest req) {
        if (req.name() != null) d.setName(blankToNull(req.name()));
        if (req.model() != null) d.setModel(blankToNull(req.model()));
        if (req.simNumber() != null) d.setSimNumber(blankToNull(req.simNumber()));
        if (req.notes() != null) d.setNotes(blankToNull(req.notes()));
        if (req.fuelUnit() != null && !req.fuelUnit().isBlank()) {
            String u = req.fuelUnit().trim().toUpperCase();
            if (!u.equals("LITERS") && !u.equals("PERCENT")) {
                throw ApiException.badRequest("A unidade do sensor tem de ser LITERS ou PERCENT.");
            }
            d.setFuelUnit(u);
        }
        if (req.provider() != null) d.setProvider(req.provider());
        else if (d.getProvider() == null) d.setProvider(TelemetryProviderKind.GENERIC);

        if (req.assetId() != null) {
            if (req.assetId().isBlank()) {
                d.setAsset(null);
            } else {
                Asset asset = requireAsset(orgId, req.assetId());
                Optional<GpsDevice> other = devices.findFirstByAssetId(asset.getId());
                if (other.isPresent() && !other.get().getId().equals(d.getId())) {
                    throw ApiException.conflict(
                            "Este ativo já tem o aparelho " + other.get().getExternalId()
                                    + " instalado.");
                }
                d.setAsset(asset);
            }
        }
    }

    private String newIngestKey(GpsDevice d) {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes) + UUID.randomUUID().toString().replace("-", "");
        d.setIngestKeyHash(sha256(key));
        return key;
    }

    private GpsDevice requireDevice(String orgId, String id) {
        return devices.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Aparelho não encontrado."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static Instant orNow(Instant value) {
        return value != null ? value : Instant.EPOCH;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
