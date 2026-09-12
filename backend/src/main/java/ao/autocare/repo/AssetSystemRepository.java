package ao.autocare.repo;

import ao.autocare.domain.AssetSystem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetSystemRepository extends JpaRepository<AssetSystem, String> {

    List<AssetSystem> findByAssetTypeIdOrderBySortOrderAsc(String assetTypeId);
}
