package ao.autocare.modules.audit;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.AuditLog;
import ao.autocare.domain.User;
import ao.autocare.modules.audit.dto.AuditDtos.AuditActionView;
import ao.autocare.modules.audit.dto.AuditDtos.AuditEntryView;
import ao.autocare.modules.audit.dto.AuditDtos.Actions;
import ao.autocare.repo.AuditLogRepository;
import ao.autocare.repo.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do registo de auditoria.
 *
 * <p>Há aqui uma regra de que nada depende a não ser a disciplina de quem
 * escreve o código: <b>toda</b> a consulta começa pelo {@code organizationId},
 * e não existe caminho nenhum que devolva linhas sem esse filtro. Uma linha sem
 * empresa atribuída não aparece a ninguém.
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditQueryService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    /** Filtros do ecrã. Qualquer um pode vir vazio. */
    public record Filter(
            String action, String entityType, String entityId,
            String userId, Instant from, Instant to, String search) {}

    @Transactional(readOnly = true)
    public PagedResponse<AuditEntryView> search(
            String organizationId, Filter filter, Pageable pageable) {

        Page<AuditLog> page = logs.findAll(specification(organizationId, filter), pageable);

        // Os nomes em lote: uma consulta, não uma por linha.
        Map<String, String> names = namesOf(page.getContent());

        return PagedResponse.of(page.map(a -> new AuditEntryView(
                a.getId(), a.getCreatedAt(), a.getUserId(),
                a.getUserId() != null ? names.get(a.getUserId()) : null,
                a.getAction(), Actions.label(a.getAction()),
                a.getEntityType(), a.getEntityId(), a.getSummary(), a.getIp())));
    }

    private Specification<AuditLog> specification(String organizationId, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            // A primeira condição, sempre, e sem alternativa.
            where.add(cb.equal(root.get("organizationId"), organizationId));

            if (notBlank(f.action())) {
                where.add(cb.equal(root.get("action"), f.action()));
            }
            if (notBlank(f.entityType())) {
                where.add(cb.equal(root.get("entityType"), f.entityType()));
            }
            if (notBlank(f.entityId())) {
                where.add(cb.equal(root.get("entityId"), f.entityId()));
            }
            if (notBlank(f.userId())) {
                where.add(cb.equal(root.get("userId"), f.userId()));
            }
            if (f.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.to()));
            }
            if (notBlank(f.search())) {
                // Escapar os universais do LIKE. Sem isto, procurar "50%" ou
                // "CAM_01" devolvia coisas que nada tinham a ver -- o % e o _
                // valiam como "qualquer coisa" em vez de valerem por si.
                String termo = f.search().trim().toLowerCase()
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                where.add(cb.like(cb.lower(root.get("summary")), "%" + termo + "%", '!'));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** Ações presentes nesta empresa, já com nome em português. */
    @Transactional(readOnly = true)
    public List<AuditActionView> actions(String organizationId) {
        return logs.distinctActions(organizationId).stream()
                .map(code -> new AuditActionView(code, Actions.label(code)))
                .sorted((a, b) -> a.label().compareToIgnoreCase(b.label()))
                .toList();
    }

    private Map<String, String> namesOf(List<AuditLog> entries) {
        Set<String> ids = entries.stream()
                .map(AuditLog::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
