package ao.autocare.repo;

import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.enums.Enums.MeterKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMeterRepository extends JpaRepository<AssetMeter, String> {

    List<AssetMeter> findByAssetId(String assetId);

    Optional<AssetMeter> findByAssetIdAndKind(String assetId, MeterKind kind);
}
