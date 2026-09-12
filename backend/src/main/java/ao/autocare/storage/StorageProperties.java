package ao.autocare.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do armazenamento de ficheiros.
 * {@code autocare.storage.local-path} — diretório em disco (dev).
 * {@code autocare.storage.public-base-url} — prefixo dos URLs devolvidos.
 * {@code autocare.storage.url-ttl-seconds} — validade dos URLs assinados.
 */
@ConfigurationProperties(prefix = "autocare.storage")
public record StorageProperties(
        String localPath,
        String publicBaseUrl,
        Long urlTtlSeconds,
        Long maxFileBytes) {

    public String localPathOrDefault() {
        return localPath != null && !localPath.isBlank() ? localPath : "./data/files";
    }

    public long urlTtlSecondsOrDefault() {
        return urlTtlSeconds != null && urlTtlSeconds > 0 ? urlTtlSeconds : 3600;
    }

    public long maxFileBytesOrDefault() {
        return maxFileBytes != null && maxFileBytes > 0 ? maxFileBytes : 15L * 1024 * 1024;
    }

    public String publicBaseUrlOrDefault() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank() ? publicBaseUrl : "";
    }
}
