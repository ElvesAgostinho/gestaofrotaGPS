package ao.autocare.repo;

import ao.autocare.domain.PartCrossRef;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartCrossRefRepository extends JpaRepository<PartCrossRef, String> {

    List<PartCrossRef> findByPartIdOrderByKindAscNumberAsc(String partId);

    Optional<PartCrossRef> findByIdAndOrganizationId(String id, String organizationId);

    /**
     * Encontra o artigo pelo número que vem escrito na caixa, seja ele o
     * original, o do fabricante ou o de um equivalente.
     */
    @Query("select r from PartCrossRef r join fetch r.part "
            + "where r.organization.id = :orgId and upper(r.number) like upper(:padrao)")
    List<PartCrossRef> searchByNumber(String orgId, String padrao);

    boolean existsByPartIdAndNumberIgnoreCase(String partId, String number);

    void deleteByPartId(String partId);
}
