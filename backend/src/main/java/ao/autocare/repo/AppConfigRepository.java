package ao.autocare.repo;

import ao.autocare.domain.AppConfigEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfigEntry, String> {

    Optional<AppConfigEntry> findByKey(String key);
}
