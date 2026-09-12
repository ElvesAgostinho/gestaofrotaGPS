package ao.autocare.modules.asset.dto;

import ao.autocare.domain.AssetPhoto;
import ao.autocare.domain.StoredFile;
import ao.autocare.domain.enums.Enums.PhotoKind;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.function.Function;

public final class AssetPhotoDtos {

    private AssetPhotoDtos() {}

    public record UpdatePhotoRequest(
            PhotoKind kind,
            @Size(max = 300) String caption,
            Boolean primary,
            Integer sortOrder) {}

    public record AssetPhotoView(
            String id,
            String url,
            String kind,
            /** Rótulo da parte fotografada, em português, vindo do servidor. */
            String kindLabel,
            String caption,
            boolean primary,
            int sortOrder,
            String contentType,
            long sizeBytes,
            Integer width,
            Integer height,
            String originalName,
            Instant createdAt) {

        /** {@code signUrl} recebe o id do ficheiro e devolve o URL assinado completo. */
        public static AssetPhotoView of(AssetPhoto p, Function<String, String> signUrl) {
            StoredFile f = p.getFile();
            return new AssetPhotoView(
                    p.getId(),
                    signUrl.apply(f.getId()),
                    p.getKind().name(),
                    p.getKind().label(),
                    p.getCaption(),
                    p.isPrimary(),
                    p.getSortOrder(),
                    f.getContentType(),
                    f.getSizeBytes(),
                    f.getWidth(),
                    f.getHeight(),
                    f.getOriginalName(),
                    p.getCreatedAt());
        }
    }
}
