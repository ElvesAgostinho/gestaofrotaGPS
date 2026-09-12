package ao.autocare.repo;

import ao.autocare.domain.OrgCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrgCounterRepository extends JpaRepository<OrgCounter, OrgCounter.Key> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrgCounter> findByOrganizationIdAndCounterKey(String organizationId, String counterKey);
}
