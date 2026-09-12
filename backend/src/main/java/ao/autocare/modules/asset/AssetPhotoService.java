package ao.autocare.modules.asset;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetPhoto;
import ao.autocare.domain.StoredFile;
import ao.autocare.domain.enums.Enums.PhotoKind;
import ao.autocare.modules.asset.dto.AssetPhotoDtos.AssetPhotoView;
import ao.autocare.modules.asset.dto.AssetPhotoDtos.UpdatePhotoRequest;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.AssetPhotoRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.storage.FileUrls;
import ao.autocare.storage.StorageProperties;
import ao.autocare.storage.StorageProvider;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetPhotoService {

    private static final Logger log = LoggerFactory.getLogger(AssetPhotoService.class);
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");
    private static final int MAX_PHOTOS = 30;

    private final AssetRepository assets;
    private final AssetPhotoRepository photos;
    private final StoredFileRepository files;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final StorageProvider storage;
    private final StorageProperties storageProps;
    private final FileUrls fileUrls;
    private final AuditService audit;

    public AssetPhotoService(
            AssetRepository assets,
            AssetPhotoRepository photos,
            StoredFileRepository files,
            OrganizationRepository organizations,
            UserRepository users,
            StorageProvider storage,
            StorageProperties storageProps,
            FileUrls fileUrls,
            AuditService audit) {
        this.assets = assets;
        this.photos = photos;
        this.files = files;
        this.organizations = organizations;
        this.users = users;
        this.storage = storage;
        this.storageProps = storageProps;
        this.fileUrls = fileUrls;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssetPhotoView> list(String orgId, String assetId) {
        requireAsset(orgId, assetId);
        return photos.findByAssetIdOrderByPrimaryDescSortOrderAscCreatedAtAsc(assetId).stream()
                .map(p -> AssetPhotoView.of(p, fileUrls::signed))
                .toList();
    }

    @Transactional
    public AssetPhotoView upload(
            String orgId, String userId, String assetId,
            MultipartFile file, PhotoKind kind, String caption) {

        Asset asset = requireAsset(orgId, assetId);

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nenhum ficheiro foi enviado.");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED.contains(contentType)) {
            throw ApiException.badRequest("Formato não suportado. Use JPG, PNG, WEBP, GIF ou PDF.");
        }
        if (file.getSize() > storageProps.maxFileBytesOrDefault()) {
            throw ApiException.badRequest("O ficheiro é demasiado grande (máx. "
                    + (storageProps.maxFileBytesOrDefault() / (1024 * 1024)) + " MB).");
        }
        if (photos.countByAssetId(assetId) >= MAX_PHOTOS) {
            throw ApiException.conflict("Este ativo já tem o número máximo de fotografias (" + MAX_PHOTOS + ").");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro enviado.");
        }

        int[] dims = dimensions(bytes, contentType);
        String key = storage.store(bytes, contentType, file.getOriginalFilename());

        StoredFile stored = new StoredFile();
        stored.setOrganization(organizations.getReferenceById(orgId));
        stored.setStorageKey(key);
        stored.setOriginalName(trim(file.getOriginalFilename(), 300));
        stored.setContentType(contentType);
        stored.setSizeBytes(bytes.length);
        stored.setWidth(dims[0] > 0 ? dims[0] : null);
        stored.setHeight(dims[1] > 0 ? dims[1] : null);
        stored.setUploadedBy(users.getReferenceById(userId));
        files.save(stored);

        boolean first = photos.countByAssetId(assetId) == 0;
        AssetPhoto photo = new AssetPhoto();
        photo.setAsset(asset);
        photo.setFile(stored);
        photo.setKind(kind != null ? kind : PhotoKind.GENERAL);
        photo.setCaption(trim(caption, 300));
        photo.setPrimary(first);
        photo.setSortOrder((int) photos.countByAssetId(assetId));
        photos.save(photo);

        audit.record(orgId, userId, "asset.photo.upload", "Asset", assetId,
                asset.getTag() + " · " + photo.getKind());
        return AssetPhotoView.of(photo, fileUrls::signed);
    }

    @Transactional
    public AssetPhotoView update(
            String orgId, String userId, String assetId, String photoId, UpdatePhotoRequest req) {
        requireAsset(orgId, assetId);
        AssetPhoto photo = photos.findByIdAndAssetId(photoId, assetId)
                .orElseThrow(() -> ApiException.notFound("Fotografia não encontrada."));

        if (req.kind() != null) photo.setKind(req.kind());
        if (req.caption() != null) photo.setCaption(trim(req.caption(), 300));
        if (req.sortOrder() != null) photo.setSortOrder(req.sortOrder());
        if (Boolean.TRUE.equals(req.primary())) {
            photos.findByAssetIdAndPrimaryTrue(assetId).forEach(p -> p.setPrimary(false));
            photo.setPrimary(true);
        } else if (Boolean.FALSE.equals(req.primary())) {
            photo.setPrimary(false);
        }
        audit.record(orgId, userId, "asset.photo.update", "Asset", assetId, photoId);
        return AssetPhotoView.of(photo, fileUrls::signed);
    }

    @Transactional
    public void delete(String orgId, String userId, String assetId, String photoId) {
        requireAsset(orgId, assetId);
        AssetPhoto photo = photos.findByIdAndAssetId(photoId, assetId)
                .orElseThrow(() -> ApiException.notFound("Fotografia não encontrada."));
        boolean wasPrimary = photo.isPrimary();
        StoredFile file = photo.getFile();

        photos.delete(photo);
        photos.flush();
        try {
            storage.delete(file.getStorageKey());
        } catch (Exception e) {
            log.warn("Falha ao remover ficheiro físico {}: {}", file.getStorageKey(), e.toString());
        }
        files.delete(file);

        if (wasPrimary) {
            photos.findByAssetIdOrderByPrimaryDescSortOrderAscCreatedAtAsc(assetId).stream()
                    .findFirst().ifPresent(p -> p.setPrimary(true));
        }
        audit.record(orgId, userId, "asset.photo.delete", "Asset", assetId, photoId);
    }

    /** URL assinado da foto principal de um ativo (para listas / cartões). */
    @Transactional(readOnly = true)
    public String primaryPhotoUrl(String assetId) {
        return photos.findByAssetIdAndPrimaryTrue(assetId).stream()
                .findFirst()
                .map(p -> fileUrls.signed(p.getFile().getId()))
                .orElse(null);
    }

    // ------------------------------------------------------------------
    private Asset requireAsset(String orgId, String assetId) {
        return assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
    }

    private static int[] dimensions(byte[] bytes, String contentType) {
        if (!contentType.startsWith("image/")) return new int[] {0, 0};
        try {
            var img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) return new int[] {img.getWidth(), img.getHeight()};
        } catch (Exception ignored) {
            // dimensões são opcionais
        }
        return new int[] {0, 0};
    }

    private static String normalizeContentType(String declared, String name) {
        if (declared != null && ALLOWED.contains(declared)) return declared;
        if (name != null) {
            String n = name.toLowerCase();
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".webp")) return "image/webp";
            if (n.endsWith(".gif")) return "image/gif";
            if (n.endsWith(".pdf")) return "application/pdf";
        }
        return declared != null ? declared : "application/octet-stream";
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }
}
