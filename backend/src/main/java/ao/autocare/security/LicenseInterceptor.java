package ao.autocare.security;

import ao.autocare.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Trava os utilizadores de uma empresa suspensa ou com a licença vencida.
 *
 * <p>Os dados ficam intactos e a sessão continua a valer: o utilizador
 * consegue entrar, ver quem é e ler o aviso na página da empresa — só não
 * consegue trabalhar. Quem resolve é o administrador da plataforma, que não
 * é travado (não é membro da empresa).
 */
@Component
public class LicenseInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return true;
        }
        if (principal.organizationBlocked() == null || principal.admin()) {
            return true;
        }
        String path = request.getRequestURI();
        boolean permitido = path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/config")
                || path.equals("/api/v1/health")
                || (path.equals("/api/v1/organization") && "GET".equals(request.getMethod()));
        if (permitido) {
            return true;
        }
        throw ApiException.forbidden(principal.organizationBlocked());
    }
}
