package ao.autocare.modules.checklist.dto;

import ao.autocare.domain.ChecklistExecution;
import ao.autocare.domain.ChecklistExecutionItem;
import ao.autocare.domain.ChecklistItem;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.enums.Enums.ChecklistItemResult;
import ao.autocare.domain.enums.Enums.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ChecklistDtos {

    private ChecklistDtos() {}

    // ---- Modelos --------------------------------------------------------
    public record ItemInput(
            @NotBlank @Size(max = 300) String text,
            VerificationType verification,
            Boolean critical) {}

    public record SaveTemplateRequest(
            @NotBlank(message = "Indique o nome do modelo.") @Size(max = 160) String name,
            String assetTypeId,
            @Size(max = 4000) String description,
            Integer estimatedMinutes,
            @NotEmpty(message = "Adicione pelo menos um item.") List<ItemInput> items) {}

    public record ItemView(String id, String text, String verification, boolean critical, int sortOrder) {
        public static ItemView of(ChecklistItem i) {
            return new ItemView(i.getId(), i.getText(), i.getVerification().name(),
                    i.isCritical(), i.getSortOrder());
        }
    }

    public record TemplateView(
            String id, String name, String assetTypeId, String assetTypeName,
            String description, Integer estimatedMinutes, boolean active,
            List<ItemView> items) {

        public static TemplateView of(ChecklistTemplate t) {
            return new TemplateView(
                    t.getId(), t.getName(),
                    t.getAssetType() != null ? t.getAssetType().getId() : null,
                    t.getAssetType() != null ? t.getAssetType().getName() : null,
                    t.getDescription(), t.getEstimatedMinutes(), t.isActive(),
                    t.getItems().stream().map(ItemView::of).toList());
        }
    }

    // ---- Execuções -----------------------------------------------------
    public record ResultInput(
            @NotBlank @Size(max = 300) String text,
            VerificationType verification,
            Boolean critical,
            ChecklistItemResult result,
            @Size(max = 300) String note) {}

    public record RecordExecutionRequest(
            String templateId,
            @Size(max = 160) String templateName,
            Instant performedAt,
            BigDecimal meterValue,
            @Size(max = 120) String performedByLabel,
            @Size(max = 1000) String notes,
            List<ResultInput> items) {}

    public record ResultView(
            String text, String verification, boolean critical, String result, String note) {
        public static ResultView of(ChecklistExecutionItem i) {
            return new ResultView(i.getText(), i.getVerification().name(), i.isCritical(),
                    i.getResult().name(), i.getNote());
        }
    }

    public record ExecutionView(
            String id,
            String assetId,
            String assetTag,
            String templateId,
            String templateName,
            String performedByLabel,
            Instant performedAt,
            BigDecimal meterValue,
            String outcome,
            String notes,
            int itemsOk,
            int itemsNotOk,
            List<ResultView> items) {

        public static ExecutionView of(ChecklistExecution e) {
            int ok = 0;
            int notOk = 0;
            for (ChecklistExecutionItem i : e.getItems()) {
                if (i.getResult() == ChecklistItemResult.OK) ok++;
                else if (i.getResult() == ChecklistItemResult.NOT_OK) notOk++;
            }
            return new ExecutionView(
                    e.getId(), e.getAsset().getId(), e.getAsset().getTag(),
                    e.getTemplate() != null ? e.getTemplate().getId() : null,
                    e.getTemplateName(),
                    e.getPerformedByLabel(), e.getPerformedAt(), e.getMeterValue(),
                    e.getOutcome().name(), e.getNotes(), ok, notOk,
                    e.getItems().stream().map(ResultView::of).toList());
        }

        public static ExecutionView summary(ChecklistExecution e) {
            return new ExecutionView(
                    e.getId(), e.getAsset().getId(), e.getAsset().getTag(),
                    e.getTemplate() != null ? e.getTemplate().getId() : null,
                    e.getTemplateName(), e.getPerformedByLabel(), e.getPerformedAt(),
                    e.getMeterValue(), e.getOutcome().name(), e.getNotes(),
                    0, 0, List.of());
        }
    }
}
