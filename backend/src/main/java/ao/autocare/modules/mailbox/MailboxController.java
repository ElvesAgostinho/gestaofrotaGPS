package ao.autocare.modules.mailbox;

import ao.autocare.modules.mailbox.dto.MailboxDtos.Envio;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Estado;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Mensagem;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Pagina;
import ao.autocare.modules.mailbox.dto.MailboxDtos.Pasta;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * O correio da empresa.
 *
 * <p>É uma caixa partilhada: há um endereço da empresa e quem tiver a
 * permissão vê o mesmo correio. Por isso está fechada a quem gere — um
 * motorista ou um técnico não têm nada que ler a correspondência comercial, e
 * o mesmo endereço que recebe a resposta do fornecedor recebe também a
 * proposta de preços da concorrência.
 */
@Tag(name = "Correio")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.MAILBOX_USE)
@RestController
@RequestMapping("/api/v1/mailbox")
public class MailboxController {

    private final MailboxService mailbox;
    private final EntityDirectory directory;
    private final OrgContext orgContext;

    public MailboxController(
            MailboxService mailbox, EntityDirectory directory, OrgContext orgContext) {
        this.mailbox = mailbox;
        this.directory = directory;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Estado da caixa de correio")
    @GetMapping("/status")
    public Estado estado(@AuthenticationPrincipal AuthPrincipal p) {
        return mailbox.estado(org(p));
    }

    @Operation(summary = "Pastas do servidor, com o que está por ler")
    @GetMapping("/folders")
    public List<Pasta> pastas(@AuthenticationPrincipal AuthPrincipal p) {
        return mailbox.pastas(org(p));
    }

    @Operation(summary = "Entidades do sistema com email",
            description = "Fornecedores, pessoas de contacto, motoristas e equipa — "
                    + "para filtrar o correio por quem o escreveu.")
    @GetMapping("/directory")
    public List<EntityDirectory.Entidade> directorio(@AuthenticationPrincipal AuthPrincipal p) {
        return directory.comEmail(org(p));
    }

    @Operation(summary = "Mensagens de uma pasta",
            description = "Trabalha sobre as mensagens mais recentes da pasta. Pode filtrar "
                    + "por não lidas, com anexos, só de entidades registadas, por um endereço "
                    + "concreto, ou por texto no assunto e no remetente.")
    @GetMapping("/messages")
    public Pagina mensagens(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "false") boolean withAttachments,
            @RequestParam(defaultValue = "false") boolean knownOnly,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String q) {
        return mailbox.listar(org(p), folder, Math.max(0, page),
                Math.min(Math.max(size, 5), 100), unread, withAttachments, knownOnly, entity, q);
    }

    @Operation(summary = "Abrir uma mensagem (marca-a como lida)")
    @GetMapping("/messages/{uid}")
    public Mensagem mensagem(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable long uid,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "true") boolean markRead) {
        return mailbox.abrirMensagem(org(p), folder, uid, markRead);
    }

    @Operation(summary = "Descarregar um anexo")
    @GetMapping("/messages/{uid}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> anexo(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable long uid,
            @PathVariable String attachmentId,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "false") boolean inline) {
        MailboxService.Ficheiro f = mailbox.anexo(org(p), folder, uid, attachmentId);
        ContentDisposition disposicao = (inline
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(f.nome(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .contentType(MediaType.parseMediaType(f.tipo()))
                .body(f.bytes());
    }

    @Operation(summary = "Marcar como lida ou por ler")
    @PostMapping("/messages/{uid}/read")
    public Map<String, String> marcar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable long uid,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "true") boolean read) {
        mailbox.marcar(org(p), folder, uid, read);
        return Map.of("message", read ? "Marcada como lida." : "Marcada como por ler.");
    }

    @Operation(summary = "Mover para o lixo",
            description = "Numa caixa partilhada, o que um apaga por engano tem de poder ser "
                    + "recuperado por outro: move-se para o lixo em vez de apagar de vez.")
    @PostMapping("/messages/{uid}/delete")
    public Map<String, String> apagar(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable long uid,
            @RequestParam(defaultValue = "INBOX") String folder) {
        mailbox.apagar(org(p), folder, uid);
        return Map.of("message", "Mensagem movida para o lixo.");
    }

    @Operation(summary = "Enviar uma mensagem, com anexos",
            description = "Os ficheiros vão no próprio pedido. Fica cópia em «Enviados», para "
                    + "quem abrir a caixa pelo telemóvel ver o que saiu daqui.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/send")
    public Map<String, String> enviar(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestPart("para") String para,
            @RequestPart(value = "cc", required = false) String cc,
            @RequestPart(value = "bcc", required = false) String bcc,
            @RequestPart("assunto") String assunto,
            @RequestPart(value = "corpo", required = false) String corpo,
            @RequestPart(value = "anexos", required = false) List<MultipartFile> anexos) {
        mailbox.enviar(org(p), p.id(),
                new Envio(para, cc, bcc, assunto, corpo, null, null), anexos);
        return Map.of("message", "Mensagem enviada.");
    }
}
