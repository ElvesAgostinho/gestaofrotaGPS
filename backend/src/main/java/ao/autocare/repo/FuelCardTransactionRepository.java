package ao.autocare.repo;

import ao.autocare.domain.FuelCardTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FuelCardTransactionRepository extends JpaRepository<FuelCardTransaction, String> {

    List<FuelCardTransaction> findByOrganizationIdOrderByTransactedAtDesc(String organizationId);

    List<FuelCardTransaction> findByOrganizationIdAndStatusOrderByTransactedAtDesc(
            String organizationId, FuelCardTransaction.Status status);

    boolean existsByOrganizationIdAndCardNumberAndTransactedAtAndLitersAndAmount(
            String organizationId, String cardNumber, Instant transactedAt, BigDecimal liters, BigDecimal amount);

    @Query("select t from FuelCardTransaction t where t.organization.id = :orgId "
            + "and t.transactedAt >= :from and t.transactedAt < :to order by t.transactedAt asc")
    List<FuelCardTransaction> between(String orgId, Instant from, Instant to);

    long countByOrganizationIdAndStatus(String organizationId, FuelCardTransaction.Status status);
}
