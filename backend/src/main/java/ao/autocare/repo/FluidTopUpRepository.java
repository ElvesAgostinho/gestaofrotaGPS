package ao.autocare.repo;

import ao.autocare.domain.FluidTopUp;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FluidTopUpRepository extends JpaRepository<FluidTopUp, String> {

    List<FluidTopUp> findByAssetIdOrderByRecordedAtDesc(String assetId);

    /** Os atestos do mesmo fluido nesta viatura desde uma data — a tendência. */
    @Query("select t from FluidTopUp t where t.asset.id = :assetId and t.kind = :kind "
            + "and t.recordedAt >= :since order by t.recordedAt desc")
    List<FluidTopUp> findByAssetIdAndKindSince(String assetId, FluidTopUp.Kind kind, Instant since);

    /** Total por fluido na empresa, desde uma data — para o relatório. */
    @Query("select t.asset.id, t.kind, sum(t.liters), count(t) from FluidTopUp t "
            + "where t.organization.id = :orgId and t.recordedAt >= :since "
            + "group by t.asset.id, t.kind")
    List<Object[]> totalsSince(String orgId, Instant since);
}
