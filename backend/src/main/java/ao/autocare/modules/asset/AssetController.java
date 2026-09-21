package ao.autocare.modules.asset;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.asset.dto.AssetDtos.AssetSummary;
import ao.autocare.modules.asset.dto.AssetDtos.AssetView;
import ao.autocare.modules.asset.dto.AssetDtos.CreateAssetRequest;
import ao.autocare.modules.asset.dto.AssetDtos.CriticalityRequest;
import ao.autocare.modules.asset.dto.AssetDtos.CriticalityView;
import ao.autocare.modules.asset.dto.AssetDtos.UpdateAssetRequest;
import ao.autocare.common.PagedResponse;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ativos")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService service;
    private final AssetRetirementService retirement;
    private final OrgContext orgContext;
    private final AssetSheetPdfService sheetPdf;
    private final AssetHistoryPdfService historyPdf;
    private final ao.autocare.modules.org.DocumentSealService seals;

    public AssetController(AssetService service, OrgContext orgContext,
            AssetRetirementService retirement,
            AssetSheetPdfService sheetPdf,
            AssetHistoryPdfService historyPdf,
            ao.autocare.modules.org.DocumentSealService seals) {
        this.sheetPdf = sheetPdf;
        this.historyPdf = historyPdf;
        this.seals = seals;
        this.service = service;
        this.retirement = retirement;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Listar ativos")
    @GetMapping
    public PagedResponse<AssetSummary> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by("tag").ascending());
        return service.list(orgContext.requireOrganizationId(principal), archived, q, pageable);
    }

    @Operation(summary = "Ficha de equipamento em PDF",
            description = "Com o timbre da empresa. Sem valores para quem nao os pode ver.")
    @GetMapping(value = "/{id}/sheet.pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> sheet(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        byte[] pdf = sheetPdf.render(orgContext.requireOrganizationId(principal), id,
                canSeeCosts(principal));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"ficha-equipamento.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Histórico de manutenção em PDF (a «pasta da viatura»)",
            description = "Todas as ordens feitas — o quê, quando, aos quantos km, por quem, "
                    + "com que peças — e as tarefas de plano executadas. Com o timbre da "
                    + "empresa. Sem valores para quem não os pode ver.")
    @GetMapping(value = "/{id}/history.pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> history(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        String orgId = orgContext.requireOrganizationId(principal);
        AssetView ativo = service.get(orgId, id);
        byte[] pdf = seals.emitir(orgId, principal.id(), "ASSET_HISTORY", id, ativo.tag(),
                selo -> historyPdf.render(orgId, id, canSeeCosts(principal), selo));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"historico-manutencao.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Obter um ativo")
    @GetMapping("/{id}")
    public AssetView get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        AssetView v = service.get(orgContext.requireOrganizationId(principal), id);
        return canSeeCosts(principal) ? v : v.withoutMoney();
    }

    /** Valores financeiros so de gestor para cima -- a mesma regra das ordens. */
    /** Valores financeiros só a quem tem a permissão de custos. */
    private static boolean canSeeCosts(AuthPrincipal p) {
        return p != null && p.has(Permission.COSTS_VIEW);
    }

    @Operation(summary = "Criar um ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAssetRequest req) {
        return service.create(orgContext.requireOrganizationId(principal), principal.id(), req);
    }

    @Operation(summary = "Atualizar um ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PatchMapping("/{id}")
    public AssetView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateAssetRequest req) {
        return service.update(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }


    // ==== Abate ============================================================

    /** O que o ecrã envia ao abater. */
    public record AbateRequest(
            String reason,
            java.time.Instant retiredAt,
            java.math.BigDecimal finalMeter,
            java.math.BigDecimal residualValue,
            String notes) {}

    @Operation(summary = "Ativos abatidos",
            description = "Os que saíram da frota, com o motivo, o contador final, quanto "
                    + "renderam e quanto custaram em manutenção ao longo da vida.")
    @GetMapping("/retired")
    public java.util.List<AssetRetirementService.Abate> retired(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return retirement.listar(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Abater um ativo",
            description = "Sai das listas e dos indicadores, mas o histórico fica: é ele "
                    + "que diz se valeu a pena. Não se abate com ordens por fechar.")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping("/{id}/retire")
    public AssetRetirementService.Abate retire(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @RequestBody AbateRequest req) {
        return retirement.abater(orgContext.requireOrganizationId(principal), principal.id(), id,
                req.reason(), req.retiredAt(), req.finalMeter(), req.residualValue(), req.notes());
    }

    @Operation(summary = "Reverter o abate de um ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping("/{id}/unretire")
    public AssetRetirementService.Abate unretire(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return retirement.reverter(orgContext.requireOrganizationId(principal), principal.id(), id);
    }

    @Operation(summary = "Arquivar / desarquivar um ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PostMapping("/{id}/archive")
    public Map<String, String> archive(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean archived) {
        service.archive(orgContext.requireOrganizationId(principal), principal.id(), id, archived);
        return Map.of("message", archived ? "Ativo arquivado." : "Ativo reativado.");
    }

    @Operation(summary = "Eliminar um ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        service.delete(orgContext.requireOrganizationId(principal), principal.id(), id);
        return Map.of("message", "Ativo eliminado.");
    }

    @Operation(summary = "Matriz de criticidade do ativo")
    @GetMapping("/{id}/criticality")
    public CriticalityView getCriticality(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return service.getCriticality(orgContext.requireOrganizationId(principal), id);
    }

    @Operation(summary = "Definir a matriz de criticidade do ativo")
    @RequirePermission(Permission.ASSETS_MANAGE)
    @PutMapping("/{id}/criticality")
    public CriticalityView setCriticality(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody CriticalityRequest req) {
        return service.setCriticality(orgContext.requireOrganizationId(principal), principal.id(), id, req);
    }
}
