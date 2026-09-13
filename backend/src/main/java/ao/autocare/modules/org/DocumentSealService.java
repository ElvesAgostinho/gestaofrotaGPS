package ao.autocare.modules.org;

import ao.autocare.domain.DocumentSeal;
import ao.autocare.repo.DocumentSealRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selo de autenticidade: cada PDF emitido leva um código no rodapé e fica
 * registado com o resumo SHA-256 do ficheiro.
 *
 * <p>Não é uma assinatura digital qualificada — isso exige certificados e
 * uma entidade certificadora. É o que uma empresa consegue garantir por si:
 * «este código foi emitido por nós, neste dia, por esta pessoa, e o ficheiro
 * que tem na mão é exatamente o que saiu daqui». Quem recebe o papel
 * verifica-o em {@code /verificar/CÓDIGO}, sem conta.
 */
@Service
public class DocumentSealService {

    /** Sem 0/O/1/I: o código vai ser lido em voz alta ao telefone. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final DocumentSealRepository seals;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();
    private final String webUrl;

    public DocumentSealService(DocumentSealRepository seals, OrganizationRepository organizations,
            UserRepository users, @Value("${autocare.app.web-url:}") String webUrl) {
        this.seals = seals;
        this.organizations = organizations;
        this.users = users;
        this.webUrl = webUrl == null ? "" : webUrl.replaceAll("/+$", "");
    }

    /** O que o rodapé imprime. */
    public record Selo(String code, String verifyUrl) {}

    /**
     * Emite um documento selado: gera o código, entrega-o a quem desenha o
     * PDF (para o pôr no rodapé) e guarda o resumo do resultado.
     */
    @Transactional
    public byte[] emitir(String orgId, String userId, String kind, String referenceId, String reference,
            Function<Selo, byte[]> renderer) {
        String code = novoCodigo();
        Selo selo = new Selo(code, webUrl + "/verificar/" + code);
        byte[] pdf = renderer.apply(selo);

        DocumentSeal s = new DocumentSeal();
        s.setOrganization(organizations.getReferenceById(orgId));
        s.setCode(code);
        s.setKind(kind);
        s.setReferenceId(referenceId);
        s.setReference(reference);
        if (userId != null) {
            s.setIssuedBy(users.getReferenceById(userId));
        }
        s.setIssuedAt(Instant.now());
        s.setSha256(sha256(pdf));
        s.setSizeBytes(pdf.length);
        seals.save(s);
        return pdf;
    }

    public record Verificacao(boolean found, String code, String organization, String kind, String kindLabel,
                              String reference, Instant issuedAt, String issuedBy, long sizeBytes,
                              /** Só quando se carregou um ficheiro: bate com o emitido? */
                              Boolean fileMatches) {}

    @Transactional(readOnly = true)
    public Verificacao verificar(String code, byte[] ficheiro) {
        String normalizado = code == null ? "" : code.trim().toUpperCase().replace("-", "");
        Optional<DocumentSeal> s = seals.findByCode(formatar(normalizado));
        if (s.isEmpty()) {
            return new Verificacao(false, code, null, null, null, null, null, null, 0, null);
        }
        DocumentSeal d = s.get();
        Boolean bate = ficheiro != null ? sha256(ficheiro).equals(d.getSha256()) : null;
        return new Verificacao(true, d.getCode(), d.getOrganization().getName(), d.getKind(), label(d.getKind()),
                d.getReference(), d.getIssuedAt(), d.getIssuedBy() != null ? d.getIssuedBy().getName() : null,
                d.getSizeBytes(), bate);
    }

    private String novoCodigo() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
            }
            String code = formatar(sb.toString());
            if (!seals.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código único.");
    }

    /** XXXX-XXXX-XXXX. */
    private static String formatar(String s) {
        if (s.length() != 12) {
            return s;
        }
        return s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String label(String kind) {
        return switch (kind) {
            case "WORK_ORDER" -> "Ordem de manutenção";
            case "TRANSPORT_NOTE" -> "Guia de transporte";
            case "ASSET_HISTORY" -> "Histórico de manutenção";
            case "ASSET_SHEET" -> "Ficha de equipamento";
            default -> kind;
        };
    }

    /** Para o teste: o resumo em texto de um conteúdo. */
    static String sha256(String s) {
        return sha256(s.getBytes(StandardCharsets.UTF_8));
    }
}
