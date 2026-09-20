package ao.autocare.modules.kpi.dto;

import java.time.Instant;

public final class KpiDtos {

    private KpiDtos() {}

    public record Metric(
            String key,
            String name,
            Double value,
            String unit,
            Double target,
            String targetDirection,   // "MIN" (>=) ou "MAX" (<=)
            boolean meetsTarget,
            String formula) {}

    public record KpiReport(
            String scope,             // ORG | ASSET
            String scopeId,
            String scopeName,
            Instant from,
            Instant to,
            double operatingHours,
            double operatingKm,
            int failures,
            int repairs,
            double totalRepairHours,
            double plannedHours,
            double downtimeHours,
            int plannedOrders,
            int executedOrders,
            java.util.List<Metric> metrics) {}
}
