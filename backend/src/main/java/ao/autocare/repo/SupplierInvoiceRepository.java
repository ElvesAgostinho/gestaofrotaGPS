package ao.autocare.repo;

import ao.autocare.domain.SupplierInvoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, String> {

    List<SupplierInvoice> findBySupplierIdOrderByIssuedAtDesc(String supplierId);

    List<SupplierInvoice> findByOrderIdOrderByIssuedAtDesc(String orderId);

    Optional<SupplierInvoice> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsBySupplierIdAndNumberIgnoreCase(String supplierId, String number);

    @Query("select i from SupplierInvoice i join fetch i.supplier "
            + "where i.organization.id = :orgId and i.status = :estado "
            + "order by i.dueAt asc nulls last")
    List<SupplierInvoice> porEstado(String orgId, SupplierInvoice.Status estado);

    @Query("select i from SupplierInvoice i join fetch i.supplier "
            + "where i.organization.id = :orgId order by i.issuedAt desc nulls last")
    List<SupplierInvoice> todas(String orgId);
}
