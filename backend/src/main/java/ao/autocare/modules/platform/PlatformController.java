package ao.autocare.modules.platform;

import ao.autocare.modules.platform.PlatformDtos.CreateOrganizationRequest;
import ao.autocare.modules.platform.PlatformDtos.CreatedOrganization;
import ao.autocare.modules.platform.PlatformDtos.OrganizationList;
import ao.autocare.modules.platform.PlatformDtos.OrganizationRow;
import ao.autocare.modules.platform.PlatformDtos.OwnerPasswordReset;
import ao.autocare.modules.platform.PlatformDtos.Summary;
import ao.autocare.modules.platform.PlatformDtos.SuspendRequest;
import ao.autocare.modules.platform.PlatformDtos.UpdateOrganizationRequest;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O ecrã «Plataforma»: as empresas clientes do sistema.
 *
 * <p>Tudo o que está em {@code /api/v1/admin/**} exige o papel ADMIN
 * (administrador da plataforma) — ver {@code SecurityConfig}. Um Dono de
 * empresa, por mais poderes que tenha na sua empresa, não entra aqui.
 */
@Tag(name = "Plataforma")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/platform")
public class PlatformController {

    private final PlatformService service;

    public PlatformController(PlatformService service) {
        this.service = service;
    }

    @Operation(summary = "[Plataforma] Números gerais")
    @GetMapping("/summary")
    public Summary summary() {
        return service.summary();
    }

    @Operation(summary = "[Plataforma] Todas as empresas")
    @GetMapping("/organizations")
    public OrganizationList list() {
        return new OrganizationList(service.list());
    }

    @Operation(summary = "[Plataforma] Uma empresa")
    @GetMapping("/organizations/{id}")
    public OrganizationRow get(@PathVariable String id) {
        return service.get(id);
    }

    @Operation(summary = "[Plataforma] Criar uma empresa cliente e o seu Dono")
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedOrganization create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateOrganizationRequest req) {
        return service.create(req, principal.id());
    }

    @Operation(summary = "[Plataforma] Alterar nome, licença ou notas")
    @PutMapping("/organizations/{id}")
    public OrganizationRow update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateOrganizationRequest req) {
        return service.update(id, req, principal.id());
    }

    @Operation(summary = "[Plataforma] Suspender: os utilizadores da empresa deixam de poder trabalhar")
    @PostMapping("/organizations/{id}/suspend")
    public OrganizationRow suspend(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) SuspendRequest req) {
        return service.suspend(id, req != null ? req.reason() : null, principal.id());
    }

    @Operation(summary = "[Plataforma] Reativar uma empresa suspensa")
    @PostMapping("/organizations/{id}/activate")
    public OrganizationRow activate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id) {
        return service.activate(id, principal.id());
    }

    @Operation(summary = "[Plataforma] Nova palavra-passe temporária para o Dono da empresa")
    @PostMapping("/organizations/{id}/owner-password")
    public OwnerPasswordReset resetOwnerPassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id) {
        return service.resetOwnerPassword(id, principal.id());
    }
}
