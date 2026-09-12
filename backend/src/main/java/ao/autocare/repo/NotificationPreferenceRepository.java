package ao.autocare.repo;

import ao.autocare.domain.NotificationPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, String> {

    List<NotificationPreference> findByUserId(String userId);

    Optional<NotificationPreference> findByUserIdAndCategory(
            String userId, ao.autocare.domain.enums.Enums.AlertCategory category);
}
