package ao.autocare.repo;

import ao.autocare.domain.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {

    Page<Asset> findByOrganizationIdAndArchived(String organizationId, boolean archived, Pageable pageable);

    List<Asset> findByOrganizationId(String organizationId);

    Optional<Asset> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByOrganizationIdAndTagIgnoreCase(String organizationId, String tag);

    long countByOrganizationId(String organizationId);

    long countByAssetTypeId(String assetTypeId);

    long countByLocationId(String locationId);
}
