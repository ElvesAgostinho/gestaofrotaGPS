package ao.autocare.modules.location;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Location;
import ao.autocare.domain.enums.Enums.LocationKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.location.dto.LocationDtos.CreateLocationRequest;
import ao.autocare.modules.location.dto.LocationDtos.LocationNode;
import ao.autocare.modules.location.dto.LocationDtos.LocationView;
import ao.autocare.modules.location.dto.LocationDtos.UpdateLocationRequest;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {

    private final LocationRepository locations;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public LocationService(
            LocationRepository locations,
            AssetRepository assets,
            OrganizationRepository organizations,
            AuditService audit) {
        this.locations = locations;
        this.assets = assets;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<LocationView> list(String orgId) {
        return locations.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(l -> LocationView.of(l, assets.countByLocationId(l.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationNode> tree(String orgId) {
        List<Location> all = locations.findByOrganizationIdOrderByNameAsc(orgId);
        Map<String, LocationNode> nodes = new LinkedHashMap<>();
        for (Location l : all) {
            nodes.put(l.getId(), LocationNode.of(l));
        }
        List<LocationNode> roots = new ArrayList<>();
        for (Location l : all) {
            LocationNode node = nodes.get(l.getId());
            Location parent = l.getParent();
            if (parent != null && nodes.containsKey(parent.getId())) {
                nodes.get(parent.getId()).children().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public LocationView get(String orgId, String id) {
        Location l = load(orgId, id);
        return LocationView.of(l, assets.countByLocationId(l.getId()));
    }

    @Transactional
    public LocationView create(String orgId, String userId, CreateLocationRequest req) {
        Location l = new Location();
        l.setOrganization(organizations.getReferenceById(orgId));
        l.setName(req.name().trim());
        l.setCode(trimToNull(req.code()));
        l.setKind(req.kind() != null ? req.kind() : LocationKind.SITE);
        l.setNotes(trimToNull(req.notes()));
        l.setLatitude(req.latitude());
        l.setLongitude(req.longitude());
        l.setCostCenter(trimToNull(req.costCenter()));
        l.setManagerUserId(trimToNull(req.managerUserId()));
        l.setAddress(trimToNull(req.address()));
        l.setCity(trimToNull(req.city()));
        l.setProvince(trimToNull(req.province()));
        l.setPhone(trimToNull(req.phone()));
        l.setRadiusMeters(req.radiusMeters());
        if (req.parentId() != null && !req.parentId().isBlank()) {
            l.setParent(load(orgId, req.parentId()));
        }
        locations.save(l);
        audit.record(orgId, userId, "location.create", "Location", l.getId(), l.getName());
        return LocationView.of(l, 0);
    }

    @Transactional
    public LocationView update(String orgId, String userId, String id, UpdateLocationRequest req) {
        Location l = load(orgId, id);
        verificarVersao(req.version(), l.getVersion());
        if (req.name() != null && !req.name().isBlank()) l.setName(req.name().trim());
        if (req.code() != null) l.setCode(trimToNull(req.code()));
        if (req.kind() != null) l.setKind(req.kind());
        if (req.notes() != null) l.setNotes(trimToNull(req.notes()));
        if (req.active() != null) l.setActive(req.active());
        if (req.latitude() != null) l.setLatitude(req.latitude());
        if (req.longitude() != null) l.setLongitude(req.longitude());
        if (req.costCenter() != null) l.setCostCenter(trimToNull(req.costCenter()));
        if (req.managerUserId() != null) l.setManagerUserId(trimToNull(req.managerUserId()));
        if (req.address() != null) l.setAddress(trimToNull(req.address()));
        if (req.city() != null) l.setCity(trimToNull(req.city()));
        if (req.province() != null) l.setProvince(trimToNull(req.province()));
        if (req.phone() != null) l.setPhone(trimToNull(req.phone()));
        if (req.radiusMeters() != null) l.setRadiusMeters(req.radiusMeters());
        if (req.parentId() != null) {
            if (req.parentId().isBlank()) {
                l.setParent(null);
            } else {
                Location parent = load(orgId, req.parentId());
                guardAgainstCycle(l, parent);
                l.setParent(parent);
            }
        }
        audit.record(orgId, userId, "location.update", "Location", l.getId(), l.getName());
        return LocationView.of(l, assets.countByLocationId(l.getId()));
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        Location l = load(orgId, id);
        if (locations.existsByOrganizationIdAndParentId(orgId, id)) {
            throw ApiException.conflict("Este local tem sublocais. Remova-os primeiro.");
        }
        if (assets.countByLocationId(id) > 0) {
            throw ApiException.conflict(
                    "Este local tem ativos associados. Mova-os para outro local primeiro.");
        }
        locations.delete(l);
        audit.record(orgId, userId, "location.delete", "Location", id, l.getName());
    }

    // ------------------------------------------------------------------
    private void guardAgainstCycle(Location location, Location candidateParent) {
        Location cursor = candidateParent;
        while (cursor != null) {
            if (cursor.getId().equals(location.getId())) {
                throw ApiException.badRequest("Não pode mover um local para dentro de si próprio.");
            }
            cursor = cursor.getParent();
        }
    }

    private Location load(String orgId, String id) {
        return locations.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Local não encontrado."));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * A versão que o ecrã leu tem de ser a que está na base de dados.
     *
     * <p>Sem isto, o {@code @Version} só apanha colisões entre transações
     * simultâneas. O caso real — duas pessoas com a mesma ficha aberta durante
     * minutos — só se apanha comparando a versão que o ecrã devolve.
     */
    private static void verificarVersao(Long lida, long atual) {
        if (lida != null && lida != atual) {
            throw ApiException.conflict(
                    ao.autocare.common.GlobalExceptionHandler.MENSAGEM_VERSAO);
        }
    }
}
