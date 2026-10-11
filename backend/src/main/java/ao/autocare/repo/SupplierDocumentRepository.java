package ao.autocare.repo;

import ao.autocare.domain.SupplierDocument;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierDocumentRepository extends JpaRepository<SupplierDocument, String> {

    List<SupplierDocument> findBySupplierIdOrderByExpiresAtAsc(String supplierId);

    Optional<SupplierDocument> findByIdAndOrganizationId(String id, String organizationId);

    /** Documentos a expirar ou expirados -- para o aviso e para travar a compra. */
    @Query("select d from SupplierDocument d join fetch d.supplier "
            + "where d.organization.id = :orgId and d.expiresAt is not null "
            + "and d.expiresAt <= :limite order by d.expiresAt asc")
    List<SupplierDocument> expirandoAte(String orgId, LocalDate limite);

    @Query("select d from SupplierDocument d join fetch d.supplier s "
            + "where d.expiresAt is not null and d.expiresAt <= :limite and s.active = true")
    List<SupplierDocument> todosExpirandoAte(LocalDate limite);
}
