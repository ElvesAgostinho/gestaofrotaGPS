package ao.autocare.repo;

import ao.autocare.domain.AssetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTypeRepository extends JpaRepository<AssetType, String> {

    List<AssetType> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<AssetType> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(String organizationId, String name);

    long countByOrganizationId(String organizationId);
}
