package ao.autocare.modules.importer.dto;

import java.util.List;

/** Relatório de uma importação em massa. */
public final class ImportDtos {

    private ImportDtos() {}

    /** Um problema numa linha concreta, com o número da linha do ficheiro. */
    public record RowError(int line, String value, String message) {}

    /**
     * Resultado de uma importação.
     *
     * <p>{@code dryRun} distingue a verificação da gravação. Numa folha de 300
     * máquinas, deixar alguém ver o que vai acontecer antes de acontecer não é
     * um luxo: é a diferença entre corrigir três linhas e ter de limpar a
     * base de dados à mão.
     *
     * @param created   linhas que criaram um registo novo
     * @param updated   linhas que atualizaram um registo existente
     * @param skipped   linhas ignoradas por erro (ver {@code errors})
     * @param createdReferences nomes de tipos de ativo e locais criados pelo
     *                  caminho, para quem importa poder confirmar que não foram
     *                  gralhas a criar entradas novas
     */
    public record ImportReport(
            String entity,
            boolean dryRun,
            int totalRows,
            int created,
            int updated,
            int skipped,
            List<String> createdReferences,
            List<RowError> errors) {

        public boolean isClean() {
            return errors.isEmpty();
        }
    }
}
