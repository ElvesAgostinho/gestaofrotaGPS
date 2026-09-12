package ao.autocare.repo;

import ao.autocare.domain.WorkOrderQuote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderQuoteRepository extends JpaRepository<WorkOrderQuote, String> {

    List<WorkOrderQuote> findByWorkOrderIdOrderByCreatedAtAsc(String workOrderId);
}
