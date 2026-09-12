package ao.autocare.repo;

import ao.autocare.domain.Warehouse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, String> {

    List<Warehouse> findByOrganizationIdOrderByNameAsc(String organizationId);

    Optional<Warehouse> findByIdAndOrganizationId(String id, String organizationId);

    long countByOrganizationId(String organizationId);
}
