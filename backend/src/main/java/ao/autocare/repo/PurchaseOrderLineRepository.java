package ao.autocare.repo;

import ao.autocare.domain.PurchaseOrder;
import ao.autocare.domain.PurchaseOrderLine;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, String> {

    /**
     * O que esta encomendado e ainda nao chegou, por artigo.
     *
     * <p>E isto que faz a lista de compras deixar de mandar comprar outra vez o
     * que ja vai a caminho.
     */
    @Query("select l.part.id, sum(l.quantity - l.receivedQuantity) from PurchaseOrderLine l "
            + "where l.order.organization.id = :orgId and l.part.id is not null "
            + "and l.order.status in :estados and l.quantity > l.receivedQuantity "
            + "group by l.part.id")
    List<Object[]> porReceberPorPeca(String orgId, Collection<PurchaseOrder.Status> estados);
}
