package ao.autocare.repo;

import ao.autocare.domain.Repair;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RepairRepository extends JpaRepository<Repair, String> {

    @Query("select coalesce(sum(r.repairHours), 0) from Repair r where r.asset.id = :assetId "
            + "and r.finishedAt >= :from and r.finishedAt < :to")
    BigDecimal totalRepairHoursForAsset(String assetId, Instant from, Instant to);

    @Query("select count(r) from Repair r where r.asset.id = :assetId "
            + "and r.finishedAt >= :from and r.finishedAt < :to")
    long countForAssetBetween(String assetId, Instant from, Instant to);

    @Query("select coalesce(sum(r.repairHours), 0) from Repair r where r.organization.id = :orgId "
            + "and r.finishedAt >= :from and r.finishedAt < :to")
    BigDecimal totalRepairHoursForOrg(String orgId, Instant from, Instant to);

    @Query("select count(r) from Repair r where r.organization.id = :orgId "
            + "and r.finishedAt >= :from and r.finishedAt < :to")
    long countForOrgBetween(String orgId, Instant from, Instant to);
}
