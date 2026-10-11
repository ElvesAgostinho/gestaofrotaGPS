package ao.autocare.repo;

import ao.autocare.domain.PurchaseSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSettingsRepository extends JpaRepository<PurchaseSettings, String> {
}
