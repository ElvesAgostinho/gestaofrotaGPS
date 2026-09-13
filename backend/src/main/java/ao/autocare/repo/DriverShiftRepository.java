package ao.autocare.repo;

import ao.autocare.domain.DriverShift;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverShiftRepository extends JpaRepository<DriverShift, String> {

    Optional<DriverShift> findByIdAndOrganizationId(String id, String organizationId);

    @Query("select s from DriverShift s where s.organization.id = :organizationId "
            + "and s.startsAt < :to and s.endsAt > :from order by s.startsAt asc")
    List<DriverShift> inWindow(String organizationId, Instant from, Instant to);

    @Query("select s from DriverShift s where s.driver.id = :driverId "
            + "and s.startsAt < :to and s.endsAt > :from order by s.startsAt asc")
    List<DriverShift> forDriverInWindow(String driverId, Instant from, Instant to);
}
