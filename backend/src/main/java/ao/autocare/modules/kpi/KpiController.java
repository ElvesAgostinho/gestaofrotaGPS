package ao.autocare.modules.kpi;

import ao.autocare.modules.kpi.dto.KpiDtos.KpiReport;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Indicadores")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class KpiController {

    private final KpiService kpis;
    private final OrgContext orgContext;

    public KpiController(KpiService kpis, OrgContext orgContext) {
        this.kpis = kpis;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Indicadores de desempenho (Disponibilidade, MTBF, MTTR, Cumprimento do plano)")
    @GetMapping("/api/v1/kpis")
    public KpiReport report(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return kpis.report(orgContext.requireOrganizationId(principal), assetId, from, to);
    }
}
