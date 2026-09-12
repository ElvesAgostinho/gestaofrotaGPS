package ao.autocare.repo;

import ao.autocare.domain.PlanTaskCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskCompletionRepository extends JpaRepository<PlanTaskCompletion, String> {

    Page<PlanTaskCompletion> findByAssetIdOrderByCompletedAtDesc(String assetId, Pageable pageable);

    long countByAssetId(String assetId);
}
