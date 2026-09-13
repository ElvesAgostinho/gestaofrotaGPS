package ao.autocare.repo;

import ao.autocare.domain.WorkOrderTimer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOrderTimerRepository extends JpaRepository<WorkOrderTimer, String> {

    @Query("select t from WorkOrderTimer t join fetch t.user where t.workOrder.id = :workOrderId and t.endedAt is null "
            + "order by t.startedAt asc")
    List<WorkOrderTimer> running(String workOrderId);

    Optional<WorkOrderTimer> findFirstByWorkOrderIdAndUserIdAndEndedAtIsNull(String workOrderId, String userId);

    /** Os cronómetros abertos de uma pessoa, em qualquer ordem: não se está em duas ao mesmo tempo. */
    @Query("select t from WorkOrderTimer t join fetch t.workOrder where t.user.id = :userId and t.endedAt is null")
    List<WorkOrderTimer> runningForUser(String userId);
}
