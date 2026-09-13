package ao.autocare.repo;

import ao.autocare.domain.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    Optional<Supplier> findByIdAndOrganizationId(String id, String organizationId);

    Optional<Supplier> findByOrganizationIdAndNameIgnoreCase(String organizationId, String name);

    List<Supplier> findByOrganizationIdOrderByNameAsc(String organizationId);

    boolean existsByOrganizationIdAndTaxId(String organizationId, String taxId);
}
