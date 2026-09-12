package ao.autocare.repo;

import ao.autocare.domain.PredictiveProgram;
import ao.autocare.domain.enums.Enums.PredictiveTechnique;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PredictiveProgramRepository extends JpaRepository<PredictiveProgram, String> {

    List<PredictiveProgram> findByAssetIdOrderByTechniqueAsc(String assetId);

    Optional<PredictiveProgram> findByIdAndOrganizationId(String id, String organizationId);

    Optional<PredictiveProgram> findByAssetIdAndTechnique(
            String assetId, PredictiveTechnique technique);

    /** Programas ativos da empresa, com o ativo carregado. */
    @Query("select p from PredictiveProgram p join fetch p.asset a "
            + "where p.organization.id = :organizationId and p.active = true "
            + "and a.archived = false order by p.nextDueAt asc")
    List<PredictiveProgram> findActive(String organizationId);

    /** Todos os programas ativos da plataforma — usado pelo agendador. */
    @Query("select p from PredictiveProgram p join fetch p.asset a join fetch p.organization "
            + "where p.active = true and a.archived = false")
    List<PredictiveProgram> findAllActive();

    long countByOrganizationId(String organizationId);
}
