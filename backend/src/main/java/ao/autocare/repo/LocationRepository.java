package ao.autocare.repo;

import ao.autocare.domain.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, String> {

    List<Location> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<Location> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByOrganizationIdAndParentId(String organizationId, String parentId);

    long countByOrganizationId(String organizationId);
}
