package ao.autocare.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Armazenamento em disco local (desenvolvimento). */
@Component
public class LocalStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;

    public LocalStorageProvider(StorageProperties props) {
        this.root = Path.of(props.localPathOrDefault()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Armazenamento local em {}", root);
        } catch (IOException e) {
            throw new StorageException("Não foi possível criar o diretório de armazenamento.", e);
        }
    }

    @Override
    public String store(byte[] content, String contentType, String suggestedName) {
        String ext = extensionFor(contentType, suggestedName);
        String key = YearMonth.now().format(MONTH) + "/" + UUID.randomUUID() + ext;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            throw new StorageException("Não foi possível guardar o ficheiro.", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new StorageException("Ficheiro não encontrado no armazenamento.", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("Não foi possível remover o ficheiro {}: {}", storageKey, e.toString());
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new StorageException("Chave de armazenamento inválida: " + key, null);
        }
        return p;
    }

    private static String extensionFor(String contentType, String name) {
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.[a-z0-9]{1,5}")) return ext;
        }
        if (contentType == null) return "";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
