package ao.autocare.modules.fuel;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetConsumptionBaseline;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fuel.dto.FuelControlDtos.AssetFuelCost;
import ao.autocare.modules.fuel.dto.FuelControlDtos.BaselineView;
import ao.autocare.modules.fuel.dto.FuelControlDtos.FuelAnomalyView;
import ao.autocare.modules.fuel.dto.FuelControlDtos.FuelDashboard;
import ao.autocare.modules.fuel.dto.FuelControlDtos.GroupedFuelCost;
import ao.autocare.modules.fuel.dto.FuelControlDtos.ResolveAnomalyRequest;
import ao.autocare.repo.AssetConsumptionBaselineRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.FuelRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As contas do combustível.
 *
 * <p>O número que esta classe existe para produzir é o <b>dinheiro por
 * explicar</b>. Custo total e litros totais uma empresa já tem na
 * contabilidade; o que não tem é quanto desse dinheiro não corresponde a nada
 * que se consiga demonstrar — e é esse que muda comportamentos.
 */
@Service
public class FuelAnalyticsService {

    private final FuelRecordRepository records;
    private final FuelAnomalyRepository anomalies;
    private final AssetConsumptionBaselineRepository baselines;
    private final AuditService audit;

    public FuelAnalyticsService(
            FuelRecordRepository records,
            FuelAnomalyRepository anomalies,
            AssetConsumptionBaselineRepository baselines,
            AuditService audit) {
        this.records = records;
        this.anomalies = anomalies;
        this.baselines = baselines;
        this.audit = audit;
    }

    // ==== Anomalias ========================================================
    @Transactional(readOnly = true)
    public PagedResponse<FuelAnomalyView> list(
            String orgId, AnomalyStatus status, Pageable pageable) {
        return PagedResponse.of((status == null
                ? anomalies.findByOrganizationIdOrderByOccurredAtDesc(orgId, pageable)
                : anomalies.findByOrganizationIdAndStatusOrderByOccurredAtDesc(
                        orgId, status, pageable))
                .map(FuelAnomalyView::of));
    }

    @Transactional(readOnly = true)
    public List<FuelAnomalyView> forAsset(String orgId, String assetId) {
        return anomalies.findByAssetIdOrderByOccurredAtDesc(assetId).stream()
                .filter(a -> a.getOrganization().getId().equals(orgId))
                .map(FuelAnomalyView::of)
                .toList();
    }

    /**
     * Fecha uma anomalia com explicação.
     *
     * <p>A explicação é obrigatória. Uma anomalia fechada em branco é
     * indistinguível de uma anomalia escondida — e se dez mil litros por
     * explicar saem do ecrã sem ninguém dizer porquê, o controlo passa a ser
     * teatro.
     */
    @Transactional
    public FuelAnomalyView resolve(
            String orgId, String userId, String anomalyId, ResolveAnomalyRequest req) {

        FuelAnomaly a = anomalies.findByIdAndOrganizationId(anomalyId, orgId)
                .orElseThrow(() -> ApiException.notFound("Anomalia não encontrada."));
        if (req.status() == AnomalyStatus.OPEN) {
            throw ApiException.badRequest(
                    "Escolha um desfecho: confirmada, sem fundamento ou resolvida.");
        }
        if (!a.isOpen()) {
            throw ApiException.conflict("Esta anomalia já foi fechada em "
                    + a.getResolvedAt() + ".");
        }
        a.setStatus(req.status());
        a.setResolution(req.resolution().trim());
        a.setResolvedAt(Instant.now());
        a.setResolvedBy(userId);

        audit.record(orgId, userId, "fuel_anomaly.resolve", "FuelAnomaly", a.getId(),
                a.getKind() + " · " + a.getAsset().getTag() + " · "
                        + req.status() + ": " + req.resolution().trim());
        return FuelAnomalyView.of(a);
    }

    @Transactional(readOnly = true)
    public List<BaselineView> baselines(String orgId) {
        return baselines.findByOrganizationId(orgId).stream()
                .map(BaselineView::of)
                .sorted(Comparator.comparing(BaselineView::assetTag))
                .toList();
    }

    // ==== Painel ===========================================================
    @Transactional(readOnly = true)
    public FuelDashboard dashboard(String orgId, Instant inicio, Instant fim) {
        List<FuelRecord> periodo = records.forOrgBetween(orgId, inicio, fim);

        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal distancia = BigDecimal.ZERO;
        String moeda = "AOA";

        Map<String, List<FuelRecord>> porAtivo = new LinkedHashMap<>();
        Map<String, List<FuelRecord>> porFilial = new LinkedHashMap<>();
        Map<String, List<FuelRecord>> porMotorista = new LinkedHashMap<>();

        for (FuelRecord r : periodo) {
            litros = litros.add(r.getLiters());
            if (r.getTotalCost() != null) {
                custo = custo.add(r.getTotalCost());
                moeda = r.getCurrency();
            }
            if (r.getDistanceOrHours() != null
                    && r.getMeterKind() == ao.autocare.domain.enums.Enums.MeterKind.ODOMETER) {
                distancia = distancia.add(r.getDistanceOrHours());
            }
            porAtivo.computeIfAbsent(r.getAsset().getId(), k -> new ArrayList<>()).add(r);
            if (r.getBranch() != null) {
                porFilial.computeIfAbsent(r.getBranch().getId(), k -> new ArrayList<>()).add(r);
            }
            if (r.getDriver() != null) {
                porMotorista.computeIfAbsent(r.getDriver().getId(), k -> new ArrayList<>())
                        .add(r);
            }
        }

        BigDecimal litrosEmRisco = orZero(anomalies.litersAtRiskBetween(orgId, inicio, fim));
        BigDecimal custoEmRisco = orZero(anomalies.costAtRiskBetween(orgId, inicio, fim));

        List<FuelAnomaly> doPeriodo = anomalies.between(orgId, inicio, fim);
        List<AssetFuelCost> ativos = new ArrayList<>();
        for (Map.Entry<String, List<FuelRecord>> e : porAtivo.entrySet()) {
            ativos.add(assetCost(e.getValue(), doPeriodo));
        }
        ativos.sort(Comparator.comparing(
                (AssetFuelCost a) -> a.costAtRisk() != null ? a.costAtRisk() : BigDecimal.ZERO)
                .reversed());

        return new FuelDashboard(
                inicio, fim, moeda, periodo.size(),
                litros.setScale(2, RoundingMode.HALF_UP),
                custo.setScale(2, RoundingMode.HALF_UP),
                distancia.setScale(2, RoundingMode.HALF_UP),
                divide(custo, distancia, 2),
                litrosEmRisco, custoEmRisco,
                percentage(custoEmRisco, custo),
                anomalies.countByOrganizationIdAndStatus(orgId, AnomalyStatus.OPEN),
                doPeriodo.stream().filter(a -> a.getSeverity() == AlertSeverity.CRITICAL).count(),
                ativos,
                grouped(porFilial, doPeriodo, true),
                grouped(porMotorista, doPeriodo, false),
                doPeriodo.stream()
                        .filter(a -> a.getCostAtRisk() != null)
                        .sorted(Comparator.comparing(FuelAnomaly::getCostAtRisk).reversed())
                        .limit(10)
                        .map(FuelAnomalyView::of)
                        .toList(),
                reading(custo, custoEmRisco, moeda));
    }

    private AssetFuelCost assetCost(List<FuelRecord> doAtivo, List<FuelAnomaly> anomaliasPeriodo) {
        Asset asset = doAtivo.get(0).getAsset();
        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal custo = BigDecimal.ZERO;
        BigDecimal distancia = BigDecimal.ZERO;
        String moeda = "AOA";

        for (FuelRecord r : doAtivo) {
            litros = litros.add(r.getLiters());
            if (r.getTotalCost() != null) {
                custo = custo.add(r.getTotalCost());
                moeda = r.getCurrency();
            }
            if (r.getDistanceOrHours() != null
                    && r.getMeterKind() == ao.autocare.domain.enums.Enums.MeterKind.ODOMETER) {
                distancia = distancia.add(r.getDistanceOrHours());
            }
        }

        BigDecimal por100 = distancia.signum() > 0
                ? litros.multiply(BigDecimal.valueOf(100))
                        .divide(distancia, 2, RoundingMode.HALF_UP)
                : null;

        AssetConsumptionBaseline base = baselines.findByAssetId(asset.getId()).orElse(null);
        BigDecimal desvio = null;
        if (base != null && por100 != null && base.getBaseline().signum() > 0) {
            desvio = por100.subtract(base.getBaseline())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(base.getBaseline(), 1, RoundingMode.HALF_UP);
        }

        List<FuelAnomaly> doAtivoAnomalias = anomaliasPeriodo.stream()
                .filter(a -> a.getAsset().getId().equals(asset.getId()))
                .filter(FuelAnalyticsService::counts)
                .toList();

        return new AssetFuelCost(
                asset.getId(), asset.getTag(), asset.getName(),
                asset.getLocation() != null ? asset.getLocation().getId() : null,
                asset.getLocation() != null ? asset.getLocation().getName() : null,
                doAtivo.size(),
                litros.setScale(2, RoundingMode.HALF_UP),
                custo.setScale(2, RoundingMode.HALF_UP), moeda,
                distancia.setScale(2, RoundingMode.HALF_UP),
                divide(custo, distancia, 2), por100,
                base != null ? base.getBaseline() : null, desvio,
                sum(doAtivoAnomalias, FuelAnomaly::getLitersAtRisk),
                sum(doAtivoAnomalias, FuelAnomaly::getCostAtRisk));
    }

    private List<GroupedFuelCost> grouped(
            Map<String, List<FuelRecord>> mapa, List<FuelAnomaly> anomaliasPeriodo,
            boolean porFilial) {

        List<GroupedFuelCost> saida = new ArrayList<>();
        for (Map.Entry<String, List<FuelRecord>> e : mapa.entrySet()) {
            List<FuelRecord> lista = e.getValue();
            FuelRecord primeiro = lista.get(0);
            String nome = porFilial
                    ? primeiro.getBranch().getName()
                    : primeiro.getDriver().getName();

            BigDecimal litros = BigDecimal.ZERO;
            BigDecimal custo = BigDecimal.ZERO;
            String moeda = "AOA";
            for (FuelRecord r : lista) {
                litros = litros.add(r.getLiters());
                if (r.getTotalCost() != null) {
                    custo = custo.add(r.getTotalCost());
                    moeda = r.getCurrency();
                }
            }
            List<FuelAnomaly> relacionadas = anomaliasPeriodo.stream()
                    .filter(FuelAnalyticsService::counts)
                    .filter(a -> {
                        if (a.getFuelRecord() == null) {
                            return false;
                        }
                        return lista.stream()
                                .anyMatch(r -> r.getId().equals(a.getFuelRecord().getId()));
                    })
                    .toList();

            saida.add(new GroupedFuelCost(e.getKey(), nome, lista.size(),
                    litros.setScale(2, RoundingMode.HALF_UP),
                    custo.setScale(2, RoundingMode.HALF_UP), moeda,
                    sum(relacionadas, FuelAnomaly::getLitersAtRisk),
                    sum(relacionadas, FuelAnomaly::getCostAtRisk)));
        }
        saida.sort(Comparator.comparing(GroupedFuelCost::cost).reversed());
        return saida;
    }

    /** Uma frase que diga o que os números querem dizer. */
    private String reading(BigDecimal custo, BigDecimal emRisco, String moeda) {
        if (custo.signum() <= 0) {
            return "Ainda não há abastecimentos registados neste período.";
        }
        if (emRisco.signum() <= 0) {
            return "Nada por explicar neste período. Atenção: isto só cobre o que foi "
                    + "lançado — abastecimentos que nunca chegaram ao sistema não aparecem "
                    + "em lado nenhum.";
        }
        BigDecimal percentagem = percentage(emRisco, custo);
        return emRisco.setScale(0, RoundingMode.HALF_UP) + " " + moeda + " ("
                + percentagem + "% do gasto) estão por explicar. Cada anomalia tem as "
                + "contas ao lado — nenhuma prova furto sozinha, mas todas merecem "
                + "uma resposta.";
    }

    /** Dispensadas não contam: tinham explicação, e somá-las inflaciona o número. */
    private static boolean counts(FuelAnomaly a) {
        return a.getStatus() == AnomalyStatus.OPEN || a.getStatus() == AnomalyStatus.CONFIRMED;
    }

    private static BigDecimal sum(
            List<FuelAnomaly> lista, java.util.function.Function<FuelAnomaly, BigDecimal> f) {
        return lista.stream()
                .map(f)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Nulo quando não há denominador — e não zero, que sugeriria "de graça". */
    private static BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
        if (b == null || b.signum() <= 0) {
            return null;
        }
        return a.divide(b, scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
