package ao.autocare.modules.telemetry;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Geofence;
import ao.autocare.domain.GeofenceAsset;
import ao.autocare.domain.GeofenceEvent;
import ao.autocare.domain.GeofencePresence;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.enums.Enums.GeofenceEventType;
import ao.autocare.domain.enums.Enums.GeofenceKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.GeofenceEventView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.GeofenceView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.PresenceView;
import ao.autocare.modules.telemetry.dto.GeofenceDtos.SaveGeofenceRequest;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.GeofenceEventRepository;
import ao.autocare.repo.GeofencePresenceRepository;
import ao.autocare.repo.GeofenceRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Áreas no mapa e deteção de entradas e saídas.
 *
 * <p>A avaliação é feita em memória a cada posição recebida. Com uma frota de
 * centenas de ativos e dezenas de áreas, o custo aritmético é irrelevante
 * (algumas centenas de nanossegundos por área); o que pesaria seriam idas à
 * base de dados, por isso cada posição faz <b>uma única</b> consulta — as
 * presenças do ativo — e só escreve quando o estado muda. Uma extensão espacial
 * como o PostGIS só compensaria com milhares de áreas ou polígonos complexos.
 */
@Service
public class GeofenceService {

    private final GeofenceRepository geofences;
    private final GeofencePresenceRepository presences;
    private final GeofenceEventRepository events;
    private final AssetRepository assets;
    private final LocationRepository locations;
    private final OrganizationRepository organizations;
    private final ObjectMapper json;
    private final AuditService audit;
    private final NotificationService notifications;

    public GeofenceService(
            GeofenceRepository geofences,
            GeofencePresenceRepository presences,
            GeofenceEventRepository events,
            AssetRepository assets,
            LocationRepository locations,
            OrganizationRepository organizations,
            ObjectMapper json,
            AuditService audit,
            NotificationService notifications) {
        this.geofences = geofences;
        this.presences = presences;
        this.events = events;
        this.assets = assets;
        this.locations = locations;
        this.organizations = organizations;
        this.json = json;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ==== Avaliação =====================================================
    /**
     * Resultado de avaliar uma posição.
     *
     * @param events os eventos criados (normalmente nenhum)
     * @param inside as áreas que contêm o ponto — servem para aplicar o limite
     *               de velocidade da zona sem repetir a geometria
     */
    public record Evaluation(List<GeofenceEvent> events, List<Geofence> inside) {
        static Evaluation empty() {
            return new Evaluation(List.of(), List.of());
        }
    }

    /** Verifica a posição contra todas as áreas ativas e regista as mudanças. */
    @Transactional
    public Evaluation evaluate(Asset asset, GpsPosition p) {
        if (asset == null) {
            return Evaluation.empty();
        }
        String orgId = asset.getOrganization().getId();
        List<Geofence> active = geofences.findActiveWithAssets(orgId);
        if (active.isEmpty()) {
            return Evaluation.empty();
        }

        Map<String, GeofencePresence> byGeofence = new HashMap<>();
        for (GeofencePresence presence : presences.findByAssetId(asset.getId())) {
            byGeofence.put(presence.getGeofence().getId(), presence);
        }

        List<GeofenceEvent> created = new ArrayList<>();
        List<Geofence> containing = new ArrayList<>();
        for (Geofence g : active) {
            if (!appliesTo(g, asset)) {
                continue;
            }
            boolean inside = contains(g, p.getLatitude(), p.getLongitude());
            if (inside) {
                containing.add(g);
            }
            GeofencePresence presence = byGeofence.get(g.getId());

            if (presence == null) {
                // Primeira vez que vemos este ativo nesta área: fica registado o
                // estado, mas não se inventa uma entrada que não observámos.
                presences.save(newPresence(asset, g, inside, p.getRecordedAt()));
                continue;
            }
            if (presence.isInside() == inside) {
                continue;
            }
            presence.setInside(inside);
            presence.setSince(p.getRecordedAt());

            boolean wanted = inside ? g.isAlertOnEnter() : g.isAlertOnExit();
            if (wanted) {
                GeofenceEvent event = events.save(newEvent(asset, g, p, inside));
                created.add(event);
                notifications.notifyManagers(NotificationService.Draft.of(
                                orgId,
                                ao.autocare.domain.enums.Enums.AlertCategory.GPS,
                                ao.autocare.domain.enums.Enums.AlertSeverity.INFO,
                                asset.getTag() + (inside ? " entrou em " : " saiu de ") + g.getName(),
                                (inside ? "Entrada" : "Saída") + " registada em "
                                        + p.getRecordedAt(),
                                "geofence_event", event.getId(),
                                "/frota/geocercas/" + g.getId())
                        .forAsset(asset));
            }
        }
        return new Evaluation(created, containing);
    }

    /** Sem ativos associados, a área vale para toda a frota. */
    private boolean appliesTo(Geofence g, Asset asset) {
        if (g.getAssets().isEmpty()) {
            return true;
        }
        return g.getAssets().stream()
                .anyMatch(link -> link.getAsset().getId().equals(asset.getId()));
    }

    /** Ponto dentro da área, conforme a sua forma. */
    public boolean contains(Geofence g, BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();

        if (g.getKind() == GeofenceKind.CIRCLE) {
            if (g.getCenterLatitude() == null || g.getCenterLongitude() == null
                    || g.getRadiusM() == null) {
                return false;
            }
            return Geo.insideCircle(lat, lon,
                    g.getCenterLatitude().doubleValue(), g.getCenterLongitude().doubleValue(),
                    g.getRadiusM().doubleValue());
        }
        return Geo.insidePolygon(lat, lon, readPolygon(g.getPolygon()));
    }

    // ==== CRUD ==========================================================
    @Transactional(readOnly = true)
    public List<GeofenceView> list(String orgId) {
        return geofences.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public GeofenceView get(String orgId, String id) {
        return toView(require(orgId, id));
    }

    @Transactional
    public GeofenceView create(String orgId, String userId, SaveGeofenceRequest req) {
        Geofence g = new Geofence();
        g.setOrganization(organizations.getReferenceById(orgId));
        g.setKind(req.kind() != null ? req.kind() : GeofenceKind.CIRCLE);
        apply(orgId, g, req);
        geofences.save(g);
        audit.record(orgId, userId, "geofence.create", "Geofence", g.getId(), g.getName());
        return toView(g);
    }

    @Transactional
    public GeofenceView update(String orgId, String userId, String id, SaveGeofenceRequest req) {
        Geofence g = require(orgId, id);
        if (req.kind() != null) {
            g.setKind(req.kind());
        }
        apply(orgId, g, req);
        audit.record(orgId, userId, "geofence.update", "Geofence", g.getId(), g.getName());
        return toView(g);
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        Geofence g = require(orgId, id);
        String name = g.getName();
        geofences.delete(g);
        audit.record(orgId, userId, "geofence.delete", "Geofence", id, name);
    }

    @Transactional(readOnly = true)
    public List<PresenceView> inside(String orgId, String geofenceId) {
        require(orgId, geofenceId);
        return presences.findInside(geofenceId).stream()
                .map(p -> new PresenceView(
                        p.getAsset().getId(), p.getAsset().getTag(), p.getAsset().getName(),
                        p.getSince()))
                .toList();
    }

    // ==== Eventos =======================================================
    @Transactional(readOnly = true)
    public PagedResponse<GeofenceEventView> listEvents(String orgId, Pageable pageable) {
        return PagedResponse.of(
                events.findByOrganizationIdOrderByOccurredAtDesc(orgId, pageable)
                        .map(GeofenceEventView::of));
    }

    @Transactional(readOnly = true)
    public PagedResponse<GeofenceEventView> listEventsForAsset(
            String orgId, String assetId, Pageable pageable) {
        requireAsset(orgId, assetId);
        return PagedResponse.of(
                events.findByAssetIdOrderByOccurredAtDesc(assetId, pageable)
                        .map(GeofenceEventView::of));
    }

    @Transactional
    public GeofenceEventView acknowledge(String orgId, String userId, String eventId) {
        GeofenceEvent e = events.findById(eventId)
                .filter(ev -> ev.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> ApiException.notFound("Evento não encontrado."));
        if (e.getAcknowledgedAt() == null) {
            e.setAcknowledgedAt(Instant.now());
            audit.record(orgId, userId, "geofence.event_ack", "GeofenceEvent", e.getId(),
                    e.getGeofence().getName() + " · " + e.getAsset().getTag());
        }
        return GeofenceEventView.of(e);
    }

    // ==== Auxiliares ====================================================
    private void apply(String orgId, Geofence g, SaveGeofenceRequest req) {
        if (req.name() != null && !req.name().isBlank()) {
            g.setName(req.name().trim());
        }
        if (req.centerLatitude() != null) g.setCenterLatitude(req.centerLatitude());
        if (req.centerLongitude() != null) g.setCenterLongitude(req.centerLongitude());
        if (req.radiusM() != null) g.setRadiusM(req.radiusM());
        if (req.speedLimitKph() != null) {
            g.setSpeedLimitKph(req.speedLimitKph().signum() > 0
                    ? req.speedLimitKph() : null);
        }
        if (req.color() != null) g.setColor(blankToNull(req.color()));
        if (req.alertOnEnter() != null) g.setAlertOnEnter(req.alertOnEnter());
        if (req.alertOnExit() != null) g.setAlertOnExit(req.alertOnExit());
        if (req.active() != null) g.setActive(req.active());
        if (req.polygon() != null) {
            g.setPolygon(writePolygon(req.polygon()));
        }
        if (req.locationId() != null) {
            g.setLocation(req.locationId().isBlank() ? null
                    : locations.findByIdAndOrganizationId(req.locationId(), orgId)
                            .orElseThrow(() -> ApiException.notFound("Local não encontrado.")));
        }
        if (req.assetIds() != null) {
            g.getAssets().clear();
            for (String assetId : req.assetIds()) {
                GeofenceAsset link = new GeofenceAsset();
                link.setGeofence(g);
                link.setAsset(requireAsset(orgId, assetId));
                g.getAssets().add(link);
            }
        }
        validate(g);
    }

    /** Uma área sem geometria utilizável nunca dispararia nada — recusa-se já. */
    private void validate(Geofence g) {
        if (g.getKind() == GeofenceKind.CIRCLE) {
            if (g.getCenterLatitude() == null || g.getCenterLongitude() == null) {
                throw ApiException.badRequest("Indique o centro da área (latitude e longitude).");
            }
            if (g.getRadiusM() == null || g.getRadiusM().signum() <= 0) {
                throw ApiException.badRequest("Indique um raio maior do que zero.");
            }
            if (!Geo.isValid(g.getCenterLatitude(), g.getCenterLongitude())) {
                throw ApiException.badRequest("As coordenadas do centro não são válidas.");
            }
        } else if (readPolygon(g.getPolygon()).size() < 3) {
            throw ApiException.badRequest("Um polígono precisa de pelo menos três pontos.");
        }
    }

    private GeofencePresence newPresence(Asset asset, Geofence g, boolean inside, Instant at) {
        GeofencePresence presence = new GeofencePresence();
        presence.setOrganization(asset.getOrganization());
        presence.setGeofence(g);
        presence.setAsset(asset);
        presence.setInside(inside);
        presence.setSince(at);
        return presence;
    }

    private GeofenceEvent newEvent(Asset asset, Geofence g, GpsPosition p, boolean inside) {
        GeofenceEvent e = new GeofenceEvent();
        e.setOrganization(asset.getOrganization());
        e.setGeofence(g);
        e.setAsset(asset);
        e.setEventType(inside ? GeofenceEventType.ENTER : GeofenceEventType.EXIT);
        e.setOccurredAt(p.getRecordedAt());
        e.setLatitude(p.getLatitude());
        e.setLongitude(p.getLongitude());
        return e;
    }

    private GeofenceView toView(Geofence g) {
        List<String> assetIds = g.getAssets().stream()
                .map(link -> link.getAsset().getId()).toList();
        int inside = presences.findInside(g.getId()).size();
        return GeofenceView.of(g, readPolygonAsBigDecimals(g.getPolygon()), assetIds, inside);
    }

    /** Vértices guardados como JSON {@code [[lat,lon],...]} em TEXT. */
    private List<Geo.Point> readPolygon(String stored) {
        List<List<BigDecimal>> raw = readPolygonAsBigDecimals(stored);
        List<Geo.Point> points = new ArrayList<>(raw.size());
        for (List<BigDecimal> pair : raw) {
            if (pair != null && pair.size() >= 2 && pair.get(0) != null && pair.get(1) != null) {
                points.add(new Geo.Point(pair.get(0).doubleValue(), pair.get(1).doubleValue()));
            }
        }
        return points;
    }

    private List<List<BigDecimal>> readPolygonAsBigDecimals(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<List<BigDecimal>>>() {});
        } catch (Exception e) {
            // Conteúdo corrompido não deve rebentar a listagem de áreas.
            return List.of();
        }
    }

    private String writePolygon(List<List<BigDecimal>> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(polygon);
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler os pontos do polígono.");
        }
    }

    private Geofence require(String orgId, String id) {
        return geofences.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Área não encontrada."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
