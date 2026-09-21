package ao.autocare.modules.kpi;

import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.enums.Enums.CriticalityLevel;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.modules.kpi.dto.KpiDtos.KpiReport;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.StockItemRepository;
import ao.autocare.repo.WorkOrderRepository;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Indicadores")
@SecurityRequirement(name = "bearerAuth")
@RequirePermission(Permission.FLEET_VIEW)
@RestController
public class DashboardController {

    private final AssetRepository assets;
    private final AssetCriticalityRepository criticalities;
    private final AssetPlanTaskRepository planTasks;
    private final WorkOrderRepository workOrders;
    private final StockItemRepository stockItems;
    private final KpiService kpiService;
    private final TodayService todayService;
    private final OrgContext orgContext;

    public DashboardController(
            AssetRepository assets,
            AssetCriticalityRepository criticalities,
            AssetPlanTaskRepository planTasks,
            WorkOrderRepository workOrders,
            StockItemRepository stockItems,
            KpiService kpiService,
            TodayService todayService,
            OrgContext orgContext) {
        this.assets = assets;
        this.criticalities = criticalities;
        this.planTasks = planTasks;
        this.workOrders = workOrders;
        this.stockItems = stockItems;
        this.kpiService = kpiService;
        this.todayService = todayService;
        this.orgContext = orgContext;
    }

    public record UpcomingTask(String assetPlanTaskId, String assetId, String title,
                               String systemName, String status,
                               java.math.BigDecimal remainingMeter, Integer remainingDays) {
        static UpcomingTask of(AssetPlanTask t) {
            return new UpcomingTask(t.getId(), t.getAssetPlan().getAsset().getId(), t.getTitle(),
                    t.getSystemName(), t.getStatus().name(), t.getRemainingMeter(), t.getRemainingDays());
        }
    }

    public record DashboardView(
            long assetsTotal,
            long assetsCritical,
            long assetsDown,
            long tasksOverdue,
            long tasksDueSoon,
            long workOrdersOpen,
            long lowStockParts,
            List<UpcomingTask> upcoming,
            KpiReport kpis,
            /** O que está mal hoje — o painel abre com isto. */
            TodayService.TodayView today) {}

    @Operation(summary = "Resumo executivo para o painel")
    @GetMapping("/api/v1/dashboard")
    @Transactional(readOnly = true)
    public DashboardView dashboard(@AuthenticationPrincipal AuthPrincipal principal) {
        String orgId = orgContext.requireOrganizationId(principal);

        var allAssets = assets.findByOrganizationId(orgId).stream()
                .filter(a -> !a.isArchived()).toList();
        long critical = allAssets.stream()
                .filter(a -> criticalities.findByAssetId(a.getId())
                        .map(c -> c.getOverall() == CriticalityLevel.CRITICAL).orElse(false))
                .count();
        long down = allAssets.stream()
                .filter(a -> a.getStatus() == ao.autocare.domain.enums.Enums.AssetStatus.DOWN
                        || a.getStatus() == ao.autocare.domain.enums.Enums.AssetStatus.MAINTENANCE)
                .count();

        var overdue = planTasks.findByOrgAndStatuses(orgId, List.of(PlanTaskStatus.OVERDUE));
        var dueSoon = planTasks.findByOrgAndStatuses(orgId, List.of(PlanTaskStatus.DUE_SOON));

        List<UpcomingTask> upcoming = planTasks
                .findByOrgAndStatuses(orgId, List.of(PlanTaskStatus.OVERDUE, PlanTaskStatus.DUE_SOON))
                .stream().limit(8).map(UpcomingTask::of).toList();

        long open = workOrders.findByOrganizationIdAndStatusIn(orgId, List.of(
                WorkOrderStatus.OPEN, WorkOrderStatus.PLANNED, WorkOrderStatus.IN_PROGRESS)).size();

        long lowStock = stockItems.findLowStock(orgId).stream()
                .map(s -> s.getPart().getId()).distinct().count();

        KpiReport kpis = kpiService.report(orgId, null, null, null);

        return new DashboardView(
                allAssets.size(), critical, down,
                overdue.size(), dueSoon.size(), open, lowStock,
                upcoming, kpis, todayService.today(orgId));
    }
}
