package ao.autocare.repo;

import ao.autocare.domain.PurchaseReceiptLine;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLine, String> {

    /**
     * Tudo o que foi recusado numa entrega desta empresa.
     *
     * <p>Com a entrega, a ordem e o fornecedor carregados: a lista de
     * devoluções mostra-os todos, e ir buscá-los um a um daria uma consulta
     * por linha.
     */
    @Query("select l from PurchaseReceiptLine l "
            + "join fetch l.receipt r join fetch r.order o left join fetch o.supplier "
            + "join fetch l.orderLine "
            + "where r.organization.id = :orgId and l.rejectedQuantity > 0 "
            + "order by r.receivedAt desc")
    List<PurchaseReceiptLine> recusadasDaOrganizacao(String orgId);

    /** As que estão por resolver desde antes de uma data — para avisar. */
    @Query("select l from PurchaseReceiptLine l "
            + "join fetch l.receipt r join fetch r.order o left join fetch o.supplier "
            + "join fetch l.orderLine "
            + "where l.rejectedQuantity > 0 "
            + "and l.resolution = ao.autocare.domain.PurchaseReceiptLine$Resolution.PENDING "
            + "and r.receivedAt < :limite")
    List<PurchaseReceiptLine> recusadasPendentesAntesDe(Instant limite);
}
