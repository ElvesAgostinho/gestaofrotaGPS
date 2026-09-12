package ao.autocare.modules.document;

import ao.autocare.common.ApiException;
import ao.autocare.common.ExpiryCalculator;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetDocument;
import ao.autocare.domain.StoredFile;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DocumentKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.document.dto.DocumentDtos.DocumentView;
import ao.autocare.modules.document.dto.DocumentDtos.KindView;
import ao.autocare.modules.document.dto.DocumentDtos.SaveDocumentRequest;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetDocumentRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.storage.StorageProperties;
import ao.autocare.storage.StorageProvider;
import ao.autocare.storage.FileUrls;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos do ativo e vigilância das suas validades.
 *
 * <p>O valor desta fatia não está em guardar ficheiros — isso as fotografias já
 * faziam. Está em avisar antes do prazo: um seguro que caduca imobiliza a
 * viatura, uma inspeção fora de prazo é uma multa. Por isso o aviso é dado com
 * antecedência e repetido nos marcos que interessam, não uma vez só.
 */
@Service
public class DocumentService {

    /** Formatos aceites. Um documento é para ler, não para editar. */
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    /**
     * Antecedências a que se avisa, em dias. São marcos, não repetição diária:
     * avisar todos os dias durante um mês faz as pessoas ignorarem o aviso.
     */
    private static final int[] WARNING_DAYS = {30, 7, 0};

    private final AssetDocumentRepository documents;
    private final AssetRepository assets;
    private final StoredFileRepository files;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final StorageProvider storage;
    private final StorageProperties storageProps;
    private final FileUrls fileUrls;
    private final NotificationService notifications;
    private final AuditService audit;

    public DocumentService(
            AssetDocumentRepository documents,
            AssetRepository assets,
            StoredFileRepository files,
            OrganizationRepository organizations,
            UserRepository users,
            StorageProvider storage,
            StorageProperties storageProps,
            FileUrls fileUrls,
            NotificationService notifications,
            AuditService audit) {
        this.documents = documents;
        this.assets = assets;
        this.files = files;
        this.organizations = organizations;
        this.users = users;
        this.storage = storage;
        this.storageProps = storageProps;
        this.fileUrls = fileUrls;
        this.notifications = notifications;
        this.audit = audit;
    }

    public List<KindView> kinds() {
        return Arrays.stream(DocumentKind.values())
                .map(k -> new KindView(k, k.label())).toList();
    }

    // ==== Consulta ======================================================
    @Transactional(readOnly = true)
    public List<DocumentView> listForAsset(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        Instant now = Instant.now();
        return documents.findByAssetIdOrderByKindAscTitleAsc(assetId).stream()
                .map(d -> DocumentView.of(d, url(d), now)).toList();
    }

    /**
     * Documentos da frota que caducam dentro de {@code withinDays} — incluindo
     * os que já caducaram, que são os mais urgentes.
     */
    @Transactional(readOnly = true)
    public List<DocumentView> expiring(String orgId, int withinDays) {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(Math.max(0, withinDays)));
        return documents.findExpiringUntil(orgId, until).stream()
                .map(d -> DocumentView.of(d, url(d), now)).toList();
    }

    // ==== Criação e edição ==============================================
    @Transactional
    public DocumentView create(
            String orgId, String userId, String assetId, SaveDocumentRequest req) {

        Asset asset = requireAsset(orgId, assetId);
        AssetDocument d = new AssetDocument();
        d.setOrganization(organizations.getReferenceById(orgId));
        d.setAsset(asset);
        d.setKind(req.kind() != null ? req.kind() : DocumentKind.OTHER);
        d.setTitle(req.title().trim());
        if (userId != null) {
            d.setCreatedBy(users.getReferenceById(userId));
        }
        apply(d, req);
        documents.save(d);

        audit.record(orgId, userId, "asset.document.create", "AssetDocument", d.getId(),
                asset.getTag() + " · " + d.getKind().label() + " · " + d.getTitle());
        return DocumentView.of(d, url(d), Instant.now());
    }

    /** Carrega o ficheiro e cria o documento numa só operação. */
    @Transactional
    public DocumentView upload(
            String orgId, String userId, String assetId,
            MultipartFile file, SaveDocumentRequest req) {

        StoredFile stored = store(orgId, userId, file);
        DocumentView created = create(orgId, userId, assetId,
                new SaveDocumentRequest(req.kind(), req.title(), req.reference(), req.issuer(),
                        req.issuedAt(), req.expiresAt(), stored.getId(), req.notes()));
        return created;
    }

    @Transactional
    public DocumentView update(String orgId, String userId, String id, SaveDocumentRequest req) {
        AssetDocument d = require(orgId, id);
        if (req.kind() != null) d.setKind(req.kind());
        if (req.title() != null && !req.title().isBlank()) d.setTitle(req.title().trim());
        apply(d, req);
        // Renovar um documento resolve o aviso de caducidade que estivesse aberto.
        notifications.resolve("document_expiry", d.getId());

        audit.record(orgId, userId, "asset.document.update", "AssetDocument", d.getId(), d.getTitle());
        return DocumentView.of(d, url(d), Instant.now());
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        AssetDocument d = require(orgId, id);
        String label = d.getAsset().getTag() + " · " + d.getTitle();
        notifications.resolve("document_expiry", d.getId());
        documents.delete(d);
        audit.record(orgId, userId, "asset.document.delete", "AssetDocument", id, label);
    }

    // ==== Vigilância das validades ======================================
    /**
     * Avisa dos documentos a caducar. Chamado pelo agendador.
     *
     * <p>Os avisos são dados em marcos (30 dias, 7 dias, no próprio dia e depois
     * de caducado), não todos os dias. A origem do aviso inclui o marco, por isso
     * o mesmo documento pode avisar mais do que uma vez sem repetir o mesmo aviso.
     *
     * @return quantos avisos foram criados
     */
    @Transactional
    public int notifyExpiring() {
        Instant now = Instant.now();
        int sent = 0;
        for (AssetDocument d : documents.findAllWithExpiry()) {
            ExpiryCalculator.ExpiryStatus status = ExpiryCalculator.status(d.getExpiresAt(), now);
            if (status == null) {
                continue;
            }
            Integer milestone = milestoneFor(status.daysRemaining());
            if (milestone == null) {
                continue;
            }
            String title = status.expired()
                    ? d.getKind().label() + " caducado — " + d.getAsset().getTag()
                    : d.getKind().label() + " a caducar — " + d.getAsset().getTag();

            sent += notifications.notifyManagers(NotificationService.Draft.of(
                            d.getOrganization().getId(),
                            AlertCategory.DOCUMENT,
                            status.expired() ? AlertSeverity.CRITICAL : status.severity(),
                            title,
                            d.getTitle() + " · " + status.label(),
                            "document_expiry", d.getId() + ":" + milestone,
                            "/ativos/" + d.getAsset().getId() + "/documentos")
                    .forAsset(d.getAsset()));
        }
        return sent;
    }

    /**
     * A que marco corresponde este número de dias, ou {@code null} se ainda não
     * chegou a nenhum. Já caducado conta sempre como o marco zero.
     */
    private Integer milestoneFor(long daysRemaining) {
        if (daysRemaining < 0) {
            return 0;
        }
        for (int marco : WARNING_DAYS) {
            if (daysRemaining <= marco) {
                return marco;
            }
        }
        return null;
    }

    // ==== Auxiliares ====================================================
    private void apply(AssetDocument d, SaveDocumentRequest req) {
        if (req.reference() != null) d.setReference(blankToNull(req.reference()));
        if (req.issuer() != null) d.setIssuer(blankToNull(req.issuer()));
        if (req.issuedAt() != null) d.setIssuedAt(req.issuedAt());
        if (req.expiresAt() != null) d.setExpiresAt(req.expiresAt());
        if (req.notes() != null) d.setNotes(blankToNull(req.notes()));
        if (req.fileId() != null) {
            d.setFile(req.fileId().isBlank() ? null
                    : files.findById(req.fileId())
                            .orElseThrow(() -> ApiException.badRequest("Ficheiro não encontrado.")));
        }
        if (d.getIssuedAt() != null && d.getExpiresAt() != null
                && !d.getExpiresAt().isAfter(d.getIssuedAt())) {
            throw ApiException.badRequest(
                    "A data de validade tem de ser posterior à data de emissão.");
        }
    }

    private StoredFile store(String orgId, String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nenhum ficheiro foi enviado.");
        }
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase() : "application/octet-stream";
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("Formato não suportado. Use PDF, JPG, PNG ou WEBP.");
        }
        if (file.getSize() > storageProps.maxFileBytesOrDefault()) {
            throw ApiException.badRequest("O ficheiro é demasiado grande (máx. "
                    + (storageProps.maxFileBytesOrDefault() / (1024 * 1024)) + " MB).");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro enviado.");
        }
        String key = storage.store(bytes, contentType, file.getOriginalFilename());

        StoredFile stored = new StoredFile();
        stored.setOrganization(organizations.getReferenceById(orgId));
        stored.setStorageKey(key);
        stored.setOriginalName(trim(file.getOriginalFilename(), 300));
        stored.setContentType(contentType);
        stored.setSizeBytes(bytes.length);
        if (userId != null) {
            stored.setUploadedBy(users.getReferenceById(userId));
        }
        return files.save(stored);
    }

    private String url(AssetDocument d) {
        return d.getFile() != null ? fileUrls.signed(d.getFile().getId()) : null;
    }

    private AssetDocument require(String orgId, String id) {
        return documents.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Documento não encontrado."));
    }

    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
