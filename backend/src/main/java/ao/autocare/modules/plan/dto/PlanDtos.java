package ao.autocare.modules.plan.dto;

import ao.autocare.domain.MaintenancePlan;
import ao.autocare.domain.PlanTask;
import ao.autocare.domain.PlanTaskPart;
import ao.autocare.domain.PlanTaskTrigger;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {}

    public record TriggerInput(
            @NotNull PlanTriggerType type,
            MeterKind meterKind,
            @NotNull @Positive BigDecimal interval,
            BigDecimal tolerance) {}

    public record PartInput(
            @NotBlank @Size(max = 200) String name,
            BigDecimal quantity,
            @Size(max = 20) String unit) {}

    public record TaskInput(
            @Size(max = 30) String systemCode,
            @Size(max = 80) String systemName,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 8000) String instructions,
            Integer estimatedMinutes,
            @Size(max = 2000) String tools,
            @NotEmpty(message = "Cada tarefa precisa de pelo menos um gatilho.")
            List<TriggerInput> triggers,
            List<PartInput> parts) {}

    public record SavePlanRequest(
            @NotBlank(message = "Indique o nome do plano.") @Size(max = 160) String name,
            String assetTypeId,
            @Size(max = 8000) String description,
            @Size(max = 8000) String notes,
            /** Objetivo do plano, como no cabeçalho de um documento técnico. */
            @Size(max = 2000) String objective,
            /** Manual do fabricante, norma ou versão de onde o plano saiu. */
            @Size(max = 300) String sourceReference,
            @Size(max = 150) String preparedByLabel,
            @NotEmpty(message = "O plano precisa de pelo menos uma tarefa.")
            List<TaskInput> tasks) {}


    /** Aprovar um plano: quem assina e em nome de quê. */
    public record ApprovePlanRequest(
            @Size(max = 150) String approvedByLabel,
            @Size(max = 300) String sourceReference) {}

    // ---- Vistas ------------------------------------------------------
    public record TriggerView(String type, String meterKind, BigDecimal interval, BigDecimal tolerance) {
        public static TriggerView of(PlanTaskTrigger t) {
            return new TriggerView(t.getTriggerType().name(),
                    t.getMeterKind() != null ? t.getMeterKind().name() : null,
                    t.getIntervalValue(), t.getToleranceValue());
        }
    }

    public record PartView(String name, BigDecimal quantity, String unit) {
        public static PartView of(PlanTaskPart p) {
            return new PartView(p.getPartName(), p.getQuantity(), p.getUnit());
        }
    }

    public record TaskView(
            String id, String systemCode, String systemName, String title,
            String instructions, Integer estimatedMinutes, String tools, int sortOrder,
            List<TriggerView> triggers, List<PartView> parts) {

        public static TaskView of(PlanTask t) {
            return new TaskView(
                    t.getId(), t.getSystemCode(), t.getSystemName(), t.getTitle(),
                    t.getInstructions(), t.getEstimatedMinutes(), t.getTools(), t.getSortOrder(),
                    t.getTriggers().stream().map(TriggerView::of).toList(),
                    t.getParts().stream().map(PartView::of).toList());
        }
    }

    public record PlanView(
            String id, String name, String assetTypeId, String assetTypeName,
            String description, String notes, boolean active,
            /** Cabeçalho do documento: objetivo, origem, quem elaborou, quem aprovou. */
            String objective, String sourceReference,
            String preparedByLabel, Instant preparedAt,
            String approvedByLabel, Instant approvedAt, boolean approved,
            int taskCount, List<TaskView> tasks) {

        public static PlanView of(MaintenancePlan p) {
            return new PlanView(
                    p.getId(), p.getName(),
                    p.getAssetType() != null ? p.getAssetType().getId() : null,
                    p.getAssetType() != null ? p.getAssetType().getName() : null,
                    p.getDescription(), p.getNotes(), p.isActive(),
                    p.getObjective(), p.getSourceReference(),
                    p.getPreparedByLabel(), p.getPreparedAt(),
                    p.getApprovedByLabel(), p.getApprovedAt(), p.isApproved(),
                    p.getTasks().size(),
                    p.getTasks().stream().map(TaskView::of).toList());
        }

        /** A mesma vista sem as tarefas, para listas. */
        public static PlanView summary(MaintenancePlan p) {
            return new PlanView(
                    p.getId(), p.getName(),
                    p.getAssetType() != null ? p.getAssetType().getId() : null,
                    p.getAssetType() != null ? p.getAssetType().getName() : null,
                    p.getDescription(), p.getNotes(), p.isActive(),
                    p.getObjective(), p.getSourceReference(),
                    p.getPreparedByLabel(), p.getPreparedAt(),
                    p.getApprovedByLabel(), p.getApprovedAt(), p.isApproved(),
                    p.getTasks().size(), List.of());
        }
    }
}
