package ao.autocare.repo;

import ao.autocare.domain.Geofence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeofenceRepository extends JpaRepository<Geofence, String> {

    List<Geofence> findByOrganizationIdOrderByNameAsc(String organizationId);

    /** Cercas ativas com as ligações a ativos já carregadas (evita N+1 na avaliação). */
    @Query("select distinct g from Geofence g left join fetch g.assets a left join fetch a.asset "
            + "where g.organization.id = :organizationId and g.active = true")
    List<Geofence> findActiveWithAssets(String organizationId);

    Optional<Geofence> findByIdAndOrganizationId(String id, String organizationId);
}
