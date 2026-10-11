package ao.autocare.repo;

import ao.autocare.domain.RfqSupplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RfqSupplierRepository extends JpaRepository<RfqSupplier, String> {

    @Query("select s from RfqSupplier s join fetch s.supplier where s.rfq.id = :rfqId")
    List<RfqSupplier> findByRfq(String rfqId);

    Optional<RfqSupplier> findByRfqIdAndSupplierId(String rfqId, String supplierId);

    /** Quantos pedidos foram feitos a este fornecedor, e a quantos respondeu. */
    @Query("select count(s), sum(case when s.respondedAt is not null then 1 else 0 end) "
            + "from RfqSupplier s where s.supplier.id = :supplierId and s.sentAt is not null")
    Object[] respostasDe(String supplierId);
}
