package ao.autocare.repo;

import ao.autocare.domain.Plan;
import ao.autocare.domain.enums.Enums.PlanCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {

    Optional<Plan> findByCode(PlanCode code);

    List<Plan> findByActiveTrueOrderByPriceMonthlyAsc();
}
