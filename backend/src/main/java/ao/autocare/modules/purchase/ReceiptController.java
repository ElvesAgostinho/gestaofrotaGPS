package ao.autocare.modules.purchase;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.purchase.InvoiceService.FacturaRequest;
import ao.autocare.modules.purchase.InvoiceService.FacturaView;
import ao.autocare.modules.purchase.ReceiptService.RecepcaoRequest;
import ao.autocare.modules.purchase.ReceiptService.RecepcaoView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Receber a mercadoria, e as facturas do fornecedor.
 *
 * <p>Receber é de quem mexe no stock — é o que está no armazém quando o camião
 * chega. As facturas são dinheiro, e por isso ficam de quem o pode ver: quem
 * descarrega não tem nada que saber o que a empresa paga.
 */
@Tag(name = "Compras")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class ReceiptController {

    private final ReceiptService receipts;
    private final InvoiceService invoices;
    private final SupplierScorecard scorecard;
    private final ReturnService devolucoes;
    private final OrgContext orgContext;

    public ReceiptController(
            ReceiptService receipts,
            InvoiceService invoices,
            SupplierScorecard scorecard,
            ReturnService devolucoes,
            OrgContext orgContext) {
        this.receipts = receipts;
        this.invoices = invoices;
        this.scorecard = scorecard;
        this.devolucoes = devolucoes;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== Recepção ==========================================================

    @Operation(summary = "Registar o que chegou de uma encomenda",
            description = "Recebe-se em partes, porque é assim que chega. O que vem bem entra "
                    + "no armazém com o preço que se pagou e actualiza o custo médio; o que vem "
                    + "mal conta-se à parte e fica a aguardar devolução.")
    @RequirePermission(Permission.STOCK_MOVE)
    @PostMapping("/api/v1/purchase-orders/{orderId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public RecepcaoView receber(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String orderId,
            @Valid @RequestBody RecepcaoRequest req) {
        return receipts.receber(org(p), p.id(), orderId, req);
    }

    @Operation(summary = "Entregas já registadas de uma encomenda")
    @RequirePermission(Permission.FLEET_VIEW)
    @GetMapping("/api/v1/purchase-orders/{orderId}/receipts")
    public List<RecepcaoView> entregas(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String orderId) {
        return receipts.daOrdem(org(p), orderId);
    }

    // ==== Devoluções ========================================================

    @Operation(summary = "O que veio mal e ainda não se resolveu",
            description = "Por omissão só as que estão por resolver, que são as únicas sobre "
                    + "as quais há alguma coisa a fazer.")
    @RequirePermission(Permission.FLEET_VIEW)
    @GetMapping("/api/v1/purchase-returns")
    public List<ReturnService.Devolucao> devolucoes(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "true") boolean pendingOnly) {
        return devolucoes.listar(org(p), pendingOnly);
    }

    @Operation(summary = "Devoluções de um fornecedor")
    @RequirePermission(Permission.FLEET_VIEW)
    @GetMapping("/api/v1/suppliers/{supplierId}/returns")
    public List<ReturnService.Devolucao> devolucoesDe(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return devolucoes.doFornecedor(org(p), supplierId);
    }

    @Operation(summary = "Resolver uma devolução",
            description = "Devolvida, substituída, creditada, ou aceite assim mesmo — às "
                    + "vezes não compensa o frete de volta. Obrigar a escolher entre devolver "
                    + "e não devolver faria metade dos casos ficar por registar.")
    @RequirePermission(Permission.STOCK_MOVE)
    @PostMapping("/api/v1/purchase-returns/{id}/resolve")
    public ReturnService.Devolucao resolver(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @Valid @RequestBody ReturnService.ResolverRequest req) {
        return devolucoes.resolver(org(p), p.id(), id, req);
    }

    // ==== Facturas ==========================================================

    @Operation(summary = "Facturas de um fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @GetMapping("/api/v1/suppliers/{supplierId}/invoices")
    public List<FacturaView> facturas(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return invoices.doFornecedor(org(p), supplierId);
    }

    @Operation(summary = "Registar uma factura, com o papel digitalizado",
            description = "Quando a factura está ligada a uma ordem, compara-se o valor: uma "
                    + "factura acima da encomenda é a forma mais comum de pagar a mais.")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping(value = "/api/v1/suppliers/{supplierId}/invoices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FacturaView registarComFicheiro(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String supplierId,
            @RequestPart("dados") @Valid FacturaRequest req,
            @RequestPart(value = "ficheiro", required = false) MultipartFile ficheiro) {
        return invoices.registar(org(p), p.id(), supplierId, req, ficheiro);
    }

    @Operation(summary = "Registar uma factura sem anexo")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping(value = "/api/v1/suppliers/{supplierId}/invoices",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FacturaView registar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String supplierId,
            @Valid @RequestBody FacturaRequest req) {
        return invoices.registar(org(p), p.id(), supplierId, req, null);
    }

    @Operation(summary = "Anexar ou substituir o ficheiro de uma factura")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping(value = "/api/v1/supplier-invoices/{id}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FacturaView anexar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestPart("ficheiro") MultipartFile ficheiro) {
        return invoices.anexar(org(p), p.id(), id, ficheiro);
    }

    @Operation(summary = "Marcar a factura como paga")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/supplier-invoices/{id}/pay")
    public FacturaView pagar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestParam(required = false) LocalDate quando) {
        return invoices.marcarPaga(org(p), p.id(), id, quando);
    }

    @Operation(summary = "Contestar a factura",
            description = "Para quando o valor não bate com a encomenda ou com a mercadoria.")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/supplier-invoices/{id}/dispute")
    public FacturaView contestar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody Map<String, String> corpo) {
        return invoices.contestar(org(p), p.id(), id, corpo.get("motivo"));
    }

    @Operation(summary = "Apagar uma factura registada por engano")
    @RequirePermission(Permission.COSTS_VIEW)
    @DeleteMapping("/api/v1/supplier-invoices/{id}")
    public Map<String, String> apagar(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        invoices.apagar(org(p), p.id(), id);
        return Map.of("message", "Factura apagada.");
    }

    @Operation(summary = "Todas as facturas, por estado")
    @RequirePermission(Permission.COSTS_VIEW)
    @GetMapping("/api/v1/supplier-invoices")
    public List<FacturaView> listar(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String status) {
        return invoices.listar(org(p), status);
    }

    // ==== Classificação =====================================================

    @Operation(summary = "Como é que este fornecedor se tem portado",
            description = "Pontualidade, qualidade, resposta a pedidos e facturação — "
                    + "calculadas do que já aconteceu. Sem nota global: uma média esconde "
                    + "exactamente o que interessa.")
    @RequirePermission(Permission.COSTS_VIEW)
    @GetMapping("/api/v1/suppliers/{supplierId}/scorecard")
    public SupplierScorecard.Ficha classificacao(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return scorecard.de(org(p), supplierId);
    }
}
