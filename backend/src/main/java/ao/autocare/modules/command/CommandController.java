package ao.autocare.modules.command;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import ao.autocare.domain.enums.Enums.LockReasonCategory;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.command.dto.CommandDtos.CommandView;
import ao.autocare.modules.command.dto.CommandDtos.DeviceSyncView;
import ao.autocare.modules.command.dto.CommandDtos.ProviderHealthView;
import ao.autocare.modules.command.dto.CommandDtos.ReasonCategoryView;
import ao.autocare.modules.command.dto.CommandDtos.LockStatusView;
import ao.autocare.modules.command.dto.CommandDtos.RequestCommandRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bloqueio remoto do motor.
 *
 * <p>Tudo o que mexe no motor exige o papel de <b>Dono</b>. Um gestor de
 * manutenção não bloqueia viaturas: a decisão tem consequências de segurança e
 * legais que ultrapassam a gestão da frota.
 */
@Tag(name = "Bloqueio remoto")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class CommandController {

    private final CommandService service;
    private final OrgContext orgContext;

    public CommandController(CommandService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Estado da ligação ao servidor de comandos (Traccar)",
            description = "Testa a ligação sem enviar nada a nenhum aparelho.")
    @RequireRole(MembershipRole.MANAGER)
    @GetMapping("/api/v1/telemetry/traccar/status")
    public ProviderHealthView providerHealth(@AuthenticationPrincipal AuthPrincipal p) {
        org(p);
        return service.providerHealth();
    }

    @Operation(summary = "Sincronizar um aparelho com o fornecedor",
            description = "Lê o protocolo e os comandos que o aparelho aceita. "
                    + "Não envia nada ao aparelho.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/gps-devices/{deviceId}/sync")
    public DeviceSyncView syncDevice(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String deviceId) {
        return service.syncDevice(org(p), p.id(), deviceId);
    }

    @Operation(summary = "Categorias de motivo de bloqueio")
    @GetMapping("/api/v1/commands/reason-categories")
    public List<ReasonCategoryView> reasonCategories() {
        return Arrays.stream(LockReasonCategory.values())
                .map(r -> new ReasonCategoryView(r, r.label())).toList();
    }

    @Operation(summary = "Estado de bloqueio de um ativo",
            description = "Diz se há fornecedor configurado e se é seguro cortar agora.")
    @GetMapping("/api/v1/assets/{assetId}/lock")
    public LockStatusView status(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return service.lockStatus(org(p), assetId);
    }

    @Operation(summary = "Pedir o bloqueio do motor",
            description = "Não bloqueia já: exige aprovação e espera que a viatura pare.")
    @RequirePermission(Permission.COMMANDS_LOCK)
    @PostMapping("/api/v1/assets/{assetId}/lock")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandView requestLock(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody RequestCommandRequest req) {
        return service.request(org(p), p.id(), assetId, DeviceCommandKind.ENGINE_STOP, req);
    }

    @Operation(summary = "Desbloquear o motor",
            description = "Imediato e sem aprovação: não conseguir desbloquear é um perigo.")
    @RequirePermission(Permission.COMMANDS_LOCK)
    @PostMapping("/api/v1/assets/{assetId}/unlock")
    @ResponseStatus(HttpStatus.CREATED)
    public CommandView requestUnlock(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody RequestCommandRequest req) {
        return service.request(org(p), p.id(), assetId, DeviceCommandKind.ENGINE_RESUME, req);
    }

    @Operation(summary = "Aprovar um pedido de bloqueio",
            description = "Com mais do que um dono na empresa, tem de ser outra pessoa.")
    @RequirePermission(Permission.COMMANDS_LOCK)
    @PostMapping("/api/v1/commands/{id}/approve")
    public CommandView approve(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.approve(org(p), p.id(), id);
    }

    @Operation(summary = "Anular um pedido por executar")
    @RequirePermission(Permission.COMMANDS_LOCK)
    @PostMapping("/api/v1/commands/{id}/cancel")
    public CommandView cancel(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.cancel(org(p), p.id(), id);
    }

    @Operation(summary = "Declarar a confirmação sem prova do aparelho",
            description = "Use apenas quando o protocolo não reporta estado e alguém "
                    + "verificou no local. Fica marcado como declaração, não como prova.")
    @RequirePermission(Permission.COMMANDS_LOCK)
    @PostMapping("/api/v1/commands/{id}/confirm")
    public CommandView confirm(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.confirmManually(org(p), p.id(), id);
    }

    @Operation(summary = "Histórico de comandos da frota")
    @RequireRole(MembershipRole.MANAGER)
    @GetMapping("/api/v1/commands")
    public PagedResponse<CommandView> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.list(org(p),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Histórico de comandos de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/commands")
    public PagedResponse<CommandView> listForAsset(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.listForAsset(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }
}
