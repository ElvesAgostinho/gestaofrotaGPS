package ao.autocare.security;

import ao.autocare.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Trava tentativas repetidas nos pontos que um atacante usaria.
 *
 * <p>Os limites são deliberadamente diferentes por endpoint, porque o que se
 * protege é diferente em cada um: o início de sessão contra adivinhação de
 * palavras-passe, o registo e a reposição contra criação de contas e envio de
 * emails em massa, e a entrada de posições contra um aparelho avariado a
 * inundar o servidor.
 *
 * <p>Desligável com {@code autocare.rate-limit.enabled=false} (desligado nos
 * testes, que fazem centenas de registos seguidos do mesmo endereço).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Uma regra: caminho, método, quantas tentativas e em que janela. */
    private record Rule(String path, String method, int limit, Duration window, String key) {}

    private static final List<Rule> RULES = List.of(
            new Rule("/api/v1/auth/login", "POST", 10, Duration.ofMinutes(5), "login"),
            new Rule("/api/v1/auth/register", "POST", 5, Duration.ofHours(1), "registo"),
            new Rule("/api/v1/auth/forgot-password", "POST", 5, Duration.ofHours(1), "reposicao"),
            new Rule("/api/v1/auth/refresh", "POST", 60, Duration.ofMinutes(5), "refresh"),
            // O aparelho publica a cada 10-60 s; 120/min dá folga larga a uma
            // frota inteira atrás do mesmo endereço e trava um aparelho em ciclo.
            new Rule("/api/v1/telemetry/positions", "POST", 120, Duration.ofMinutes(1), "gps"),
            new Rule("/api/v1/invitations/", "POST", 20, Duration.ofHours(1), "convite"),
            // Bloqueio de motor: poucos pedidos legítimos por hora. Um token
            // roubado não pode servir para inundar a frota de pedidos de corte.
            new Rule("/api/v1/commands/", "POST", 30, Duration.ofHours(1), "comando"));

    /** Endpoints de bloqueio: o caminho traz o id do ativo pelo meio. */
    private static final List<Rule> SUFFIX_RULES = List.of(
            new Rule("/lock", "POST", 10, Duration.ofHours(1), "bloqueio"),
            new Rule("/unlock", "POST", 10, Duration.ofHours(1), "desbloqueio"));

    private final RateLimiter limiter;
    private final boolean enabled;

    public RateLimitInterceptor(
            RateLimiter limiter,
            @Value("${autocare.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Rule rule : RULES) {
            if (method.equalsIgnoreCase(rule.method()) && path.startsWith(rule.path())) {
                return enforce(request, response, rule);
            }
        }
        // /api/v1/assets/{id}/lock e /unlock: o id fica no meio do caminho.
        for (Rule rule : SUFFIX_RULES) {
            if (method.equalsIgnoreCase(rule.method()) && path.endsWith(rule.path())) {
                return enforce(request, response, rule);
            }
        }
        return true;
    }

    private boolean enforce(
            HttpServletRequest request, HttpServletResponse response, Rule rule) {

        String key = rule.key() + ":" + clientIp(request);
        if (!limiter.tryAcquire(key, rule.limit(), rule.window())) {
            long retry = limiter.retryAfterSeconds(key, rule.window());
            response.setHeader("Retry-After", String.valueOf(retry));
            throw ApiException.tooManyRequests(
                    "Demasiadas tentativas. Tente novamente daqui a " + describe(retry) + ".");
        }
        return true;
    }

    /**
     * Endereço do cliente. Atrás de um proxy o endereço da ligação é o do
     * proxy — todos os pedidos partilhariam o mesmo contador —, por isso
     * respeita-se {@code X-Forwarded-For} quando existe.
     *
     * <p>Este cabeçalho é falsificável por quem chegue diretamente ao servidor:
     * em produção a aplicação tem de estar atrás de um proxy que o reescreva.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "desconhecido";
    }

    private static String describe(long seconds) {
        if (seconds < 60) {
            return seconds + " segundos";
        }
        long minutes = Math.round(seconds / 60.0);
        return minutes == 1 ? "um minuto" : minutes + " minutos";
    }
}
