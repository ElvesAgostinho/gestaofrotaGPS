package ao.autocare.repo;

import ao.autocare.domain.TyreReading;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TyreReadingRepository extends JpaRepository<TyreReading, String> {

    List<TyreReading> findByTyreIdOrderByMeasuredAtDesc(String tyreId);
}
