package ao.autocare.repo;

import ao.autocare.domain.ChecklistExecution;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistExecutionRepository extends JpaRepository<ChecklistExecution, String> {

    Page<ChecklistExecution> findByAssetIdOrderByPerformedAtDesc(String assetId, Pageable pageable);

    Optional<ChecklistExecution> findByIdAndOrganizationId(String id, String organizationId);

    long countByAssetId(String assetId);
}
