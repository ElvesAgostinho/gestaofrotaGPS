package ao.autocare.repo;

import ao.autocare.domain.Tyre;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TyreRepository extends JpaRepository<Tyre, String> {

    List<Tyre> findByAssetIdOrderByStatusAscPositionAsc(String assetId);

    List<Tyre> findByOrganizationIdOrderByStatusAscUpdatedAtDesc(String organizationId);

    Optional<Tyre> findByIdAndOrganizationId(String id, String organizationId);

    Optional<Tyre> findByAssetIdAndPositionAndStatus(String assetId, String position, Tyre.Status status);

    long countByOrganizationId(String organizationId);
}
