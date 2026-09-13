package ao.autocare.security;

/**
 * Utilizador autenticado, exposto aos controladores via {@code @AuthenticationPrincipal}.
 *
 * @param organizationId organização principal do utilizador (pode ser {@code null}
 *                       para contas antigas sem organização — os módulos CMMS
 *                       devolvem um erro amigável nesse caso).
 * @param role           papel na organização principal ({@code null} se não houver).
 */
public record AuthPrincipal(
        String id,
        String email,
        boolean admin,
        String organizationId,
        String role,
        /** Permissões efetivas na organização: as do papel, mais e menos as do membro. */
        java.util.Set<Permission> permissions,
        /**
         * Frase que explica por que a empresa está bloqueada (suspensa pela
         * plataforma ou licença vencida), ou nulo quando pode trabalhar.
         */
        String organizationBlocked) {

    public AuthPrincipal(String id, String email, boolean admin, String organizationId,
            String role, java.util.Set<Permission> permissions) {
        this(id, email, admin, organizationId, role, permissions, null);
    }

    /** Construtor antigo, para quem não tem permissões a dar: só as do papel. */
    public AuthPrincipal(String id, String email, boolean admin, String organizationId,
            String role) {
        this(id, email, admin, organizationId, role,
                Permission.defaultsFor(parseRole(role)), null);
    }

    /** Pode fazer isto? O administrador da plataforma pode tudo. */
    public boolean has(Permission p) {
        return admin || (permissions != null && permissions.contains(p));
    }

    private static ao.autocare.domain.enums.Enums.MembershipRole parseRole(String role) {
        if (role == null) {
            return null;
        }
        try {
            return ao.autocare.domain.enums.Enums.MembershipRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
