package ao.autocare.modules.report;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportações em CSV. Qualquer membro pode exportar o que já pode ver — o
 * controlo de acesso está nos dados, não no formato.
 */
@Tag(name = "Relatórios")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequirePermission(Permission.REPORTS_VIEW)
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final OrgContext orgContext;

    public ReportController(ReportService reports, OrgContext orgContext) {
        this.reports = reports;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Inventário de ativos (CSV)")
    @GetMapping("/assets.csv")
    public ResponseEntity<byte[]> assets(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.assets(org(p)), "ativos");
    }

    @Operation(summary = "Ordens de manutenção (CSV)")
    @GetMapping("/work-orders.csv")
    public ResponseEntity<byte[]> workOrders(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.workOrders(org(p)), "ordens-de-manutencao");
    }

    @Operation(summary = "Abastecimentos (CSV)",
            description = "Linha a linha, com consumo e o que ficou por explicar.")
    @GetMapping("/fuel.csv")
    public ResponseEntity<byte[]> fuel(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.fuel(org(p)), "abastecimentos");
    }

    @Operation(summary = "Custo de manutenção por ativo (CSV)",
            description = "Responde a: vale a pena continuar a reparar esta viatura?")
    @GetMapping("/maintenance-by-asset.csv")
    public ResponseEntity<byte[]> maintenanceByAsset(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.maintenanceByAsset(org(p)), "manutencao-por-ativo");
    }

    @Operation(summary = "Manutenção por oficina (CSV)")
    @GetMapping("/maintenance-by-supplier.csv")
    public ResponseEntity<byte[]> maintenanceBySupplier(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.maintenanceBySupplier(org(p)), "manutencao-por-oficina");
    }

    @Operation(summary = "Imobilização de viaturas (CSV)")
    @GetMapping("/maintenance-downtime.csv")
    public ResponseEntity<byte[]> maintenanceDowntime(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.maintenanceDowntime(org(p)), "imobilizacao");
    }

    @Operation(summary = "Stock por armazém (CSV)")
    @GetMapping("/stock.csv")
    public ResponseEntity<byte[]> stock(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.stock(org(p)), "stock");
    }

    @Operation(summary = "Viagens (CSV) — por omissão os últimos 30 dias")
    @GetMapping("/trips.csv")
    public ResponseEntity<byte[]> trips(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return csv(reports.trips(org(p), from, to), "viagens");
    }

    @Operation(summary = "Documentos e validades (CSV)")
    @GetMapping("/documents.csv")
    public ResponseEntity<byte[]> documents(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.documents(org(p)), "documentos");
    }

    @Operation(summary = "Programas de manutenção preditiva (CSV)")
    @GetMapping("/predictive.csv")
    public ResponseEntity<byte[]> predictive(@AuthenticationPrincipal AuthPrincipal p) {
        return csv(reports.predictive(org(p)), "preditiva");
    }

    @Operation(summary = "Indicadores do período (CSV)",
            description = "Disponibilidade, MTBF, MTTR e cumprimento do plano, "
                    + "com os números que alimentam as fórmulas.")
    @GetMapping("/kpis.csv")
    public ResponseEntity<byte[]> kpis(
            @AuthenticationPrincipal AuthPrincipal p,
            @Parameter(description = "Um ativo específico; vazio = toda a frota")
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return csv(reports.kpis(org(p), assetId, from, to), "indicadores");
    }

    /**
     * O CSV vai como anexo e em UTF-8. O tipo de conteúdo declara o charset
     * porque sem isso alguns navegadores adivinham mal e estragam os acentos.
     */
    private ResponseEntity<byte[]> csv(String content, String prefix) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + reports.fileName(prefix) + "\"")
                .body(bytes);
    }
}
