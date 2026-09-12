package ao.autocare.repo;

import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetPlanTaskRepository extends JpaRepository<AssetPlanTask, String> {

    List<AssetPlanTask> findByAssetPlanId(String assetPlanId);

    List<AssetPlanTask> findByAssetPlanAssetId(String assetId);

    @Query("select t from AssetPlanTask t "
            + "where t.assetPlan.asset.id = :assetId and t.assetPlan.organization.id = :orgId")
    List<AssetPlanTask> findForAsset(String orgId, String assetId);

    Optional<AssetPlanTask> findByIdAndAssetPlanOrganizationId(String id, String organizationId);

    @Query("select t from AssetPlanTask t "
            + "where t.assetPlan.organization.id = :orgId and t.status in :statuses and t.assetPlan.active = true "
            + "order by t.remainingDays asc")
    List<AssetPlanTask> findByOrgAndStatuses(String orgId, List<PlanTaskStatus> statuses);

    @Query("select distinct t.assetPlan.asset.id from AssetPlanTask t where t.assetPlan.active = true")
    List<String> findDistinctActiveAssetIds();

    @Query("select distinct t.assetPlan.asset.id from AssetPlanTask t "
            + "where t.assetPlan.active = true and t.status = :status")
    List<String> findAssetIdsWithTaskStatus(PlanTaskStatus status);

    /** Tarefas num determinado estado, com o ativo carregado (para avisar). */
    @Query("select t from AssetPlanTask t join fetch t.assetPlan ap join fetch ap.asset a "
            + "where t.status = :status and a.archived = false")
    List<AssetPlanTask> findAllWithStatus(@Param("status") PlanTaskStatus status);
}
