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

    /** Os ativos da empresa que já têm um plano activo atribuído. */
    @org.springframework.data.jpa.repository.Query(
            "select distinct ap.asset.id from AssetPlan ap "
                    + "where ap.organization.id = :orgId and ap.active = true")
    List<String> findAssetIdsWithActivePlan(String orgId);
}
