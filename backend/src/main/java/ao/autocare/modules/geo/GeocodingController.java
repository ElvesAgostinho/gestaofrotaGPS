package ao.autocare.modules.geo;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Escrever o nome de um sitio e obter o ponto no mapa. */
@Tag(name = "Mapa")
@RestController
@RequestMapping("/api/v1/geo")
public class GeocodingController {

    private final GeocodingService service;
    private final OrgContext orgContext;

    public GeocodingController(GeocodingService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Procurar um sitio pelo nome",
            description = "Primeiro os locais da empresa; depois o OpenStreetMap, restrito a Angola. "
                    + "externalAvailable diz se o mapa respondeu.")
    @GetMapping("/search")
    public GeocodingService.Resposta search(
            @AuthenticationPrincipal AuthPrincipal p, @RequestParam("q") String q) {
        return service.search(orgContext.requireOrganizationId(p), q);
    }
}
