package ao.autocare.modules.kpi;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.kpi.dto.KpiDtos.KpiReport;
import ao.autocare.modules.kpi.dto.KpiDtos.Metric;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FailureRepository;
import ao.autocare.repo.MeterReadingRepository;
import ao.autocare.repo.RepairRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores de desempenho, com as fórmulas do documento de referência:
 *   Disponibilidade ≥ 90%  = (Horas Disponíveis / Horas Planeadas) × 100
 *   MTBF ≥ 500 h            = Horas de Operação / Nº de Falhas
 *   MTTR ≤ 4 h              = Tempo Total de Reparação / Nº de Reparações
 *   Cumprimento ≥ 95%       = (Ordens Executadas / Ordens Planeadas) × 100
 */
@Service
public class KpiService {

    private final AssetRepository assets;
    private final MeterReadingRepository readings;
    private final FailureRepository failures;
    private final RepairRepository repairs;
    private final WorkOrderRepository workOrders;

    public KpiService(
            AssetRepository assets,
            MeterReadingRepository readings,
            FailureRepository failures,
            RepairRepository repairs,
            WorkOrderRepository workOrders) {
        this.assets = assets;
        this.readings = readings;
        this.failures = failures;
        this.repairs = repairs;
        this.workOrders = workOrders;
    }

    @Transactional(readOnly = true)
    public KpiReport report(String orgId, String assetId, Instant from, Instant to) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(90, ChronoUnit.DAYS);
        if (!start.isBefore(end)) {
            throw ApiException.badRequest("A data inicial tem de ser anterior à final.");
        }

        double periodHours = Duration.between(start, end).toMillis() / 3_600_000.0;

        List<Asset> scopeAssets;
        String scope;
        String scopeId;
        String scopeName;
        if (assetId != null && !assetId.isBlank()) {
            Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                    .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
            scopeAssets = List.of(a);
            scope = "ASSET";
            scopeId = a.getId();
            scopeName = a.getTag() + " · " + a.getName();
        } else {
            scopeAssets = assets.findByOrganizationId(orgId).stream()
                    .filter(a -> !a.isArchived())
                    .toList();
            scope = "ORG";
            scopeId = orgId;
            scopeName = "Toda a frota";
        }

        // Horas de operação = soma dos deltas de horímetro no período
        double operatingHours = 0;
        for (Asset a : scopeAssets) {
            BigDecimal units = readings.operatingUnitsForAsset(a.getId(), start, end);
            if (units != null && units.signum() > 0) {
                operatingHours += units.doubleValue();
            }
        }

        int failureCount = (int) (assetId != null
                ? failures.countForAssetBetween(assetId, start, end)
                : failures.countForOrgBetween(orgId, start, end));

        int repairCount = (int) (assetId != null
                ? repairs.countForAssetBetween(assetId, start, end)
                : repairs.countForOrgBetween(orgId, start, end));
        BigDecimal repairHoursBd = assetId != null
                ? repairs.totalRepairHoursForAsset(assetId, start, end)
                : repairs.totalRepairHoursForOrg(orgId, start, end);
        double totalRepairHours = repairHoursBd != null ? repairHoursBd.doubleValue() : 0;

        // Downtime: sobreposição das paragens com o período
        double downtimeHours = 0;
        for (WorkOrder w : workOrders.downtimeOverlapping(orgId, start, end)) {
            if (assetId != null && !w.getAsset().getId().equals(assetId)) continue;
            Instant ds = max(w.getDowntimeStart(), start);
            Instant de = min(w.getDowntimeEnd() != null ? w.getDowntimeEnd() : end, end);
            if (de.isAfter(ds)) {
                downtimeHours += Duration.between(ds, de).toMillis() / 3_600_000.0;
            }
        }

        double plannedHours = periodHours * Math.max(scopeAssets.size(), 1);
        Double availability = plannedHours > 0
                ? round((plannedHours - downtimeHours) / plannedHours * 100)
                : null;

        int plannedOrders = (int) workOrders.countByTypeOpenedBetween(
                orgId, WorkOrderType.PREVENTIVE, start, end);
        int executedOrders = (int) (assetId != null
                ? workOrders.countCompletedForAssetBetween(assetId, WorkOrderType.PREVENTIVE, start, end)
                : workOrders.countCompletedBetween(orgId, start, end));
        Double compliance = plannedOrders > 0
                ? round((double) executedOrders / plannedOrders * 100)
                : null;

        Double mtbf = failureCount > 0 ? round(operatingHours / failureCount) : null;
        Double mttr = repairCount > 0 ? round(totalRepairHours / repairCount) : null;

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("availability", "Disponibilidade", availability, "%", 90.0, "MIN",
                availability != null && availability >= 90,
                "(Horas Disponíveis / Horas Planeadas) × 100"));
        metrics.add(new Metric("mtbf", "MTBF", mtbf, "h", 500.0, "MIN",
                mtbf != null && mtbf >= 500,
                "Horas de Operação / Número de Falhas"));
        metrics.add(new Metric("mttr", "MTTR", mttr, "h", 4.0, "MAX",
                mttr != null && mttr <= 4,
                "Tempo Total de Reparação / Número de Reparações"));
        metrics.add(new Metric("plan_compliance", "Cumprimento do plano", compliance, "%", 95.0, "MIN",
                compliance != null && compliance >= 95,
                "(Ordens Executadas / Ordens Planeadas) × 100"));

        return new KpiReport(scope, scopeId, scopeName, start, end,
                round(operatingHours), failureCount, repairCount, round(totalRepairHours),
                round(plannedHours), round(downtimeHours), plannedOrders, executedOrders, metrics);
    }

    private static Double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
