package ao.autocare.repo;

import ao.autocare.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, String> {

    Page<StockMovement> findByOrganizationIdOrderByCreatedAtDesc(String organizationId, Pageable pageable);

    Page<StockMovement> findByPartIdOrderByCreatedAtDesc(String partId, Pageable pageable);
}
