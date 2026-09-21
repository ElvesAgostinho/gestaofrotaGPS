package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.FluidTopUp;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Atestos de fluidos: quem regista e quem lê não são a mesma pessoa.
 *
 * <p>Vive num controlador próprio por causa disso. <b>Registar</b> é trabalho
 * de quem conduz — é ele que está ao pé do radiador com o garrafão na mão — e
 * por isso o motorista pode. <b>Ver a lista</b> é olhar para o histórico da
 * frota, e isso pede a permissão de quem gere.
 *
 * <p>Estiveram no controlador da frota e isso passou a ser um problema no dia
 * em que a leitura da frota ficou fechada ao motorista: ele deixava de poder
 * registar o que só ele vê. Separar não é arrumação — é o que faz a regra de
 * acesso coincidir com o trabalho real.
 */
@Tag(name = "Frota")
@RestController
public class FluidTopUpController {

    /** O que a aplicação envia quando alguém atesta água, óleo ou travões. */
    public record AtestoRequest(
            String kind,
            BigDecimal liters,
            BigDecimal meterValue,
            String note,
            Instant recordedAt) {}

    private final FluidTopUpService fluids;
    private final OrgContext orgContext;

    public FluidTopUpController(FluidTopUpService fluids, OrgContext orgContext) {
        this.fluids = fluids;
        this.orgContext = orgContext;
    }

    @Operation(summary = "Atestos de fluidos de uma viatura",
            description = "Água, óleo, hidráulico e travões. Fluido que se atesta é fluido "
                    + "que se perdeu: a lista mostra a tendência.")
    @RequirePermission(Permission.FLEET_VIEW)
    @GetMapping("/api/v1/assets/{assetId}/fluid-topups")
    public List<FluidTopUpService.Registo> list(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId) {
        return fluids.list(orgContext.requireOrganizationId(p), assetId);
    }

    @Operation(summary = "Registar um atesto de fluido",
            description = "Ao terceiro atesto de arrefecimento em 30 dias — ou aos cinco "
                    + "litros — os gestores são avisados de que há fuga. No líquido de "
                    + "travões avisa-se logo ao primeiro.")
    @RequireRole(MembershipRole.DRIVER)
    @PostMapping("/api/v1/assets/{assetId}/fluid-topups")
    @ResponseStatus(HttpStatus.CREATED)
    public FluidTopUpService.Resultado record(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestBody AtestoRequest req) {
        FluidTopUp.Kind kind;
        try {
            kind = req.kind() == null || req.kind().isBlank()
                    ? FluidTopUp.Kind.COOLANT
                    : FluidTopUp.Kind.valueOf(req.kind().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Fluido desconhecido: " + req.kind());
        }
        return fluids.record(orgContext.requireOrganizationId(p), p.id(), assetId, kind,
                req.liters(), req.meterValue(), req.note(), req.recordedAt());
    }
}
