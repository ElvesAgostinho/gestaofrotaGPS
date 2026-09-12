package ao.autocare.repo;

import ao.autocare.domain.PredictiveReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictiveReadingRepository extends JpaRepository<PredictiveReading, String> {

    Page<PredictiveReading> findByProgramIdOrderByPerformedAtDesc(
            String programId, Pageable pageable);

    Page<PredictiveReading> findByAssetIdOrderByPerformedAtDesc(String assetId, Pageable pageable);
}
