package ao.autocare.modules.budget.dto;

import ao.autocare.domain.Budget;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class BudgetDtos {

    private BudgetDtos() {}

    public record SaveBudgetRequest(
            Integer year,
            Budget.Scope scope,
            String assetId,
            String locationId,
            Budget.Category category,
            BigDecimal amount,
            @Size(max = 3) String currency,
            @Size(max = 500) String notes) {}

    public record BudgetView(
            String id, int year, String scope, String assetId, String locationId,
            /** «toda a frota», o nome da filial ou a etiqueta da viatura. */
            String target,
            String category, String categoryLabel,
            BigDecimal amount, String currency,
            /** O gasto real do ano até hoje. */
            BigDecimal actual,
            BigDecimal remaining,
            /** Percentagem gasta. */
            int percent,
            /** Percentagem do ano decorrida — para comparar o ritmo. */
            int yearPercent,
            /** OK · AHEAD (a gastar mais depressa do que o ano) · WARNING (≥ 80 %) · EXCEEDED. */
            String status,
            String notes) {}
}
