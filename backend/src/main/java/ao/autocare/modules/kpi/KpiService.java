package ao.autocare.modules.kpi;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.enums.Enums.MeterKind;
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
    private final ao.autocare.repo.AssetMeterRepository meters;
    private final FailureRepository failures;
    private final RepairRepository repairs;
    private final WorkOrderRepository workOrders;

    public KpiService(
            AssetRepository assets,
            MeterReadingRepository readings,
            ao.autocare.repo.AssetMeterRepository meters,
            FailureRepository failures,
            RepairRepository repairs,
            WorkOrderRepository workOrders) {
        this.assets = assets;
        this.readings = readings;
        this.meters = meters;
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

        // O que a frota andou, cada coisa na sua unidade: as máquinas contam-se
        // em horas de horímetro, as viaturas em quilómetros de odómetro. Somar
        // as duas dá um número que não é nenhuma das duas — e era com ele que o
        // MTBF de um camião saía em «horas» quando eram quilómetros.
        java.util.Set<String> noAmbito = scopeAssets.stream().map(Asset::getId)
                .collect(java.util.stream.Collectors.toSet());

        // O grupo de cada ativo vem do contador que ele tem — uma viatura parada
        // o mês inteiro continua a medir-se em quilómetros.
        java.util.Set<String> ativosPorHoras = new java.util.HashSet<>();
        java.util.Set<String> ativosPorKm = new java.util.HashSet<>();
        for (Object[] linha : meters.primaryKindsForOrg(orgId)) {
            String id = (String) linha[0];
            if (!noAmbito.contains(id)) {
                continue;
            }
            if (linha[1] == MeterKind.ODOMETER) {
                ativosPorKm.add(id);
            } else {
                ativosPorHoras.add(id);
            }
        }

        double operatingHours = 0;
        double operatingKm = 0;
        for (Object[] linha : readings.operatingUnitsByAsset(orgId, start, end)) {
            String id = (String) linha[0];
            if (!noAmbito.contains(id)) {
                continue;
            }
            double unidades = linha[2] != null ? ((BigDecimal) linha[2]).doubleValue() : 0;
            if (unidades <= 0) {
                continue;
            }
            if (linha[1] == MeterKind.ODOMETER) {
                operatingKm += unidades;
            } else {
                operatingHours += unidades;
            }
        }

        // As avarias também se separam: as do grupo das horas e as do grupo dos
        // quilómetros. Uma avaria de camião não entra no MTBF das máquinas.
        int falhasHoras = 0;
        int falhasKm = 0;
        for (Object[] linha : failures.countByAssetBetween(orgId, start, end)) {
            String id = (String) linha[0];
            int n = ((Number) linha[1]).intValue();
            if (ativosPorHoras.contains(id)) {
                falhasHoras += n;
            } else if (ativosPorKm.contains(id)) {
                falhasKm += n;
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
                : workOrders.countCompletedBetween(orgId, WorkOrderType.PREVENTIVE, start, end));
        // Cumprir mais do que o planeado não é 200 %: é 100 % com trabalho a mais.
        Double compliance = plannedOrders > 0
                ? Math.min(100.0, round((double) executedOrders / plannedOrders * 100))
                : null;

        // Sem contador a andar, o MTBF não é zero: é desconhecido.
        Double mtbf = falhasHoras > 0 && operatingHours > 0
                ? round(operatingHours / falhasHoras) : null;
        Double mtbfKm = falhasKm > 0 && operatingKm > 0
                ? round(operatingKm / falhasKm) : null;
        Double mttr = repairCount > 0 ? round(totalRepairHours / repairCount) : null;

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("availability", "Disponibilidade", availability, "%", 90.0, "MIN",
                availability != null && availability >= 90,
                "(Horas Disponíveis / Horas Planeadas) × 100"));
        // Só se mostra o MTBF da unidade que o âmbito tem. Numa frota mista
        // aparecem os dois, cada um com a sua meta; numa retroescavadora
        // aparece só o das horas, e num ligeiro só o dos quilómetros.
        boolean temHoras = !ativosPorHoras.isEmpty();
        boolean temKm = !ativosPorKm.isEmpty();
        if (temHoras || !temKm) {
            metrics.add(new Metric("mtbf", "MTBF (equipamento com horímetro)", mtbf, "h", 500.0, "MIN",
                    mtbf != null && mtbf >= 500,
                    "Horas de Operação / Número de Falhas"));
        }
        if (temKm) {
            metrics.add(new Metric("mtbf_km", "MTBF (viaturas, por quilómetros)", mtbfKm, "km",
                    20_000.0, "MIN", mtbfKm != null && mtbfKm >= 20_000,
                    "Quilómetros Percorridos / Número de Falhas"));
        }
        metrics.add(new Metric("mttr", "MTTR", mttr, "h", 4.0, "MAX",
                mttr != null && mttr <= 4,
                "Tempo Total de Reparação / Número de Reparações"));
        metrics.add(new Metric("plan_compliance", "Cumprimento do plano", compliance, "%", 95.0, "MIN",
                compliance != null && compliance >= 95,
                "(Ordens Executadas / Ordens Planeadas) × 100"));

        return new KpiReport(scope, scopeId, scopeName, start, end,
                round(operatingHours), round(operatingKm),
                failureCount, repairCount, round(totalRepairHours),
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
