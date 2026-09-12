package ao.autocare.repo;

import ao.autocare.domain.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Consultas ao registo de auditoria.
 *
 * <p>Os filtros são construídos com {@link JpaSpecificationExecutor} em vez de
 * JPQL com {@code (:param is null or ...)}: comparar um parâmetro a nulo
 * comporta-se de maneira diferente em H2 e em PostgreSQL, e este é código que
 * corre nos dois.
 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    /** Ações que esta empresa chegou a ter, para preencher o filtro do ecrã. */
    @Query("""
            select distinct a.action from AuditLog a
            where a.organizationId = :organizationId
            order by a.action
            """)
    List<String> distinctActions(String organizationId);
}
