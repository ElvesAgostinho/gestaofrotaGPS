package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.modules.mailbox.MailboxService;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.purchase.dto.OrderDtos.DecisaoRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.AdjudicarRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.PedidoRequest;
import ao.autocare.modules.purchase.dto.RfqDtos.PedidoView;
import ao.autocare.modules.purchase.dto.RfqDtos.PropostaView;
import ao.autocare.modules.purchase.dto.RfqDtos.RespostaRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pedidos de cotação.
 *
 * <p>Mexe em preços, portanto está fechado a quem os pode ver. O envio aos
 * fornecedores sai pela caixa de correio da empresa, como tudo o resto — e
 * fica registado a quem se pediu e quando, que é o que permite dizer «pediram-
 * se três preços» e ter como o provar.
 */
@Tag(name = "Compras")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.COSTS_VIEW)
@RestController
@RequestMapping("/api/v1/rfqs")
public class RfqController {

    private final RfqService service;
    private final MailboxService mailbox;
    private final OrgContext orgContext;

    public RfqController(RfqService service, MailboxService mailbox, OrgContext orgContext) {
        this.service = service;
        this.mailbox = mailbox;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Pedidos de cotação")
    @GetMapping
    public List<PedidoView> listar(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String status) {
        return service.listar(org(p), status);
    }

    @Operation(summary = "Um pedido, com a comparação das propostas")
    @GetMapping("/{id}")
    public PedidoView obter(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.obter(org(p), id);
    }

    @Operation(summary = "Criar um pedido de cotação",
            description = "A especificação de cada linha vai no pedido: sem ela, três "
                    + "fornecedores cotam três coisas diferentes e a comparação não compara nada.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoView criar(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody PedidoRequest req) {
        return service.criar(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar um pedido que ainda não teve respostas")
    @PutMapping("/{id}")
    public PedidoView alterar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @Valid @RequestBody PedidoRequest req) {
        return service.alterar(org(p), p.id(), id, req);
    }

    @Operation(summary = "Enviar o pedido a um fornecedor por email")
    @PostMapping("/{id}/suppliers/{supplierId}/send")
    public PedidoView enviar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @PathVariable String supplierId) {
        String orgId = org(p);
        PedidoView pedido = service.obter(orgId, id);
        PropostaView proposta = pedido.propostas().stream()
                .filter(x -> x.supplierId().equals(supplierId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Este fornecedor não está neste pedido."));

        String para = emailDe(orgId, supplierId);
        if (para == null) {
            throw ApiException.badRequest(
                    "O fornecedor «" + proposta.supplierName() + "» não tem email na ficha.");
        }
        mailbox.enviarDocumento(orgId, p.id(), para, null,
                "Pedido de cotação " + pedido.number() + " — " + pedido.title(),
                corpo(pedido), null, null, null);
        return service.marcarEnviado(orgId, supplierId, id, para);
    }

    @Operation(summary = "Registar o que um fornecedor respondeu",
            description = "Quem responde «não fornece» também fica registado: ao fim de três "
                    + "pedidos sem resposta, isso deixa de ser uma impressão.")
    @PostMapping("/{id}/suppliers/{supplierId}/response")
    public PedidoView responder(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @PathVariable String supplierId,
            @Valid @RequestBody RespostaRequest req) {
        return service.registarResposta(org(p), p.id(), id, supplierId, req);
    }

    @Operation(summary = "Adjudicar e gerar a ordem de compra",
            description = "Se não escolher o mais barato, o motivo é obrigatório. Pode ser a "
                    + "decisão certa — mas tem de ficar escrito porquê.")
    @PostMapping("/{id}/award")
    public PedidoView adjudicar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @Valid @RequestBody AdjudicarRequest req) {
        return service.adjudicar(org(p), p.id(), id, req);
    }

    @Operation(summary = "Anular o pedido")
    @PostMapping("/{id}/cancel")
    public PedidoView anular(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody(required = false) DecisaoRequest req) {
        return service.anular(org(p), p.id(), id, req != null ? req.motivo() : null);
    }

    private String emailDe(String orgId, String supplierId) {
        return service.emailDoFornecedor(orgId, supplierId);
    }

    /** O pedido em texto: o fornecedor tem de poder responder sem abrir nada. */
    private static String corpo(PedidoView pedido) {
        StringBuilder sb = new StringBuilder("<p>Bom dia,</p>");
        sb.append("<p>Agradecemos proposta de preço para o seguinte, com prazo de entrega e "
                + "validade da cotação:</p><ul>");
        pedido.lines().forEach(l -> {
            sb.append("<li>").append(l.quantity()).append(' ')
                    .append(l.unit() == null ? "" : l.unit()).append(" — ")
                    .append(l.description());
            if (l.specification() != null) {
                sb.append(" <b>(").append(l.specification()).append(")</b>");
            }
            sb.append("</li>");
        });
        sb.append("</ul>");
        if (pedido.dueDate() != null) {
            sb.append("<p>Agradecemos resposta até <b>").append(pedido.dueDate())
                    .append("</b>.</p>");
        }
        sb.append("<p>Referência do pedido: <b>").append(pedido.number()).append("</b></p>");
        return sb.toString();
    }
}
