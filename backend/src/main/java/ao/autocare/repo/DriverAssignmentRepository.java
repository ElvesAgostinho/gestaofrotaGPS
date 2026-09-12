package ao.autocare.repo;

import ao.autocare.domain.DriverAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverAssignmentRepository extends JpaRepository<DriverAssignment, String> {

    Optional<DriverAssignment> findByIdAndOrganizationId(String id, String organizationId);

    List<DriverAssignment> findByAssetIdOrderByStartedAtDesc(String assetId);

    List<DriverAssignment> findByDriverIdOrderByStartedAtDesc(String driverId);

    /** Atribuicoes em vigor de um ativo. */
    @Query("""
            select a from DriverAssignment a
            join fetch a.driver
            where a.asset.id = :assetId and a.endedAt is null
            order by a.primaryDriver desc, a.startedAt desc
            """)
    List<DriverAssignment> openForAsset(String assetId);

    /** Atribuicoes em vigor de um motorista. */
    @Query("""
            select a from DriverAssignment a
            join fetch a.asset
            where a.driver.id = :driverId and a.endedAt is null
            order by a.startedAt desc
            """)
    List<DriverAssignment> openForDriver(String driverId);

    /**
     * Quem conduzia este ativo neste instante.
     *
     * <p>O condutor titular vem primeiro: quando ha titular e ajudante ao mesmo
     * tempo, e ao titular que se imputa o que aconteceu.
     */
    @Query("""
            select a from DriverAssignment a
            join fetch a.driver
            where a.asset.id = :assetId
              and a.startedAt <= :momento
              and (a.endedAt is null or a.endedAt > :momento)
            order by a.primaryDriver desc, a.startedAt desc
            """)
    List<DriverAssignment> coveringAt(String assetId, Instant momento);

    @Query("""
            select a from DriverAssignment a
            where a.organization.id = :organizationId and a.endedAt is null
            """)
    List<DriverAssignment> allOpen(String organizationId);
}
