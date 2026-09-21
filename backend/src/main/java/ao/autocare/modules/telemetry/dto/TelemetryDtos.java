package ao.autocare.modules.telemetry.dto;

import ao.autocare.domain.Asset;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.GpsDeviceStatus;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import ao.autocare.domain.enums.Enums.TelemetryProviderKind;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedidos e respostas da telemetria / GPS. */
public final class TelemetryDtos {

    private TelemetryDtos() {}

    // ---- aparelhos ----------------------------------------------------
    public record SaveDeviceRequest(
            @NotBlank(message = "Indique o identificador do aparelho (IMEI ou id do fornecedor).")
            @Size(max = 120) String externalId,
            @Size(max = 160) String name,
            @Size(max = 120) String model,
            @Size(max = 40) String simNumber,
            TelemetryProviderKind provider,
            String assetId,
            @Size(max = 500) String notes,
            /** LITERS ou PERCENT. Ausente: não mexe (LITERS ao criar). */
            String fuelUnit) {}

    public record DeviceView(
            String id,
            String externalId,
            String name,
            String model,
            String simNumber,
            TelemetryProviderKind provider,
            GpsDeviceStatus status,
            Instant lastSeenAt,
            Integer batteryPercent,
            String assetId,
            String assetTag,
            String assetName,
            String notes,
            String fuelUnit) {

        public static DeviceView of(GpsDevice d) {
            Asset a = d.getAsset();
            return new DeviceView(
                    d.getId(), d.getExternalId(), d.getName(), d.getModel(), d.getSimNumber(),
                    d.getProvider(), d.currentStatus(), d.getLastSeenAt(),
                    d.getBatteryPercent(),
                    a != null ? a.getId() : null,
                    a != null ? a.getTag() : null,
                    a != null ? a.getName() : null,
                    d.getNotes(), d.getFuelUnit());
        }
    }

    /**
     * Resposta ao criar um aparelho. A {@code ingestKey} é mostrada uma única
     * vez — só o seu SHA-256 fica guardado.
     */
    public record DeviceCreated(DeviceView device, String ingestKey, String ingestUrl,
            /** O que aconteceu do lado do Traccar: registado, já existia, ou o que falta fazer. */
            String traccarNote) {

        public DeviceCreated(DeviceView device, String ingestKey, String ingestUrl) {
            this(device, ingestKey, ingestUrl, null);
        }
    }

    // ---- ingestão de posições ------------------------------------------
    public record PositionInput(
            @NotNull(message = "Indique a latitude.")
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal latitude,
            @NotNull(message = "Indique a longitude.")
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal longitude,
            Instant recordedAt,
            BigDecimal speedKph,
            BigDecimal heading,
            BigDecimal altitudeM,
            BigDecimal accuracyM,
            Integer satellites,
            Boolean ignition,
            Integer batteryPercent,
            /** Contadores do aparelho — alimentam os medidores do ativo. */
            BigDecimal odometerKm,
            BigDecimal engineHours,
            /** Nível do depósito pelo sensor: litros, ou percentagem se o aparelho só der isso. */
            BigDecimal fuelLevelLiters,
            BigDecimal fuelLevelPercent,
            BigDecimal totalDistanceKm,
            /** Id da posição no fornecedor, para não entrar duas vezes. */
            String providerPositionId) {

        /** Construtor antigo: os aparelhos que publicam JSON não têm sensor. */
        public PositionInput(BigDecimal latitude, BigDecimal longitude, Instant recordedAt,
                BigDecimal speedKph, BigDecimal heading, BigDecimal altitudeM, BigDecimal accuracyM,
                Integer satellites, Boolean ignition, Integer batteryPercent,
                BigDecimal odometerKm, BigDecimal engineHours) {
            this(latitude, longitude, recordedAt, speedKph, heading, altitudeM, accuracyM,
                    satellites, ignition, batteryPercent, odometerKm, engineHours,
                    null, null, null, null);
        }
    }

    /** Publicação feita pelo aparelho: identifica-se pelo id externo + chave. */
    public record IngestRequest(
            @NotBlank(message = "Falta o identificador do aparelho.")
            @Size(max = 120) String deviceId,
            @NotBlank(message = "Falta a chave de publicação.") String key,
            @NotNull PositionInput position) {}

    public record IngestResult(
            boolean accepted,
            String reason,
            String positionId,
            String assetId,
            String tripId,
            int geofenceEvents) {}

    public record PositionView(
            String id,
            Instant recordedAt,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKph,
            BigDecimal heading,
            BigDecimal altitudeM,
            Integer satellites,
            Boolean ignition,
            Boolean moving,
            BigDecimal odometerKm,
            BigDecimal engineHours,
            String tripId) {

        public static PositionView of(GpsPosition p) {
            return new PositionView(
                    p.getId(), p.getRecordedAt(), p.getLatitude(), p.getLongitude(),
                    p.getSpeedKph(), p.getHeading(), p.getAltitudeM(), p.getSatellites(),
                    p.getIgnition(), p.getMoving(), p.getOdometerKm(), p.getEngineHours(),
                    p.getTripId());
        }
    }

    /**
     * Uma linha do mapa ao vivo: onde está cada ativo agora e há quanto tempo
     * não dá notícias.
     */
    public record LiveAssetView(
            String assetId,
            String tag,
            String name,
            String category,
            /** A família do catálogo: o mapa desenha o veículo certo, não um camião para tudo. */
            String family,
            String status,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant positionAt,
            BigDecimal speedKph,
            BigDecimal heading,
            Boolean ignition,
            Boolean moving,
            Long secondsSincePosition,
            GpsDeviceStatus deviceStatus,
            String deviceId,
            String locationName) {}

    /**
     * O que segue pelo fluxo em tempo real a cada posição aceite. Deliberadamente
     * pequeno: é enviado a todos os mapas abertos da empresa.
     */
    public record LivePositionEvent(
            String assetId,
            String tag,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKph,
            BigDecimal heading,
            Boolean ignition,
            Boolean moving,
            Instant recordedAt,
            String tripId,
            String deviceId) {}

    /**
     * Um alerta de telemetria. {@code open} distingue o que ainda está a
     * acontecer (excesso a decorrer, aparelho ainda calado) do que já terminou.
     */
    public record AlertView(
            String id,
            TelemetryAlertKind kind,
            String assetId,
            String assetTag,
            String assetName,
            String deviceId,
            String geofenceId,
            String geofenceName,
            Instant startedAt,
            Instant endedAt,
            BigDecimal limitValue,
            BigDecimal peakValue,
            BigDecimal latitude,
            BigDecimal longitude,
            String message,
            boolean open,
            boolean acknowledged) {

        public static AlertView of(ao.autocare.domain.TelemetryAlert a) {
            return new AlertView(
                    a.getId(), a.getKind(),
                    a.getAsset() != null ? a.getAsset().getId() : null,
                    a.getAsset() != null ? a.getAsset().getTag() : null,
                    a.getAsset() != null ? a.getAsset().getName() : null,
                    a.getDevice() != null ? a.getDevice().getId() : null,
                    a.getGeofence() != null ? a.getGeofence().getId() : null,
                    a.getGeofence() != null ? a.getGeofence().getName() : null,
                    a.getStartedAt(), a.getEndedAt(),
                    a.getLimitValue(), a.getPeakValue(),
                    a.getLatitude(), a.getLongitude(), a.getMessage(),
                    a.isOpen(), a.getAcknowledgedAt() != null);
        }
    }

    /** Bilhete de curta duração para abrir o fluxo em tempo real. */
    public record StreamTicketView(String ticket, String url, int expiresInSeconds) {}

    /** Ponto simplificado do trajeto — só o que o mapa precisa de desenhar. */
    public record TrackPoint(
            Instant at, BigDecimal latitude, BigDecimal longitude,
            BigDecimal speedKph, BigDecimal heading) {

        public static TrackPoint of(GpsPosition p) {
            return new TrackPoint(p.getRecordedAt(), p.getLatitude(), p.getLongitude(),
                    p.getSpeedKph(), p.getHeading());
        }
    }

    public record TrackView(
            String assetId, Instant from, Instant to,
            int points, BigDecimal distanceKm, BigDecimal maxSpeedKph,
            java.util.List<TrackPoint> track) {}

    /** Uma deslocação fechada (ou ainda a decorrer, com {@code endedAt} nulo). */
    public record TripView(
            String id,
            String assetId,
            String assetTag,
            Instant startedAt,
            Instant endedAt,
            BigDecimal startLatitude,
            BigDecimal startLongitude,
            BigDecimal endLatitude,
            BigDecimal endLongitude,
            BigDecimal distanceKm,
            BigDecimal maxSpeedKph,
            Integer durationMinutes,
            int positionCount,
            boolean open,
            /**
             * Nome do sitio, quando bate certo com uma filial, local ou
             * geocerca da empresa. Nulo quando nao bate: o ecra mostra entao as
             * coordenadas, que e a verdade, em vez de um nome aproximado.
             */
            String startPlaceName,
            String startPlaceKind,
            String endPlaceName,
            String endPlaceKind,
            String driverId,
            String driverName,
            String routeId,
            String routeName,
            int idleMinutes,
            int nightMinutes,
            int harshBrakeCount,
            int harshAccelCount,
            int harshCornerCount,
            int overspeedCount,
            int eventCount,
            String purpose) {

        public static TripView of(ao.autocare.domain.Trip t) {
            return new TripView(
                    t.getId(), t.getAsset().getId(), t.getAsset().getTag(),
                    t.getStartedAt(), t.getEndedAt(),
                    t.getStartLatitude(), t.getStartLongitude(),
                    t.getEndLatitude(), t.getEndLongitude(),
                    t.getDistanceKm(), t.getMaxSpeedKph(), t.getDurationMinutes(),
                    t.getPositionCount(), t.isOpen(),
                    t.getStartPlaceName(),
                    t.getStartPlaceKind() != null ? t.getStartPlaceKind().name() : null,
                    t.getEndPlaceName(),
                    t.getEndPlaceKind() != null ? t.getEndPlaceKind().name() : null,
                    t.getDriver() != null ? t.getDriver().getId() : null,
                    t.getDriver() != null ? t.getDriver().getName() : null,
                    t.getRoute() != null ? t.getRoute().getId() : null,
                    t.getRoute() != null ? t.getRoute().getName() : null,
                    t.getIdleMinutes(), t.getNightMinutes(),
                    t.getHarshBrakeCount(), t.getHarshAccelCount(), t.getHarshCornerCount(),
                    t.getOverspeedCount(), t.getEventCount(), t.getPurpose());
        }
    }

    /** Posição introduzida à mão (sem aparelho instalado). */
    public record ManualPositionRequest(
            @NotNull(message = "Indique a latitude.") BigDecimal latitude,
            @NotNull(message = "Indique a longitude.") BigDecimal longitude,
            Instant recordedAt,
            @Size(max = 200) String note) {}
}
