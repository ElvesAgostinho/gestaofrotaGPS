package ao.autocare.repo;

import ao.autocare.domain.RfqQuote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RfqQuoteRepository extends JpaRepository<RfqQuote, String> {

    @Query("select q from RfqQuote q where q.rfqSupplier.id in :ids")
    List<RfqQuote> findByFornecedores(Collection<String> ids);

    Optional<RfqQuote> findByRfqSupplierIdAndRfqLineId(String rfqSupplierId, String rfqLineId);

    List<RfqQuote> findByRfqSupplierId(String rfqSupplierId);
}
