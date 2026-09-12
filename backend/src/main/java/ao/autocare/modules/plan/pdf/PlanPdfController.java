package ao.autocare.modules.plan.pdf;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Manutenção do ativo")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class PlanPdfController {

    private final MaintenancePlanPdfService pdf;
    private final OrgContext orgContext;

    public PlanPdfController(MaintenancePlanPdfService pdf, OrgContext orgContext) {
        this.pdf = pdf;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Gerar o PDF do Plano de Manutenção Preventiva do ativo")
    @GetMapping("/api/v1/assets/{assetId}/maintenance-plan.pdf")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        byte[] bytes = pdf.render(orgContext.requireOrganizationId(principal), assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename("plano-manutencao-" + assetId + ".pdf").build().toString())
                .body(bytes);
    }
}
