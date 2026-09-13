package ao.autocare.repo;

import ao.autocare.domain.DriverInfraction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverInfractionRepository extends JpaRepository<DriverInfraction, String> {

    List<DriverInfraction> findByDriverIdOrderByOccurredAtDesc(String driverId);

    Optional<DriverInfraction> findByIdAndOrganizationId(String id, String organizationId);

    List<DriverInfraction> findByOrganizationIdAndOccurredAtAfterOrderByOccurredAtDesc(
            String organizationId, Instant since);

    @Query("select coalesce(sum(i.points), 0) from DriverInfraction i "
            + "where i.driver.id = :driverId and i.occurredAt >= :since")
    int pointsSince(String driverId, Instant since);
}
