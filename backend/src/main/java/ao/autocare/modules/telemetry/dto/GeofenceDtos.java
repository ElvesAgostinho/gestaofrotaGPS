package ao.autocare.modules.telemetry.dto;

import ao.autocare.domain.Geofence;
import ao.autocare.domain.GeofenceEvent;
import ao.autocare.domain.enums.Enums.GeofenceEventType;
import ao.autocare.domain.enums.Enums.GeofenceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedidos e respostas das geocercas. */
public final class GeofenceDtos {

    private GeofenceDtos() {}

    public record SaveGeofenceRequest(
            @NotBlank(message = "Dê um nome à área.") @Size(max = 160) String name,
            GeofenceKind kind,
            BigDecimal centerLatitude,
            BigDecimal centerLongitude,
            BigDecimal radiusM,
            /** Vértices {@code [[lat,lon],...]}, para áreas em polígono. */
            List<List<BigDecimal>> polygon,
            @Size(max = 20) String color,
            /** Limite de velocidade dentro da zona, km/h. Zero ou ausente = sem limite. */
            BigDecimal speedLimitKph,
            Boolean alertOnEnter,
            Boolean alertOnExit,
            Boolean active,
            String locationId,
            /** Ativos a que se aplica. Vazio ou ausente = toda a frota. */
            List<String> assetIds) {}

    public record GeofenceView(
            String id,
            String name,
            GeofenceKind kind,
            BigDecimal centerLatitude,
            BigDecimal centerLongitude,
            BigDecimal radiusM,
            List<List<BigDecimal>> polygon,
            String color,
            BigDecimal speedLimitKph,
            boolean alertOnEnter,
            boolean alertOnExit,
            boolean active,
            String locationId,
            String locationName,
            List<String> assetIds,
            boolean appliesToWholeFleet,
            int assetsInside) {

        public static GeofenceView of(
                Geofence g, List<List<BigDecimal>> polygon, List<String> assetIds, int inside) {
            return new GeofenceView(
                    g.getId(), g.getName(), g.getKind(),
                    g.getCenterLatitude(), g.getCenterLongitude(), g.getRadiusM(),
                    polygon, g.getColor(), g.getSpeedLimitKph(),
                    g.isAlertOnEnter(), g.isAlertOnExit(), g.isActive(),
                    g.getLocation() != null ? g.getLocation().getId() : null,
                    g.getLocation() != null ? g.getLocation().getName() : null,
                    assetIds, assetIds.isEmpty(), inside);
        }
    }

    public record GeofenceEventView(
            String id,
            String geofenceId,
            String geofenceName,
            String assetId,
            String assetTag,
            String assetName,
            GeofenceEventType eventType,
            Instant occurredAt,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean acknowledged) {

        public static GeofenceEventView of(GeofenceEvent e) {
            return new GeofenceEventView(
                    e.getId(), e.getGeofence().getId(), e.getGeofence().getName(),
                    e.getAsset().getId(), e.getAsset().getTag(), e.getAsset().getName(),
                    e.getEventType(), e.getOccurredAt(), e.getLatitude(), e.getLongitude(),
                    e.getAcknowledgedAt() != null);
        }
    }

    /** Quem está dentro de uma área agora, e desde quando. */
    public record PresenceView(
            String assetId, String assetTag, String assetName, Instant since) {}
}
