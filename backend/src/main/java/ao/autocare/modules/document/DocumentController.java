package ao.autocare.modules.document;

import ao.autocare.domain.enums.Enums.DocumentKind;
import ao.autocare.modules.document.dto.DocumentDtos.DocumentView;
import ao.autocare.modules.document.dto.DocumentDtos.KindView;
import ao.autocare.modules.document.dto.DocumentDtos.SaveDocumentRequest;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Documentos do ativo")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class DocumentController {

    private final DocumentService documents;
    private final OrgContext orgContext;

    public DocumentController(DocumentService documents, OrgContext orgContext) {
        this.documents = documents;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Tipos de documento disponíveis")
    @GetMapping("/api/v1/documents/kinds")
    public List<KindView> kinds() {
        return documents.kinds();
    }

    @Operation(summary = "Documentos de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/documents")
    public List<DocumentView> forAsset(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return documents.listForAsset(org(p), assetId);
    }

    @Operation(summary = "Documentos da frota a caducar",
            description = "Inclui os já caducados, que aparecem primeiro.")
    @GetMapping("/api/v1/documents/expiring")
    public List<DocumentView> expiring(
            @AuthenticationPrincipal AuthPrincipal p,
            @Parameter(description = "Janela em dias (por omissão 60)")
            @RequestParam(defaultValue = "60") int withinDays) {
        return documents.expiring(org(p), withinDays);
    }

    @Operation(summary = "Registar um documento (ficheiro já carregado, ou sem ficheiro)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/assets/{assetId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView create(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody SaveDocumentRequest req) {
        return documents.create(org(p), p.id(), assetId, req);
    }

    @Operation(summary = "Carregar um documento (multipart: file, title, kind?, expiresAt?…)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping(value = "/api/v1/assets/{assetId}/documents/upload",
            consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "reference", required = false) String reference,
            @RequestParam(value = "issuer", required = false) String issuer,
            @RequestParam(value = "issuedAt", required = false) Instant issuedAt,
            @RequestParam(value = "expiresAt", required = false) Instant expiresAt,
            @RequestParam(value = "notes", required = false) String notes) {

        return documents.upload(org(p), p.id(), assetId, file,
                new SaveDocumentRequest(parseKind(kind), title, reference, issuer,
                        issuedAt, expiresAt, null, notes));
    }

    @Operation(summary = "Atualizar um documento (renovar a validade, corrigir dados)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PatchMapping("/api/v1/documents/{id}")
    public DocumentView update(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody SaveDocumentRequest req) {
        return documents.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Remover um documento")
    @RequireRole(MembershipRole.MANAGER)
    @DeleteMapping("/api/v1/documents/{id}")
    public Map<String, String> delete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        documents.delete(org(p), p.id(), id);
        return Map.of("message", "Documento removido.");
    }

    private DocumentKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return DocumentKind.OTHER;
        }
        try {
            return DocumentKind.valueOf(kind.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ao.autocare.common.ApiException.badRequest(
                    "Tipo de documento desconhecido: " + kind);
        }
    }
}
