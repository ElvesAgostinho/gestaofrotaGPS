package ao.autocare.modules.fuel;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.fuel.dto.FuelDtos.ConsumptionSummary;
import ao.autocare.modules.fuel.dto.FuelDtos.FuelRecordView;
import ao.autocare.modules.fuel.dto.FuelDtos.SaveFuelRecordRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.modules.fuel.dto.FuelControlDtos.BaselineView;
import ao.autocare.modules.fuel.dto.FuelControlDtos.FuelAnomalyView;
import ao.autocare.modules.fuel.dto.FuelControlDtos.FuelDashboard;
import ao.autocare.modules.fuel.dto.FuelControlDtos.ResolveAnomalyRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Combustível")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class FuelController {

    private final FuelService fuel;
    private final FuelAnalyticsService analytics;
    private final OrgContext orgContext;

    public FuelController(FuelService fuel, OrgContext orgContext, FuelAnalyticsService analytics) {
        this.fuel = fuel;
        this.analytics = analytics;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Registar um abastecimento")
    @RequirePermission(Permission.FUEL_RECORD)
    @PostMapping("/api/v1/assets/{assetId}/fuel")
    @ResponseStatus(HttpStatus.CREATED)
    public FuelRecordView record(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody SaveFuelRecordRequest req) {
        return fuel.record(org(p), p.id(), assetId, req);
    }

    @Operation(summary = "Abastecimentos de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/fuel")
    public PagedResponse<FuelRecordView> history(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return fuel.history(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Resumo de consumo de um ativo",
            description = "Inclui o texto que explica o que o sistema faz e não faz.")
    @GetMapping("/api/v1/assets/{assetId}/fuel/summary")
    public ConsumptionSummary summary(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return fuel.summary(org(p), assetId);
    }

    @Operation(summary = "Remover um abastecimento mal lançado")
    @RequirePermission(Permission.FUEL_MANAGE)
    @DeleteMapping("/api/v1/fuel/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        fuel.delete(org(p), p.id(), id);
        return Map.of("message", "Abastecimento removido.");
    }

    // ==== Controlo de consumo (Fatia 15) ===================================
    @Operation(summary = "Anomalias de combustível",
            description = "O que não bate certo, com litros e dinheiro em risco.")
    @RequirePermission(Permission.FUEL_MANAGE)
    @GetMapping("/api/v1/fuel/anomalies")
    public PagedResponse<FuelAnomalyView> anomalies(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) AnomalyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return analytics.list(org(p), status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Anomalias de combustível de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/fuel/anomalies")
    public List<FuelAnomalyView> assetAnomalies(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return analytics.forAsset(org(p), assetId);
    }

    @Operation(summary = "Fechar uma anomalia",
            description = "A explicação é obrigatória: uma anomalia fechada em branco "
                    + "é indistinguível de uma anomalia escondida.")
    @RequirePermission(Permission.FUEL_MANAGE)
    @PostMapping("/api/v1/fuel/anomalies/{id}/resolve")
    public FuelAnomalyView resolveAnomaly(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ResolveAnomalyRequest req) {
        return analytics.resolve(org(p), p.id(), id, req);
    }

    @Operation(summary = "Base de consumo de cada ativo",
            description = "Cada ativo é comparado consigo próprio, nunca com os outros.")
    @GetMapping("/api/v1/fuel/baselines")
    public List<BaselineView> baselines(@AuthenticationPrincipal AuthPrincipal p) {
        return analytics.baselines(org(p));
    }

    @Operation(summary = "Painel de combustível",
            description = "Custo por km, por filial e por motorista — e quanto do "
                    + "dinheiro gasto está por explicar.")
    @RequirePermission(Permission.FUEL_MANAGE)
    @GetMapping("/api/v1/fuel/dashboard")
    public FuelDashboard dashboard(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant fim = to != null ? to : Instant.now();
        Instant inicio = from != null ? from : fim.minus(90, java.time.temporal.ChronoUnit.DAYS);
        return analytics.dashboard(org(p), inicio, fim);
    }
}
