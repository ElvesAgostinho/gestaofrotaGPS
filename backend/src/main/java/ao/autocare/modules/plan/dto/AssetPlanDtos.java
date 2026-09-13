package ao.autocare.modules.plan.dto;

import ao.autocare.domain.AssetPlan;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.PlanTaskCompletion;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AssetPlanDtos {

    private AssetPlanDtos() {}

    public record AssignPlanRequest(
            @NotBlank(message = "Indique o plano a atribuir.") String planId,
            /** Se verdadeiro, considera as tarefas como feitas agora (relógio começa hoje). */
            Boolean startFromNow,
            /** Quando foi a última revisão (se não for «agora»). */
            Instant lastDoneAt,
            /** Leitura do contador na última revisão (km ou horas, conforme o contador principal). */
            BigDecimal lastDoneMeter) {}

    /**
     * O limite de manutenção de um ativo, dito da forma mais simples: «revisão a
     * cada 5 000 km» ou «a cada 250 h», com ou sem prazo em dias. Por trás cria um
     * plano de uma tarefa e atribui-o ao ativo — o motor é o mesmo dos planos
     * completos, e é ele que faz a tarefa vencer quando o GPS chega lá.
     */
    public record IntervalRequest(
            @Size(max = 200) String title,
            /** A cada N km (ativos com odómetro). */
            BigDecimal everyKm,
            /** A cada N horas (ativos com horímetro). */
            BigDecimal everyHours,
            /** A cada N dias de calendário, independentemente do contador. */
            Integer everyDays,
            /** Leitura do contador na última revisão; vazio = a leitura atual. */
            BigDecimal lastDoneMeter,
            /** Data da última revisão; vazio = agora. */
            Instant lastDoneAt,
            @Size(max = 2000) String notes) {}

    public record CompleteTaskRequest(
            Instant completedAt,
            BigDecimal meterValue,
            @Size(max = 120) String performedByLabel,
            @Size(max = 1000) String notes) {}

    public record TaskView(
            String id,
            String title,
            String systemName,
            String status,
            Instant lastDoneAt,
            BigDecimal lastDoneMeter,
            Instant nextDueAt,
            BigDecimal nextDueMeter,
            String nextDueMeterKind,
            BigDecimal remainingMeter,
            Integer remainingDays,
            List<PlanDtos.TriggerView> triggers) {

        public static TaskView of(AssetPlanTask t) {
            return new TaskView(
                    t.getId(), t.getTitle(), t.getSystemName(), t.getStatus().name(),
                    t.getLastDoneAt(), t.getLastDoneMeter(),
                    t.getNextDueAt(), t.getNextDueMeter(),
                    t.getNextDueMeterKind() != null ? t.getNextDueMeterKind().name() : null,
                    t.getRemainingMeter(), t.getRemainingDays(),
                    t.getTask() != null
                            ? t.getTask().getTriggers().stream().map(PlanDtos.TriggerView::of).toList()
                            : List.of());
        }
    }

    public record AssetPlanView(
            String id,
            String planId,
            String planName,
            boolean active,
            Instant assignedAt,
            int taskCount,
            long dueSoon,
            long overdue,
            List<TaskView> tasks) {

        public static AssetPlanView of(AssetPlan ap, List<AssetPlanTask> tasks) {
            long soon = tasks.stream().filter(t -> t.getStatus() == PlanTaskStatus.DUE_SOON).count();
            long over = tasks.stream().filter(t -> t.getStatus() == PlanTaskStatus.OVERDUE).count();
            return new AssetPlanView(
                    ap.getId(), ap.getPlan().getId(), ap.getPlanName(), ap.isActive(),
                    ap.getAssignedAt(), tasks.size(), soon, over,
                    tasks.stream().map(TaskView::of).toList());
        }
    }

    public record CompletionView(
            String id, String title, Instant completedAt, BigDecimal meterValue,
            String performedByLabel, String notes) {

        public static CompletionView of(PlanTaskCompletion c) {
            return new CompletionView(c.getId(), c.getTitle(), c.getCompletedAt(),
                    c.getMeterValue(), c.getPerformedByLabel(), c.getNotes());
        }
    }

    /** Resultado de completar uma tarefa: estado atualizado do plano + o registo criado. */
    public record TaskCompletionResult(AssetPlanView plan, CompletionView completion) {}
}
