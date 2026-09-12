package ao.autocare.modules.predictive.dto;

import ao.autocare.domain.PredictiveProgram;
import ao.autocare.domain.PredictiveReading;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PredictiveResult;
import ao.autocare.domain.enums.Enums.PredictiveTechnique;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedidos e respostas da manutenção preditiva. */
public final class PredictiveDtos {

    private PredictiveDtos() {}

    public record SaveProgramRequest(
            @NotNull(message = "Escolha a técnica de monitorização.")
            PredictiveTechnique technique,
            /** Periodicidade em meses. Vazio usa a habitual da técnica. */
            @Min(value = 1, message = "A periodicidade tem de ser de pelo menos um mês.")
            @Max(value = 120, message = "A periodicidade não pode passar de dez anos.")
            Integer frequencyMonths,
            @Size(max = 300) String components,
            @Size(max = 300) String goal,
            @Size(max = 120) String responsibleLabel,
            /** Data da última medição conhecida, para a agenda arrancar certa. */
            Instant lastDoneAt,
            Boolean active,
            @Size(max = 2000) String notes) {}

    /**
     * Alterações a um programa existente. A técnica não está aqui de propósito:
     * é a identidade do programa (é única por ativo), e trocá-la faria do
     * histórico de medições um registo de outra coisa. Para mudar de técnica
     * remove-se o programa e cria-se outro.
     */
    public record UpdateProgramRequest(
            @Min(value = 1, message = "A periodicidade tem de ser de pelo menos um mês.")
            @Max(value = 120, message = "A periodicidade não pode passar de dez anos.")
            Integer frequencyMonths,
            @Size(max = 300) String components,
            @Size(max = 300) String goal,
            @Size(max = 120) String responsibleLabel,
            Instant lastDoneAt,
            Boolean active,
            @Size(max = 2000) String notes) {

        /** Reaproveita a aplicação de campos comum à criação. */
        public SaveProgramRequest asSave(
                ao.autocare.domain.enums.Enums.PredictiveTechnique technique) {
            return new SaveProgramRequest(technique, frequencyMonths, components, goal,
                    responsibleLabel, lastDoneAt, active, notes);
        }
    }

    public record ProgramView(
            String id,
            String assetId,
            String assetTag,
            String assetName,
            PredictiveTechnique technique,
            String techniqueLabel,
            int frequencyMonths,
            String frequencyLabel,
            String components,
            String goal,
            String responsibleLabel,
            Instant lastDoneAt,
            Instant nextDueAt,
            Long remainingDays,
            PlanTaskStatus status,
            PredictiveResult lastResult,
            boolean active,
            String notes) {

        public static ProgramView of(
                PredictiveProgram p, Instant now, PredictiveResult lastResult) {
            return new ProgramView(
                    p.getId(), p.getAsset().getId(), p.getAsset().getTag(), p.getAsset().getName(),
                    p.getTechnique(), p.getTechnique().label(),
                    p.getFrequencyMonths(), frequencyLabel(p.getFrequencyMonths()),
                    p.getComponents(), p.getGoal(), p.getResponsibleLabel(),
                    p.getLastDoneAt(), p.getNextDueAt(), p.remainingDays(now),
                    p.statusAt(now), lastResult, p.isActive(), p.getNotes());
        }

        /** "Mensal", "Trimestral"… como no documento de referência. */
        public static String frequencyLabel(int months) {
            return switch (months) {
                case 1 -> "Mensal";
                case 2 -> "Bimestral";
                case 3 -> "Trimestral";
                case 4 -> "Quadrimestral";
                case 6 -> "Semestral";
                case 12 -> "Anual";
                case 24 -> "Bienal";
                default -> "A cada " + months + " meses";
            };
        }
    }

    public record RecordReadingRequest(
            @NotNull(message = "Classifique o resultado da medição.")
            PredictiveResult result,
            Instant performedAt,
            @Size(max = 120) String measurement,
            @Size(max = 8000) String findings,
            @Size(max = 8000) String recommendation,
            @Size(max = 120) String performedByLabel,
            String fileId,
            BigDecimal meterValue,
            /**
             * Abrir uma ordem corretiva a partir deste resultado. Deliberadamente
             * explícito: abrir sozinho seria uma surpresa desagradável.
             */
            Boolean openWorkOrder) {}

    public record ReadingView(
            String id,
            String programId,
            PredictiveTechnique technique,
            String techniqueLabel,
            String assetId,
            String assetTag,
            Instant performedAt,
            PredictiveResult result,
            String measurement,
            String findings,
            String recommendation,
            String performedByLabel,
            String fileUrl,
            String workOrderId,
            BigDecimal meterValue) {

        public static ReadingView of(PredictiveReading r, String fileUrl) {
            return new ReadingView(
                    r.getId(), r.getProgram().getId(),
                    r.getProgram().getTechnique(), r.getProgram().getTechnique().label(),
                    r.getAsset().getId(), r.getAsset().getTag(),
                    r.getPerformedAt(), r.getResult(), r.getMeasurement(),
                    r.getFindings(), r.getRecommendation(), r.getPerformedByLabel(),
                    fileUrl, r.getWorkOrderId(), r.getMeterValue());
        }
    }

    /** Resposta ao registar uma medição: o programa reagendado e o que se abriu. */
    public record ReadingRecorded(
            ReadingView reading, ProgramView program, String workOrderNumber) {}

    /** Técnica disponível, para preencher listas na interface. */
    public record TechniqueView(
            PredictiveTechnique code, String label,
            int defaultFrequencyMonths, String defaultFrequencyLabel) {}
}
