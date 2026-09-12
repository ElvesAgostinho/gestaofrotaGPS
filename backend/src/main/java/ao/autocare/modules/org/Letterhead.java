package ao.autocare.modules.org;

import ao.autocare.domain.Organization;
import ao.autocare.storage.StorageProvider;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * O cabeçalho dos impressos: a empresa do cliente, não a marca do software.
 *
 * <p>Todos os PDFs passam por aqui para o timbre ser o mesmo em todos — o nome
 * escrito da mesma forma, o NIF no mesmo sítio, o logótipo do mesmo tamanho.
 * O logótipo vai embutido como data URI: o gerador de PDF não faz pedidos à
 * rede, e não devia — o documento tem de sair igual sem Internet.
 */
@Component
public class Letterhead {

    private static final Logger log = LoggerFactory.getLogger(Letterhead.class);

    /** O que o template recebe. Tudo já em texto; o template não faz contas. */
    public record Timbre(
            String name, String taxId, String address, String phone, String email,
            /** {@code data:image/png;base64,...} ou nulo. */
            String logoDataUri) {

        public boolean hasLogo() {
            return logoDataUri != null;
        }

        /** Morada e contactos numa linha, sem os que faltam. */
        public String contactLine() {
            StringBuilder b = new StringBuilder();
            for (String v : new String[] {address, phone, email}) {
                if (v != null && !v.isBlank()) {
                    if (b.length() > 0) {
                        b.append("  ·  ");
                    }
                    b.append(v.trim());
                }
            }
            return b.toString();
        }
    }

    private final StorageProvider storage;

    public Letterhead(StorageProvider storage) {
        this.storage = storage;
    }

    public Timbre of(Organization org) {
        String morada = org.getAddress();
        if (org.getCity() != null && !org.getCity().isBlank()) {
            morada = morada == null || morada.isBlank() ? org.getCity() : morada + ", " + org.getCity();
        }
        return new Timbre(org.getName(), org.getTaxId(), morada, org.getPhone(), org.getEmail(),
                logo(org));
    }

    private String logo(Organization org) {
        if (org.getLogoKey() == null) {
            return null;
        }
        try {
            byte[] bytes = storage.load(org.getLogoKey());
            String tipo = org.getLogoContentType() != null ? org.getLogoContentType() : "image/png";
            return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (RuntimeException e) {
            // Um logótipo que se perdeu no disco não pode impedir a impressão de
            // uma ordem: o documento sai sem ele, e diz-se no log.
            log.warn("Logótipo da empresa {} não carregou: {}", org.getId(), e.toString());
            return null;
        }
    }
}
