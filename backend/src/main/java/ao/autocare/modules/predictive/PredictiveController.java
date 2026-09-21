package ao.autocare.modules.predictive;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ProgramView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ReadingRecorded;
import ao.autocare.modules.predictive.dto.PredictiveDtos.ReadingView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.RecordReadingRequest;
import ao.autocare.modules.predictive.dto.PredictiveDtos.SaveProgramRequest;
import ao.autocare.modules.predictive.dto.PredictiveDtos.TechniqueView;
import ao.autocare.modules.predictive.dto.PredictiveDtos.UpdateProgramRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Manutenção preditiva")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class PredictiveController {

    private final PredictiveService predictive;
    private final FailureForecastService forecasts;
    private final OrgContext orgContext;

    public PredictiveController(PredictiveService predictive, OrgContext orgContext,
            FailureForecastService forecasts) {
        this.predictive = predictive;
        this.orgContext = orgContext;
        this.forecasts = forecasts;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Próximas avarias prováveis por sistema (avarias repetidas + ritmo de uso + consumo)")
    @GetMapping("/api/v1/predictive/forecast")
    public java.util.List<FailureForecastService.Forecast> forecast(@AuthenticationPrincipal AuthPrincipal principal) {
        return forecasts.forOrganization(orgContext.requireOrganizationId(principal));
    }

    @Operation(summary = "Próximas avarias prováveis de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/predictive/forecast")
    public java.util.List<FailureForecastService.Forecast> forecastForAsset(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return forecasts.forAsset(orgContext.requireOrganizationId(principal), assetId);
    }

    @Operation(summary = "Técnicas disponíveis e periodicidade habitual")
    @GetMapping("/api/v1/predictive/techniques")
    public List<TechniqueView> techniques() {
        return predictive.techniques();
    }

    @Operation(summary = "Programas preditivos da frota")
    @GetMapping("/api/v1/predictive")
    public List<ProgramView> fleet(
            @AuthenticationPrincipal AuthPrincipal p,
            @Parameter(description = "OK, DUE_SOON ou OVERDUE")
            @RequestParam(required = false) String status) {
        return predictive.listForOrg(org(p), status);
    }

    @Operation(summary = "Programas preditivos de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/predictive")
    public List<ProgramView> forAsset(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return predictive.listForAsset(org(p), assetId);
    }

    @Operation(summary = "Criar um programa preditivo")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/assets/{assetId}/predictive")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramView create(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody SaveProgramRequest req) {
        return predictive.create(org(p), p.id(), assetId, req);
    }

    @Operation(summary = "Aplicar o conjunto do documento de referência",
            description = "Vibração mensal, termografia trimestral e análise de óleo semestral.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/assets/{assetId}/predictive/standard")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProgramView> applyStandard(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return predictive.applyStandardSet(org(p), p.id(), assetId);
    }

    @Operation(summary = "Atualizar um programa preditivo",
            description = "A técnica não se altera: para mudar, remova e crie outro programa.")
    @RequireRole(MembershipRole.MANAGER)
    @PatchMapping("/api/v1/predictive/{programId}")
    public ProgramView update(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String programId,
            @Valid @RequestBody UpdateProgramRequest req) {
        return predictive.update(org(p), p.id(), programId, req);
    }

    @Operation(summary = "Remover um programa preditivo")
    @RequireRole(MembershipRole.MANAGER)
    @DeleteMapping("/api/v1/predictive/{programId}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String programId) {
        predictive.delete(org(p), p.id(), programId);
        return Map.of("message", "Programa removido.");
    }

    @Operation(summary = "Registar uma medição",
            description = "Reagenda o programa e, se for pedido, abre uma ordem corretiva.")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/predictive/{programId}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingRecorded record(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String programId,
            @Valid @RequestBody RecordReadingRequest req) {
        return predictive.record(org(p), p.id(), programId, req);
    }

    @Operation(summary = "Histórico de medições de um programa")
    @GetMapping("/api/v1/predictive/{programId}/readings")
    public PagedResponse<ReadingView> history(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String programId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return predictive.history(org(p), programId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Histórico preditivo de um ativo (todas as técnicas)")
    @GetMapping("/api/v1/assets/{assetId}/predictive-readings")
    public PagedResponse<ReadingView> assetHistory(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return predictive.historyForAsset(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }
}
