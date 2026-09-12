package ao.autocare.repo;

import ao.autocare.domain.AssetPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetPlanRepository extends JpaRepository<AssetPlan, String> {

    List<AssetPlan> findByAssetId(String assetId);

    List<AssetPlan> findByOrganizationId(String organizationId);

    Optional<AssetPlan> findByIdAndOrganizationId(String id, String organizationId);

    Optional<AssetPlan> findByAssetIdAndPlanId(String assetId, String planId);

    long countByPlanId(String planId);
}
