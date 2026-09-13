package ao.autocare.modules.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma tabela de relatório: cabeçalhos e linhas com os valores ainda tipados.
 *
 * <p>Chama-se {@code CsvWriter} por história; hoje é a tabela de onde saem os
 * três formatos — CSV ({@link #build()}), Excel ({@link XlsxWriter}) e PDF
 * ({@link TablePdf}). Guardar os valores tipados (e não já formatados) é o que
 * permite ao Excel receber números como números e datas como datas.
 */
public final class CsvWriter {

    /** Sem isto o Excel abre o ficheiro com os acentos trocados. */
    public static final String BOM = "﻿";

    static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Fuso de Angola (WAT, UTC+1, sem horário de verão). */
    static final ZoneId ZONE = ZoneId.of("Africa/Luanda");

    private final String[] headers;
    private final List<Object[]> rows = new ArrayList<>();
    private String title;

    public CsvWriter(String... headers) {
        this.headers = headers;
    }

    public CsvWriter row(Object... cells) {
        rows.add(cells);
        return this;
    }

    /** Título do documento (só o PDF e a folha Excel o usam). */
    public CsvWriter titulo(String title) {
        this.title = title;
        return this;
    }

    public String[] headers() {
        return headers;
    }

    public List<Object[]> rows() {
        return rows;
    }

    public String title() {
        return title;
    }

    /** O CSV: ponto e vírgula, decimais com vírgula, BOM — como o Excel em português espera. */
    public String build() {
        StringBuilder out = new StringBuilder(BOM);
        linha(out, headers);
        for (Object[] r : rows) {
            linha(out, r);
        }
        return out.toString();
    }

    private static void linha(StringBuilder out, Object[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(escape(format(cells[i])));
        }
        out.append('\n');
    }

    /** Datas no fuso local e decimais com vírgula — é assim que se lê aqui. */
    static String format(Object cell) {
        if (cell == null) {
            return "";
        }
        if (cell instanceof Instant instant) {
            return DATE_TIME.format(instant.atZone(ZONE));
        }
        if (cell instanceof BigDecimal number) {
            return number.toPlainString().replace('.', ',');
        }
        if (cell instanceof Double || cell instanceof Float) {
            return cell.toString().replace('.', ',');
        }
        if (cell instanceof List<?> list) {
            return String.join(", ", list.stream().map(CsvWriter::format).toList());
        }
        return cell.toString();
    }

    /** Aspas à volta do que contenha separador, aspas ou quebra de linha. */
    private static String escape(String value) {
        if (value.indexOf(';') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
