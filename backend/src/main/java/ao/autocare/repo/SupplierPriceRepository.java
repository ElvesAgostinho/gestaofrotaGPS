package ao.autocare.repo;

import ao.autocare.domain.SupplierPrice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierPriceRepository extends JpaRepository<SupplierPrice, String> {

    List<SupplierPrice> findBySupplierIdOrderByCreatedAtDesc(String supplierId);

    Optional<SupplierPrice> findByIdAndOrganizationId(String id, String organizationId);

    Optional<SupplierPrice> findBySupplierIdAndPartId(String supplierId, String partId);

    /** Os precos de uma peca, de todos os fornecedores -- para comparar. */
    @Query("select p from SupplierPrice p join fetch p.supplier s "
            + "where p.part.id = :partId and s.active = true order by p.price asc")
    List<SupplierPrice> findByPartIdComFornecedor(String partId);

    /** Os precos de varias pecas de uma vez -- para a lista de compras. */
    @Query("select p from SupplierPrice p join fetch p.supplier s "
            + "where p.part.id in :partIds and s.active = true")
    List<SupplierPrice> findByPartIds(Collection<String> partIds);

    @Query("select p from SupplierPrice p join fetch p.part join fetch p.supplier "
            + "where p.organization.id = :orgId order by p.part.name asc, p.price asc")
    List<SupplierPrice> findAllForOrg(String orgId);
}
