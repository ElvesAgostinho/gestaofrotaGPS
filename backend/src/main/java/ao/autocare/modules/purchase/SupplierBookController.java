package ao.autocare.modules.purchase;

import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.purchase.dto.PurchaseDtos.ComparacaoPreco;
import ao.autocare.modules.purchase.dto.PurchaseDtos.ContactView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.DocumentView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.PriceView;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SaveContactRequest;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SaveDocumentRequest;
import ao.autocare.modules.purchase.dto.PurchaseDtos.SavePriceRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O caderno do fornecedor: pessoas, papéis e preços.
 *
 * <p>Ver é de quem vê o equipamento — um mecânico precisa de saber a quem
 * telefonar quando a peça não chega. Alterar é de quem gere custos: um preço
 * errado aqui entra em todas as ordens de compra que se fizerem a seguir.
 */
@Tag(name = "Fornecedores")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class SupplierBookController {

    private final SupplierBookService service;
    private final OrgContext orgContext;

    public SupplierBookController(SupplierBookService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== Contactos =========================================================

    @Operation(summary = "Pessoas dentro de um fornecedor",
            description = "O comercial que recebe a encomenda, o financeiro que quer a "
                    + "factura e o armazém onde se vai buscar a peça não são a mesma pessoa.")
    @GetMapping("/api/v1/suppliers/{supplierId}/contacts")
    public List<ContactView> contactos(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return service.listarContactos(org(p), supplierId);
    }

    @Operation(summary = "Acrescentar uma pessoa de contacto")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/suppliers/{supplierId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactView criarContacto(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String supplierId,
            @Valid @RequestBody SaveContactRequest req) {
        return service.criarContacto(org(p), p.id(), supplierId, req);
    }

    @Operation(summary = "Alterar uma pessoa de contacto")
    @RequirePermission(Permission.COSTS_VIEW)
    @PutMapping("/api/v1/supplier-contacts/{id}")
    public ContactView actualizarContacto(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @Valid @RequestBody SaveContactRequest req) {
        return service.actualizarContacto(org(p), p.id(), id, req);
    }

    @Operation(summary = "Apagar uma pessoa de contacto")
    @RequirePermission(Permission.COSTS_VIEW)
    @DeleteMapping("/api/v1/supplier-contacts/{id}")
    public Map<String, String> apagarContacto(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.apagarContacto(org(p), p.id(), id);
        return Map.of("message", "Contacto apagado.");
    }

    // ==== Documentos ========================================================

    @Operation(summary = "Documentos do fornecedor, com validade",
            description = "Alvará, certidão de não dívida, declaração de início de "
                    + "actividade. Comprar a quem os tem fora de prazo é um problema que "
                    + "só aparece meses depois, na contabilidade.")
    @GetMapping("/api/v1/suppliers/{supplierId}/documents")
    public List<DocumentView> documentos(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return service.listarDocumentos(org(p), supplierId);
    }

    @Operation(summary = "Registar um documento do fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/suppliers/{supplierId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView criarDocumento(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String supplierId,
            @Valid @RequestBody SaveDocumentRequest req) {
        return service.criarDocumento(org(p), p.id(), supplierId, req);
    }

    @Operation(summary = "Apagar um documento do fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @DeleteMapping("/api/v1/supplier-documents/{id}")
    public Map<String, String> apagarDocumento(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.apagarDocumento(org(p), p.id(), id);
        return Map.of("message", "Documento apagado.");
    }

    // ==== Preços ============================================================

    @Operation(summary = "Lista de preços de um fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @GetMapping("/api/v1/suppliers/{supplierId}/prices")
    public List<PriceView> precos(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String supplierId) {
        return service.listarPrecos(org(p), supplierId);
    }

    @Operation(summary = "Guardar o preço de uma peça neste fornecedor",
            description = "Um fornecedor tem um preço por peça: cotar de novo substitui a "
                    + "cotação anterior em vez de acumular linhas que ninguém sabe qual vale.")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/suppliers/{supplierId}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceView guardarPreco(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String supplierId,
            @Valid @RequestBody SavePriceRequest req) {
        return service.guardarPreco(org(p), p.id(), supplierId, req);
    }

    @Operation(summary = "Apagar um preço")
    @RequirePermission(Permission.COSTS_VIEW)
    @DeleteMapping("/api/v1/supplier-prices/{id}")
    public Map<String, String> apagarPreco(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.apagarPreco(org(p), p.id(), id);
        return Map.of("message", "Preço apagado.");
    }

    @Operation(summary = "Os preços de uma peça, lado a lado",
            description = "Marca o mais barato e o mais rápido — que são quase sempre "
                    + "fornecedores diferentes, e é essa a decisão que o gestor tem de tomar.")
    @RequirePermission(Permission.COSTS_VIEW)
    @GetMapping("/api/v1/parts/{partId}/prices")
    public ComparacaoPreco comparar(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String partId) {
        return service.comparar(org(p), partId);
    }
}
