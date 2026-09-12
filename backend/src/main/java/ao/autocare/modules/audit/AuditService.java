package ao.autocare.modules.audit;

import ao.autocare.domain.AuditLog;
import ao.autocare.repo.AuditLogRepository;
import ao.autocare.repo.MembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Regista quem fez o quê, quando e a partir de onde.
 *
 * <p>A empresa é <b>obrigatória</b> na assinatura, e isso é deliberado. Enquanto
 * era opcional, uma linha sem empresa era indistinguível de uma linha bem
 * atribuída, e o registo inteiro ficava impossível de mostrar a alguém sem
 * mostrar a atividade de todas as empresas. Agora o compilador obriga quem
 * escreve uma ação nova a decidir a que empresa ela pertence.
 *
 * <p>Falhas de auditoria nunca quebram o pedido: um erro a registar não pode
 * impedir o trabalho de quem está a usar o sistema.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final MembershipRepository memberships;

    public AuditService(AuditLogRepository repository, MembershipRepository memberships) {
        this.repository = repository;
        this.memberships = memberships;
    }

    public void record(String organizationId, String userId, String action,
                       String entityType, String entityId, String summary,
                       HttpServletRequest request) {
        try {
            AuditLog entry = new AuditLog();
            entry.setOrganizationId(organizationId);
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setSummary(summary);
            if (request != null) {
                entry.setIp(request.getRemoteAddr());
                entry.setUserAgent(request.getHeader("User-Agent"));
            }
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Não foi possível registar auditoria \"{}\": {}", action, e.toString());
        }
    }

    public void record(String organizationId, String userId, String action,
                       String entityType, String entityId, String summary) {
        record(organizationId, userId, action, entityType, entityId, summary, null);
    }

    /**
     * Ações de conta (entrar, mudar palavra-passe, eliminar conta), onde a
     * empresa não está no contexto do pedido.
     *
     * <p>Resolve a <b>mesma</b> empresa que a sessão do utilizador usaria — a
     * primeira inscrição não suspensa. Usar outra regra faria o registo de
     * segurança apontar para sítio diferente daquele onde a pessoa trabalhou.
     */
    public void recordForUser(String userId, String action, String entityType,
                              String entityId, String summary, HttpServletRequest request) {
        record(organizationOf(userId), userId, action, entityType, entityId, summary, request);
    }

    public void recordForUser(String userId, String action, String entityType,
                              String entityId, String summary) {
        recordForUser(userId, action, entityType, entityId, summary, null);
    }

    private String organizationOf(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return memberships.findFirstByUserIdAndSuspendedAtIsNullOrderByCreatedAtAsc(userId)
                    .map(m -> m.getOrganization().getId())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
