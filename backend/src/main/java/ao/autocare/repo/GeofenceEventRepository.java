package ao.autocare.repo;

import ao.autocare.domain.GeofenceEvent;
import ao.autocare.domain.enums.Enums.GeofenceEventType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceEventRepository extends JpaRepository<GeofenceEvent, String> {

    Page<GeofenceEvent> findByOrganizationIdOrderByOccurredAtDesc(
            String organizationId, Pageable pageable);

    Page<GeofenceEvent> findByAssetIdOrderByOccurredAtDesc(String assetId, Pageable pageable);

    /** Último evento deste par ativo/geocerca — diz se o ativo está dentro ou fora. */
    Optional<GeofenceEvent> findFirstByGeofenceIdAndAssetIdOrderByOccurredAtDesc(
            String geofenceId, String assetId);

    long countByOrganizationIdAndEventTypeAndAcknowledgedAtIsNull(
            String organizationId, GeofenceEventType type);
}
