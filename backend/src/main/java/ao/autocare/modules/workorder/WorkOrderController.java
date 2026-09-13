package ao.autocare.modules.workorder;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CompleteRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CreateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureCreated;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FromDueRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.LaborInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.PartInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.StartRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.UpdateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderSummary;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import ao.autocare.modules.workorder.dto.WorkOrderDtos;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
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

@Tag(name = "Ordens de Manutenção")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class WorkOrderController {

    private final WorkOrderService service;
    private final SupplierService supplierService;
    private final WorkOrderAttachmentService attachments;
    private final WorkOrderPdfService pdfService;
    private final ao.autocare.modules.org.DocumentSealService seals;
    private final OrgContext orgContext;

    public WorkOrderController(WorkOrderService service, OrgContext orgContext, SupplierService supplierService,
            WorkOrderAttachmentService attachments,
            WorkOrderPdfService pdfService,
            ao.autocare.modules.org.DocumentSealService seals) {
        this.service = service;
        this.supplierService = supplierService;
        this.attachments = attachments;
        this.pdfService = pdfService;
        this.seals = seals;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Listar ordens de manutenção")
    @GetMapping("/api/v1/work-orders")
    public PagedResponse<WorkOrderSummary> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assetId,
            @Parameter(description = "Id de um membro da equipa, ou \"me\" para as minhas ordens")
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        String assignee = "me".equalsIgnoreCase(assignedTo) ? p.id() : assignedTo;
        return service.list(org(p), status, assetId, assignee,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Obter uma ordem de manutenção")
    @GetMapping("/api/v1/work-orders/{id}")
    public WorkOrderView get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return money(p, service.get(org(p), id));
    }

    @Operation(summary = "Criar uma ordem (corretiva / inspeção / preventiva manual)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView create(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CreateWorkOrderRequest req) {
        return money(p, service.create(org(p), p.id(), req));
    }

    @Operation(summary = "Gerar uma ordem preventiva das tarefas de plano vencidas de um ativo")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/from-due")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView fromDue(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody FromDueRequest req) {
        return service.fromDue(org(p), p.id(), req);
    }

    @Operation(summary = "Atualizar uma ordem")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PatchMapping("/api/v1/work-orders/{id}")
    public WorkOrderView update(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody UpdateWorkOrderRequest req) {
        return money(p, service.update(org(p), p.id(), id, req));
    }

    @Operation(summary = "Iniciar a execução")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/start")
    public WorkOrderView start(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody(required = false) StartRequest req) {
        return money(p, service.start(org(p), p.id(), id,
                req != null ? req : new StartRequest(null, null)));
    }

    @Operation(summary = "Concluir (consome peças, repõe relógios do plano, regista reparação)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/complete")
    public WorkOrderView complete(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody(required = false) CompleteRequest req) {
        return money(p, service.complete(org(p), p.id(), id,
                req != null ? req : new CompleteRequest(null, null, null, null, null, null, null, null, null)));
    }

    @Operation(summary = "Verificar / aprovar uma ordem concluída")
    @RequirePermission(Permission.WORKORDERS_CLOSE)
    @PostMapping("/api/v1/work-orders/{id}/verify")
    public WorkOrderView verify(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return money(p, service.verify(org(p), p.id(), id));
    }

    @Operation(summary = "Cancelar uma ordem")
    @RequirePermission(Permission.WORKORDERS_CLOSE)
    @PostMapping("/api/v1/work-orders/{id}/cancel")
    public WorkOrderView cancel(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        return money(p, service.cancel(org(p), p.id(), id,
                body != null ? body.get("reason") : null));
    }

    @Operation(summary = "Registar um serviço de oficina externa",
            description = "Sem isto o custo da ordem fica sistematicamente abaixo do real.")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/work-orders/{id}/external-services")
    public WorkOrderView addExternalService(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ao.autocare.modules.workorder.dto.WorkOrderDtos
                    .ExternalServiceInput in) {
        return money(p, service.addExternalService(org(p), p.id(), id, in));
    }

    @Operation(summary = "Retirar um serviço externo da ordem")
    @RequirePermission(Permission.COSTS_VIEW)
    @DeleteMapping("/api/v1/work-orders/{id}/external-services/{serviceId}")
    public WorkOrderView removeExternalService(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String serviceId) {
        return money(p, service.removeExternalService(org(p), p.id(), id, serviceId));
    }

    // ==== Modulo de manutencao (Fatia 17) ==================================
    @Operation(summary = "Registar o diagnostico",
            description = "Sintoma, diagnostico, causa e solucao em campos separados.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/diagnosis")
    public WorkOrderView diagnose(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ao.autocare.modules.workorder.dto.WorkOrderDtos
                    .DiagnosisRequest req) {
        return money(p, service.diagnose(org(p), p.id(), id, req));
    }

    @Operation(summary = "Registar um orcamento",
            description = "Varios por ordem: comparar propostas e o que uma empresa faz.")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/work-orders/{id}/quotes")
    public WorkOrderView addQuote(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ao.autocare.modules.workorder.dto.WorkOrderDtos
                    .QuoteRequest req) {
        return money(p, service.addQuote(org(p), p.id(), id, req));
    }

    @Operation(summary = "Escolher o orcamento que vale para aprovacao")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/work-orders/{id}/quotes/{quoteId}/select")
    public WorkOrderView selectQuote(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String quoteId) {
        return money(p, service.selectQuote(org(p), p.id(), id, quoteId));
    }

    @Operation(summary = "Pedir aprovacao",
            description = "Abaixo do limite da empresa aprova-se sozinha.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/request-approval")
    public WorkOrderView requestApproval(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return money(p, service.requestApproval(org(p), p.id(), id));
    }

    @Operation(summary = "Aprovar a despesa")
    @RequirePermission(Permission.WORKORDERS_APPROVE)
    @PostMapping("/api/v1/work-orders/{id}/approve")
    public WorkOrderView approve(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody(required = false) ao.autocare.modules.workorder.dto.WorkOrderDtos
                    .ApprovalRequest req) {
        return money(p, service.approve(org(p), p.id(), id, req));
    }

    @Operation(summary = "Rejeitar o orcamento",
            description = "Exige explicacao: quem pediu precisa de saber o que mudar.")
    @RequirePermission(Permission.WORKORDERS_APPROVE)
    @PostMapping("/api/v1/work-orders/{id}/reject")
    public WorkOrderView reject(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody ao.autocare.modules.workorder.dto.WorkOrderDtos.ApprovalRequest req) {
        return money(p, service.reject(org(p), p.id(), id, req));
    }

    @Operation(summary = "Mudar o estado da ordem",
            description = "A transicao e validada e fica registada com o tempo no estado anterior.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/status")
    public WorkOrderView changeStatus(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody ao.autocare.modules.workorder.dto.WorkOrderDtos
                    .StatusChangeRequest req) {
        return money(p, service.moveTo(org(p), p.id(), id, req.status(), req.note()));
    }

    @Operation(summary = "Fechar a ordem em definitivo",
            description = "Depois disto os custos entram nos indicadores e nao mudam mais.")
    @RequirePermission(Permission.WORKORDERS_CLOSE)
    @PostMapping("/api/v1/work-orders/{id}/close")
    public WorkOrderView close(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return money(p, service.close(org(p), p.id(), id));
    }

    // ==== Fornecedores =====================================================
    @Operation(summary = "Oficinas e fornecedores")
    @GetMapping("/api/v1/suppliers")
    public java.util.List<SupplierService.SupplierView> suppliers(
            @AuthenticationPrincipal AuthPrincipal p) {
        return supplierService.list(org(p));
    }

    @Operation(summary = "Registar uma oficina ou fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @PostMapping("/api/v1/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierService.SupplierView createSupplier(
            @AuthenticationPrincipal AuthPrincipal p,
            @Valid @RequestBody SupplierService.SaveSupplierRequest req) {
        return supplierService.create(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar uma oficina ou fornecedor")
    @RequirePermission(Permission.COSTS_VIEW)
    @PutMapping("/api/v1/suppliers/{id}")
    public SupplierService.SupplierView updateSupplier(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SupplierService.SaveSupplierRequest req) {
        return supplierService.update(org(p), p.id(), id, req);
    }

    /**
     * Remove os valores financeiros para quem nao pode ve-los.
     *
     * <p>O documento pede que um utilizador sem permissao de custos nao veja
     * dinheiro. Esconder no ecra nao chega: os numeros continuariam a viajar na
     * resposta e bastava abrir as ferramentas do browser. Sao removidos aqui,
     * antes de sair do servidor.
     */
    private WorkOrderView money(AuthPrincipal p, WorkOrderView v) {
        return canSeeCosts(p) ? v : WorkOrderDtos.withoutMoney(v);
    }

    /** Dinheiro e assunto de gestao: um tecnico regista o trabalho, nao o custo. */
    /** Valores financeiros só a quem tem a permissão de custos. */
    private static boolean canSeeCosts(AuthPrincipal p) {
        return p != null && p.has(Permission.COSTS_VIEW);
    }

    // ==== Anexos e impressao (Fatia 18) ====================================
    @Operation(summary = "Anexar documento ou fotografia",
            description = "Fatura da oficina, relatorio de ensaio, fotografia do antes.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping(value = "/api/v1/work-orders/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkOrderAttachmentService.AttachmentView attach(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false)
            ao.autocare.domain.enums.Enums.WorkOrderAttachmentKind kind,
            @RequestParam(required = false) String caption) {
        return attachments.upload(org(p), p.id(), id, file, kind, caption);
    }

    @Operation(summary = "Anexos de uma ordem")
    @GetMapping("/api/v1/work-orders/{id}/attachments")
    public java.util.List<WorkOrderAttachmentService.AttachmentView> attachments(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return attachments.list(org(p), id);
    }

    @Operation(summary = "Remover um anexo")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @DeleteMapping("/api/v1/work-orders/{id}/attachments/{attachmentId}")
    public Map<String, String> removeAttachment(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String attachmentId) {
        attachments.delete(org(p), p.id(), id, attachmentId);
        return Map.of("message", "Anexo removido.");
    }

    @Operation(summary = "Imprimir a ordem de manutencao (PDF)",
            description = "Documento para assinar. Sem valores para quem nao pode ve-los.")
    @GetMapping(value = "/api/v1/work-orders/{id}/print.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> print(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {

        WorkOrderView ordem = service.get(org(p), id);
        byte[] pdf = seals.emitir(org(p), p.id(), "WORK_ORDER", id, ordem.number(),
                selo -> pdfService.render(org(p), id, canSeeCosts(p), selo));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"ordem-manutencao.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "Registar mão de obra")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/labor")
    public WorkOrderView addLabor(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody LaborInput in) {
        return money(p, service.addLabor(org(p), p.id(), id, in));
    }

    @Operation(summary = "Adicionar uma peça (consumida ao concluir)")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/parts")
    public WorkOrderView addPart(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody PartInput in) {
        return money(p, service.addPart(org(p), p.id(), id, in));
    }

    @Operation(summary = "Marcar/desmarcar uma tarefa da ordem")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/tasks/{taskId}")
    public WorkOrderView markTask(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body) {
        boolean done = body == null || body.get("done") == null || Boolean.TRUE.equals(body.get("done"));
        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        return service.markTaskDone(org(p), p.id(), id, taskId, done, notes);
    }

    @Operation(summary = "Registar uma avaria de um ativo")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/assets/{assetId}/failures")
    @ResponseStatus(HttpStatus.CREATED)
    public FailureCreated recordFailure(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String assetId,
            @Valid @RequestBody FailureInput in) {
        return service.recordStandaloneFailure(org(p), p.id(), assetId, in);
    }

    @Operation(summary = "Eliminar uma ordem (apenas se ainda não iniciada)")
    @RequirePermission(Permission.WORKORDERS_CLOSE)
    @DeleteMapping("/api/v1/work-orders/{id}")
    public Map<String, String> delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.cancel(org(p), p.id(), id, "removida");
        return Map.of("message", "Ordem cancelada.");
    }
}
