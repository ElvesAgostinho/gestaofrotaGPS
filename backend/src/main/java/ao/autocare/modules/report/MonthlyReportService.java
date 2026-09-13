package ao.autocare.modules.report;

import static ao.autocare.modules.org.PdfRenderer.numero;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.Organization;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.kpi.KpiService;
import ao.autocare.modules.kpi.TodayService;
import ao.autocare.modules.kpi.dto.KpiDtos.KpiReport;
import ao.autocare.modules.kpi.dto.KpiDtos.Metric;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.FuelRecordRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O relatório mensal do gestor: o sistema a falar sozinho. Uma página que
 * responde às perguntas que o dono faz no primeiro dia do mês — quanto
 * custou a frota, quanto tempo esteve parada, quanto gastou em gasóleo,
 * que viaturas doem mais, o que ficou por resolver.
 *
 * <p>Só o que os dados sustentam: um mês sem abastecimentos tem a secção de
 * combustível a dizer «sem registos», não a zeros que parecem economia.
 */
@Service
public class MonthlyReportService {

    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");

    public record Linha(String tag, String name, int orders, String cost, String downtime, String costPerUnit) {}

    public record Combustivel(String liters, String cost, int records, String consumption, String consumptionUnit,
                              int anomalies) {}

    public record Doc(String month, String monthLabel, String periodo, boolean showMoney, String currency,
                      int fleetSize, int assetsDown, String availability, String availabilityTarget,
                      int ordersOpened, int ordersClosed, int corrective, int preventive,
                      String totalCost, String laborCost, String partsCost, String externalCost,
                      String downtimeHours, String mtbf, String mttr, String planCompliance,
                      List<Linha> topAssets, Combustivel fuel, int openAlerts,
                      List<TodayService.Group> pending) {}

    private final OrganizationRepository organizations;
    private final AssetRepository assets;
    private final WorkOrderRepository workOrders;
    private final FuelRecordRepository fuelRecords;
    private final FuelAnomalyRepository anomalies;
    private final KpiService kpis;
    private final TodayService today;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public MonthlyReportService(OrganizationRepository organizations, AssetRepository assets,
            WorkOrderRepository workOrders, FuelRecordRepository fuelRecords, FuelAnomalyRepository anomalies,
            KpiService kpis, TodayService today, Letterhead letterhead, PdfRenderer renderer) {
        this.organizations = organizations;
        this.assets = assets;
        this.workOrders = workOrders;
        this.fuelRecords = fuelRecords;
        this.anomalies = anomalies;
        this.kpis = kpis;
        this.today = today;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public Doc build(String orgId, YearMonth mes, boolean showMoney) {
        Organization org = organizations.findById(orgId).orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
        Instant inicio = mes.atDay(1).atStartOfDay(FUSO).toInstant();
        Instant fim = mes.plusMonths(1).atDay(1).atStartOfDay(FUSO).toInstant();
        if (fim.isAfter(Instant.now())) {
            fim = Instant.now();
        }

        List<Asset> frota = assets.findByOrganizationId(orgId).stream()
                .filter(a -> !a.isArchived() && a.getStatus() != AssetStatus.RETIRED).toList();
        int paradas = (int) frota.stream().filter(a -> a.getStatus() == AssetStatus.DOWN).count();

        // Ordens do mês: abertas nele e concluídas nele (o custo conta quando se conclui).
        List<WorkOrder> todas = workOrders.findByOrganizationIdOrderByOpenedAtDesc(orgId, PageRequest.of(0, 5000)).getContent();
        final Instant ini = inicio;
        final Instant fi = fim;
        int abertas = (int) todas.stream().filter(w -> w.getOpenedAt() != null && !w.getOpenedAt().isBefore(ini) && w.getOpenedAt().isBefore(fi)).count();
        List<WorkOrder> concluidas = todas.stream()
                .filter(w -> w.getCompletedAt() != null && !w.getCompletedAt().isBefore(ini) && w.getCompletedAt().isBefore(fi))
                .filter(w -> w.getStatus() != WorkOrderStatus.CANCELLED)
                .toList();
        int corretivas = (int) concluidas.stream().filter(w -> w.getType() == WorkOrderType.CORRECTIVE || w.getType() == WorkOrderType.EMERGENCY).count();
        int preventivas = concluidas.size() - corretivas;

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal mao = BigDecimal.ZERO;
        BigDecimal pecas = BigDecimal.ZERO;
        BigDecimal externo = BigDecimal.ZERO;
        BigDecimal paragem = BigDecimal.ZERO;
        String moeda = "AOA";
        Map<String, List<WorkOrder>> porAtivo = new LinkedHashMap<>();
        for (WorkOrder w : concluidas) {
            total = total.add(z(w.getTotalCost()));
            mao = mao.add(z(w.getTotalLaborCost()));
            pecas = pecas.add(z(w.getTotalPartsCost()));
            externo = externo.add(z(w.getTotalExternalCost()));
            paragem = paragem.add(z(w.getDowntimeHours()));
            if (w.getCurrency() != null) {
                moeda = w.getCurrency();
            }
            porAtivo.computeIfAbsent(w.getAsset().getId(), k -> new ArrayList<>()).add(w);
        }

        List<Linha> top = new ArrayList<>();
        porAtivo.values().stream()
                .sorted(Comparator.comparing((List<WorkOrder> l) -> l.stream().map(w -> z(w.getTotalCost())).reduce(BigDecimal.ZERO, BigDecimal::add)).reversed())
                .limit(8)
                .forEach(l -> {
                    Asset a = l.get(0).getAsset();
                    BigDecimal custo = l.stream().map(w -> z(w.getTotalCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal horas = l.stream().map(w -> z(w.getDowntimeHours())).reduce(BigDecimal.ZERO, BigDecimal::add);
                    top.add(new Linha(a.getTag(), a.getName(), l.size(), showMoney ? numero(custo, 0) : null,
                            numero(horas, 1), null));
                });

        // Combustível do mês.
        List<FuelRecord> abastecimentos = fuelRecords.forOrgBetween(orgId, inicio, fim);
        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal custoComb = BigDecimal.ZERO;
        BigDecimal somaConsumo = BigDecimal.ZERO;
        int comConsumo = 0;
        String unidadeConsumo = null;
        for (FuelRecord f : abastecimentos) {
            litros = litros.add(z(f.getLiters()));
            custoComb = custoComb.add(z(f.getTotalCost()));
            if (f.getConsumption() != null && f.getConsumption().signum() > 0) {
                somaConsumo = somaConsumo.add(f.getConsumption());
                comConsumo++;
                unidadeConsumo = f.getConsumptionUnit();
            }
        }
        int anomaliasMes = (int) anomalies.between(orgId, inicio, fim).size();
        Combustivel comb = new Combustivel(
                abastecimentos.isEmpty() ? null : numero(litros, 0),
                abastecimentos.isEmpty() || !showMoney ? null : numero(custoComb, 0),
                abastecimentos.size(),
                comConsumo > 0 ? numero(somaConsumo.divide(BigDecimal.valueOf(comConsumo), 1, RoundingMode.HALF_UP), 1) : null,
                unidadeConsumo, anomaliasMes);

        KpiReport kpi = kpis.report(orgId, null, inicio, fim);
        String disponibilidade = metrica(kpi, "availability");
        String mtbf = metrica(kpi, "mtbf");
        String mttr = metrica(kpi, "mttr");
        String plano = metrica(kpi, "plan_compliance");
        String metaDisp = kpi.metrics().stream().filter(m -> m.key().equals("availability") && m.target() != null)
                .map(m -> numero(BigDecimal.valueOf(m.target()), 0) + " %").findFirst().orElse(null);

        TodayService.TodayView pendentes = today.today(orgId);

        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt")) + " " + mes.getYear();
        nomeMes = Character.toUpperCase(nomeMes.charAt(0)) + nomeMes.substring(1);
        return new Doc(mes.toString(), nomeMes,
                PdfRenderer.data(inicio) + " a " + PdfRenderer.data(fim.minusSeconds(1)),
                showMoney, moeda, frota.size(), paradas, disponibilidade, metaDisp,
                abertas, concluidas.size(), corretivas, preventivas,
                showMoney ? numero(total, 0) : null, showMoney ? numero(mao, 0) : null,
                showMoney ? numero(pecas, 0) : null, showMoney ? numero(externo, 0) : null,
                numero(paragem, 1), mtbf, mttr, plano, top, comb, pendentes.total(), pendentes.groups());
    }

    @Transactional(readOnly = true)
    public byte[] pdf(String orgId, YearMonth mes, boolean showMoney) {
        Organization org = organizations.findById(orgId).orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
        return renderer.render("monthly-report", letterhead.of(org), "r", build(orgId, mes, showMoney));
    }

    /** O resumo curto para o WhatsApp: quatro números e o que está pendente. */
    public String resumoCurto(Organization org, Doc d) {
        StringBuilder sb = new StringBuilder();
        sb.append(org.getName()).append(" — relatório de ").append(d.monthLabel()).append('\n');
        sb.append("Frota: ").append(d.fleetSize()).append(" viatura(s)");
        if (d.assetsDown() > 0) {
            sb.append(", ").append(d.assetsDown()).append(" parada(s)");
        }
        sb.append('\n');
        if (d.availability() != null) {
            sb.append("Disponibilidade: ").append(d.availability()).append('\n');
        }
        sb.append("Ordens: ").append(d.ordersClosed()).append(" concluída(s) (").append(d.corrective()).append(" corretiva(s))");
        if (d.totalCost() != null) {
            sb.append(", custo ").append(d.totalCost()).append(' ').append(d.currency());
        }
        sb.append('\n');
        if (d.fuel().liters() != null) {
            sb.append("Combustível: ").append(d.fuel().liters()).append(" L");
            if (d.fuel().cost() != null) {
                sb.append(" (").append(d.fuel().cost()).append(' ').append(d.currency()).append(')');
            }
            if (d.fuel().anomalies() > 0) {
                sb.append(", ").append(d.fuel().anomalies()).append(" anomalia(s)");
            }
            sb.append('\n');
        }
        sb.append("Pendente hoje: ").append(d.openAlerts()).append(" item(ns). O PDF completo seguiu por email.");
        return sb.toString();
    }

    private static String metrica(KpiReport kpi, String key) {
        for (Metric m : kpi.metrics()) {
            if (m.key().equals(key) && m.value() != null) {
                return numero(BigDecimal.valueOf(m.value()), 1) + (m.unit() != null ? " " + m.unit() : "");
            }
        }
        return null;
    }

    private static BigDecimal z(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
