package ao.autocare.modules.mobile;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.WorkOrderAttachmentKind;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.modules.mobile.MobileDtos.AssetPick;
import ao.autocare.modules.mobile.MobileDtos.HomeView;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.workorder.WorkOrderAttachmentService;
import ao.autocare.modules.workorder.WorkOrderService;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.CreateWorkOrderRequest;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.FailureInput;
import ao.autocare.modules.workorder.dto.WorkOrderDtos.WorkOrderView;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverAssignmentRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * O que o telemóvel precisa e o resto do sistema não tem: um ecrã inicial
 * pequeno (a minha viatura, as minhas ordens) e «comunicar avaria» num só
 * passo — viatura, o que se passa, fotografia — que abre uma ordem corretiva
 * e avisa quem gere. O motorista não precisa de saber o que é uma ordem.
 */
@Service
public class MobileService {

    /** Estados em que uma ordem ainda tem trabalho pela frente. */
    private static final List<WorkOrderStatus> ABERTAS = List.of(
            WorkOrderStatus.OPEN, WorkOrderStatus.PLANNED, WorkOrderStatus.DIAGNOSIS, WorkOrderStatus.QUOTING,
            WorkOrderStatus.AWAITING_APPROVAL, WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.TESTING);

    private final AssetRepository assets;
    private final DriverRepository drivers;
    private final DriverAssignmentRepository assignments;
    private final WorkOrderRepository workOrders;
    private final UserRepository users;
    private final WorkOrderService workOrderService;
    private final WorkOrderAttachmentService attachments;
    private final NotificationService notifications;
    private final ao.autocare.modules.meter.MeterService meters;

    public MobileService(AssetRepository assets, DriverRepository drivers,
            DriverAssignmentRepository assignments, WorkOrderRepository workOrders, UserRepository users,
            WorkOrderService workOrderService, WorkOrderAttachmentService attachments,
            NotificationService notifications, ao.autocare.modules.meter.MeterService meters) {
        this.assets = assets;
        this.drivers = drivers;
        this.assignments = assignments;
        this.workOrders = workOrders;
        this.users = users;
        this.workOrderService = workOrderService;
        this.attachments = attachments;
        this.notifications = notifications;
        this.meters = meters;
    }

    @Transactional(readOnly = true)
    public HomeView home(String orgId, String userId) {
        // As viaturas atribuídas a esta pessoa (quando é motorista) vêm primeiro.
        Set<String> minhas = new HashSet<>();
        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (d.getUser() != null && userId.equals(d.getUser().getId())) {
                for (DriverAssignment a : assignments.openForDriver(d.getId())) {
                    minhas.add(a.getAsset().getId());
                }
            }
        }
        List<AssetPick> lista = new ArrayList<>();
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (a.isArchived() || a.getStatus() == AssetStatus.RETIRED) {
                continue;
            }
            lista.add(new AssetPick(a.getId(), a.getTag(), a.getName(), a.getPlate(),
                    a.getStatus().name(), minhas.contains(a.getId())));
        }
        lista.sort(Comparator.comparing((AssetPick p) -> !p.mine()).thenComparing(AssetPick::tag,
                String.CASE_INSENSITIVE_ORDER));

        long abertas = workOrders.countByOrganizationIdAndAssignedToIdAndStatusIn(orgId, userId, ABERTAS);
        User u = users.findById(userId).orElse(null);
        return new HomeView(u != null ? u.getName() : "", lista, abertas, !minhas.isEmpty());
    }

    /**
     * Comunicar uma avaria: abre uma ordem corretiva «Alta» com a descrição e
     * a fotografia, e avisa os gestores (também no telemóvel, que é grave).
     */
    @Transactional
    public WorkOrderView reportBreakdown(String orgId, String userId, String assetId, String title,
            String description, BigDecimal meterValue, boolean stopped, MultipartFile photo) {
        if (assetId == null || assetId.isBlank()) {
            throw ApiException.badRequest("Indique a viatura.");
        }
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("Diga em poucas palavras o que se passa.");
        }
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Viatura não encontrada."));
        User quem = users.findById(userId).orElseThrow(() -> ApiException.notFound("Utilizador não encontrado."));

        String titulo = title.trim().length() > 200 ? title.trim().substring(0, 200) : title.trim();
        String detalhe = "Comunicado do telemóvel por " + quem.getName()
                + (description != null && !description.isBlank() ? ".\n\n" + description.trim() : ".");
        CreateWorkOrderRequest req = new CreateWorkOrderRequest(
                a.getId(), WorkOrderType.CORRECTIVE, titulo, detalhe,
                stopped ? WorkOrderPriority.URGENT : WorkOrderPriority.HIGH,
                null, null, null, null,
                new FailureInput(titulo, null, null, stopped),
                null, null, null, null, null, null, null, null, null, null, null);
        WorkOrderView ordem = workOrderService.create(orgId, userId, req);

        if (photo != null && !photo.isEmpty()) {
            attachments.upload(orgId, userId, ordem.id(), photo, WorkOrderAttachmentKind.BEFORE,
                    "Fotografia da avaria, tirada no telemóvel");
        }
        if (meterValue != null) {
            // A leitura do painel no momento da avaria é uma leitura como outra qualquer.
            meters.recordFromWorkOrder(a, meterValue, java.time.Instant.now(), userId, ordem.number());
        }

        notifications.notifyManagers(NotificationService.Draft.of(
                orgId, AlertCategory.WORK_ORDER,
                stopped ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                (stopped ? "Viatura parada: " : "Avaria comunicada: ") + a.getTag(),
                quem.getName() + " comunicou pelo telemóvel: " + titulo
                        + (description != null && !description.isBlank() ? " — " + description.trim() : "")
                        + ". Ordem " + ordem.number() + ".",
                "breakdown_report", ordem.id(), "/ordens/" + ordem.id()).forAsset(a));
        return workOrderService.get(orgId, ordem.id());
    }
}
