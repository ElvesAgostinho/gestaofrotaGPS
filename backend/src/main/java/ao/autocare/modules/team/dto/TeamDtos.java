package ao.autocare.modules.team.dto;

import ao.autocare.domain.Invitation;
import ao.autocare.domain.Membership;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.security.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Pedidos e respostas da gestão de equipa. */
public final class TeamDtos {

    private TeamDtos() {}

    // ---- membros ------------------------------------------------------
    public record MemberView(
            String id,
            String userId,
            String name,
            String email,
            String phone,
            MembershipRole role,
            String roleLabel,
            String jobTitle,
            boolean suspended,
            Instant joinedAt,
            /** O que esta pessoa pode fazer, tudo somado. */
            List<String> permissions,
            /** Dadas por cima do papel. */
            List<String> granted,
            /** Tiradas ao papel. */
            List<String> denied) {

        public static MemberView of(Membership m) {
            User u = m.getUser();
            return new MemberView(
                    m.getId(), u.getId(), u.getName(), u.getEmail(), u.getPhone(),
                    m.getRole(), m.getRole().label(), m.getJobTitle(),
                    m.isSuspended(), m.getCreatedAt(),
                    codes(m.effectivePermissions()),
                    codes(m.grantedPermissions()),
                    codes(m.deniedPermissions()));
        }

        private static List<String> codes(java.util.Set<Permission> ps) {
            return ps.stream().map(Enum::name).sorted().toList();
        }
    }

    /** Uma permissão do catálogo, com o que o ecrã precisa para a explicar. */
    public record PermissionView(String code, String module, String label,
                                 List<String> defaultRoles) {

        public static PermissionView of(Permission p) {
            return new PermissionView(p.name(), p.module(), p.label(),
                    p.defaultRoles().stream().map(Enum::name).sorted().toList());
        }
    }

    public record UpdateMemberRequest(
            MembershipRole role,
            @Size(max = 120) String jobTitle,
            Boolean suspended,
            /** Substitui a lista de permissões dadas. Ausente: não mexe. */
            List<String> granted,
            /** Substitui a lista de permissões tiradas. Ausente: não mexe. */
            List<String> denied) {}

    // ---- convites -----------------------------------------------------
    public record InviteRequest(
            @NotBlank(message = "Indique o email de quem quer convidar.")
            @Email(message = "O email indicado não é válido.")
            @Size(max = 190) String email,
            @Size(max = 160) String name,
            @Size(max = 120) String jobTitle,
            @NotNull(message = "Escolha o papel desta pessoa na empresa.")
            MembershipRole role) {}

    public record InvitationView(
            String id,
            String email,
            String invitedName,
            String jobTitle,
            MembershipRole role,
            String roleLabel,
            String status,
            String invitedByName,
            Instant expiresAt,
            Instant createdAt,
            Instant acceptedAt) {

        public static InvitationView of(Invitation i) {
            String by = null;
            try {
                by = i.getInvitedBy() != null ? i.getInvitedBy().getName() : null;
            } catch (RuntimeException ignored) {
                // utilizador removido entretanto — o convite continua a ser mostrável
            }
            return new InvitationView(
                    i.getId(), i.getEmail(), i.getInvitedName(), i.getJobTitle(),
                    i.getRole(), i.getRole().label(), i.statusLabel(), by,
                    i.getExpiresAt(), i.getCreatedAt(), i.getAcceptedAt());
        }
    }

    /**
     * Resposta ao criar/reenviar um convite. Enquanto não houver serviço de email
     * configurado, {@code demoMode} é {@code true} e o link vem na resposta para
     * o gestor o poder enviar por outro meio (regra: nada de integrações falsas).
     */
    public record InviteResponse(
            InvitationView invitation,
            boolean demoMode,
            String message,
            String acceptUrl,
            String token) {}

    /** O que a pessoa convidada vê antes de aceitar (endpoint público). */
    public record InvitationPreview(
            String organizationName,
            String email,
            String invitedName,
            MembershipRole role,
            String roleLabel,
            String invitedByName,
            Instant expiresAt,
            boolean accountExists) {}

    public record AcceptInvitationRequest(
            @NotBlank(message = "Indique o seu nome.")
            @Size(max = 160) String name,
            @NotBlank(message = "Escolha uma palavra-passe.")
            @Size(min = 8, max = 72, message = "A palavra-passe deve ter pelo menos 8 caracteres.")
            String password) {}

    /** Aceitação por quem já tem conta (autenticado). */
    public record AcceptWithAccountRequest(
            @NotBlank(message = "Falta o código do convite.") String token) {}
}
