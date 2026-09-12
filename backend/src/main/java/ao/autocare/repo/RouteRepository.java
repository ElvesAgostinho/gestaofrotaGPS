package ao.autocare.repo;

import ao.autocare.domain.Route;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RouteRepository extends JpaRepository<Route, String> {

    Optional<Route> findByIdAndOrganizationId(String id, String organizationId);

    List<Route> findByOrganizationIdOrderByNameAsc(String organizationId);

    boolean existsByOrganizationIdAndCode(String organizationId, String code);

    @Query("""
            select r from Route r
            left join fetch r.waypoints
            where r.id = :id and r.organization.id = :organizationId
            """)
    Optional<Route> findWithWaypoints(String id, String organizationId);
}
