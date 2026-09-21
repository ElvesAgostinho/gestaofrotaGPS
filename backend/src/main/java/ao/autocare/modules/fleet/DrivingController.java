package ao.autocare.modules.fleet;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.fleet.dto.DrivingDtos.DismissRequest;
import ao.autocare.modules.fleet.dto.DrivingDtos.DriverScoreView;
import ao.autocare.modules.fleet.dto.DrivingDtos.DrivingEventView;
import ao.autocare.modules.fleet.dto.DrivingDtos.DrivingSummary;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Infrações de condução e pontuação de motoristas. */
@Tag(name = "Condução")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class DrivingController {

    /** Período por omissão dos ecrãs: os últimos 30 dias. */
    private static final int DEFAULT_PERIOD_DAYS = 30;

    private final DrivingService driving;
    private final OrgContext orgContext;

    public DrivingController(DrivingService driving, OrgContext orgContext) {
        this.driving = driving;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Infrações de condução da frota")
    @GetMapping("/api/v1/driving-events")
    public PagedResponse<DrivingEventView> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return driving.list(org(p),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Infrações de uma viagem")
    @GetMapping("/api/v1/trips/{tripId}/driving-events")
    public List<DrivingEventView> forTrip(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String tripId) {
        return driving.forTrip(org(p), tripId);
    }

    @Operation(summary = "Anular uma infração",
            description = "Nem toda a travagem violenta é má condução: travar a fundo "
                    + "para não atropelar alguém é o que se quer que aconteça. "
                    + "Fica registado quem anulou e porquê.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/api/v1/driving-events/{id}/dismiss")
    public DrivingEventView dismiss(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody DismissRequest req) {
        return driving.dismiss(org(p), p.id(), id, req.reason());
    }

    @Operation(summary = "Pontuação de condução de um motorista",
            description = "Recalculada no momento com as infrações que contam.")
    @GetMapping("/api/v1/drivers/{driverId}/score")
    public DriverScoreView score(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String driverId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant fim = to != null ? to : Instant.now();
        Instant inicio = from != null ? from : fim.minus(DEFAULT_PERIOD_DAYS, ChronoUnit.DAYS);
        return driving.scoreView(org(p), driverId, inicio, fim);
    }

    @Operation(summary = "Painel de condução da frota",
            description = "Ranking de motoristas e contagem de infrações no período.")
    @RequireRole(MembershipRole.MANAGER)
    @GetMapping("/api/v1/driving/summary")
    public DrivingSummary summary(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        String orgId = org(p);
        Instant fim = to != null ? to : Instant.now();
        Instant inicio = from != null ? from : fim.minus(DEFAULT_PERIOD_DAYS, ChronoUnit.DAYS);

        List<DrivingEventView> periodo = driving.countingBetween(orgId, inicio, fim);
        List<DriverScoreView> ranking = driving.rankAll(orgId, inicio, fim);

        return new DrivingSummary(inicio, fim, periodo.size(),
                count(periodo, DrivingEventKind.OVERSPEED),
                count(periodo, DrivingEventKind.HARSH_BRAKE),
                count(periodo, DrivingEventKind.HARSH_ACCELERATION),
                count(periodo, DrivingEventKind.HARSH_CORNERING),
                count(periodo, DrivingEventKind.IDLING),
                count(periodo, DrivingEventKind.NIGHT_DRIVING),
                ranking);
    }

    private static int count(List<DrivingEventView> lista, DrivingEventKind kind) {
        return (int) lista.stream().filter(e -> e.kind() == kind).count();
    }
}
