package ao.autocare.modules.mobile;

import ao.autocare.modules.mobile.MobileDtos.HomeView;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** O que a app do telemovel (PWA) usa e o resto do sistema nao tem. */
@Tag(name = "Telemóvel")
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileController {

    private final MobileService service;
    private final OrgContext orgContext;

    public MobileController(MobileService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Ecrã inicial do telemóvel: as minhas viaturas e as minhas ordens")
    @GetMapping("/home")
    public HomeView home(@AuthenticationPrincipal AuthPrincipal p) {
        return service.home(orgContext.requireOrganizationId(p), p.id());
    }

    @Operation(summary = "Comunicar uma avaria (abre uma ordem corretiva e avisa os gestores)")
    @RequirePermission(Permission.BREAKDOWN_REPORT)
    @PostMapping(value = "/breakdowns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView report(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam String assetId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal meterValue,
            @RequestParam(required = false, defaultValue = "false") boolean stopped,
            @RequestParam(required = false) MultipartFile photo) {
        return service.reportBreakdown(orgContext.requireOrganizationId(p), p.id(), assetId, title,
                description, meterValue, stopped, photo);
    }
}
