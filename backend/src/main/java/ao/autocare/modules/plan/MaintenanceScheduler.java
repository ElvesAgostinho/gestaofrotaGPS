package ao.autocare.modules.plan;

import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.modules.document.DocumentService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.predictive.PredictiveService;
import ao.autocare.modules.workorder.WorkOrderService;
import ao.autocare.repo.AssetPlanTaskRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém os planos de manutenção atualizados e gera Ordens de Manutenção
 * preventivas quando há tarefas vencidas. Desligável com
 * {@code autocare.scheduler.enabled=false} (desligado nos testes).
 */
@Component
@ConditionalOnProperty(name = "autocare.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final AssetPlanTaskRepository planTasks;
    private final AssetPlanService assetPlans;
    private final WorkOrderService workOrders;
    private final NotificationService notifications;
    private final PredictiveService predictive;
    private final ao.autocare.modules.predictive.FailureForecastService forecasts;
    private final ao.autocare.modules.tyre.TyreService tyreService;
    private final DocumentService documents;
    private final ao.autocare.modules.fleet.DriverRecordsService driverRecords;
    private final ao.autocare.modules.budget.BudgetService budgets;

    public MaintenanceScheduler(
            AssetPlanTaskRepository planTasks,
            AssetPlanService assetPlans,
            WorkOrderService workOrders,
            NotificationService notifications,
            PredictiveService predictive,
            DocumentService documents,
            ao.autocare.modules.fleet.DriverRecordsService driverRecords,
            ao.autocare.modules.budget.BudgetService budgets,
            ao.autocare.modules.predictive.FailureForecastService forecasts,
            ao.autocare.modules.tyre.TyreService tyreService) {
        this.planTasks = planTasks;
        this.assetPlans = assetPlans;
        this.workOrders = workOrders;
        this.notifications = notifications;
        this.predictive = predictive;
        this.forecasts = forecasts;
        this.tyreService = tyreService;
        this.documents = documents;
        this.driverRecords = driverRecords;
        this.budgets = budgets;
    }

    /** De hora a hora: recalcula o vencimento de todas as tarefas de plano ativas. */
    @Scheduled(fixedDelayString = "${autocare.scheduler.recompute-ms:3600000}", initialDelay = 120000)
    public void recomputeAllPlans() {
        List<String> assetIds = planTasks.findDistinctActiveAssetIds();
        int done = 0;
        for (String assetId : assetIds) {
            try {
                assetPlans.recomputeForAsset(assetId);
                done++;
            } catch (Exception e) {
                log.warn("Falha ao recalcular plano do ativo {}: {}", assetId, e.toString());
            }
        }
        if (done > 0) {
            log.debug("Planos recalculados para {} ativos", done);
        }
    }

    /** Todas as manhãs às 06:00: gera OM preventivas para ativos com tarefas vencidas. */
    @Scheduled(cron = "${autocare.scheduler.generate-cron:0 0 6 * * *}")
    public void generatePreventiveWorkOrders() {
        List<String> assetIds = planTasks.findAssetIdsWithTaskStatus(PlanTaskStatus.OVERDUE);
        int created = 0;
        for (String assetId : assetIds) {
            try {
                if (workOrders.autoGeneratePreventive(assetId)) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("Falha ao gerar OM preventiva para o ativo {}: {}", assetId, e.toString());
            }
        }
        if (created > 0) {
            log.info("{} Ordens de Manutenção preventivas geradas automaticamente", created);
        }
    }

    /**
     * Avisa quem gere a manutenção das tarefas vencidas.
     *
     * <p>Corre de hora a hora, mas cada tarefa só avisa uma vez: a origem do
     * aviso é a própria tarefa, e o aviso é apagado quando ela deixa de estar
     * vencida — ou seja, quando alguém a executa. Sem isso, o mesmo problema
     * enchia a caixa de avisos até ninguém a ler.
     */
    @Scheduled(fixedDelayString = "${autocare.scheduler.notify-ms:3600000}", initialDelay = 180000)
    public void notifyOverdueTasks() {
        for (AssetPlanTask task : planTasks.findAllWithStatus(PlanTaskStatus.OVERDUE)) {
            try {
                Asset asset = task.getAssetPlan().getAsset();
                notifications.notifyManagers(NotificationService.Draft.of(
                                task.getAssetPlan().getOrganization().getId(),
                                ao.autocare.domain.enums.Enums.AlertCategory.MAINTENANCE,
                                ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                                "Manutenção vencida — " + asset.getTag(),
                                task.getTitle()
                                        + (task.getSystemName() != null
                                                ? " (" + task.getSystemName() + ")" : ""),
                                "plan_task_overdue", task.getId(),
                                "/ativos/" + asset.getId() + "/plano")
                        .forAsset(asset));
            } catch (Exception e) {
                log.warn("Falha ao avisar da tarefa {}: {}", task.getId(), e.toString());
            }
        }
        // A vencer: avisa-se uma vez, antes — é para isso que serve um plano.
        for (AssetPlanTask task : planTasks.findAllWithStatus(PlanTaskStatus.DUE_SOON)) {
            try {
                Asset asset = task.getAssetPlan().getAsset();
                String falta = task.getRemainingMeter() != null
                        ? "faltam " + task.getRemainingMeter().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + " no contador"
                        : task.getRemainingDays() != null ? "faltam " + task.getRemainingDays() + " dia(s)" : "a vencer";
                notifications.notifyManagers(NotificationService.Draft.of(
                                task.getAssetPlan().getOrganization().getId(),
                                ao.autocare.domain.enums.Enums.AlertCategory.MAINTENANCE,
                                ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                                "Manutenção a vencer — " + asset.getTag(),
                                task.getTitle() + (task.getSystemName() != null ? " (" + task.getSystemName() + ")" : "")
                                        + ": " + falta + ". Marque a oficina antes de passar o limite.",
                                "plan_task_due_soon", task.getId(),
                                "/ativos/" + asset.getId() + "?tab=plano")
                        .forAsset(asset));
            } catch (Exception e) {
                log.warn("Falha ao avisar da tarefa a vencer {}: {}", task.getId(), e.toString());
            }
            notifications.resolve("plan_task_overdue", task.getId());
        }
        // Tarefas que já não estão vencidas nem a vencer deixam de ter aviso pendente.
        for (AssetPlanTask task : planTasks.findAllWithStatus(PlanTaskStatus.OK)) {
            notifications.resolve("plan_task_overdue", task.getId());
            notifications.resolve("plan_task_due_soon", task.getId());
        }
        for (AssetPlanTask task : planTasks.findAllWithStatus(PlanTaskStatus.OVERDUE)) {
            notifications.resolve("plan_task_due_soon", task.getId());
        }
    }

    /**
     * Avisa das análises preditivas vencidas. Corre uma vez por dia porque a
     * agenda preditiva é de calendário e mede-se em meses — verificar de hora a
     * hora não acrescentaria nada.
     */
    /**
     * Avisa dos documentos a caducar — seguros, inspeções, licenças. Uma vez por
     * dia: a validade mede-se em dias, não em horas.
     */
    @Scheduled(cron = "${autocare.scheduler.documents-cron:0 45 6 * * *}")
    public void notifyExpiringDocuments() {
        try {
            int sent = documents.notifyExpiring();
            if (sent > 0) {
                log.info("{} aviso(s) de documento a caducar", sent);
            }
            // As cartas, cartões e exames médicos dos motoristas caducam da mesma maneira.
            int motoristas = driverRecords.notifyExpiring();
            if (motoristas > 0) {
                log.info("{} aviso(s) de documentos de motorista a caducar", motoristas);
            }
            int orcamentos = budgets.notifyOverruns();
            if (orcamentos > 0) {
                log.info("{} aviso(s) de orçamento a 80 %/100 %", orcamentos);
            }
        } catch (Exception e) {
            log.warn("Falha ao avisar de documentos: {}", e.toString());
        }
    }

    @Scheduled(cron = "${autocare.scheduler.predictive-cron:0 30 6 * * *}")
    public void notifyDuePredictive() {
        try {
            int sent = predictive.notifyDue();
            if (sent > 0) {
                log.info("{} aviso(s) de análise preditiva vencida", sent);
            }
            int pneus = tyreService.notifyAlerts();
            if (pneus > 0) {
                log.info("{} aviso(s) de pneus", pneus);
            }
            int previstas = forecasts.notifyImminent();
            if (previstas > 0) {
                log.info("{} aviso(s) de avaria provável a 14 dias", previstas);
            }
        } catch (Exception e) {
            log.warn("Falha ao avisar de análises preditivas: {}", e.toString());
        }
    }
}
