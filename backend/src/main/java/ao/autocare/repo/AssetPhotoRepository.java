package ao.autocare.repo;

import ao.autocare.domain.AssetPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetPhotoRepository extends JpaRepository<AssetPhoto, String> {

    List<AssetPhoto> findByAssetIdOrderByPrimaryDescSortOrderAscCreatedAtAsc(String assetId);

    Optional<AssetPhoto> findByIdAndAssetId(String id, String assetId);

    long countByAssetId(String assetId);

    List<AssetPhoto> findByAssetIdAndPrimaryTrue(String assetId);
}
