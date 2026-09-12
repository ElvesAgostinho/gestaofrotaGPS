package ao.autocare.modules.importer;

import ao.autocare.common.ApiException;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.importer.dto.ImportDtos.ImportReport;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Importação em massa")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService imports;
    private final OrgContext orgContext;

    public ImportController(ImportService imports, OrgContext orgContext) {
        this.imports = imports;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Modelo de ficheiro para importar ativos")
    @GetMapping(value = "/assets/template", produces = "text/csv")
    public String assetTemplate() {
        return imports.assetTemplate();
    }

    @Operation(summary = "Modelo de ficheiro para importar peças")
    @GetMapping(value = "/parts/template", produces = "text/csv")
    public String partTemplate() {
        return imports.partTemplate();
    }

    @Operation(summary = "Importar ativos a partir de um CSV",
            description = "Com dryRun=true verifica o ficheiro e não grava nada.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importAssets(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Verificar sem gravar")
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return imports.importAssets(org(p), p.id(), read(file), dryRun);
    }

    @Operation(summary = "Importar peças a partir de um CSV",
            description = "Com dryRun=true verifica o ficheiro e não grava nada.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping(value = "/parts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importParts(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return imports.importParts(org(p), p.id(), read(file), dryRun);
    }

    @Operation(summary = "Modelo de ficheiro para importar abastecimentos")
    @GetMapping(value = "/fuel/template", produces = "text/csv")
    public String fuelTemplate() {
        return imports.fuelTemplate();
    }

    @Operation(summary = "Importar abastecimentos a partir de um CSV",
            description = "O ficheiro que o posto ou a gestora de cartões envia todos os meses. "
                    + "Com dryRun=true verifica e não grava nada.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping(value = "/fuel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importFuel(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Verificar sem gravar")
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return imports.importFuel(org(p), p.id(), read(file), dryRun);
    }

    private String read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nenhum ficheiro foi enviado.");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro enviado.");
        }
    }
}
