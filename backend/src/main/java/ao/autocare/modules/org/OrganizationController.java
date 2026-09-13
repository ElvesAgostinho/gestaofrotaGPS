package ao.autocare.modules.org;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Organization;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Empresa")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {

    private final OrgContext orgContext;
    private final MembershipRepository memberships;
    private final AssetRepository assets;
    private final AssetTypeRepository assetTypes;
    private final LocationRepository locations;
    private final AuditService audit;
    private final ao.autocare.storage.StorageProvider storage;
    private final ao.autocare.storage.FileUrls fileUrls;
    private final ao.autocare.repo.StoredFileRepository files;
    private final ao.autocare.repo.UserRepository users;

    public OrganizationController(
            OrgContext orgContext,
            MembershipRepository memberships,
            AssetRepository assets,
            AssetTypeRepository assetTypes,
            LocationRepository locations,
            AuditService audit,
            ao.autocare.storage.StorageProvider storage,
            ao.autocare.storage.FileUrls fileUrls,
            ao.autocare.repo.StoredFileRepository files,
            ao.autocare.repo.UserRepository users) {
        this.files = files;
        this.users = users;
        this.storage = storage;
        this.fileUrls = fileUrls;
        this.orgContext = orgContext;
        this.memberships = memberships;
        this.assets = assets;
        this.assetTypes = assetTypes;
        this.locations = locations;
        this.audit = audit;
    }

    public record OrganizationView(
            String id, String name, String type, String myRole,
            long assetCount, long assetTypeCount, long locationCount, int memberCount,
            java.math.BigDecimal defaultSpeedLimitKph,
            String taxId, String address, String city, String phone, String email,
            /** URL assinado do logótipo, ou nulo. */
            String logoUrl,
            /** As minhas permissões efetivas: é com isto que o ecrã esconde o que não posso. */
            java.util.List<String> myPermissions,
            /** Último dia da licença (nulo = sem prazo). */
            java.time.LocalDate licenseUntil,
            /** Por que a empresa está travada (suspensa ou licença vencida); nulo = tudo bem. */
            String blockedReason) {}

    /** Alterações às definições da empresa. Cada campo é opcional. */
    public record UpdateOrganizationRequest(
            @Size(max = 160) String name,
            @Size(max = 40) String taxId,
            @Size(max = 300) String address,
            @Size(max = 120) String city,
            @Size(max = 40) String phone,
            @Size(max = 190) String email,
            /** Limite de velocidade por omissão da frota, km/h. Zero remove a vigilância. */
            java.math.BigDecimal defaultSpeedLimitKph) {}

    @Operation(summary = "Dados da empresa atual")
    @GetMapping
    @Transactional(readOnly = true)
    public OrganizationView current(@AuthenticationPrincipal AuthPrincipal principal) {
        Organization org = orgContext.require(principal);
        String id = org.getId();
        return new OrganizationView(
                id, org.getName(), org.getType().name(), principal.role(),
                assets.countByOrganizationId(id),
                assetTypes.countByOrganizationId(id),
                locations.countByOrganizationId(id),
                memberships.findByUserId(principal.id()).size() > 0
                        ? countMembers(id) : 0,
                org.getDefaultSpeedLimitKph(),
                org.getTaxId(), org.getAddress(), org.getCity(), org.getPhone(), org.getEmail(),
                org.getLogoFileId() != null ? fileUrls.signed(org.getLogoFileId()) : null,
                principal.permissions() == null ? java.util.List.of()
                        : principal.permissions().stream().map(Enum::name).sorted().toList(),
                org.getLicenseUntil(),
                org.blockedReason(java.time.LocalDate.now()));
    }

    @Operation(summary = "Alterar os dados da empresa (nome, limite de velocidade da frota)")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PatchMapping
    @Transactional
    public OrganizationView update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateOrganizationRequest req) {
        Organization org = orgContext.require(principal);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw ApiException.badRequest("Indique o nome da empresa.");
            }
            org.setName(req.name().trim());
        }
        if (req.taxId() != null) org.setTaxId(limpar(req.taxId()));
        if (req.address() != null) org.setAddress(limpar(req.address()));
        if (req.city() != null) org.setCity(limpar(req.city()));
        if (req.phone() != null) org.setPhone(limpar(req.phone()));
        if (req.email() != null) org.setEmail(limpar(req.email()));
        if (req.defaultSpeedLimitKph() != null) {
            java.math.BigDecimal limit = req.defaultSpeedLimitKph();
            if (limit.compareTo(java.math.BigDecimal.valueOf(400)) > 0) {
                throw ApiException.badRequest("O limite de velocidade indicado não é plausível.");
            }
            org.setDefaultSpeedLimitKph(limit.signum() > 0 ? limit : null);
        }
        audit.record(org.getId(), principal.id(), "organization.update", "Organization",
                org.getId(), org.getName());
        return current(principal);
    }

    /**
     * O logótipo da empresa, para os impressos.
     *
     * <p>PNG ou JPEG até 2 MB: um logótipo é um logótipo, não uma fotografia.
     * Vai para o armazenamento de ficheiros, como as fotografias dos ativos.
     */
    @Operation(summary = "Carregar o logotipo da empresa")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @org.springframework.web.bind.annotation.PostMapping(value = "/logo",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public OrganizationView uploadLogo(
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        Organization org = orgContext.require(principal);
        String tipo = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!tipo.equals("image/png") && !tipo.equals("image/jpeg")) {
            throw ApiException.badRequest("O logótipo tem de ser PNG ou JPEG.");
        }
        if (file.getSize() > 2L * 1024 * 1024) {
            throw ApiException.badRequest("O logótipo não pode passar de 2 MB.");
        }
        apagarLogo(org);
        byte[] bytes = file.getBytes();
        String key = storage.store(bytes, tipo, file.getOriginalFilename());
        // Um registo de ficheiro, como as fotografias: é o id dele que o URL
        // assinado conhece, não a chave no disco.
        ao.autocare.domain.StoredFile stored = new ao.autocare.domain.StoredFile();
        stored.setOrganization(org);
        stored.setStorageKey(key);
        stored.setOriginalName(file.getOriginalFilename());
        stored.setContentType(tipo);
        stored.setSizeBytes(bytes.length);
        stored.setUploadedBy(users.getReferenceById(principal.id()));
        files.save(stored);
        org.setLogoKey(key);
        org.setLogoFileId(stored.getId());
        org.setLogoContentType(tipo);
        audit.record(org.getId(), principal.id(), "organization.logo", "Organization",
                org.getId(), "Logótipo atualizado");
        return current(principal);
    }

    @Operation(summary = "Remover o logotipo da empresa")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @org.springframework.web.bind.annotation.DeleteMapping("/logo")
    @Transactional
    public OrganizationView removeLogo(@AuthenticationPrincipal AuthPrincipal principal) {
        Organization org = orgContext.require(principal);
        apagarLogo(org);
        return current(principal);
    }

    private void apagarLogo(Organization org) {
        if (org.getLogoKey() != null) {
            try {
                storage.delete(org.getLogoKey());
            } catch (RuntimeException e) {
                // Um logótipo antigo que já não existe no disco não impede o novo.
            }
        }
        if (org.getLogoFileId() != null) {
            files.deleteById(org.getLogoFileId());
        }
        org.setLogoKey(null);
        org.setLogoFileId(null);
        org.setLogoContentType(null);
    }

    private static String limpar(String v) {
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private int countMembers(String orgId) {
        return (int) memberships.findAll().stream()
                .filter(m -> m.getOrganization().getId().equals(orgId))
                .count();
    }
}
