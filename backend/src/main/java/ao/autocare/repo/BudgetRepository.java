package ao.autocare.repo;

import ao.autocare.domain.Budget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, String> {

    List<Budget> findByOrganizationIdAndYearOrderByScopeAscCategoryAsc(String organizationId, int year);

    List<Budget> findByOrganizationIdOrderByYearDescScopeAsc(String organizationId);

    Optional<Budget> findByIdAndOrganizationId(String id, String organizationId);

    List<Budget> findByYear(int year);
}
