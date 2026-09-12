package ao.autocare.modules.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Escrita de CSV para abrir no Excel português.
 *
 * <p>Duas escolhas que decidem se o ficheiro abre bem ou sai todo numa coluna:
 * o separador é <b>ponto e vírgula</b> e os decimais levam <b>vírgula</b>, que é
 * o que o Excel configurado em português espera. Vai também o marcador BOM, sem
 * o qual o Excel lê UTF-8 como ANSI e estraga todos os acentos.
 */
public final class CsvWriter {

    /** Sem isto o Excel abre o ficheiro com os acentos trocados. */
    public static final String BOM = "﻿";

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Fuso de Angola (WAT, UTC+1, sem horário de verão). */
    private static final ZoneId ZONE = ZoneId.of("Africa/Luanda");

    private final StringBuilder out = new StringBuilder(BOM);

    public CsvWriter(String... headers) {
        row((Object[]) headers);
    }

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(escape(format(cells[i])));
        }
        out.append('\n');
        return this;
    }

    public String build() {
        return out.toString();
    }

    /** Datas no fuso local e decimais com vírgula — é assim que se lê aqui. */
    private static String format(Object cell) {
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
