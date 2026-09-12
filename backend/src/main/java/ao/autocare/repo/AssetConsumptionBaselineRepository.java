package ao.autocare.repo;

import ao.autocare.domain.AssetConsumptionBaseline;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetConsumptionBaselineRepository
        extends JpaRepository<AssetConsumptionBaseline, String> {

    Optional<AssetConsumptionBaseline> findByAssetId(String assetId);

    List<AssetConsumptionBaseline> findByOrganizationId(String organizationId);
}
