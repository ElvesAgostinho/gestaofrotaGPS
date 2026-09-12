package ao.autocare.security;

import ao.autocare.common.ApiException;
import ao.autocare.domain.enums.Enums.MembershipRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica a matriz de permissões da empresa: lê a anotação {@link RequireRole}
 * do endpoint e compara-a com o papel do utilizador na sua organização.
 *
 * <p>Corre depois do {@link JwtAuthenticationFilter}, por isso o utilizador já
 * está autenticado; aqui decide-se apenas o que ele pode <em>fazer</em>.
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequirePermission permission = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequirePermission.class);
        if (permission == null) {
            permission = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RequirePermission.class);
        }
        if (permission != null) {
            checkPermission(permission.value());
        }

        RequireRole required = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequireRole.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RequireRole.class);
        }
        if (required == null) {
            return true;
        }

        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.unauthorized("Precisa de iniciar sessão para aceder a este recurso.");
        }
        // Administrador da plataforma (suporte) não é membro da empresa mas pode agir.
        if (principal.admin()) {
            return true;
        }
        if (principal.role() == null) {
            throw ApiException.forbidden("A sua conta ainda não está associada a nenhuma empresa.");
        }

        MembershipRole mine = parse(principal.role());
        if (mine == null || !mine.covers(required.value())) {
            throw ApiException.forbidden(
                    "Não tem permissão para esta ação. É necessário o papel de "
                            + required.value().label() + " ou superior"
                            + (mine != null ? " (o seu papel é " + mine.label() + ")." : "."));
        }
        return true;
    }

    /**
     * A permissão de módulo, por cima do papel.
     *
     * <p>A mensagem diz o nome da permissão que falta, com as palavras que o
     * administrador vê no ecrã de utilizadores — é lá que a vai dar.
     */
    private void checkPermission(Permission p) {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.unauthorized("Precisa de iniciar sessão para aceder a este recurso.");
        }
        if (principal.admin()) {
            return;
        }
        if (principal.role() == null) {
            throw ApiException.forbidden("A sua conta ainda não está associada a nenhuma empresa.");
        }
        if (!principal.has(p)) {
            throw ApiException.forbidden(
                    "Não tem a permissão «" + p.label() + "». "
                            + "Peça ao administrador da empresa para a atribuir.");
        }
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    private MembershipRole parse(String role) {
        try {
            return MembershipRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
