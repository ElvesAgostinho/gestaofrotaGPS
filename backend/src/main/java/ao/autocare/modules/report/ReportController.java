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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportações em CSV, Excel e PDF. Qualquer membro pode exportar o que já
 * pode ver — o controlo de acesso está nos dados, não no formato.
 */
@Tag(name = "Relatórios")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequirePermission(Permission.REPORTS_VIEW)
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final MonthlyReportService monthly;
    private final OrgContext orgContext;
    private final TablePdf tablePdf;

    public ReportController(ReportService reports, OrgContext orgContext, TablePdf tablePdf, MonthlyReportService monthly) {
        this.reports = reports;
        this.monthly = monthly;
        this.orgContext = orgContext;
        this.tablePdf = tablePdf;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Inventário de ativos (CSV)")
    @GetMapping("/assets.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> assets(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.assets(org(p)), "ativos", "Inventário de ativos");
    }

    @Operation(summary = "Ordens de manutenção (CSV)")
    @GetMapping("/work-orders.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> workOrders(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.workOrders(org(p)), "ordens-de-manutencao", "Ordens de manutenção");
    }

    @Operation(summary = "Abastecimentos (CSV)",
            description = "Linha a linha, com consumo e o que ficou por explicar.")
    @GetMapping("/fuel.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> fuel(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.fuel(org(p)), "abastecimentos", "Abastecimentos");
    }

    @Operation(summary = "Custo de manutenção por ativo (CSV)",
            description = "Responde a: vale a pena continuar a reparar esta viatura?")
    @GetMapping("/maintenance-by-asset.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> maintenanceByAsset(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.maintenanceByAsset(org(p)), "manutencao-por-ativo", "Custo de manutenção por ativo");
    }

    @Operation(summary = "Manutenção por oficina (CSV)")
    @GetMapping("/maintenance-by-supplier.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> maintenanceBySupplier(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.maintenanceBySupplier(org(p)), "manutencao-por-oficina", "Manutenção por oficina");
    }

    @Operation(summary = "Imobilização de viaturas (CSV)")
    @GetMapping("/maintenance-downtime.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> maintenanceDowntime(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.maintenanceDowntime(org(p)), "imobilizacao", "Imobilização de viaturas");
    }

    @Operation(summary = "Stock por armazém (CSV)")
    @GetMapping("/stock.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> stock(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.stock(org(p)), "stock", "Stock por armazém");
    }

    @Operation(summary = "Viagens (CSV) — por omissão os últimos 30 dias")
    @GetMapping("/trips.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> trips(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return exportar(p, ext, reports.trips(org(p), from, to), "viagens", "Viagens");
    }

    @Operation(summary = "Documentos e validades (CSV)")
    @GetMapping("/documents.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> documents(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.documents(org(p)), "documentos", "Documentos e validades");
    }

    @Operation(summary = "Relatório mensal da frota em PDF (o mesmo que segue por email no dia 1)")
    @GetMapping(value = "/monthly.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> monthly(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String month) {
        java.time.YearMonth mes;
        try {
            mes = month == null || month.isBlank() ? java.time.YearMonth.now(java.time.ZoneId.of("Africa/Luanda")).minusMonths(1)
                    : java.time.YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            throw ao.autocare.common.ApiException.badRequest("Indique o mês como AAAA-MM (ex.: 2026-08).");
        }
        byte[] pdf = monthly.pdf(org(p), mes, p.permissions() != null
                && p.permissions().contains(ao.autocare.security.Permission.COSTS_VIEW));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"relatorio-mensal-" + mes + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Registo de auditoria: quem fez o quê e quando (por omissão os últimos 90 dias)")
    @GetMapping("/audit.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> audit(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return exportar(p, ext, reports.audit(org(p), from, to), "auditoria", "Registo de auditoria");
    }

    @Operation(summary = "Pneus: vida, custo por km e alertas")
    @GetMapping("/tyres.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> tyres(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.tyres(org(p)), "pneus", "Pneus");
    }

    @Operation(summary = "Programas de manutenção preditiva (CSV)")
    @GetMapping("/predictive.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> predictive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext) {
        return exportar(p, ext, reports.predictive(org(p)), "preditiva", "Programas de manutenção preditiva");
    }

    @Operation(summary = "Indicadores do período (CSV)",
            description = "Disponibilidade, MTBF, MTTR e cumprimento do plano, "
                    + "com os números que alimentam as fórmulas.")
    @GetMapping("/kpis.{ext:csv|xlsx|pdf}")
    public ResponseEntity<byte[]> kpis(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String ext,
            @Parameter(description = "Um ativo específico; vazio = toda a frota")
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return exportar(p, ext, reports.kpis(org(p), assetId, from, to), "indicadores", "Indicadores do período");
    }

    /**
     * O mesmo relatório em três formatos, pela extensão do endereço: CSV para
     * quem trata os dados, Excel para quem os quer filtrar, PDF (com o timbre
     * da empresa) para quem os imprime. O CSV vai em UTF-8 com o charset
     * declarado, porque sem isso alguns navegadores estragam os acentos.
     */
    private ResponseEntity<byte[]> exportar(AuthPrincipal p, String ext, CsvWriter tabela,
            String prefix, String titulo) {
        String nome = reports.fileName(prefix);
        return switch (ext) {
            case "xlsx" -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + ".xlsx\"")
                    .body(XlsxWriter.write(tabela));
            case "pdf" -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nome + ".pdf\"")
                    .body(tablePdf.render(orgContext.require(p), tabela, titulo));
            default -> ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + ".csv\"")
                    .body(tabela.build().getBytes(StandardCharsets.UTF_8));
        };
    }
}
