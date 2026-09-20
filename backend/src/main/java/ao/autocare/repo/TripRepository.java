package ao.autocare.repo;

import ao.autocare.domain.Trip;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<Trip, String> {

    Optional<Trip> findFirstByAssetIdAndEndedAtIsNullOrderByStartedAtDesc(String assetId);

    Page<Trip> findByAssetIdOrderByStartedAtDesc(String assetId, Pageable pageable);

    /** As viagens que fizeram uma rota, da mais recente para a mais antiga. */
    @Query("select t from Trip t join fetch t.asset where t.route.id = :routeId order by t.startedAt desc")
    List<Trip> findByRouteIdOrderByStartedAtDesc(String routeId, Pageable pageable);

    Page<Trip> findByOrganizationIdOrderByStartedAtDesc(String organizationId, Pageable pageable);

    List<Trip> findByOrganizationIdAndEndedAtIsNull(String organizationId);

    /** Viagens fechadas de um motorista no periodo -- base da pontuacao. */
    @org.springframework.data.jpa.repository.Query("""
            select t from Trip t
            where t.driver.id = :driverId
              and t.endedAt is not null
              and t.startedAt >= :inicio and t.startedAt < :fim
            order by t.startedAt asc
            """)
    List<Trip> findByDriverIdBetween(
            String driverId, java.time.Instant inicio, java.time.Instant fim);

    @org.springframework.data.jpa.repository.Query("""
            select t from Trip t
            where t.organization.id = :organizationId
              and t.endedAt is not null
              and t.startedAt >= :inicio and t.startedAt < :fim
            order by t.startedAt desc
            """)
    List<Trip> findClosedBetween(
            String organizationId, java.time.Instant inicio, java.time.Instant fim);

    /** Quilómetros percorridos por um ativo num período. */
    @Query("""
            select coalesce(sum(t.distanceKm), 0) from Trip t
            where t.asset.id = :assetId and t.startedAt >= :from and t.startedAt < :to
            """)
    java.math.BigDecimal distanceForAsset(String assetId, Instant from, Instant to);
}
