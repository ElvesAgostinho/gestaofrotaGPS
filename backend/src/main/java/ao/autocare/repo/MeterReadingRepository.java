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

    /**
     * O mesmo para toda a empresa, mas <b>com a unidade do contador</b>.
     *
     * <p>Sem a unidade, somar o contador de um camião com o de uma máquina dá
     * um número que não é horas nem quilómetros — e é com ele que o MTBF sai
     * errado. Devolve, por ativo: id, tipo de contador e unidades do período.
     */
    @Query("""
            select r.meter.asset.id, r.meter.kind, max(r.value) - min(r.value)
            from MeterReading r
            where r.meter.asset.organization.id = :orgId and r.meter.primary = true
              and r.readingAt >= :from and r.readingAt < :to
            group by r.meter.asset.id, r.meter.kind
            """)
    List<Object[]> operatingUnitsByAsset(String orgId, Instant from, Instant to);
}
