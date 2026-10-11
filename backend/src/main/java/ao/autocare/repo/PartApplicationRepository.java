package ao.autocare.repo;

import ao.autocare.domain.PartApplication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartApplicationRepository extends JpaRepository<PartApplication, String> {

    List<PartApplication> findByPartIdOrderByCreatedAtAsc(String partId);

    Optional<PartApplication> findByIdAndOrganizationId(String id, String organizationId);

    /** Todas as aplicações da empresa, com a peça carregada. */
    @Query("select a from PartApplication a join fetch a.part p "
            + "where a.organization.id = :orgId order by p.name asc")
    List<PartApplication> findAllForOrg(String orgId);

    /** As aplicações de várias peças de uma vez — para verificar uma lista. */
    @Query("select a from PartApplication a where a.part.id in :partIds")
    List<PartApplication> findByPartIds(Collection<String> partIds);

    void deleteByPartId(String partId);
}
