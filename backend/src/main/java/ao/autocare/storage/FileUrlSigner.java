package ao.autocare.storage;

import ao.autocare.config.AutoCareProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Assina URLs de ficheiros para poderem ser usados em {@code <img src>} sem
 * cabeçalho de autenticação. O token expira ({@code autocare.storage.url-ttl-seconds}).
 */
@Component
public class FileUrlSigner {

    private final byte[] key;
    private final long ttlSeconds;

    public FileUrlSigner(AutoCareProperties props, StorageProperties storage) {
        // Reutiliza o segredo de acesso JWT como material de chave (já é secreto e longo).
        this.key = props.security().jwt().accessSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = storage.urlTtlSecondsOrDefault();
    }

    /** Devolve o parâmetro de query {@code sig} para um ficheiro (inclui a expiração). */
    public String sign(String fileId) {
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payload = fileId + ":" + exp;
        return exp + "." + hmac(payload);
    }

    public boolean verify(String fileId, String sig) {
        if (sig == null || !sig.contains(".")) return false;
        int dot = sig.indexOf('.');
        String expPart = sig.substring(0, dot);
        String macPart = sig.substring(dot + 1);
        long exp;
        try {
            exp = Long.parseLong(expPart);
        } catch (NumberFormatException e) {
            return false;
        }
        if (exp < System.currentTimeMillis() / 1000) return false;
        String expected = hmac(fileId + ":" + exp);
        return constantTimeEquals(expected, macPart);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
