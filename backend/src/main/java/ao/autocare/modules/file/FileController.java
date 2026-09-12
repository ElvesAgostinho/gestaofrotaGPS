package ao.autocare.modules.file;

import ao.autocare.common.ApiException;
import ao.autocare.domain.StoredFile;
import ao.autocare.repo.StoredFileRepository;
import ao.autocare.storage.FileUrlSigner;
import ao.autocare.storage.StorageProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serve os bytes de um ficheiro. Autenticação por URL assinado ({@code ?sig=...}),
 * para funcionar em {@code <img src>} sem cabeçalhos.
 */
@Tag(name = "Ficheiros")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final StoredFileRepository files;
    private final StorageProvider storage;
    private final FileUrlSigner signer;

    public FileController(StoredFileRepository files, StorageProvider storage, FileUrlSigner signer) {
        this.files = files;
        this.storage = storage;
        this.signer = signer;
    }

    @Operation(summary = "Descarregar um ficheiro (URL assinado)")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable String id, @RequestParam(required = false) String sig) {

        if (!signer.verify(id, sig)) {
            throw ApiException.forbidden("Ligação ao ficheiro inválida ou expirada.");
        }
        StoredFile file = files.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ficheiro não encontrado."));

        byte[] bytes = storage.load(file.getStorageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate())
                .headers(h -> h.setContentDisposition(ContentDisposition.inline()
                        .filename(file.getOriginalName() != null ? file.getOriginalName() : id)
                        .build()))
                .body(new ByteArrayResource(bytes));
    }
}
