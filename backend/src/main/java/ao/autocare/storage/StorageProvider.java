package ao.autocare.storage;

import java.io.InputStream;

/**
 * Abstração de armazenamento de ficheiros (regra #78: trocável sem tocar no resto).
 * Implementação local em desenvolvimento; object storage em produção.
 */
public interface StorageProvider {

    /** Guarda os bytes e devolve a chave de armazenamento. */
    String store(byte[] content, String contentType, String suggestedName);

    /** Lê os bytes de um ficheiro previamente guardado. */
    byte[] load(String storageKey);

    /** Remove um ficheiro. Não falha se já não existir. */
    void delete(String storageKey);

    default String store(InputStream in, long size, String contentType, String suggestedName) {
        try {
            return store(in.readAllBytes(), contentType, suggestedName);
        } catch (Exception e) {
            throw new StorageException("Não foi possível ler o ficheiro enviado.", e);
        }
    }

    class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
