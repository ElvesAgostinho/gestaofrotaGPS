package ao.autocare.modules.asset;

import static ao.autocare.modules.org.PdfRenderer.data;
import static ao.autocare.modules.org.PdfRenderer.numero;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.WorkOrder;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPhotoRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.WorkOrderRepository;
import ao.autocare.storage.StorageProvider;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A ficha de equipamento em papel: identificação, fotografia, medidor e as
 * últimas ordens. É o que se põe na pasta da máquina e o que se entrega a
 * um comprador quando ela é vendida.
 */
@Service
public class AssetSheetPdfService {

    public record Doc(
            String tag, String name, String type, String status, String manufacturer,
            String model, String modelYear, String serialNumber, String plate, String location,
            String responsible, String criticality, String meter, String acquisitionDate,
            boolean showMoney, String acquisitionValue, String downtimeCostPerHour,
            String notes, String photoDataUri, List<Order> orders,
            List<PlanLine> plan, List<TyreLine> tyres, List<DocLine> documents) {}

    public record Order(String number, String title, String type, String openedAt,
                        String completedAt, String status) {}

    /** Uma tarefa do plano: o intervalo, a última vez e a próxima. */
    public record PlanLine(String title, String system, String interval, String lastDone, String nextDue,
                           String remaining, String status, boolean overdue, boolean dueSoon) {}

    public record TyreLine(String position, String tyre, String status, String installed, String run,
                           String tread, String alert) {}

    public record DocLine(String kind, String title, String reference, String expires, String state) {}

    /** Os rótulos em português, como no ecrã. Os enums não os têm. */
    private static final java.util.Map<String, String> ESTADO = java.util.Map.of(
            "OPERATIONAL", "Operacional", "MAINTENANCE", "Em manutenção", "DOWN", "Parado",
            "STANDBY", "Em espera", "RETIRED", "Abatido");
    private static final java.util.Map<String, String> CRITICIDADE = java.util.Map.of(
            "LOW", "Baixa", "MEDIUM", "Média", "HIGH", "Alta", "CRITICAL", "Crítica");

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final AssetPhotoRepository photos;
    private final WorkOrderRepository workOrders;
    private final ao.autocare.repo.AssetCriticalityRepository criticalities;
    private final StorageProvider storage;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;
    private final ao.autocare.repo.AssetPlanTaskRepository planTasks;
    private final ao.autocare.repo.TyreRepository tyres;
    private final ao.autocare.repo.AssetDocumentRepository documents;

    public AssetSheetPdfService(AssetRepository assets, AssetMeterRepository meters,
            AssetPhotoRepository photos, WorkOrderRepository workOrders,
            ao.autocare.repo.AssetCriticalityRepository criticalities, StorageProvider storage,
            Letterhead letterhead, PdfRenderer renderer,
            ao.autocare.repo.AssetPlanTaskRepository planTasks, ao.autocare.repo.TyreRepository tyres,
            ao.autocare.repo.AssetDocumentRepository documents) {
        this.planTasks = planTasks;
        this.tyres = tyres;
        this.documents = documents;
        this.criticalities = criticalities;
        this.assets = assets;
        this.meters = meters;
        this.photos = photos;
        this.workOrders = workOrders;
        this.storage = storage;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String assetId, boolean showMoney) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));

        AssetMeter principal = meters.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
        String unidade = principal != null ? principal.getUnit() : "";
        String medidor = principal != null ? numero(principal.getCurrentValue(), 0) + " " + unidade : null;

        // O plano e os intervalos: é isto que se consulta na oficina antes de decidir o que fazer.
        List<PlanLine> plano = new ArrayList<>();
        for (ao.autocare.domain.AssetPlanTask t : planTasks.findForAsset(orgId, a.getId())) {
            StringBuilder intervalo = new StringBuilder();
            if (t.getTask() != null) {
                for (ao.autocare.domain.PlanTaskTrigger g : t.getTask().getTriggers()) {
                    if (intervalo.length() > 0) intervalo.append(" ou ");
                    intervalo.append("cada ").append(numero(g.getIntervalValue(), 0)).append(' ')
                            .append(g.getTriggerType() == ao.autocare.domain.enums.Enums.PlanTriggerType.METER_INTERVAL
                                    ? (g.getMeterKind() == ao.autocare.domain.enums.Enums.MeterKind.HOURMETER ? "h" : "km")
                                    : "dias");
                }
            }
            String ultima = t.getLastDoneAt() != null
                    ? data(t.getLastDoneAt()) + (t.getLastDoneMeter() != null ? " · " + numero(t.getLastDoneMeter(), 0) + " " + unidade : "")
                    : "nunca";
            String proxima = t.getNextDueMeter() != null ? numero(t.getNextDueMeter(), 0) + " " + unidade
                    : t.getNextDueAt() != null ? data(t.getNextDueAt()) : "—";
            String falta = t.getRemainingMeter() != null
                    ? (t.getRemainingMeter().signum() < 0 ? "passou " + numero(t.getRemainingMeter().abs(), 0) : "faltam " + numero(t.getRemainingMeter(), 0)) + " " + unidade
                    : t.getRemainingDays() != null
                            ? (t.getRemainingDays() < 0 ? "passou " + (-t.getRemainingDays()) : "faltam " + t.getRemainingDays()) + " dia(s)"
                            : "";
            String estado = switch (t.getStatus()) {
                case OVERDUE -> "VENCIDA";
                case DUE_SOON -> "A vencer";
                default -> "Em dia";
            };
            plano.add(new PlanLine(t.getTitle(), t.getSystemName(), intervalo.toString(), ultima, proxima, falta, estado,
                    t.getStatus() == ao.autocare.domain.enums.Enums.PlanTaskStatus.OVERDUE,
                    t.getStatus() == ao.autocare.domain.enums.Enums.PlanTaskStatus.DUE_SOON));
        }

        List<TyreLine> pneus = new ArrayList<>();
        java.math.BigDecimal contadorAtual = principal != null ? principal.getCurrentValue() : null;
        for (ao.autocare.domain.Tyre t : tyres.findByAssetIdOrderByStatusAscPositionAsc(a.getId())) {
            if (t.getStatus() != ao.autocare.domain.Tyre.Status.INSTALLED) continue;
            String nome = java.util.stream.Stream.of(t.getBrand(), t.getModel(), t.getSize())
                    .filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(" "));
            pneus.add(new TyreLine(t.getPosition(), nome.isBlank() ? (t.getSerialNumber() != null ? t.getSerialNumber() : "—") : nome,
                    "Montado", data(t.getInstalledAt()) + (t.getInstalledMeter() != null ? " · " + numero(t.getInstalledMeter(), 0) + " " + unidade : ""),
                    t.distanceRun(contadorAtual) != null ? numero(t.distanceRun(contadorAtual), 0) + " " + unidade : "—",
                    t.getLastTreadMm() != null ? numero(t.getLastTreadMm(), 1) + " mm" : "—",
                    t.alerta()));
        }

        List<DocLine> docs = new ArrayList<>();
        java.time.Instant agora = java.time.Instant.now();
        for (ao.autocare.domain.AssetDocument d : documents.findByAssetIdOrderByKindAscTitleAsc(a.getId())) {
            String estado;
            if (d.getExpiresAt() == null) estado = "Não caduca";
            else if (d.getExpiresAt().isBefore(agora)) estado = "CADUCADO";
            else if (d.getExpiresAt().isBefore(agora.plus(30, java.time.temporal.ChronoUnit.DAYS))) estado = "A caducar";
            else estado = "Válido";
            docs.add(new DocLine(d.getKind().label(), d.getTitle(), d.getReference(), data(d.getExpiresAt()), estado));
        }

        List<Order> ordens = new ArrayList<>();
        for (WorkOrder w : workOrders.findByAssetIdOrderByOpenedAtDesc(a.getId(), PageRequest.of(0, 12))) {
            ordens.add(new Order(w.getNumber(), w.getTitle(), w.getType().label(),
                    data(w.getOpenedAt()), data(w.getCompletedAt()), w.getStatus().label()));
        }

        Doc doc = new Doc(
                a.getTag(), a.getName(),
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getStatus() != null ? ESTADO.getOrDefault(a.getStatus().name(), a.getStatus().name()) : null,
                a.getManufacturer(), a.getModel(),
                a.getModelYear() == null ? null : String.valueOf(a.getModelYear()),
                a.getSerialNumber(), a.getPlate(),
                a.getLocation() != null ? a.getLocation().getName() : null,
                a.getResponsibleLabel(),
                criticalities.findByAssetId(a.getId())
                        .map(c -> c.getOverall() != null
                                ? CRITICIDADE.getOrDefault(c.getOverall().name(), c.getOverall().name())
                                : null)
                        .orElse(null),
                medidor, data(a.getAcquisitionDate()),
                showMoney,
                showMoney ? numero(a.getAcquisitionValue(), 2) : null,
                showMoney ? numero(a.getDowntimeCostPerHour(), 2) : null,
                a.getNotes(), foto(a), ordens, plano, pneus, docs);

        return renderer.render("asset-sheet", letterhead.of(a.getOrganization()), "a", doc);
    }

    /** A fotografia principal, embutida. Sem fotografia, sem caixa. */
    private String foto(Asset a) {
        return photos.findByAssetIdOrderByPrimaryDescSortOrderAscCreatedAtAsc(a.getId()).stream()
                .findFirst()
                .map(p -> {
                    try {
                        byte[] bytes = storage.load(p.getFile().getStorageKey());
                        return "data:" + p.getFile().getContentType() + ";base64,"
                                + Base64.getEncoder().encodeToString(bytes);
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}
