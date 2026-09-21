package ao.autocare.modules.transport;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.transport.dto.TransportDtos.CancelRequest;
import ao.autocare.modules.transport.dto.TransportDtos.DeliverRequest;
import ao.autocare.modules.transport.dto.TransportDtos.DepartRequest;
import ao.autocare.modules.transport.dto.TransportDtos.NoteSummary;
import ao.autocare.modules.transport.dto.TransportDtos.NoteView;
import ao.autocare.modules.transport.dto.TransportDtos.SaveNoteRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guias de transporte — o documento que acompanha a carga.
 *
 * <p>E o que a autoridade pede na estrada e o que o cliente assina na entrega.
 */
@Tag(name = "Guias de transporte")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
@RequestMapping("/api/v1/transport-notes")
public class TransportNoteController {

    private final TransportNotePdfService pdfService;

    private final TransportNoteService service;
    private final OrgContext orgContext;

    private final ao.autocare.modules.org.DocumentSealService seals;

    public TransportNoteController(TransportNoteService service, OrgContext orgContext,
            TransportNotePdfService pdfService, ao.autocare.modules.org.DocumentSealService seals) {
        this.pdfService = pdfService;
        this.seals = seals;
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Guias de transporte")
    @GetMapping
    public PagedResponse<NoteSummary> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PagedResponse.of(
                service.list(org(p), status, PageRequest.of(page, Math.min(size, 200))));
    }

    @Operation(summary = "Ficha de uma guia")
    @GetMapping("/{id}")
    public NoteView get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.get(org(p), id);
    }

    @Operation(summary = "Criar uma guia",
            description = "Nasce em rascunho. Os totais de peso e volume sao sempre "
                    + "calculados a partir das linhas da carga.")
    @RequirePermission(Permission.TRANSPORT_MANAGE)
    @PostMapping
    public NoteView create(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SaveNoteRequest req) {
        return service.create(org(p), p.id(), req);
    }

    @Operation(summary = "Alterar uma guia em rascunho",
            description = "Depois de emitida ja nao se altera: o papel saiu com o condutor.")
    @RequirePermission(Permission.TRANSPORT_MANAGE)
    @PutMapping("/{id}")
    public NoteView update(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveNoteRequest req) {
        return service.update(org(p), p.id(), id, req);
    }

    @Operation(summary = "Emitir a guia",
            description = "Exige viatura, motorista e carga. Uma guia em branco na estrada "
                    + "nao serve a ninguem.")
    @RequirePermission(Permission.TRANSPORT_MANAGE)
    @PostMapping("/{id}/issue")
    public NoteView issue(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return service.issue(org(p), p.id(), id);
    }

    @Operation(summary = "Registar a saida")
    @RequirePermission(Permission.TRANSPORT_MANAGE)
    @PostMapping("/{id}/depart")
    public NoteView depart(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody DepartRequest req) {
        return service.depart(org(p), p.id(), id, req.meter());
    }

    @Operation(summary = "Registar a entrega",
            description = "Quem recebe com reservas tem de dizer quais.")
    @RequirePermission(Permission.TRANSPORT_MANAGE)
    @PostMapping("/{id}/deliver")
    public NoteView deliver(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody DeliverRequest req) {
        return service.deliver(org(p), p.id(), id, req);
    }

    @Operation(summary = "Anular a guia",
            description = "Nao se apaga: o registo explica o salto na numeracao.")
    @RequireRole(MembershipRole.MANAGER)
    @PostMapping("/{id}/cancel")
    public NoteView cancel(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody CancelRequest req) {
        return service.cancel(org(p), p.id(), id, req.reason());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Imprimir a guia de transporte (PDF)",
            description = "Com o timbre da empresa. Vai na cabina e assina-se na entrega.")
    @org.springframework.web.bind.annotation.GetMapping(value = "/{id}/print.pdf",
            produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> print(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            ao.autocare.security.AuthPrincipal p,
            @org.springframework.web.bind.annotation.PathVariable String id) {
        String numero = service.get(org(p), id).number();
        byte[] pdf = seals.emitir(org(p), p.id(), "TRANSPORT_NOTE", id, numero,
                selo -> pdfService.render(org(p), id, selo));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"guia-transporte.pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
