package ao.autocare.modules.notification;

import ao.autocare.common.ApiException;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.UserRepository;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnóstico do envio de email.
 *
 * <p>Existe porque configurar SMTP é sempre uma sequência de tentativas — porta
 * errada, TLS em falta, palavra-passe de aplicação em vez da normal. Sem uma
 * forma de testar, só se descobre que está mal quando um convite não chega, e
 * nessa altura ninguém sabe porquê.
 */
@Tag(name = "Diagnóstico de email")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/email")
public class EmailDiagnosticsController {

    private final EmailSender email;
    private final UserRepository users;
    private final AuditService audit;
    private final String host;
    private final String from;

    public EmailDiagnosticsController(
            EmailSender email,
            UserRepository users,
            AuditService audit,
            @Value("${spring.mail.host:}") String host,
            @Value("${autocare.mail.from:${spring.mail.username:}}") String from) {
        this.email = email;
        this.users = users;
        this.audit = audit;
        this.host = host;
        this.from = from;
    }

    /**
     * Estado da configuração.
     *
     * @param remetente devolvido para se confirmar que é o esperado; não é
     *                  segredo. A palavra-passe nunca sai daqui.
     */
    public record EmailStatus(
            boolean configured, String host, String remetente, String mensagem) {}

    public record TestResult(boolean sent, String destinatario, String mensagem, Instant at) {}

    @Operation(summary = "Ver se o envio de email está configurado")
    @RequireRole(MembershipRole.OWNER)
    @GetMapping("/status")
    public EmailStatus status() {
        if (!email.isConfigured()) {
            return new EmailStatus(false, null, null,
                    "Sem servidor de email configurado. Os avisos existem dentro da "
                            + "aplicação mas nenhum email sai. Defina MAIL_HOST, MAIL_PORT, "
                            + "MAIL_USERNAME, MAIL_PASSWORD e MAIL_FROM — ver docs/EMAIL.md.");
        }
        return new EmailStatus(true, host, from,
                "Servidor configurado. Use o teste de envio para confirmar que funciona.");
    }

    /**
     * Envia uma mensagem de teste. Por omissão para o próprio, que é o caso
     * seguro: testar contra o endereço de outra pessoa é enviar-lhe correio.
     */
    @Operation(summary = "Enviar um email de teste",
            description = "Por omissão para o próprio endereço de quem pede.")
    @RequireRole(MembershipRole.OWNER)
    @PostMapping("/test")
    public TestResult test(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Email String to) {

        User user = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
        String destination = to != null && !to.isBlank() ? to.trim() : user.getEmail();

        if (destination == null || destination.isBlank()) {
            throw ApiException.badRequest(
                    "A sua conta não tem email. Indique um endereço de destino.");
        }
        if (!email.isConfigured()) {
            // Falha explícita em vez de um "enviado" que não corresponde a nada.
            throw ApiException.conflict(
                    "Não há servidor de email configurado. Defina MAIL_HOST antes de testar.");
        }

        boolean sent = email.send(destination,
                "AutoCare — teste de configuração de email",
                "Se está a ler esta mensagem, o envio de email do AutoCare está a funcionar.\n\n"
                        + "Pedido por " + user.getName() + " em " + Instant.now() + ".");

        audit.record(principal.organizationId(), principal.id(),
                "email.test", "User", principal.id(),
                destination + " · " + (sent ? "enviado" : "falhou"));

        return new TestResult(sent, destination,
                sent ? "Mensagem entregue ao servidor de email. Verifique a caixa de entrada "
                        + "(e a pasta de spam)."
                     : "O servidor de email recusou a mensagem. Veja os registos do servidor "
                        + "para a causa exata.",
                Instant.now());
    }
}
