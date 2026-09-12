package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;

import ao.autocare.domain.StoredFile;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderAttachment;
import ao.autocare.domain.enums.Enums.WorkOrderAttachmentKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.storage.FileUrls;
import ao.autocare.storage.StorageProperties;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import ao.autocare.storage.StorageProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos e fotografias de uma ordem.
 *
 * <p>Fatura da oficina, relatório de ensaio, fotografia do antes e do depois.
 * O ficheiro em si vai para o mesmo armazenamento de tudo o resto — não há um
 * segundo sistema de ficheiros só para manutenção.
 *
 * <p>A fotografia do antes é o que resolve a discussão meses depois: sem ela,
 * "a viatura já vinha assim" e "estragaram-na na oficina" são duas afirmações
 * igualmente indemonstráveis.
 */
@Service
public class WorkOrderAttachmentService {

    /** Formatos aceites: o que uma oficina realmente envia. */
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");

    /** Acima disto, a ordem deixa de ser consultável e passa a ser um arquivo. */
    private static final int MAX_ATTACHMENTS = 30;

    private final WorkOrderRepository workOrders;
    private final StoredFileRepository files;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final StorageProvider storage;
    private final FileUrls fileUrls;
    private final StorageProperties storageProps;
    private final AuditService audit;

    public WorkOrderAttachmentService(
            WorkOrderRepository workOrders,
            StoredFileRepository files,
            OrganizationRepository organizations,
            UserRepository users,
            StorageProvider storage,
            FileUrls fileUrls,
            StorageProperties storageProps,
            AuditService audit) {
        this.workOrders = workOrders;
        this.files = files;
        this.organizations = organizations;
        this.users = users;
        this.storage = storage;
        this.fileUrls = fileUrls;
        this.storageProps = storageProps;
        this.audit = audit;
    }

    public record AttachmentView(
            String id,
            String fileId,
            String url,
            String name,
            String contentType,
            long sizeBytes,
            WorkOrderAttachmentKind kind,
            String kindLabel,
            String caption,
            Instant uploadedAt) {}

    @Transactional
    public AttachmentView upload(
            String orgId, String userId, String workOrderId,
            MultipartFile file, WorkOrderAttachmentKind kind, String caption) {

        WorkOrder w = require(orgId, workOrderId);

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nenhum ficheiro foi enviado.");
        }
        String contentType = normalize(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest(
                    "Formato não suportado. Use JPG, PNG, WEBP, GIF ou PDF.");
        }
        if (file.getSize() > storageProps.maxFileBytesOrDefault()) {
            throw ApiException.badRequest("O ficheiro é demasiado grande (máx. "
                    + (storageProps.maxFileBytesOrDefault() / (1024 * 1024)) + " MB).");
        }
        if (w.getAttachments().size() >= MAX_ATTACHMENTS) {
            throw ApiException.conflict(
                    "Esta ordem já tem " + MAX_ATTACHMENTS + " anexos. "
                            + "Acima disto deixa de se conseguir consultar.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro enviado.");
        }

        StoredFile stored = new StoredFile();
        stored.setOrganization(organizations.getReferenceById(orgId));
        stored.setStorageKey(storage.store(bytes, contentType, file.getOriginalFilename()));
        stored.setOriginalName(trim(file.getOriginalFilename()));
        stored.setContentType(contentType);
        stored.setSizeBytes(bytes.length);
        stored.setUploadedBy(users.getReferenceById(userId));
        files.save(stored);

        WorkOrderAttachment a = new WorkOrderAttachment();
        a.setFile(stored);
        a.setKind(kind != null ? kind : WorkOrderAttachmentKind.OTHER);
        a.setCaption(trim(caption));
        a.setUploadedBy(userId);
        w.addAttachment(a);
        // Sem isto o anexo sai sem id e o ecrã mostra-o mas não o consegue apagar.
        workOrders.flush();

        audit.record(orgId, userId, "work_order.attachment", "WorkOrder", w.getId(),
                w.getNumber() + " · " + a.getKind() + " · " + stored.getOriginalName());
        return view(a);
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> list(String orgId, String workOrderId) {
        return require(orgId, workOrderId).getAttachments().stream().map(this::view).toList();
    }

    @Transactional
    public void delete(String orgId, String userId, String workOrderId, String attachmentId) {
        WorkOrder w = require(orgId, workOrderId);
        WorkOrderAttachment a = w.getAttachments().stream()
                .filter(x -> x.getId().equals(attachmentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Anexo não encontrado nesta ordem."));

        // Uma ordem fechada é o registo do que aconteceu: apagar-lhe a fatura
        // depois de os custos entrarem nos indicadores do ano seria apagar a
        // prova de uma despesa já contabilizada.
        if (w.getStatus().isTerminal()) {
            throw ApiException.conflict(
                    "Esta ordem está " + w.getStatus().label().toLowerCase()
                            + "; os anexos fazem parte do registo e já não se apagam.");
        }
        String nome = a.getFile().getOriginalName();
        w.getAttachments().remove(a);

        audit.record(orgId, userId, "work_order.attachment_delete", "WorkOrder", w.getId(),
                w.getNumber() + " · " + nome);
    }

    private AttachmentView view(WorkOrderAttachment a) {
        StoredFile f = a.getFile();
        return new AttachmentView(
                a.getId(), f.getId(), fileUrls.signed(f.getId()),
                f.getOriginalName(), f.getContentType(),
                f.getSizeBytes(),
                a.getKind(), a.getKind().label(), a.getCaption(), a.getCreatedAt());
    }

    private WorkOrder require(String orgId, String id) {
        return workOrders.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem não encontrada."));
    }

    private static String normalize(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()
                && !contentType.equals("application/octet-stream")) {
            return contentType.toLowerCase();
        }
        String nome = filename != null ? filename.toLowerCase() : "";
        if (nome.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (nome.endsWith(".png")) {
            return "image/png";
        }
        if (nome.endsWith(".webp")) {
            return "image/webp";
        }
        if (nome.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
