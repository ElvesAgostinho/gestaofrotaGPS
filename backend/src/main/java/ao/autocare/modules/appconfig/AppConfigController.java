package ao.autocare.modules.appconfig;

import ao.autocare.domain.AppConfigEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Configuração")
@RestController
@RequestMapping("/api/v1")
public class AppConfigController {

    private final AppConfigService service;
    private final BrandingController branding;

    public AppConfigController(AppConfigService service, BrandingController branding) {
        this.service = service;
        this.branding = branding;
    }

    public record SetConfigRequest(
            @NotBlank @Size(max = 80) String key,
            @NotBlank @Size(max = 1000) String value) {}

    @Operation(summary = "Configuração pública da aplicação (nome, moeda, contactos)")
    @GetMapping("/config")
    public Map<String, Object> publicConfig(jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>(service.publicConfig());
        // Marca branca: quem entra pelo domínio de uma empresa vê a marca dela.
        branding.brandFor(request).ifPresent(b -> cfg.put("brand", b));
        return cfg;
    }

    @Operation(summary = "[Admin] Todas as chaves de configuração")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/config")
    public List<AppConfigEntry> all() {
        return service.all();
    }

    @Operation(summary = "[Admin] Definir uma chave de configuração")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/admin/config")
    public AppConfigEntry set(@jakarta.validation.Valid @RequestBody SetConfigRequest req) {
        return service.set(req.key(), req.value());
    }
}
