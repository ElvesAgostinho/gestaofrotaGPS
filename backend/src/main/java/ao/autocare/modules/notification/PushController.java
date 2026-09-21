package ao.autocare.modules.notification;

import ao.autocare.common.ApiException;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.modules.org.OrgContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autorizar (e retirar) os avisos no telemóvel.
 *
 * <p>O browser gera a morada e as chaves quando a pessoa aceita receber
 * notificações; a aplicação manda-as para aqui. Retirar é igualmente simples —
 * e o botão de terminar sessão fá-lo, para um telemóvel devolvido à empresa
 * não continuar a receber os avisos de quem saiu.
 */
@Tag(name = "Notificações")
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    /** O que o browser entrega quando a pessoa aceita os avisos. */
    public record SubscricaoRequest(String endpoint, String p256dh, String auth) {}

    private final PushService push;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrgContext orgContext;

    public PushController(PushService push, UserRepository users,
            OrganizationRepository organizations, OrgContext orgContext) {
        this.push = push;
        this.users = users;
        this.organizations = organizations;
        this.orgContext = orgContext;
    }

    @Operation(summary = "A chave pública desta instalação, para o browser subscrever")
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", push.publicKey());
    }

    @Operation(summary = "Autorizar os avisos neste telemóvel")
    @PostMapping("/subscriptions")
    public Map<String, String> subscribe(@AuthenticationPrincipal AuthPrincipal p,
            @RequestBody SubscricaoRequest req, HttpServletRequest http) {
        if (req == null || req.endpoint() == null || req.endpoint().isBlank()
                || req.p256dh() == null || req.auth() == null) {
            throw ApiException.badRequest("Dados de subscrição incompletos.");
        }
        var user = users.findById(p.id())
                .orElseThrow(() -> ApiException.notFound("Utilizador não encontrado."));
        // Sem empresa ainda associada, guarda-se na mesma: o aviso é da pessoa.
        String orgId = p.organizationId();
        push.subscrever(user, orgId, req.endpoint(), req.p256dh(), req.auth(),
                http.getHeader("User-Agent"), organizations::getReferenceById);
        return Map.of("message", "Este telemóvel passa a receber os avisos.");
    }

    @Operation(summary = "Deixar de receber avisos neste telemóvel")
    @DeleteMapping("/subscriptions")
    public Map<String, String> unsubscribe(@AuthenticationPrincipal AuthPrincipal p,
            @RequestBody SubscricaoRequest req) {
        if (req != null && req.endpoint() != null) {
            push.remover(p.id(), req.endpoint());
        }
        return Map.of("message", "Avisos desligados neste telemóvel.");
    }
}
