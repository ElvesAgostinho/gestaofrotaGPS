package ao.autocare.modules.meter;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.meter.dto.MeterDtos.AddReadingRequest;
import ao.autocare.modules.meter.dto.MeterDtos.AddReadingResponse;
import ao.autocare.modules.meter.dto.MeterDtos.MeterView;
import ao.autocare.modules.meter.dto.MeterDtos.ReadingView;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Medidores")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{assetId}/meters")
public class MeterController {

    private final MeterService service;
    private final OrgContext orgContext;

    public MeterController(MeterService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Medidores do ativo")
    @GetMapping
    public List<MeterView> meters(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return service.listMeters(orgContext.requireOrganizationId(principal), assetId);
    }

    @Operation(summary = "Histórico de leituras de um medidor")
    @GetMapping("/{kind}/readings")
    public PagedResponse<ReadingView> history(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.history(
                orgContext.requireOrganizationId(principal), assetId, parseKind(kind),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Registar uma leitura de medidor")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/{kind}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public AddReadingResponse addReading(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String kind,
            @Valid @RequestBody AddReadingRequest req) {
        return service.addReading(
                orgContext.requireOrganizationId(principal), principal.id(),
                assetId, parseKind(kind), req);
    }

    private MeterKind parseKind(String kind) {
        try {
            return MeterKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Tipo de medidor inválido: " + kind);
        }
    }
}
