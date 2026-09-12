package ao.autocare.repo;

import ao.autocare.domain.DrivingEvent;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DrivingEventRepository extends JpaRepository<DrivingEvent, String> {

    Optional<DrivingEvent> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsBySourceAlertId(String sourceAlertId);

    Page<DrivingEvent> findByOrganizationIdOrderByOccurredAtDesc(
            String organizationId, Pageable pageable);

    List<DrivingEvent> findByTripIdOrderByOccurredAtAsc(String tripId);

    /** Infracoes que contam: as anuladas ficam de fora da pontuacao. */
    @Query("""
            select e from DrivingEvent e
            where e.driver.id = :driverId
              and e.dismissedAt is null
              and e.occurredAt >= :inicio and e.occurredAt < :fim
            order by e.occurredAt asc
            """)
    List<DrivingEvent> countingForDriver(String driverId, Instant inicio, Instant fim);

    @Query("""
            select e from DrivingEvent e
            where e.organization.id = :organizationId
              and e.dismissedAt is null
              and e.occurredAt >= :inicio and e.occurredAt < :fim
            order by e.occurredAt desc
            """)
    List<DrivingEvent> countingForOrganization(String organizationId, Instant inicio, Instant fim);

    @Query("""
            select count(e) from DrivingEvent e
            where e.organization.id = :organizationId
              and e.kind = :kind and e.dismissedAt is null
              and e.occurredAt >= :inicio
            """)
    long countByKindSince(String organizationId, DrivingEventKind kind, Instant inicio);
}
