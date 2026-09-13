package ao.autocare.modules.fleet;

import ao.autocare.modules.fleet.dto.FleetDtos.InfractionView;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveInfractionRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveShiftRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.ShiftView;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

/** Infrações e escala dos motoristas. */
@Tag(name = "Frota")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class DriverRecordsController {

    private final DriverRecordsService service;
    private final OrgContext orgContext;

    public DriverRecordsController(DriverRecordsService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    private static boolean money(AuthPrincipal p) {
        return p.has(Permission.COSTS_VIEW);
    }

    @Operation(summary = "Infrações de um motorista, com os pontos dos últimos 12 meses")
    @GetMapping("/api/v1/drivers/{id}/infractions")
    public Map<String, Object> infractions(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        List<InfractionView> lista = service.infractions(org(p), id, money(p));
        return Map.of("items", lista, "pointsLastYear", service.pointsLastYear(id));
    }

    @Operation(summary = "Registar uma infração")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{id}/infractions")
    @ResponseStatus(HttpStatus.CREATED)
    public InfractionView addInfraction(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveInfractionRequest req) {
        return service.addInfraction(org(p), p.id(), id, req, money(p));
    }

    @Operation(summary = "Marcar a multa como paga (ou por pagar)")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/driver-infractions/{id}/paid")
    public InfractionView paid(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean paid) {
        return service.setInfractionPaid(org(p), p.id(), id, paid, money(p));
    }

    @Operation(summary = "Apagar uma infração registada por engano")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @DeleteMapping("/api/v1/driver-infractions/{id}")
    public Map<String, String> deleteInfraction(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.deleteInfraction(org(p), p.id(), id);
        return Map.of("message", "Infração apagada.");
    }

    @Operation(summary = "Escala de serviço da empresa num período (por omissão, os próximos 7 dias)")
    @GetMapping("/api/v1/drivers/roster")
    public List<ShiftView> roster(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        Instant f = from != null ? from : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant t = to != null ? to : f.plus(7, ChronoUnit.DAYS);
        return service.roster(org(p), f, t);
    }

    @Operation(summary = "Turnos de um motorista num período")
    @GetMapping("/api/v1/drivers/{id}/shifts")
    public List<ShiftView> shifts(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        Instant f = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant t = to != null ? to : Instant.now().plus(30, ChronoUnit.DAYS);
        return service.shiftsOf(org(p), id, f, t);
    }

    @Operation(summary = "Escalar um turno")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @PostMapping("/api/v1/drivers/{id}/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftView addShift(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveShiftRequest req) {
        return service.addShift(org(p), p.id(), id, req);
    }

    @Operation(summary = "Apagar um turno")
    @RequirePermission(Permission.DRIVERS_MANAGE)
    @DeleteMapping("/api/v1/driver-shifts/{id}")
    public Map<String, String> deleteShift(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.deleteShift(org(p), p.id(), id);
        return Map.of("message", "Turno apagado.");
    }
}
