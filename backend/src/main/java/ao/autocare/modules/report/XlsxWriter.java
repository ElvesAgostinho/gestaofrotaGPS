package ao.autocare.modules.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escreve um ficheiro Excel (.xlsx) sem biblioteca nenhuma.
 *
 * <p>Um .xlsx é um zip com meia dúzia de XML; para uma tabela simples — uma
 * folha, cabeçalho a negrito, números como números e datas como datas — não
 * é preciso mais do que isto. Poupa 20 MB de dependências e uma superfície
 * de vulnerabilidades inteira. Textos vão como {@code inlineStr}, que o Excel,
 * o LibreOffice e o Google Sheets leem sem se queixar.
 */
public final class XlsxWriter {

    private XlsxWriter() {}

    /** Dia 0 do Excel (30-12-1899): as datas são dias decorridos desde aí. */
    private static final LocalDateTime EPOCA_EXCEL = LocalDateTime.of(1899, 12, 30, 0, 0);

    public static byte[] write(CsvWriter tabela) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                entrada(zip, "[Content_Types].xml", CONTENT_TYPES);
                entrada(zip, "_rels/.rels", RELS);
                entrada(zip, "xl/workbook.xml", WORKBOOK);
                entrada(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
                entrada(zip, "xl/styles.xml", STYLES);
                entrada(zip, "xl/worksheets/sheet1.xml", folha(tabela));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar o ficheiro Excel.", e);
        }
    }

    private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String folha(CsvWriter t) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        // Larguras razoáveis: o Excel não ajusta sozinho e uma coluna de 8 caracteres esconde tudo.
        sb.append("<cols>");
        for (int c = 0; c < t.headers().length; c++) {
            int largura = Math.min(60, Math.max(12, larguraDa(t, c)));
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
              .append("\" width=\"").append(largura).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        int linha = 1;
        sb.append("<row r=\"").append(linha).append("\">");
        for (int c = 0; c < t.headers().length; c++) {
            texto(sb, ref(c, linha), t.headers()[c], 1);
        }
        sb.append("</row>");
        for (Object[] r : t.rows()) {
            linha++;
            sb.append("<row r=\"").append(linha).append("\">");
            for (int c = 0; c < r.length; c++) {
                celula(sb, ref(c, linha), r[c]);
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        // Filtro automático no cabeçalho: é o primeiro gesto de quem abre um relatório.
        sb.append("<autoFilter ref=\"A1:").append(coluna(t.headers().length - 1)).append(linha).append("\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static void celula(StringBuilder sb, String ref, Object v) {
        if (v == null) {
            return;
        }
        if (v instanceof BigDecimal n) {
            sb.append("<c r=\"").append(ref).append("\" s=\"2\"><v>").append(n.toPlainString()).append("</v></c>");
        } else if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            sb.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
        } else if (v instanceof Double || v instanceof Float) {
            sb.append("<c r=\"").append(ref).append("\" s=\"2\"><v>").append(v).append("</v></c>");
        } else if (v instanceof Instant i) {
            LocalDateTime local = LocalDateTime.ofInstant(i, CsvWriter.ZONE);
            double serial = ChronoUnit.SECONDS.between(EPOCA_EXCEL, local) / 86400.0;
            sb.append("<c r=\"").append(ref).append("\" s=\"3\"><v>").append(serial).append("</v></c>");
        } else if (v instanceof Boolean b) {
            texto(sb, ref, b ? "Sim" : "Não", 0);
        } else {
            texto(sb, ref, CsvWriter.format(v), 0);
        }
    }

    private static void texto(StringBuilder sb, String ref, String s, int estilo) {
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
        if (estilo > 0) {
            sb.append(" s=\"").append(estilo).append('"');
        }
        sb.append("><is><t xml:space=\"preserve\">").append(escapar(s)).append("</t></is></c>");
    }

    private static int larguraDa(CsvWriter t, int c) {
        int max = t.headers()[c].length();
        int vistas = 0;
        for (Object[] r : t.rows()) {
            if (c < r.length && r[c] != null) {
                max = Math.max(max, CsvWriter.format(r[c]).length());
            }
            if (++vistas > 200) {
                break;
            }
        }
        return max + 2;
    }

    private static String ref(int col, int row) {
        return coluna(col) + row;
    }

    private static String coluna(int col) {
        StringBuilder s = new StringBuilder();
        int c = col;
        do {
            s.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        } while (c >= 0);
        return s.toString();
    }

    private static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    // Caracteres de controlo não são XML válido; o Excel recusa o ficheiro inteiro.
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>""";

    private static final String RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>""";

    private static final String WORKBOOK = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Relatório" sheetId="1" r:id="rId1"/></sheets>
            </workbook>""";

    private static final String WORKBOOK_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>""";

    /** Estilos: 0 normal, 1 cabeçalho a negrito, 2 número com duas casas, 3 data-hora. */
    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="2">
                <numFmt numFmtId="164" formatCode="#,##0.00"/>
                <numFmt numFmtId="165" formatCode="yyyy-mm-dd hh:mm"/>
              </numFmts>
              <fonts count="2"><font><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><name val="Calibri"/></font></fonts>
              <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF4F4F5"/></patternFill></fill></fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellXfs count="4">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" applyFont="1" applyFill="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
                <xf numFmtId="165" fontId="0" fillId="0" borderId="0" applyNumberFormat="1"/>
              </cellXfs>
            </styleSheet>""";
}
