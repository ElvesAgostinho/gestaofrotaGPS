package ao.autocare.repo;

import ao.autocare.domain.PurchaseOrder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {

    Optional<PurchaseOrder> findByIdAndOrganizationId(String id, String organizationId);

    @Query("select o from PurchaseOrder o left join fetch o.supplier "
            + "where o.organization.id = :orgId order by o.createdAt desc")
    List<PurchaseOrder> findAllForOrg(String orgId);

    @Query("select o from PurchaseOrder o where o.organization.id = :orgId "
            + "and o.status in :estados order by o.createdAt desc")
    List<PurchaseOrder> findByEstados(String orgId, Collection<PurchaseOrder.Status> estados);

    long countByOrganizationIdAndStatus(String organizationId, PurchaseOrder.Status status);
}
