package ao.autocare.modules.kpi;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetDocument;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.Driver;
import ao.autocare.domain.FuelAnomaly;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.enums.Enums.AnomalyStatus;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.DriverStatus;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.repo.AssetDocumentRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.FuelAnomalyRepository;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * «O que está mal hoje»: o painel abre com isto, não com gráficos. Cada
 * grupo é uma lista curta de coisas que exigem uma ação, cada linha leva ao
 * sítio onde se resolve. Quando não há nada, diz-se — e isso também é
 * informação.
 */
@Service
public class TodayService {

    /** Uma coisa a resolver: o que é, em que ativo, há quanto tempo, e o link. */
    public record Item(String assetId, String assetTag, String title, String detail, String link) {}

    public record Group(String key, String label, String severity, int count, List<Item> items, String link) {}

    public record TodayView(int total, List<Group> groups) {}

    private static final int MAX_POR_GRUPO = 6;
    private static final List<WorkOrderStatus> FECHADAS = List.of(
            WorkOrderStatus.DONE, WorkOrderStatus.VERIFIED, WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED, WorkOrderStatus.REJECTED);

    private final AssetRepository assets;
    private final AssetPlanTaskRepository planTasks;
    private final AssetDocumentRepository documents;
    private final FuelAnomalyRepository anomalies;
    private final GpsDeviceRepository devices;
    private final WorkOrderRepository workOrders;
    private final DriverRepository drivers;

    public TodayService(AssetRepository assets, AssetPlanTaskRepository planTasks,
            AssetDocumentRepository documents, FuelAnomalyRepository anomalies, GpsDeviceRepository devices,
            WorkOrderRepository workOrders, DriverRepository drivers) {
        this.assets = assets;
        this.planTasks = planTasks;
        this.documents = documents;
        this.anomalies = anomalies;
        this.devices = devices;
        this.workOrders = workOrders;
        this.drivers = drivers;
    }

    @Transactional(readOnly = true)
    public TodayView today(String orgId) {
        Instant agora = Instant.now();
        List<Group> grupos = new ArrayList<>();

        // 1. Viaturas paradas: a frota que não está a render.
        List<Item> paradas = new ArrayList<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (a.isArchived() || a.getStatus() != AssetStatus.DOWN) {
                continue;
            }
            paradas.add(new Item(a.getId(), a.getTag(), a.getName(), "Parada (avaria)", "/ativos/" + a.getId()));
        }
        grupo(grupos, "down", "Viaturas paradas", "CRITICAL", paradas, "/ativos?estado=DOWN");

        // 2. Manutenções vencidas.
        List<Item> vencidas = new ArrayList<>();
        for (AssetPlanTask t : planTasks.findByOrgAndStatuses(orgId, List.of(PlanTaskStatus.OVERDUE))) {
            Asset a = t.getAssetPlan().getAsset();
            String detalhe = t.getRemainingMeter() != null && t.getRemainingMeter().signum() < 0
                    ? "Passou " + t.getRemainingMeter().abs().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + " do limite"
                    : t.getRemainingDays() != null && t.getRemainingDays() < 0
                            ? "Venceu há " + (-t.getRemainingDays()) + " dia(s)" : "Vencida";
            vencidas.add(new Item(a.getId(), a.getTag(), t.getTitle(), detalhe, "/ativos/" + a.getId() + "?tab=plano"));
        }
        grupo(grupos, "overdue", "Manutenções vencidas", "CRITICAL", vencidas, "/planos");

        // 3. Ordens atrasadas e aprovações pendentes.
        List<Item> atrasadas = new ArrayList<>();
        List<Item> aprovacoes = new ArrayList<>();
        for (WorkOrder w : workOrders.findByOrganizationIdOrderByOpenedAtDesc(orgId, PageRequest.of(0, 500))) {
            if (FECHADAS.contains(w.getStatus())) {
                continue;
            }
            if (w.getStatus() == WorkOrderStatus.AWAITING_APPROVAL) {
                aprovacoes.add(new Item(w.getAsset().getId(), w.getAsset().getTag(), w.getNumber() + " · " + w.getTitle(),
                        "A aguardar aprovação", "/ordens/" + w.getId()));
            } else if (w.getDueAt() != null && w.getDueAt().isBefore(agora)) {
                long dias = Duration.between(w.getDueAt(), agora).toDays();
                atrasadas.add(new Item(w.getAsset().getId(), w.getAsset().getTag(), w.getNumber() + " · " + w.getTitle(),
                        dias == 0 ? "Prazo passou hoje" : "Prazo passou há " + dias + " dia(s)", "/ordens/" + w.getId()));
            }
        }
        grupo(grupos, "late_orders", "Ordens fora do prazo", "WARNING", atrasadas, "/ordens");
        grupo(grupos, "approvals", "Aprovações pendentes", "WARNING", aprovacoes, "/ordens?estado=AWAITING_APPROVAL");

        // 4. Documentos caducados ou a caducar em 30 dias.
        List<Item> docs = new ArrayList<>();
        for (AssetDocument d : documents.findExpiringUntil(orgId, agora.plus(30, ChronoUnit.DAYS))) {
            long dias = ChronoUnit.DAYS.between(agora, d.getExpiresAt());
            docs.add(new Item(d.getAsset().getId(), d.getAsset().getTag(),
                    d.getKind().label() + (d.getTitle() != null ? " · " + d.getTitle() : ""),
                    dias < 0 ? "Caducado há " + (-dias) + " dia(s)" : dias == 0 ? "Caduca hoje" : "Caduca em " + dias + " dia(s)",
                    "/documentos"));
        }
        grupo(grupos, "documents", "Documentos a caducar", docs.stream().anyMatch(i -> i.detail().startsWith("Caducado")) ? "CRITICAL" : "WARNING",
                docs, "/documentos");

        // 5. Motoristas com carta, cartão ou exame médico a caducar.
        List<Item> motoristas = new ArrayList<>();
        for (Driver d : drivers.findByOrganizationIdAndStatusOrderByNameAsc(orgId, DriverStatus.ACTIVE)) {
            for (String aviso : d.avisos()) {
                motoristas.add(new Item(null, null, d.getName(), aviso, "/motoristas"));
            }
        }
        grupo(grupos, "drivers", "Motoristas com documentos a caducar", "WARNING", motoristas, "/motoristas");

        // 6. Anomalias de combustível por analisar.
        List<Item> combustivel = new ArrayList<>();
        for (FuelAnomaly f : anomalies.findByOrganizationIdAndStatusOrderByOccurredAtDesc(orgId, AnomalyStatus.OPEN,
                PageRequest.of(0, 50))) {
            combustivel.add(new Item(f.getAsset() != null ? f.getAsset().getId() : null,
                    f.getAsset() != null ? f.getAsset().getTag() : null, f.getKind().label(),
                    "Por analisar", "/combustivel?tab=anomalias"));
        }
        grupo(grupos, "fuel", "Anomalias de combustível", "WARNING", combustivel, "/combustivel?tab=anomalias");

        // 7. Aparelhos GPS sem sinal.
        List<Item> semSinal = new ArrayList<>();
        for (GpsDevice g : devices.findByOrganizationIdOrderByCreatedAtDesc(orgId)) {
            var estado = g.currentStatus();
            if (estado != ao.autocare.domain.enums.Enums.GpsDeviceStatus.OFFLINE
                    && estado != ao.autocare.domain.enums.Enums.GpsDeviceStatus.NEVER_SEEN) {
                continue;
            }
            String detalhe = g.getLastSeenAt() == null ? "Nunca comunicou"
                    : "Sem sinal há " + humano(Duration.between(g.getLastSeenAt(), agora));
            semSinal.add(new Item(g.getAsset() != null ? g.getAsset().getId() : null,
                    g.getAsset() != null ? g.getAsset().getTag() : null,
                    g.getName() != null ? g.getName() : g.getExternalId(), detalhe, "/aparelhos-gps"));
        }
        grupo(grupos, "gps", "Aparelhos GPS sem sinal", "WARNING", semSinal, "/aparelhos-gps");

        int total = grupos.stream().mapToInt(Group::count).sum();
        return new TodayView(total, grupos);
    }

    private static void grupo(List<Group> grupos, String key, String label, String severity, List<Item> itens, String link) {
        if (itens.isEmpty()) {
            return;
        }
        grupos.add(new Group(key, label, severity, itens.size(),
                itens.size() > MAX_POR_GRUPO ? new ArrayList<>(itens.subList(0, MAX_POR_GRUPO)) : itens, link));
    }

    private static String humano(Duration d) {
        if (d.toDays() >= 1) {
            return d.toDays() + " dia(s)";
        }
        if (d.toHours() >= 1) {
            return d.toHours() + " h";
        }
        return Math.max(1, d.toMinutes()) + " min";
    }
}
