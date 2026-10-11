package ao.autocare.repo;

import ao.autocare.domain.PurchaseReceipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, String> {

    List<PurchaseReceipt> findByOrderIdOrderByReceivedAtAsc(String orderId);

    Optional<PurchaseReceipt> findByIdAndOrganizationId(String id, String organizationId);

    long countByOrderId(String orderId);
}
