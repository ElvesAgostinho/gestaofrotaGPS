package ao.autocare.modules.integration;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.integration.IntegrationDtos.SettingsView;
import ao.autocare.modules.integration.IntegrationDtos.RoutingRequest;
import ao.autocare.modules.integration.IntegrationDtos.SmtpRequest;
import ao.autocare.modules.integration.IntegrationDtos.TestRequest;
import ao.autocare.modules.integration.IntegrationDtos.TestResult;
import ao.autocare.modules.integration.IntegrationDtos.TraccarRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credenciais de Traccar e de email da empresa.
 *
 * <p>So o dono mexe nisto: quem tem estas credenciais consegue bloquear
 * viaturas e enviar email em nome da empresa.
 */
@Tag(name = "Integracoes")
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final IntegrationService service;
    private final OrgContext orgContext;

    public IntegrationController(IntegrationService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Configuracao das integracoes",
            description = "As palavras-passe nunca saem: diz-se apenas se estao definidas.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @GetMapping
    public SettingsView get(@AuthenticationPrincipal AuthPrincipal p) {
        return SettingsView.of(service.forOrganization(org(p)));
    }

    @Operation(summary = "Guardar as credenciais do Traccar")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PutMapping("/traccar")
    public SettingsView saveTraccar(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody TraccarRequest req) {
        return SettingsView.of(service.saveTraccar(org(p), p.id(), req));
    }

    @Operation(summary = "Testar a ligacao ao Traccar",
            description = "Liga-se mesmo ao servidor indicado e valida as credenciais.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PostMapping("/traccar/test")
    public TestResult testTraccar(@AuthenticationPrincipal AuthPrincipal p) {
        return service.testTraccar(org(p), p.id());
    }

    @Operation(summary = "Ligar ou desligar a sondagem das posicoes do Traccar")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PutMapping("/traccar/poll")
    public SettingsView setPoll(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestBody IntegrationDtos.PollRequest req) {
        return SettingsView.of(service.setTraccarPoll(org(p), p.id(), req.enabled()));
    }

    @Operation(summary = "Gerar o segredo do encaminhamento (forward.url do Traccar)",
            description = "Mostrado uma unica vez. Gerar outro invalida o anterior.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PostMapping("/traccar/forward-secret")
    public IntegrationDtos.ForwardSecret forwardSecret(
            @AuthenticationPrincipal AuthPrincipal p,
            jakarta.servlet.http.HttpServletRequest http) {
        String segredo = service.newForwardSecret(org(p), p.id());
        String base = http.getScheme() + "://" + http.getServerName()
                + (http.getServerPort() == 80 || http.getServerPort() == 443 ? "" : ":" + http.getServerPort());
        return new IntegrationDtos.ForwardSecret(segredo,
                base + "/api/v1/telemetry/traccar/forward?secret=" + segredo);
    }

    @Operation(summary = "Guardar o servidor de email")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PutMapping("/email")
    public SettingsView saveSmtp(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SmtpRequest req) {
        return SettingsView.of(service.saveSmtp(org(p), p.id(), req));
    }

    @Operation(summary = "Enviar um email de teste",
            description = "Envia mesmo a mensagem: e a unica forma de saber se chega.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PostMapping("/email/test")
    public TestResult testSmtp(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody TestRequest req) {
        return service.testSmtp(org(p), p.id(), req.to());
    }

    @Operation(summary = "Guardar o endereco do motor de rotas")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PutMapping("/routing")
    public SettingsView saveRouting(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody RoutingRequest req) {
        return SettingsView.of(service.saveRouting(org(p), p.id(), req.url()));
    }

    @Operation(summary = "Testar o motor de rotas",
            description = "Pede um percurso a serio: um motor sem mapa carregado nao passa.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PostMapping("/routing/test")
    public TestResult testRouting(@AuthenticationPrincipal AuthPrincipal p) {
        return service.testRouting(org(p), p.id());
    }
}
