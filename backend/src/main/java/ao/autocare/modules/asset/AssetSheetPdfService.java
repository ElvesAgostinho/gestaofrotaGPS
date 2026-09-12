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
            String notes, String photoDataUri, List<Order> orders) {}

    public record Order(String number, String title, String type, String openedAt,
                        String completedAt, String status) {}

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

    public AssetSheetPdfService(AssetRepository assets, AssetMeterRepository meters,
            AssetPhotoRepository photos, WorkOrderRepository workOrders,
            ao.autocare.repo.AssetCriticalityRepository criticalities, StorageProvider storage,
            Letterhead letterhead, PdfRenderer renderer) {
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

        String medidor = meters.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst()
                .map(m -> numero(m.getCurrentValue(), 0) + " " + m.getUnit())
                .orElse(null);

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
                a.getNotes(), foto(a), ordens);

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
