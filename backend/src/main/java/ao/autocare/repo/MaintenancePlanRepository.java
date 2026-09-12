package ao.autocare.repo;

import ao.autocare.domain.MaintenancePlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, String> {

    List<MaintenancePlan> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<MaintenancePlan> findByIdAndOrganizationId(String id, String organizationId);

    long countByOrganizationId(String organizationId);
}
