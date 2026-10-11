package ao.autocare.repo;

import ao.autocare.domain.Rfq;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RfqRepository extends JpaRepository<Rfq, String> {

    Optional<Rfq> findByIdAndOrganizationId(String id, String organizationId);

    @Query("select r from Rfq r where r.organization.id = :orgId order by r.createdAt desc")
    List<Rfq> findAllForOrg(String orgId);

    @Query("select r from Rfq r where r.organization.id = :orgId and r.status = :estado "
            + "order by r.createdAt desc")
    List<Rfq> findByEstado(String orgId, Rfq.Status estado);
}
