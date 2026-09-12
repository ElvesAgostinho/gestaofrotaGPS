package ao.autocare.modules.fuel.dto;

import ao.autocare.domain.AssetConsumptionBaseline;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.domain.enums.Enums.FuelAnomalyKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FuelControlDtos {

    private FuelControlDtos() {}

    // ===== Anomalias =======================================================
    public record FuelAnomalyView(
            String id,
            FuelAnomalyKind kind,
            String kindLabel,
            AlertSeverity severity,
            AnomalyStatus status,
            String statusLabel,
            Instant occurredAt,
            Instant detectedAt,
            String assetId,
            String assetTag,
            String driverId,
            String driverName,
            String fuelRecordId,
            BigDecimal expectedValue,
            BigDecimal observedValue,
            String unit,
            BigDecimal litersAtRisk,
            BigDecimal costAtRisk,
            String currency,
            String title,
            String detail,
            String resolution,
            Instant resolvedAt) {

        public static FuelAnomalyView of(FuelAnomaly a) {
            return new FuelAnomalyView(
                    a.getId(), a.getKind(), a.getKind().label(), a.getSeverity(),
                    a.getStatus(), a.getStatus().label(),
                    a.getOccurredAt(), a.getDetectedAt(),
                    a.getAsset().getId(), a.getAsset().getTag(),
                    a.getDriver() != null ? a.getDriver().getId() : null,
                    a.getDriver() != null ? a.getDriver().getName() : null,
                    a.getFuelRecord() != null ? a.getFuelRecord().getId() : null,
                    a.getExpectedValue(), a.getObservedValue(), a.getUnit(),
                    a.getLitersAtRisk(), a.getCostAtRisk(), a.getCurrency(),
                    a.getTitle(), a.getDetail(), a.getResolution(), a.getResolvedAt());
        }
    }

    /**
     * Fecho de uma anomalia.
     *
     * <p>A explicação é obrigatória, e não por burocracia: uma anomalia fechada
     * sem justificação é indistinguível de uma anomalia escondida. Se dez mil
     * litros por explicar desaparecem do ecrã sem ninguém dizer porquê, o
     * controlo passa a ser teatro.
     */
    public record ResolveAnomalyRequest(
            @NotNull(message = "Indique o desfecho.") AnomalyStatus status,
            @NotBlank(message = "Explique o que se apurou. Fica registado.")
            @Size(max = 1000) String resolution) {}

    // ===== Base de consumo =================================================
    public record BaselineView(
            String assetId,
            String assetTag,
            String unit,
            BigDecimal baseline,
            BigDecimal stdDeviation,
            int sampleCount,
            BigDecimal best,
            BigDecimal worst,
            Instant firstSampleAt,
            Instant lastSampleAt,
            Instant computedAt,
            String explanation) {

        public static BaselineView of(AssetConsumptionBaseline b) {
            return new BaselineView(
                    b.getAsset().getId(), b.getAsset().getTag(), b.getUnit(),
                    b.getBaseline(), b.getStdDeviation(), b.getSampleCount(),
                    b.getBest(), b.getWorst(),
                    b.getFirstSampleAt(), b.getLastSampleAt(), b.getComputedAt(),
                    "Média de " + b.getSampleCount() + " depósitos cheios deste ativo. "
                            + "Só se compara o ativo consigo próprio: um camião de obra "
                            + "gasta o dobro de um ligeiro e isso não é anomalia nenhuma.");
        }
    }

    // ===== Custos ==========================================================
    /** Custo de combustível de um ativo no período. */
    public record AssetFuelCost(
            String assetId,
            String assetTag,
            String assetName,
            String branchId,
            String branchName,
            int refuels,
            BigDecimal liters,
            BigDecimal cost,
            String currency,
            BigDecimal distanceKm,
            /** Nulo quando não houve distância registada — e não zero. */
            BigDecimal costPerKm,
            BigDecimal litersPer100Km,
            BigDecimal baseline,
            /** Diferença percentual face à base do próprio ativo. */
            BigDecimal deviationPercent,
            BigDecimal litersAtRisk,
            BigDecimal costAtRisk) {}

    /** Custo agregado por filial ou por motorista. */
    public record GroupedFuelCost(
            String id,
            String name,
            int refuels,
            BigDecimal liters,
            BigDecimal cost,
            String currency,
            BigDecimal litersAtRisk,
            BigDecimal costAtRisk) {}

    /**
     * O painel de combustível.
     *
     * <p>{@code costAtRisk} é o número que interessa a uma direção: dinheiro
     * gasto que o sistema não consegue explicar. Sem ele isto seria mais um
     * ecrã de gráficos bonitos.
     */
    public record FuelDashboard(
            Instant periodStart,
            Instant periodEnd,
            String currency,
            int refuels,
            BigDecimal totalLiters,
            BigDecimal totalCost,
            BigDecimal totalDistanceKm,
            BigDecimal costPerKm,
            BigDecimal litersAtRisk,
            BigDecimal costAtRisk,
            BigDecimal percentAtRisk,
            long openAnomalies,
            long criticalAnomalies,
            List<AssetFuelCost> byAsset,
            List<GroupedFuelCost> byBranch,
            List<GroupedFuelCost> byDriver,
            List<FuelAnomalyView> worstAnomalies,
            String reading) {}
}
