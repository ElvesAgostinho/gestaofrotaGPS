package ao.autocare.modules.org;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Transforma um template num PDF, com o timbre e o rodapé já postos.
 *
 * <p>Cada impresso tinha o seu bloco de {@code PdfRendererBuilder}; três
 * cópias do mesmo código eram três sítios para um dia divergirem — um com
 * modo rápido, outro sem, um a falhar de uma forma e outro de outra.
 */
@Component
public class PdfRenderer {

    public static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Luanda"));
    public static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Africa/Luanda"));

    private final TemplateEngine templateEngine;

    public PdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param template nome do template em {@code templates/}
     * @param timbre o cabeçalho da empresa
     * @param variavel nome da variável do modelo no template
     * @param modelo o modelo, já formatado
     */
    public byte[] render(String template, Letterhead.Timbre timbre, String variavel, Object modelo) {
        Context ctx = new Context(Locale.forLanguageTag("pt"));
        ctx.setVariable("timbre", timbre);
        ctx.setVariable(variavel, modelo);
        ctx.setVariable("geradoEm", DATA_HORA.format(Instant.now()));
        String html = templateEngine.process(template, ctx);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o PDF «" + template + "»", e);
        }
    }

    public static String texto(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static String data(Instant i) {
        return i == null ? null : DATA.format(i);
    }

    public static String dataHora(Instant i) {
        return i == null ? null : DATA_HORA.format(i);
    }

    public static String numero(java.math.BigDecimal v, int casas) {
        if (v == null) {
            return null;
        }
        java.text.NumberFormat f = java.text.NumberFormat.getInstance(Locale.forLanguageTag("pt-PT"));
        f.setMinimumFractionDigits(casas);
        f.setMaximumFractionDigits(casas);
        return f.format(v);
    }
}
