package ao.autocare.repo;

import ao.autocare.domain.StockItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockItemRepository extends JpaRepository<StockItem, String> {

    List<StockItem> findByPartId(String partId);

    List<StockItem> findByWarehouseId(String warehouseId);

    Optional<StockItem> findByPartIdAndWarehouseId(String partId, String warehouseId);

    @Query("select coalesce(sum(s.quantity), 0) from StockItem s where s.part.id = :partId")
    BigDecimal totalQuantityForPart(String partId);

    /** Todo o stock da empresa, com peça e armazém carregados (para exportar). */
    @Query("""
            select s from StockItem s
            join fetch s.part p join fetch s.warehouse w
            where p.organization.id = :orgId
            order by p.name asc, w.name asc
            """)
    List<StockItem> findAllForOrg(String orgId);

    @Query("""
            select s from StockItem s
            where s.part.organization.id = :orgId
              and s.quantity <= coalesce(s.minQuantity, s.part.minQuantity)
              and coalesce(s.minQuantity, s.part.minQuantity) > 0
            """)
    List<StockItem> findLowStock(String orgId);
}
