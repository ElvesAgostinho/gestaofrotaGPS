package ao.autocare.repo;

import ao.autocare.domain.AssetCriticality;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetCriticalityRepository extends JpaRepository<AssetCriticality, String> {

    Optional<AssetCriticality> findByAssetId(String assetId);
}
