package ao.autocare.repo;

import ao.autocare.domain.MeterReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeterReadingRepository extends JpaRepository<MeterReading, String> {

    Page<MeterReading> findByMeterIdOrderByReadingAtDesc(String meterId, Pageable pageable);

    Optional<MeterReading> findFirstByMeterIdOrderByReadingAtDescCreatedAtDesc(String meterId);

    List<MeterReading> findTop12ByMeterIdOrderByReadingAtDescCreatedAtDesc(String meterId);

    /** Unidades acumuladas (máx - mín das leituras) do medidor principal de um ativo, no período. */
    @Query("""
            select max(r.value) - min(r.value) from MeterReading r
            where r.meter.asset.id = :assetId and r.meter.primary = true
              and r.readingAt >= :from and r.readingAt < :to
            """)
    BigDecimal operatingUnitsForAsset(String assetId, Instant from, Instant to);
}
