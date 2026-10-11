package ao.autocare.modules.part;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A norma escondida no nome de um artigo.
 *
 * <p>«Óleo do motor 15W-40» não é «óleo do motor»: quem compra precisa da
 * viscosidade e da especificação API, e quem recebe a encomenda precisa de
 * conferir. Em quase todas as frotas a norma está escrita no nome — porque é
 * onde cabe — e fica inútil ali, misturada com o resto do texto.
 *
 * <p>Esta classe tira-a de lá. Serve o catálogo, que escreve os nomes, e serve
 * o armazém, que recebe artigos criados à mão e importados de folhas de Excel:
 * em todos os casos a norma acaba no campo próprio, sem ninguém a repetir.
 */
public final class Normas {

    private Normas() {}

    private static final Pattern NORMA = Pattern.compile(
            "(SAE ?)?[0-9]{1,2}W-?[0-9]{2,3}"
                    + "|SAE ?[0-9]{2,3}"
                    + "|API ?[A-Z]{2}-?[0-9]?"
                    + "|DOT ?[0-9]"
                    + "|ATF ?[A-Z0-9-]+"
                    + "|EP ?[0-9]"
                    + "|ISO ?VG ?[0-9]{2,3}",
            Pattern.CASE_INSENSITIVE);

    /** A norma encontrada no texto, ou {@code null} se não houver nenhuma. */
    public static String extrair(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = NORMA.matcher(texto);
        return m.find() ? m.group().trim() : null;
    }
}
