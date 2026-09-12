package ao.autocare.modules.importer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de CSV suficiente para ficheiros de escritório, sem dependências novas.
 *
 * <p>Trata o que aparece mesmo na prática angolana e portuguesa: o Excel em
 * português grava com <b>ponto e vírgula</b>, o resto do mundo com vírgula, e
 * ambos aparecem no mesmo dia. O separador é detetado pela linha de cabeçalho.
 *
 * <p>Suporta aspas (para campos que contenham o separador ou quebras de linha)
 * com o escape por aspas duplicadas, que é o que o Excel produz. Não é uma
 * implementação completa do RFC 4180 — é o subconjunto que os ficheiros reais
 * usam, com os casos que dão erro silencioso cobertos por testes.
 */
public final class Csv {

    private static final char[] CANDIDATE_SEPARATORS = {';', ',', '\t'};

    private Csv() {}

    /** Uma linha já associada ao cabeçalho, com o número original para o relatório. */
    public record Row(int lineNumber, Map<String, String> values) {

        /** Valor de uma coluna, ou {@code null} se estiver em branco ou ausente. */
        public String get(String column) {
            String value = values.get(normalizeHeader(column));
            return value == null || value.isBlank() ? null : value.trim();
        }

        /**
         * Primeiro valor encontrado entre vários nomes possíveis para a mesma
         * coluna. A normalização resolve acentos e maiúsculas, mas não
         * sinónimos: "Nº Série", "numero_serie" e "serial" são grafias
         * diferentes da mesma coisa, e quem preenche a folha não tem de saber
         * qual delas o sistema espera.
         */
        public String getAny(String... columns) {
            for (String column : columns) {
                String value = get(column);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        public boolean isEmpty() {
            return values.values().stream().allMatch(v -> v == null || v.isBlank());
        }
    }

    public record Sheet(List<String> headers, List<Row> rows) {}

    /**
     * Lê o conteúdo completo. A primeira linha não vazia é o cabeçalho.
     *
     * @throws IllegalArgumentException se não houver cabeçalho
     */
    public static Sheet parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("O ficheiro está vazio.");
        }
        // O Excel grava UTF-8 com BOM; sem isto a primeira coluna nunca coincide.
        if (content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        List<List<String>> lines = splitRecords(content, detectSeparator(content));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("O ficheiro está vazio.");
        }

        List<String> rawHeaders = lines.get(0);
        List<String> headers = new ArrayList<>(rawHeaders.size());
        for (String header : rawHeaders) {
            headers.add(normalizeHeader(header));
        }

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                values.put(headers.get(c), c < cells.size() ? cells.get(c) : null);
            }
            Row row = new Row(i + 1, values); // +1 porque as linhas contam-se a partir de 1
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return new Sheet(headers, rows);
    }

    /**
     * Separador mais provável: o que aparece mais vezes na linha de cabeçalho.
     * Contar só o cabeçalho evita ser enganado por vírgulas dentro dos dados.
     */
    static char detectSeparator(String content) {
        String firstLine = content.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char best = ';';
        int bestCount = 0;
        for (char candidate : CANDIDATE_SEPARATORS) {
            int count = 0;
            boolean inQuotes = false;
            for (int i = 0; i < firstLine.length(); i++) {
                char ch = firstLine.charAt(i);
                if (ch == '"') {
                    inQuotes = !inQuotes;
                } else if (ch == candidate && !inQuotes) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }

    /** Divide em registos, respeitando aspas (que podem conter quebras de linha). */
    private static List<List<String>> splitRecords(String content, char separator) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (inQuotes) {
                if (ch == '"') {
                    // Aspas duplicadas dentro de aspas são umas aspas literais.
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
            } else if (ch == separator) {
                current.add(field.toString().trim());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                // \r\n conta como uma só quebra.
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString().trim());
                field.setLength(0);
                if (current.stream().anyMatch(v -> !v.isBlank())) {
                    records.add(current);
                }
                current = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        current.add(field.toString().trim());
        if (current.stream().anyMatch(v -> !v.isBlank())) {
            records.add(current);
        }
        return records;
    }

    /**
     * Normaliza um nome de coluna para comparação: minúsculas, sem acentos e sem
     * espaços. Assim "Nº Série", "numero_serie" e "Numero Serie" são a mesma
     * coluna — quem preenche a folha não tem de adivinhar a grafia exata.
     */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(header.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase()
                .replace('º', 'o')
                .replace('ª', 'a')
                .replaceAll("[^a-z0-9]", "");
    }
}
