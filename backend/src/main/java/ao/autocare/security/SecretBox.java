package ao.autocare.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra segredos que têm de ser guardados e voltar a ser lidos.
 *
 * <p>É o caso da palavra-passe do Traccar e do servidor de email: ao contrário
 * de uma palavra-passe de utilizador, que só precisa de ser comparada (e por
 * isso leva BCrypt), estas têm de ser apresentadas outra vez ao serviço
 * remoto — e portanto não podem ser um resumo de sentido único.
 *
 * <p>AES-GCM, que cifra e autentica ao mesmo tempo: um valor adulterado na base
 * de dados falha a decifrar em vez de devolver lixo silenciosamente. Cada
 * cifragem leva o seu próprio vetor de iniciação, guardado à frente do texto
 * cifrado — sem isso, dois segredos iguais dariam o mesmo resultado e bastava
 * olhar para a coluna para ver quais as empresas que repetem a palavra-passe.
 *
 * <p>A chave vem de {@code autocare.security.secret-key}. Em produção tem de
 * ser definida; se mudar, os segredos já guardados deixam de ser legíveis e as
 * empresas têm de os voltar a introduzir — o que é o comportamento correto,
 * porque a alternativa seria uma chave fixa no código.
 */
@Component
public class SecretBox {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_ETIQUETA = 128;

    private final SecretKeySpec chave;
    private final SecureRandom aleatorio = new SecureRandom();

    public SecretBox(
            @Value("${autocare.security.secret-key:${autocare.security.jwt.access-secret:}}")
            String segredo) {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException(
                    "Falta autocare.security.secret-key para cifrar credenciais.");
        }
        // A chave AES tem de ter 256 bits exatos; o segredo da configuração tem
        // o comprimento que tiver. SHA-256 normaliza-o sem o enfraquecer.
        this.chave = new SecretKeySpec(sha256(segredo), "AES");
    }

    /** Cifra. Devolve {@code null} para entrada nula ou vazia. */
    public String encrypt(String texto) {
        if (texto == null || texto.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV];
            aleatorio.nextBytes(iv);

            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_ETIQUETA, iv));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));

            byte[] tudo = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, tudo, 0, iv.length);
            System.arraycopy(cifrado, 0, tudo, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(tudo);

        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível cifrar o segredo.", e);
        }
    }

    /**
     * Decifra.
     *
     * <p>Devolve {@code null} quando o valor não se consegue ler — tipicamente
     * porque a chave mudou. Rebentar aqui deixaria a empresa sem conseguir
     * sequer abrir o ecrã de configuração para corrigir.
     */
    public String decrypt(String cifrado) {
        if (cifrado == null || cifrado.isEmpty()) {
            return null;
        }
        try {
            byte[] tudo = Base64.getDecoder().decode(cifrado);
            if (tudo.length <= TAMANHO_IV) {
                return null;
            }
            byte[] iv = new byte[TAMANHO_IV];
            System.arraycopy(tudo, 0, iv, 0, TAMANHO_IV);
            byte[] corpo = new byte[tudo.length - TAMANHO_IV];
            System.arraycopy(tudo, TAMANHO_IV, corpo, 0, corpo.length);

            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_ETIQUETA, iv));
            return new String(cifra.doFinal(corpo), StandardCharsets.UTF_8);

        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] sha256(String v) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(v.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM.", e);
        }
    }
}
