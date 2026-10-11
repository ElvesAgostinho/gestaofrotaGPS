package ao.autocare.repo;

import ao.autocare.domain.SupplierContact;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierContactRepository extends JpaRepository<SupplierContact, String> {

    List<SupplierContact> findBySupplierIdOrderByPrimaryContactDescNameAsc(String supplierId);

    Optional<SupplierContact> findByIdAndOrganizationId(String id, String organizationId);
}
