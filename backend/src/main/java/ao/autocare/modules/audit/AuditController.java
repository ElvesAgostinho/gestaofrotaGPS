package ao.autocare.modules.audit;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.dto.AuditDtos.AuditActionView;
import ao.autocare.modules.audit.dto.AuditDtos.AuditEntryView;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registo de auditoria da empresa.
 *
 * <p>Exige o papel de <b>Dono</b>. O registo mostra o que toda a gente fez —
 * incluindo entradas na conta e alterações de palavra-passe — e essa é
 * informação que não pertence a quem trabalha na empresa, mas a quem responde
 * por ela.
 */
@Tag(name = "Auditoria")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class AuditController {

    private final AuditQueryService audit;
    private final OrgContext orgContext;

    public AuditController(AuditQueryService audit, OrgContext orgContext) {
        this.audit = audit;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Registo de auditoria da empresa",
            description = "Quem fez o quê, quando e a partir de que endereço. "
                    + "Apenas a empresa de quem consulta.")
    @RequireRole(MembershipRole.OWNER)
    @GetMapping("/api/v1/audit")
    public PagedResponse<AuditEntryView> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return audit.search(
                orgContext.requireOrganizationId(p),
                new AuditQueryService.Filter(action, entityType, entityId, userId,
                        from, to, search),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Operation(summary = "Ações presentes no registo desta empresa",
            description = "Para preencher o filtro sem inventar ações que nunca aconteceram.")
    @RequireRole(MembershipRole.OWNER)
    @GetMapping("/api/v1/audit/actions")
    public List<AuditActionView> actions(@AuthenticationPrincipal AuthPrincipal p) {
        return audit.actions(orgContext.requireOrganizationId(p));
    }
}
