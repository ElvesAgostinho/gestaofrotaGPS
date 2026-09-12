package ao.autocare.repo;

import ao.autocare.domain.DriverScore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverScoreRepository extends JpaRepository<DriverScore, String> {

    Optional<DriverScore> findByDriverIdAndPeriodStartAndPeriodEnd(
            String driverId, Instant periodStart, Instant periodEnd);

    /** Ranking do periodo: quem nao tem dados suficientes fica no fim. */
    @Query("""
            select s from DriverScore s
            join fetch s.driver
            where s.organization.id = :organizationId
              and s.periodStart = :inicio and s.periodEnd = :fim
            order by s.insufficientData asc, s.score desc nulls last
            """)
    List<DriverScore> ranking(String organizationId, Instant inicio, Instant fim);

    List<DriverScore> findByDriverIdOrderByPeriodStartDesc(String driverId);
}
