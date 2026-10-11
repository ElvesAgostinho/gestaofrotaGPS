package ao.autocare.repo;

import ao.autocare.domain.PlanTaskPart;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanTaskPartRepository extends JpaRepository<PlanTaskPart, String> {

    /** As peças de várias tarefas de uma vez, já com o artigo do armazém. */
    @Query("select p from PlanTaskPart p left join fetch p.part where p.task.id in :taskIds")
    List<PlanTaskPart> findByTaskIds(Collection<String> taskIds);

    /** As peças de um plano da empresa que ainda não estão ligadas ao armazém. */
    @Query("select p from PlanTaskPart p where p.part is null "
            + "and p.task.plan.organization.id = :orgId")
    List<PlanTaskPart> findUnlinkedForOrg(String orgId);
}
