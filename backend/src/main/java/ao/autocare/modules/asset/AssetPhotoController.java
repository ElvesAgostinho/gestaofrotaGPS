package ao.autocare.modules.asset;

import ao.autocare.common.ApiException;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.domain.enums.Enums.PhotoKind;
import ao.autocare.modules.asset.dto.AssetPhotoDtos.AssetPhotoView;
import ao.autocare.modules.asset.dto.AssetPhotoDtos.UpdatePhotoRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Fotografias de ativos")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
@RequestMapping("/api/v1/assets/{assetId}/photos")
public class AssetPhotoController {

    private final AssetPhotoService service;
    private final OrgContext orgContext;

    public AssetPhotoController(AssetPhotoService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Fotografias do ativo")
    @GetMapping
    public List<AssetPhotoView> list(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String assetId) {
        return service.list(orgContext.requireOrganizationId(principal), assetId);
    }

    @Operation(summary = "Carregar uma fotografia (multipart: file, kind?, caption?)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetPhotoView upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "caption", required = false) String caption) {
        return service.upload(
                orgContext.requireOrganizationId(principal), principal.id(), assetId,
                file, parseKind(kind), caption);
    }

    @Operation(summary = "Atualizar uma fotografia (legenda, tipo, principal, ordem)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PatchMapping("/{photoId}")
    public AssetPhotoView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String photoId,
            @Valid @RequestBody UpdatePhotoRequest req) {
        return service.update(
                orgContext.requireOrganizationId(principal), principal.id(), assetId, photoId, req);
    }

    @Operation(summary = "Eliminar uma fotografia")
    @RequireRole(MembershipRole.MANAGER)
    @DeleteMapping("/{photoId}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String assetId,
            @PathVariable String photoId) {
        service.delete(orgContext.requireOrganizationId(principal), principal.id(), assetId, photoId);
        return Map.of("message", "Fotografia eliminada.");
    }

    private PhotoKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) return PhotoKind.GENERAL;
        try {
            return PhotoKind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Tipo de fotografia inválido: " + kind);
        }
    }
}
