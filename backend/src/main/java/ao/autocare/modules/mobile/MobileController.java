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
import org.springframework.web.bind.annotation.RequestBody;
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
    private final PhoneTrackingService tracking;
    private final OrgContext orgContext;

    public MobileController(MobileService service, PhoneTrackingService tracking,
            OrgContext orgContext) {
        this.service = service;
        this.tracking = tracking;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Ecrã inicial do telemóvel: as minhas viaturas e as minhas ordens")
    @GetMapping("/home")
    public HomeView home(@AuthenticationPrincipal AuthPrincipal p) {
        return service.home(orgContext.requireOrganizationId(p), p.id());
    }

    @Operation(summary = "Comunicar uma ocorrência com fotografias e local",
            description = "Avaria, acidente, pneu ou combustível. Abre uma ordem corretiva com as "
                    + "fotografias anexadas e o sítio onde aconteceu, e avisa os gestores na hora.")
    @RequirePermission(Permission.BREAKDOWN_REPORT)
    @PostMapping(value = "/occurrences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView occurrence(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam String assetId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal meterValue,
            @RequestParam(required = false, defaultValue = "false") boolean stopped,
            @RequestParam(required = false, defaultValue = "AVARIA") String kind,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) java.util.List<MultipartFile> photos) {
        return service.reportOccurrence(orgContext.requireOrganizationId(p), p.id(), assetId, title,
                description, meterValue, stopped, kind, latitude, longitude,
                photos == null ? java.util.List.of() : photos);
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

    @Operation(summary = "A rota de hoje deste motorista, com o traçado para o mapa")
    @GetMapping("/route")
    public MobileDtos.RotaDetalhe route(@AuthenticationPrincipal AuthPrincipal p) {
        return service.rotaDetalhe(orgContext.requireOrganizationId(p), p.id());
    }

    @Operation(summary = "Enviar posições do telemóvel do motorista",
            description = "Em lote: a aplicação guarda o percurso e envia de tempos a tempos, ou "
                    + "quando volta a haver rede. As posições entram no mesmo caminho das do "
                    + "aparelho da viatura — viagens, geocercas, desvio de rota e mapa ao vivo.")
    @PostMapping("/positions")
    public PhoneTrackingService.Resultado positions(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestBody PhoneTrackingService.Lote lote) {
        return tracking.receber(orgContext.requireOrganizationId(p), p.id(), lote);
    }
}
