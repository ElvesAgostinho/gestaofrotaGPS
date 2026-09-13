package ao.autocare.modules.appconfig;

import ao.autocare.domain.Organization;
import ao.autocare.domain.StoredFile;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.storage.StorageProvider;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marca branca: pelo domínio por onde o pedido entrou, a marca que se mostra
 * antes de alguém entrar.
 *
 * <p>Só o logótipo de uma empresa <b>com domínio próprio</b> é público — é a
 * marca dela, na página de entrada dela. As outras continuam a servir o
 * logótipo por URL assinado.
 */
@Hidden
@RestController
public class BrandingController {

    /** O que o ecrã de entrada mostra. */
    public record Brand(String organizationId, String name, String color, String logoUrl) {}

    private final OrganizationRepository organizations;
    private final StoredFileRepository files;
    private final StorageProvider storage;

    public BrandingController(OrganizationRepository organizations, StoredFileRepository files,
            StorageProvider storage) {
        this.organizations = organizations;
        this.files = files;
        this.storage = storage;
    }

    /** A marca do domínio do pedido, se houver uma empresa com esse domínio. */
    public Optional<Brand> brandFor(HttpServletRequest request) {
        String host = host(request);
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return organizations.findByCustomDomainIgnoreCase(host).map(o -> new Brand(
                o.getId(),
                o.getBrandName() != null && !o.getBrandName().isBlank() ? o.getBrandName() : o.getName(),
                o.getBrandColor(),
                o.getLogoFileId() != null ? "/api/v1/public/brand-logo/" + o.getId() : null));
    }

    /** O domínio como o cliente o vê: atrás do Traefik/nginx vem em X-Forwarded-Host. */
    static String host(HttpServletRequest request) {
        String h = request.getHeader("X-Forwarded-Host");
        if (h == null || h.isBlank()) {
            h = request.getHeader("Host");
        }
        if (h == null) {
            return null;
        }
        h = h.split(",")[0].trim().toLowerCase(Locale.ROOT);
        int porta = h.indexOf(':');
        return porta > 0 ? h.substring(0, porta) : h;
    }

    @GetMapping("/api/v1/public/brand-logo/{organizationId}")
    public ResponseEntity<byte[]> logo(@PathVariable String organizationId) {
        Organization o = organizations.findById(organizationId).orElse(null);
        if (o == null || o.getCustomDomain() == null || o.getLogoFileId() == null) {
            return ResponseEntity.notFound().build();
        }
        StoredFile f = files.findById(o.getLogoFileId()).orElse(null);
        if (f == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes;
        try {
            bytes = storage.load(f.getStorageKey());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)).cachePublic())
                .contentType(f.getContentType() != null ? MediaType.parseMediaType(f.getContentType())
                        : MediaType.IMAGE_PNG)
                .body(bytes);
    }
}
