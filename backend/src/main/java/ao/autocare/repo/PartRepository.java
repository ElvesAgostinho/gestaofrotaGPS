package ao.autocare.repo;

import ao.autocare.domain.Part;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartRepository extends JpaRepository<Part, String> {

    List<Part> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<Part> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByOrganizationIdAndPartNumberIgnoreCase(String organizationId, String partNumber);

    long countByOrganizationId(String organizationId);
}
