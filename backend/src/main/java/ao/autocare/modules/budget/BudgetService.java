package ao.autocare.modules.budget;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Budget;
import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.Location;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.budget.dto.BudgetDtos.BudgetView;
import ao.autocare.modules.budget.dto.BudgetDtos.SaveBudgetRequest;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.BudgetRepository;
import ao.autocare.repo.FuelRecordRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orçamento anual: previsto contra real, por frota, filial ou viatura.
 *
 * <p>O real não se escreve — sai das ordens concluídas (custo total) e dos
 * abastecimentos (custo total) do ano. Aos 80 % e aos 100 % o gestor recebe
 * um aviso, uma vez por marco, para não ser surpreendido em dezembro.
 */
@Service
public class BudgetService {

    private static final ZoneId ZONA = ZoneId.of("Africa/Luanda");

    private final BudgetRepository budgets;
    private final WorkOrderRepository workOrders;
    private final FuelRecordRepository fuelRecords;
    private final AssetRepository assets;
    private final LocationRepository locations;
    private final OrganizationRepository organizations;
    private final AuditService audit;
    private final NotificationService notifications;

    public BudgetService(BudgetRepository budgets, WorkOrderRepository workOrders,
            FuelRecordRepository fuelRecords, AssetRepository assets, LocationRepository locations,
            OrganizationRepository organizations, AuditService audit, NotificationService notifications) {
        this.budgets = budgets;
        this.workOrders = workOrders;
        this.fuelRecords = fuelRecords;
        this.assets = assets;
        this.locations = locations;
        this.organizations = organizations;
        this.audit = audit;
        this.notifications = notifications;
    }

    /** As contas de um ano: por ativo e por filial, manutenção e combustível. */
    record Reais(Map<String, BigDecimal> manutencaoPorAtivo, Map<String, BigDecimal> combustivelPorAtivo,
                 Map<String, String> filialDoAtivo, BigDecimal manutencaoTotal, BigDecimal combustivelTotal) {

        BigDecimal para(Budget b) {
            BigDecimal m;
            BigDecimal c;
            switch (b.getScope()) {
                case ASSET -> {
                    String id = b.getAsset() != null ? b.getAsset().getId() : "";
                    m = manutencaoPorAtivo.getOrDefault(id, BigDecimal.ZERO);
                    c = combustivelPorAtivo.getOrDefault(id, BigDecimal.ZERO);
                }
                case LOCATION -> {
                    String loc = b.getLocation() != null ? b.getLocation().getId() : "";
                    m = BigDecimal.ZERO;
                    c = BigDecimal.ZERO;
                    for (Map.Entry<String, String> e : filialDoAtivo.entrySet()) {
                        if (loc.equals(e.getValue())) {
                            m = m.add(manutencaoPorAtivo.getOrDefault(e.getKey(), BigDecimal.ZERO));
                            c = c.add(combustivelPorAtivo.getOrDefault(e.getKey(), BigDecimal.ZERO));
                        }
                    }
                }
                default -> {
                    m = manutencaoTotal;
                    c = combustivelTotal;
                }
            }
            return switch (b.getCategory()) {
                case MAINTENANCE -> m;
                case FUEL -> c;
                case TOTAL -> m.add(c);
            };
        }
    }

    @Transactional(readOnly = true)
    public List<BudgetView> list(String orgId, int year) {
        Reais reais = reais(orgId, year);
        return budgets.findByOrganizationIdAndYearOrderByScopeAscCategoryAsc(orgId, year).stream()
                .map(b -> vista(b, reais.para(b), year))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Integer> years(String orgId) {
        return budgets.findByOrganizationIdOrderByYearDescScopeAsc(orgId).stream()
                .map(Budget::getYear).distinct().toList();
    }

    @Transactional
    public BudgetView create(String orgId, String userId, SaveBudgetRequest req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw ApiException.badRequest("Indique o valor do orçamento.");
        }
        Budget b = new Budget();
        b.setOrganization(organizations.getReferenceById(orgId));
        b.setYear(req.year() != null ? req.year() : LocalDate.now(ZONA).getYear());
        b.setScope(req.scope() != null ? req.scope() : Budget.Scope.ORG);
        b.setCategory(req.category() != null ? req.category() : Budget.Category.TOTAL);
        if (b.getScope() == Budget.Scope.ASSET) {
            if (req.assetId() == null || req.assetId().isBlank()) {
                throw ApiException.badRequest("Indique a viatura do orçamento.");
            }
            Asset a = assets.findByIdAndOrganizationId(req.assetId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
            b.setAsset(a);
        } else if (b.getScope() == Budget.Scope.LOCATION) {
            if (req.locationId() == null || req.locationId().isBlank()) {
                throw ApiException.badRequest("Indique a filial ou centro de custo do orçamento.");
            }
            Location l = locations.findByIdAndOrganizationId(req.locationId(), orgId)
                    .orElseThrow(() -> ApiException.notFound("Local não encontrado."));
            b.setLocation(l);
        }
        // O mesmo âmbito e categoria no mesmo ano só tem um orçamento.
        for (Budget outro : budgets.findByOrganizationIdAndYearOrderByScopeAscCategoryAsc(orgId, b.getYear())) {
            if (outro.getScope() == b.getScope() && outro.getCategory() == b.getCategory()
                    && mesmoAlvo(outro, b)) {
                throw ApiException.conflict("Já existe um orçamento de " + b.getCategory().label()
                        + " para este âmbito em " + b.getYear() + ". Altere esse.");
            }
        }
        b.setAmount(req.amount());
        if (req.currency() != null && !req.currency().isBlank()) b.setCurrency(req.currency().trim().toUpperCase());
        b.setNotes(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : null);
        budgets.save(b);
        audit.record(orgId, userId, "budget.create", "Budget", b.getId(),
                b.getYear() + " · " + b.getCategory().label() + " · " + b.getAmount());
        return vista(b, reais(orgId, b.getYear()).para(b), b.getYear());
    }

    @Transactional
    public BudgetView update(String orgId, String userId, String id, SaveBudgetRequest req) {
        Budget b = require(orgId, id);
        if (req.amount() != null) {
            if (req.amount().signum() <= 0) {
                throw ApiException.badRequest("Indique o valor do orçamento.");
            }
            b.setAmount(req.amount());
            b.setWarnedAtPct(null); // o valor mudou; os avisos recomeçam
        }
        if (req.notes() != null) b.setNotes(req.notes().isBlank() ? null : req.notes().trim());
        audit.record(orgId, userId, "budget.update", "Budget", id, String.valueOf(b.getAmount()));
        return vista(b, reais(orgId, b.getYear()).para(b), b.getYear());
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        Budget b = require(orgId, id);
        budgets.delete(b);
        audit.record(orgId, userId, "budget.delete", "Budget", id, b.getCategory().label());
    }

    /**
     * Avisa dos orçamentos que passaram dos 80 % e dos 100 %. Uma vez por
     * marco; corre uma vez por dia.
     */
    @Transactional
    public int notifyOverruns() {
        int enviados = 0;
        int ano = LocalDate.now(ZONA).getYear();
        Map<String, Reais> porEmpresa = new HashMap<>();
        for (Budget b : budgets.findByYear(ano)) {
            String orgId = b.getOrganization().getId();
            Reais reais = porEmpresa.computeIfAbsent(orgId, id -> reais(id, ano));
            BigDecimal real = reais.para(b);
            int pct = percentagem(real, b.getAmount());
            int marco = pct >= 100 ? 100 : pct >= 80 ? 80 : 0;
            if (marco == 0 || (b.getWarnedAtPct() != null && b.getWarnedAtPct() >= marco)) {
                continue;
            }
            b.setWarnedAtPct(marco);
            String alvo = alvo(b);
            String titulo = marco >= 100
                    ? "Orçamento esgotado — " + alvo
                    : "Orçamento a " + pct + " % — " + alvo;
            String corpo = b.getCategory().label() + " " + ano + ": gasto " + real.setScale(0, RoundingMode.HALF_UP)
                    + " de " + b.getAmount().setScale(0, RoundingMode.HALF_UP) + " " + b.getCurrency() + ".";
            enviados += notifications.notifyManagers(NotificationService.Draft.of(
                    orgId, AlertCategory.EXPENSE,
                    marco >= 100 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                    titulo, corpo, "budget_" + marco, b.getId() + ":" + ano, "/orcamentos"));
        }
        return enviados;
    }

    // -----------------------------------------------------------------------

    Reais reais(String orgId, int year) {
        Instant de = LocalDate.of(year, 1, 1).atStartOfDay(ZONA).toInstant();
        Instant ate = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZONA).toInstant();
        Map<String, BigDecimal> manutencao = new HashMap<>();
        Map<String, BigDecimal> combustivel = new HashMap<>();
        Map<String, String> filial = new HashMap<>();
        BigDecimal mTotal = BigDecimal.ZERO;
        BigDecimal cTotal = BigDecimal.ZERO;
        for (WorkOrder w : workOrders.completedBetween(orgId, de, ate)) {
            if (w.getStatus() == WorkOrderStatus.CANCELLED || w.getTotalCost() == null) continue;
            String a = w.getAsset().getId();
            manutencao.merge(a, w.getTotalCost(), BigDecimal::add);
            mTotal = mTotal.add(w.getTotalCost());
            if (w.getAsset().getLocation() != null) filial.put(a, w.getAsset().getLocation().getId());
        }
        for (FuelRecord f : fuelRecords.forOrgBetween(orgId, de, ate)) {
            if (f.getTotalCost() == null) continue;
            String a = f.getAsset().getId();
            combustivel.merge(a, f.getTotalCost(), BigDecimal::add);
            cTotal = cTotal.add(f.getTotalCost());
            if (f.getAsset().getLocation() != null) filial.put(a, f.getAsset().getLocation().getId());
        }
        return new Reais(manutencao, combustivel, filial, mTotal, cTotal);
    }

    private static boolean mesmoAlvo(Budget a, Budget b) {
        return switch (a.getScope()) {
            case ASSET -> a.getAsset() != null && b.getAsset() != null && a.getAsset().getId().equals(b.getAsset().getId());
            case LOCATION -> a.getLocation() != null && b.getLocation() != null
                    && a.getLocation().getId().equals(b.getLocation().getId());
            case ORG -> true;
        };
    }

    private static int percentagem(BigDecimal real, BigDecimal previsto) {
        if (previsto == null || previsto.signum() <= 0) return 0;
        return real.multiply(BigDecimal.valueOf(100)).divide(previsto, 0, RoundingMode.HALF_UP).intValue();
    }

    private static String alvo(Budget b) {
        return switch (b.getScope()) {
            case ASSET -> b.getAsset() != null ? b.getAsset().getTag() : "viatura";
            case LOCATION -> b.getLocation() != null ? b.getLocation().getName() : "filial";
            case ORG -> "toda a frota";
        };
    }

    private BudgetView vista(Budget b, BigDecimal real, int year) {
        int pct = percentagem(real, b.getAmount());
        // O ritmo esperado: se estamos a 50 % do ano, gastar 50 % é normal.
        LocalDate hoje = LocalDate.now(ZONA);
        int diaDoAno = hoje.getYear() == year ? hoje.getDayOfYear() : (hoje.getYear() > year ? 365 : 0);
        int pctAno = Math.min(100, diaDoAno * 100 / 365);
        String estado = pct >= 100 ? "EXCEEDED" : pct >= 80 ? "WARNING" : pct > pctAno + 10 ? "AHEAD" : "OK";
        return new BudgetView(b.getId(), b.getYear(), b.getScope().name(),
                b.getAsset() != null ? b.getAsset().getId() : null,
                b.getLocation() != null ? b.getLocation().getId() : null,
                alvo(b), b.getCategory().name(), b.getCategory().label(),
                b.getAmount(), b.getCurrency(), real.setScale(2, RoundingMode.HALF_UP),
                b.getAmount().subtract(real).setScale(2, RoundingMode.HALF_UP), pct, pctAno, estado, b.getNotes());
    }

    private Budget require(String orgId, String id) {
        return budgets.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Orçamento não encontrado."));
    }
}
