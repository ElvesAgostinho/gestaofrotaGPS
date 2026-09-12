package ao.autocare.modules.team;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.team.dto.TeamDtos.AcceptWithAccountRequest;
import ao.autocare.modules.team.dto.TeamDtos.InvitationView;
import ao.autocare.modules.team.dto.TeamDtos.InviteRequest;
import ao.autocare.modules.team.dto.TeamDtos.InviteResponse;
import ao.autocare.modules.team.dto.TeamDtos.MemberView;
import ao.autocare.modules.team.dto.TeamDtos.PermissionView;
import ao.autocare.modules.team.dto.TeamDtos.UpdateMemberRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Equipa")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final TeamService team;
    private final OrgContext orgContext;

    public TeamController(TeamService team, OrgContext orgContext) {
        this.team = team;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Catalogo de permissoes",
            description = "Codigo, modulo, rotulo e os papeis que a trazem por omissao.")
    @GetMapping("/permissions")
    public List<PermissionView> permissions() {
        return java.util.Arrays.stream(Permission.values()).map(PermissionView::of).toList();
    }

    // ---- Membros ----------------------------------------------------
    @Operation(summary = "Listar a equipa da empresa")
    @GetMapping("/members")
    public List<MemberView> members(@AuthenticationPrincipal AuthPrincipal p) {
        return team.listMembers(org(p));
    }

    @Operation(summary = "Alterar papel, cargo ou suspensão de um membro")
    @RequirePermission(Permission.TEAM_MANAGE)
    @PatchMapping("/members/{membershipId}")
    public MemberView updateMember(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String membershipId,
            @Valid @RequestBody UpdateMemberRequest req) {
        return team.updateMember(org(p), p.id(), membershipId, req);
    }

    @Operation(summary = "Remover um membro da empresa")
    @RequirePermission(Permission.TEAM_MANAGE)
    @DeleteMapping("/members/{membershipId}")
    public Map<String, String> removeMember(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String membershipId) {
        team.removeMember(org(p), p.id(), membershipId);
        return Map.of("message", "Membro removido da empresa.");
    }

    // ---- Papéis disponíveis -----------------------------------------
    @Operation(summary = "Papéis que podem ser atribuídos, do mais forte ao mais fraco")
    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return Arrays.stream(MembershipRole.values())
                .filter(r -> r != MembershipRole.DRIVER) // legado, não oferecido
                .sorted((a, b) -> Integer.compare(b.rank(), a.rank()))
                .<Map<String, Object>>map(r -> Map.of(
                        "code", r.name(),
                        "label", r.label(),
                        "rank", r.rank(),
                        "description", describe(r)))
                .toList();
    }

    private static String describe(MembershipRole role) {
        return switch (role) {
            case OWNER -> "Acesso total, incluindo gerir a equipa e a empresa.";
            case MANAGER -> "Gere ativos, planos, peças e aprova ordens de manutenção.";
            case TECHNICIAN, DRIVER ->
                    "Executa ordens de manutenção, regista leituras e checklists.";
            case VIEWER -> "Apenas consulta; não pode alterar nada.";
        };
    }

    // ---- Convites ---------------------------------------------------
    @Operation(summary = "Listar convites enviados")
    @RequireRole(MembershipRole.MANAGER)
    @GetMapping("/invitations")
    public List<InvitationView> invitations(@AuthenticationPrincipal AuthPrincipal p) {
        return team.listInvitations(org(p));
    }

    @Operation(summary = "Convidar alguém para a empresa")
    @RequirePermission(Permission.TEAM_MANAGE)
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse invite(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody InviteRequest req) {
        return team.invite(org(p), p.id(), req);
    }

    @Operation(summary = "Gerar um novo link para um convite pendente")
    @RequirePermission(Permission.TEAM_MANAGE)
    @PostMapping("/invitations/{invitationId}/resend")
    public InviteResponse resend(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String invitationId) {
        return team.resend(org(p), p.id(), invitationId);
    }

    @Operation(summary = "Anular um convite")
    @RequirePermission(Permission.TEAM_MANAGE)
    @DeleteMapping("/invitations/{invitationId}")
    public Map<String, String> revoke(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String invitationId) {
        team.revoke(org(p), p.id(), invitationId);
        return Map.of("message", "Convite anulado.");
    }

    @Operation(summary = "Aceitar um convite com a conta atual")
    @PostMapping("/invitations/accept")
    public MemberView acceptWithAccount(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody AcceptWithAccountRequest req) {
        return team.acceptAsExistingUser(p.id(), req.token());
    }
}
