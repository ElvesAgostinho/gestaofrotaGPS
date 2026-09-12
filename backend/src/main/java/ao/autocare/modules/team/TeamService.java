package ao.autocare.modules.team;

import ao.autocare.common.ApiException;
import ao.autocare.config.AutoCareProperties;
import ao.autocare.domain.Invitation;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.auth.AuthService;
import ao.autocare.modules.auth.dto.AuthDtos.AuthResponse;
import ao.autocare.modules.team.dto.TeamDtos.AcceptInvitationRequest;
import ao.autocare.modules.team.dto.TeamDtos.InvitationPreview;
import ao.autocare.modules.team.dto.TeamDtos.InvitationView;
import ao.autocare.modules.team.dto.TeamDtos.InviteRequest;
import ao.autocare.modules.team.dto.TeamDtos.InviteResponse;
import ao.autocare.modules.team.dto.TeamDtos.MemberView;
import ao.autocare.modules.team.dto.TeamDtos.UpdateMemberRequest;
import ao.autocare.repo.InvitationRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.RefreshTokenRepository;
import ao.autocare.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão da equipa de uma empresa: convidar pessoas, atribuir papéis, suspender
 * e remover membros.
 *
 * <p>Duas invariantes protegem a empresa de ficar inacessível:
 * <ul>
 *   <li>tem de existir sempre pelo menos um {@code OWNER} ativo;</li>
 *   <li>ninguém se pode remover, suspender ou despromover a si próprio.</li>
 * </ul>
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    /** Validade do convite. Passado este prazo é preciso reenviar. */
    private static final int INVITE_VALID_DAYS = 14;

    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuthService auth;
    private final AuditService audit;
    private final AutoCareProperties props;

    public TeamService(
            MembershipRepository memberships,
            InvitationRepository invitations,
            OrganizationRepository organizations,
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            AuthService auth,
            AuditService audit,
            AutoCareProperties props) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.organizations = organizations;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.auth = auth;
        this.audit = audit;
        this.props = props;
    }

    // ==== Membros ======================================================
    @Transactional(readOnly = true)
    public List<MemberView> listMembers(String orgId) {
        return memberships.findTeam(orgId).stream().map(MemberView::of).toList();
    }

    @Transactional
    public MemberView updateMember(
            String orgId, String actorUserId, String membershipId, UpdateMemberRequest req) {

        Membership m = requireMember(orgId, membershipId);
        boolean isSelf = m.getUser().getId().equals(actorUserId);

        if (req.role() != null && req.role() != m.getRole()) {
            if (isSelf) {
                throw ApiException.conflict("Não pode alterar o seu próprio papel.");
            }
            if (m.getRole() == MembershipRole.OWNER) {
                requireAnotherOwner(orgId, m);
            }
            m.setRole(req.role());
        }
        if (req.jobTitle() != null) {
            m.setJobTitle(blankToNull(req.jobTitle()));
        }
        if (req.granted() != null || req.denied() != null) {
            // Ninguém tira permissões a si próprio: a forma clássica de um
            // administrador se trancar fora da administração.
            if (isSelf) {
                throw ApiException.conflict("Não pode alterar as suas próprias permissões.");
            }
            if (req.granted() != null) {
                m.setGranted(parsePermissions(req.granted()));
            }
            if (req.denied() != null) {
                m.setDenied(parsePermissions(req.denied()));
            }
            // Cortar as sessões: a permissão a menos tem de valer já, não
            // quando o token expirar.
            refreshTokens.revokeAllForUser(m.getUser().getId(), Instant.now());
        }
        if (req.suspended() != null && req.suspended() != m.isSuspended()) {
            if (isSelf) {
                throw ApiException.conflict("Não pode suspender a sua própria conta.");
            }
            if (req.suspended()) {
                if (m.getRole() == MembershipRole.OWNER) {
                    requireAnotherOwner(orgId, m);
                }
                m.setSuspendedAt(Instant.now());
                // Corta as sessões abertas para a suspensão ter efeito imediato.
                refreshTokens.revokeAllForUser(m.getUser().getId(), Instant.now());
            } else {
                m.setSuspendedAt(null);
            }
        }

        audit.record(orgId, actorUserId, "team.member_update", "Membership", m.getId(),
                m.getUser().getName() + " · " + m.getRole().label()
                        + (m.isSuspended() ? " · suspenso" : ""));
        return MemberView.of(m);
    }

    @Transactional
    public void removeMember(String orgId, String actorUserId, String membershipId) {
        Membership m = requireMember(orgId, membershipId);
        if (m.getUser().getId().equals(actorUserId)) {
            throw ApiException.conflict("Não pode remover-se a si próprio da empresa.");
        }
        if (m.getRole() == MembershipRole.OWNER) {
            requireAnotherOwner(orgId, m);
        }
        String name = m.getUser().getName();
        refreshTokens.revokeAllForUser(m.getUser().getId(), Instant.now());
        memberships.delete(m);
        audit.record(orgId, actorUserId, "team.member_remove", "Membership", membershipId, name);
    }

    // ==== Convites =====================================================
    @Transactional(readOnly = true)
    public List<InvitationView> listInvitations(String orgId) {
        return invitations.findByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(InvitationView::of).toList();
    }

    @Transactional
    public InviteResponse invite(String orgId, String actorUserId, InviteRequest req) {
        String email = req.email().trim().toLowerCase();
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));

        users.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (memberships.findByUserIdAndOrganizationId(existing.getId(), orgId).isPresent()) {
                throw ApiException.conflict("Esta pessoa já faz parte da equipa.");
            }
        });
        invitations
                .findFirstByOrganizationIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull(
                        orgId, email)
                .ifPresent(pending -> {
                    if (pending.isUsable()) {
                        throw ApiException.conflict(
                                "Já existe um convite pendente para este email. "
                                        + "Anule-o ou reenvie-o.");
                    }
                    pending.setRevokedAt(Instant.now()); // expirado — dá lugar ao novo
                });

        Invitation invite = new Invitation();
        invite.setOrganization(org);
        invite.setEmail(email);
        invite.setInvitedName(blankToNull(req.name()));
        invite.setJobTitle(blankToNull(req.jobTitle()));
        invite.setRole(req.role());
        invite.setInvitedBy(users.getReferenceById(actorUserId));
        String token = newToken(invite);
        invitations.save(invite);

        audit.record(orgId, actorUserId, "team.invite", "Invitation", invite.getId(),
                email + " como " + req.role().label());
        return deliver(invite, token, org);
    }

    @Transactional
    public InviteResponse resend(String orgId, String actorUserId, String invitationId) {
        Invitation invite = invitations.findByIdAndOrganizationId(invitationId, orgId)
                .orElseThrow(() -> ApiException.notFound("Convite não encontrado."));
        if (invite.getAcceptedAt() != null) {
            throw ApiException.conflict("Este convite já foi aceite.");
        }
        invite.setRevokedAt(null);
        String token = newToken(invite);
        audit.record(orgId, actorUserId, "team.invite_resend", "Invitation", invite.getId(),
                invite.getEmail());
        return deliver(invite, token, invite.getOrganization());
    }

    @Transactional
    public void revoke(String orgId, String actorUserId, String invitationId) {
        Invitation invite = invitations.findByIdAndOrganizationId(invitationId, orgId)
                .orElseThrow(() -> ApiException.notFound("Convite não encontrado."));
        if (invite.getAcceptedAt() != null) {
            throw ApiException.conflict("Este convite já foi aceite; remova antes o membro.");
        }
        invite.setRevokedAt(Instant.now());
        audit.record(orgId, actorUserId, "team.invite_revoke", "Invitation", invite.getId(),
                invite.getEmail());
    }

    // ==== Aceitação (endpoints públicos) ================================
    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        Invitation invite = requireUsable(token);
        return new InvitationPreview(
                invite.getOrganization().getName(),
                invite.getEmail(),
                invite.getInvitedName(),
                invite.getRole(),
                invite.getRole().label(),
                invite.getInvitedBy() != null ? invite.getInvitedBy().getName() : null,
                invite.getExpiresAt(),
                users.existsByEmailIgnoreCase(invite.getEmail()));
    }

    /** Aceitar criando conta nova. Se o email já tiver conta, tem de entrar primeiro. */
    @Transactional
    public AuthResponse acceptAsNewUser(
            String token, AcceptInvitationRequest req, HttpServletRequest http) {

        Invitation invite = requireUsable(token);
        if (users.existsByEmailIgnoreCase(invite.getEmail())) {
            throw ApiException.conflict(
                    "Já existe uma conta AutoCare com este email. Inicie sessão e aceite "
                            + "o convite a partir da sua conta.");
        }
        User user = auth.createInvitedUser(req.name(), invite.getEmail(), req.password());
        join(invite, user);
        return auth.issueSession(user, http);
    }

    /** Aceitar com uma conta AutoCare já existente (o email tem de coincidir). */
    @Transactional
    public MemberView acceptAsExistingUser(String userId, String token) {
        Invitation invite = requireUsable(token);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(invite.getEmail())) {
            throw ApiException.forbidden("Este convite foi enviado para outro email.");
        }
        return MemberView.of(join(invite, user));
    }

    // ==== Auxiliares ====================================================
    private Membership join(Invitation invite, User user) {
        String orgId = invite.getOrganization().getId();
        Optional<Membership> already =
                memberships.findByUserIdAndOrganizationId(user.getId(), orgId);
        if (already.isPresent()) {
            throw ApiException.conflict("Já faz parte desta equipa.");
        }
        Membership m = new Membership();
        m.setOrganization(invite.getOrganization());
        m.setUser(user);
        m.setRole(invite.getRole());
        m.setJobTitle(invite.getJobTitle());
        m.setInvitedBy(invite.getInvitedBy() != null ? invite.getInvitedBy().getId() : null);
        memberships.save(m);

        invite.setAcceptedAt(Instant.now());
        invite.setAcceptedBy(user);

        audit.record(invite.getOrganization().getId(), user.getId(),
                "team.invite_accept", "Membership", m.getId(),
                user.getName() + " entrou em " + invite.getOrganization().getName());
        return m;
    }

    /**
     * Gera um novo token, guarda apenas o hash e devolve o token em claro para
     * ser entregue uma única vez.
     */
    private String newToken(Invitation invite) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        invite.setTokenHash(sha256(token));
        invite.setExpiresAt(Instant.now().plus(INVITE_VALID_DAYS, ChronoUnit.DAYS));
        return token;
    }

    /**
     * Entrega o convite. Sem serviço de email configurado o sistema não finge
     * que enviou: assume modo demonstração e devolve o link a quem convidou.
     */
    private InviteResponse deliver(Invitation invite, String token, Organization org) {
        String url = acceptUrl(token);
        log.warn("[MODO DEMONSTRAÇÃO] Sem serviço de email configurado. "
                + "Convite de {} para {}: {}", org.getName(), invite.getEmail(), url);
        return new InviteResponse(
                InvitationView.of(invite),
                true,
                "Ainda não há serviço de email configurado. Envie este link a "
                        + invite.getEmail() + " pelo meio que preferir.",
                url,
                token);
    }

    private String acceptUrl(String token) {
        String base = props.app().webUrl();
        if (base == null || base.isBlank()) {
            base = "";
        } else if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/convite/" + token;
    }

    private Invitation requireUsable(String token) {
        Invitation invite = invitations.findByTokenHash(sha256(token))
                .orElseThrow(() -> ApiException.badRequest(
                        "Este convite não é válido. Peça um novo à sua empresa."));
        if (invite.getAcceptedAt() != null) {
            throw ApiException.conflict("Este convite já foi utilizado.");
        }
        if (!invite.isUsable()) {
            throw ApiException.badRequest(
                    "Este convite expirou ou foi anulado. Peça um novo à sua empresa.");
        }
        return invite;
    }

    private Membership requireMember(String orgId, String membershipId) {
        return memberships.findByIdAndOrganizationId(membershipId, orgId)
                .orElseThrow(() -> ApiException.notFound("Membro não encontrado nesta empresa."));
    }

    /** Impede que a empresa fique sem nenhum dono ativo. */
    private void requireAnotherOwner(String orgId, Membership target) {
        long activeOwners = memberships.countByOrganizationIdAndRoleAndSuspendedAtIsNull(
                orgId, MembershipRole.OWNER);
        boolean targetCounts = target.getRole() == MembershipRole.OWNER && !target.isSuspended();
        if (targetCounts && activeOwners <= 1) {
            throw ApiException.conflict(
                    "A empresa tem de ter sempre pelo menos um dono ativo. "
                            + "Promova outra pessoa a dono antes de continuar.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Códigos para permissões; um código desconhecido é um erro de quem chamou. */
    private static java.util.List<ao.autocare.security.Permission> parsePermissions(
            java.util.List<String> codes) {
        java.util.List<ao.autocare.security.Permission> out = new java.util.ArrayList<>();
        for (String c : codes) {
            ao.autocare.security.Permission p = ao.autocare.security.Permission.parse(c);
            if (p == null) {
                throw ApiException.badRequest("Permissão desconhecida: " + c);
            }
            out.add(p);
        }
        return out;
    }
}
