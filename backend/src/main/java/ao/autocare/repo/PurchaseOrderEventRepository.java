package ao.autocare.repo;

import ao.autocare.domain.PurchaseOrderEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEvent, String> {

    List<PurchaseOrderEvent> findByOrderIdOrderByAtAsc(String orderId);
}
