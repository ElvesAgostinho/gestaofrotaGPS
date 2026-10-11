package ao.autocare.modules.purchase;

import ao.autocare.common.ApiException;
import ao.autocare.domain.PurchaseOrder;
import ao.autocare.modules.mailbox.MailboxService;
import ao.autocare.modules.org.DocumentSealService;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.purchase.dto.OrderDtos.ConfiguracaoRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.ConfiguracaoView;
import ao.autocare.modules.purchase.dto.OrderDtos.DecisaoRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.EnvioRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.OrdemRequest;
import ao.autocare.modules.purchase.dto.OrderDtos.OrdemView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Ordens de compra.
 *
 * <p>Tudo aqui mexe em dinheiro, e por isso está fechado a quem o pode ver. A
 * aprovação tem a sua própria regra, que não é um papel fixo mas um valor: o
 * serviço decide, pelo total da ordem e pelos limites da empresa, se esta
 * pessoa pode aprovar — e, quando não pode, diz porquê em vez de esconder o
 * botão sem explicação.
 */
@Tag(name = "Compras")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.COSTS_VIEW)
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    private final PurchaseOrderPdfService pdf;
    private final DocumentSealService seals;
    private final MailboxService mailbox;
    private final OrgContext orgContext;

    public PurchaseOrderController(
            PurchaseOrderService service,
            PurchaseOrderPdfService pdf,
            DocumentSealService seals,
            MailboxService mailbox,
            OrgContext orgContext) {
        this.service = service;
        this.pdf = pdf;
        this.seals = seals;
        this.mailbox = mailbox;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== Lista e ficha =====================================================

    @Operation(summary = "Ordens de compra")
    @GetMapping
    public List<OrdemView> listar(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String status) {
        return service.listar(org(p), p.id(), status);
    }

    @Operation(summary = "Uma ordem de compra, com histórico")
    @GetMapping("/{id}")
    public OrdemView obter(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.obter(org(p), p.id(), id);
    }

    @Operation(summary = "Criar uma ordem de compra")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdemView criar(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody OrdemRequest req) {
        return service.criar(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar uma ordem que ainda não saiu")
    @PutMapping("/{id}")
    public OrdemView alterar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @Valid @RequestBody OrdemRequest req) {
        return service.alterar(org(p), p.id(), id, req);
    }

    // ==== O percurso ========================================================

    @Operation(summary = "Submeter para aprovação",
            description = "Abaixo do limite de aprovação automática, fica logo aprovada — "
                    + "senão ninguém usa o sistema para comprar um filtro.")
    @PostMapping("/{id}/submit")
    public OrdemView submeter(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.submeter(org(p), p.id(), id);
    }

    @Operation(summary = "Aprovar",
            description = "Quem pode aprovar depende do valor: acima do limite da empresa, "
                    + "só o dono.")
    @PostMapping("/{id}/approve")
    public OrdemView aprovar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody(required = false) DecisaoRequest req) {
        return service.aprovar(org(p), p.id(), id, req != null ? req.motivo() : null);
    }

    @Operation(summary = "Rejeitar",
            description = "O motivo é obrigatório: devolver uma ordem sem dizer o que está "
                    + "mal faz com que ela volte igual.")
    @PostMapping("/{id}/reject")
    public OrdemView rejeitar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody DecisaoRequest req) {
        return service.rejeitar(org(p), p.id(), id, req != null ? req.motivo() : null);
    }

    @Operation(summary = "Confirmar que o fornecedor aceitou",
            description = "Enviar não é o mesmo que o fornecedor ter lido. São estados "
                    + "diferentes de propósito.")
    @PostMapping("/{id}/confirm")
    public OrdemView confirmar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody(required = false) DecisaoRequest req) {
        return service.confirmar(org(p), p.id(), id, req != null ? req.motivo() : null);
    }

    @Operation(summary = "Anular")
    @PostMapping("/{id}/cancel")
    public OrdemView anular(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody(required = false) DecisaoRequest req) {
        return service.anular(org(p), p.id(), id, req != null ? req.motivo() : null);
    }

    // ==== O papel e o envio =================================================

    @Operation(summary = "A ordem em PDF, com timbre e selo de verificação")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> imprimir(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        String orgId = org(p);
        OrdemView o = service.obter(orgId, p.id(), id);
        byte[] bytes = seals.emitir(orgId, p.id(), "PURCHASE_ORDER", id, o.number(),
                selo -> pdf.render(orgId, id, selo));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + o.number() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @Operation(summary = "Enviar a ordem ao fornecedor por email",
            description = "O PDF nasce no servidor e segue em anexo, pela caixa de correio da "
                    + "empresa. Fica cópia em «Enviados» e a ordem passa a «enviada».")
    @PostMapping("/{id}/send")
    public OrdemView enviar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String id,
            @RequestBody(required = false) EnvioRequest req) {
        String orgId = org(p);
        OrdemView o = service.obter(orgId, p.id(), id);
        if (!PurchaseOrder.Status.APPROVED.name().equals(o.status())
                && !PurchaseOrder.Status.SENT.name().equals(o.status())) {
            throw ApiException.conflict(
                    "Só se envia uma ordem aprovada. Esta está «" + o.statusLabel() + "».");
        }

        String para = req != null && req.para() != null && !req.para().isBlank()
                ? req.para().trim()
                : o.supplierEmail();
        if (para == null || para.isBlank()) {
            throw ApiException.badRequest(
                    "Este fornecedor não tem email. Indique um destinatário, ou preencha o "
                            + "email na ficha dele.");
        }

        byte[] bytes = seals.emitir(orgId, p.id(), "PURCHASE_ORDER", id, o.number(),
                selo -> pdf.render(orgId, id, selo));

        String mensagem = req != null && req.mensagem() != null && !req.mensagem().isBlank()
                ? req.mensagem()
                : corpoPorOmissao(o);
        mailbox.enviarDocumento(orgId, p.id(), para, null,
                "Ordem de compra " + o.number(), mensagem,
                o.number() + ".pdf", bytes, MediaType.APPLICATION_PDF_VALUE);

        return service.marcarEnviada(orgId, p.id(), id, para);
    }

    /** Um corpo curto e claro: quem o lê tem de saber o que fazer com ele. */
    private static String corpoPorOmissao(OrdemView o) {
        StringBuilder sb = new StringBuilder("<p>Bom dia,</p>");
        sb.append("<p>Segue em anexo a nossa ordem de compra <b>").append(o.number())
                .append("</b>, no valor de ").append(o.total()).append(' ')
                .append(o.currency()).append(".</p>");
        if (o.expectedAt() != null) {
            sb.append("<p>Entrega pretendida até <b>").append(o.expectedAt()).append("</b>.</p>");
        }
        if (o.deliveryAddress() != null && !o.deliveryAddress().isBlank()) {
            sb.append("<p>Local de entrega: ").append(o.deliveryAddress()).append("</p>");
        }
        sb.append("<p>Agradecemos a confirmação por resposta a este email.</p>");
        return sb.toString();
    }

    // ==== Limites de aprovação ==============================================

    @Operation(summary = "Limites de aprovação de compras")
    @GetMapping("/settings")
    public ConfiguracaoView configuracao(@AuthenticationPrincipal AuthPrincipal p) {
        return service.verConfiguracao(org(p));
    }

    @Operation(summary = "Guardar os limites de aprovação",
            description = "Dois números: abaixo do primeiro quem cria aprova; acima do segundo "
                    + "só o dono; no meio, basta quem gere.")
    @RequirePermission(Permission.SETTINGS_MANAGE)
    @PutMapping("/settings")
    public ConfiguracaoView guardarConfiguracao(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody ConfiguracaoRequest req) {
        return service.guardarConfiguracao(org(p), p.id(), req);
    }

    @Operation(summary = "Quanto está encomendado e ainda não chegou, por peça")
    @GetMapping("/on-order")
    public Map<String, java.math.BigDecimal> porReceber(@AuthenticationPrincipal AuthPrincipal p) {
        return service.porReceberPorPeca(org(p));
    }
}
