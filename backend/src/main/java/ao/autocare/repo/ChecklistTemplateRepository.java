package ao.autocare.repo;

import ao.autocare.domain.ChecklistTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, String> {

    List<ChecklistTemplate> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<ChecklistTemplate> findByIdAndOrganizationId(String id, String organizationId);

    long countByOrganizationId(String organizationId);
}
