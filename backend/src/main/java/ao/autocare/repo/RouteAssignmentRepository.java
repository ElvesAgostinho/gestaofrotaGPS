package ao.autocare.repo;

import ao.autocare.domain.RouteAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RouteAssignmentRepository extends JpaRepository<RouteAssignment, String> {

    @Query("""
            select a from RouteAssignment a join fetch a.asset left join fetch a.driver
            where a.route.id = :routeId and a.active = true
            order by a.plannedFor desc nulls last, a.createdAt desc
            """)
    List<RouteAssignment> forRoute(String routeId);

    @Query("""
            select a from RouteAssignment a join fetch a.asset left join fetch a.driver join fetch a.route
            where a.organization.id = :orgId and a.active = true
            order by a.plannedFor desc nulls last, a.createdAt desc
            """)
    List<RouteAssignment> forOrganization(String orgId);

    /** As atribuições ativas de uma viatura, com a rota já carregada. */
    @Query("""
            select a from RouteAssignment a join fetch a.route left join fetch a.driver
            where a.asset.id = :assetId and a.active = true
            order by a.plannedFor desc nulls last, a.createdAt desc
            """)
    List<RouteAssignment> forAsset(String assetId);

    Optional<RouteAssignment> findByIdAndOrganizationId(String id, String organizationId);

    long countByRouteIdAndActiveTrue(String routeId);
}
