package ao.autocare.modules.report;

import ao.autocare.domain.Organization;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Um relatório tabular em PDF, com o timbre da empresa, em paisagem — o que
 * se imprime para levar à reunião ou se guarda na pasta.
 */
@Component
public class TablePdf {

    public record Cell(String text, boolean numeric) {}

    public record Doc(String title, String data, List<String> headers, List<List<Cell>> rows) {}

    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public TablePdf(Letterhead letterhead, PdfRenderer renderer) {
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    public byte[] render(Organization org, CsvWriter tabela, String titulo) {
        List<List<Cell>> linhas = new ArrayList<>();
        for (Object[] r : tabela.rows()) {
            List<Cell> cells = new ArrayList<>(r.length);
            for (Object v : r) {
                boolean numerico = v instanceof Number;
                cells.add(new Cell(CsvWriter.format(v), numerico));
            }
            // Linhas mais curtas do que o cabeçalho: completar, para a tabela não desalinhar.
            while (cells.size() < tabela.headers().length) {
                cells.add(new Cell("", false));
            }
            linhas.add(cells);
        }
        Doc doc = new Doc(titulo != null ? titulo : "Relatório",
                LocalDate.now(CsvWriter.ZONE).toString(),
                List.of(tabela.headers()), linhas);
        return renderer.render("report-table", letterhead.of(org), "r", doc);
    }

}
