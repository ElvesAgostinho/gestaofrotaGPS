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

    /**
     * Quantos pedidos foram feitos a este fornecedor, e a quantos respondeu.
     *
     * <p>Conta os que lhe foram enviados, os que ele respondeu, e os de pedidos
     * que ja fecharam -- nesses, quem nao respondeu demonstrou que nao
     * responde. Contar so os enviados por email deixava esta medida vazia para
     * sempre em quem pede preco por telefone, que e metade de Angola.
     */
    @Query("select count(s), sum(case when s.respondedAt is not null then 1 else 0 end) "
            + "from RfqSupplier s where s.supplier.id = :supplierId "
            + "and (s.sentAt is not null or s.respondedAt is not null "
            + "or s.rfq.status <> ao.autocare.domain.Rfq$Status.OPEN)")
    Object[] respostasDe(String supplierId);
}
