package ao.autocare.modules.part;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.part.dto.FitmentDtos.Achado;
import ao.autocare.modules.part.dto.FitmentDtos.ApplicationView;
import ao.autocare.modules.part.dto.FitmentDtos.CrossRefView;
import ao.autocare.modules.part.dto.FitmentDtos.SaveApplicationRequest;
import ao.autocare.modules.part.dto.FitmentDtos.SaveCrossRefRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compatibilidade de peças.
 *
 * <p>Ler é de quem vê o equipamento — um mecânico tem de poder saber o que
 * serve o camião que tem à frente. Declarar compatibilidades é de quem gere o
 * armazém: é informação de catálogo, e enganar-se aqui faz montar a peça
 * errada em toda a frota, não só numa viatura.
 */
@Tag(name = "Peças")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class FitmentController {

    private final FitmentService service;
    private final OrgContext orgContext;

    public FitmentController(FitmentService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== O que serve este equipamento ======================================

    @Operation(summary = "Peças compatíveis com um equipamento",
            description = "O catálogo filtrado pela família, marca, modelo, motor e ano do "
                    + "equipamento, com o stock ao lado. As peças sem compatibilidade "
                    + "declarada ficam de fora, mas vem a contagem de quantas são.")
    @GetMapping("/api/v1/assets/{assetId}/compatible-parts")
    public FitmentService.Resultado compativeis(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean includeUnknown) {
        return service.compativeis(org(p), assetId, category, includeUnknown);
    }

    @Operation(summary = "Procurar uma peça por referência",
            description = "Procura pelo número do artigo e também pelos números dos "
                    + "equivalentes e dos originais. É o que permite encontrar o artigo "
                    + "escrevendo o número que vem impresso na caixa.")
    @GetMapping("/api/v1/parts/lookup")
    public List<Achado> procurar(
            @AuthenticationPrincipal AuthPrincipal p, @RequestParam String number) {
        return service.procurar(org(p), number);
    }

    // ==== Aplicações ========================================================

    @Operation(summary = "Onde é que esta peça se monta")
    @GetMapping("/api/v1/parts/{partId}/applications")
    public List<ApplicationView> aplicacoes(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String partId) {
        return service.listarAplicacoes(org(p), partId);
    }

    @Operation(summary = "Declarar onde a peça se monta",
            description = "Cada campo vazio significa «qualquer»: só a família serve toda a "
                    + "família; marca, modelo, motor e anos servem exactamente uma viatura.")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/parts/{partId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView criarAplicacao(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String partId,
            @Valid @RequestBody SaveApplicationRequest req) {
        return service.criarAplicacao(org(p), p.id(), partId, req);
    }

    @Operation(summary = "«Esta peça serve este equipamento»",
            description = "Copia a família, a marca, o modelo, o motor e o ano do equipamento. "
                    + "Com family=true guarda só a família, que é o que serve para peças "
                    + "comuns a toda uma classe de viaturas.")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/parts/{partId}/applications/from-asset/{assetId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView aplicacaoDoEquipamento(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String partId,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "false") boolean familyOnly) {
        return service.aplicacaoDoEquipamento(org(p), p.id(), partId, assetId, familyOnly);
    }

    @RequirePermission(Permission.PARTS_MANAGE)
    @Operation(summary = "Apagar uma aplicação")
    @DeleteMapping("/api/v1/part-applications/{id}")
    public Map<String, String> apagarAplicacao(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.apagarAplicacao(org(p), p.id(), id);
        return Map.of("message", "Aplicação apagada.");
    }

    // ==== Referências cruzadas ==============================================

    @Operation(summary = "Referências equivalentes de uma peça")
    @GetMapping("/api/v1/parts/{partId}/cross-refs")
    public List<CrossRefView> referencias(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String partId) {
        return service.listarReferencias(org(p), partId);
    }

    @Operation(summary = "Registar uma referência equivalente",
            description = "OEM, equivalente de outra marca, ou a referência que substitui/foi "
                    + "substituída. Em Angola é o que permite aceitar o que o fornecedor tem.")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/parts/{partId}/cross-refs")
    @ResponseStatus(HttpStatus.CREATED)
    public CrossRefView criarReferencia(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String partId,
            @Valid @RequestBody SaveCrossRefRequest req) {
        return service.criarReferencia(org(p), p.id(), partId, req);
    }

    @Operation(summary = "Apagar uma referência")
    @RequirePermission(Permission.PARTS_MANAGE)
    @DeleteMapping("/api/v1/part-cross-refs/{id}")
    public Map<String, String> apagarReferencia(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.apagarReferencia(org(p), p.id(), id);
        return Map.of("message", "Referência apagada.");
    }
}
