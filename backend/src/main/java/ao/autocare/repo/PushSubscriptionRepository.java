package ao.autocare.repo;

import ao.autocare.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, String> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(String userId);
}
