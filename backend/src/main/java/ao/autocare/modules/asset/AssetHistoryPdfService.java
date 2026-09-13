package ao.autocare.modules.asset;

import static ao.autocare.modules.org.PdfRenderer.data;
import static ao.autocare.modules.org.PdfRenderer.numero;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.PlanTaskCompletion;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderExternalService;
import ao.autocare.domain.WorkOrderPart;
import ao.autocare.domain.WorkOrderTask;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.PlanTaskCompletionRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O histórico de manutenção de um equipamento, em papel: a «pasta da
 * viatura».
 *
 * <p>Nas oficinas guarda-se, por cada máquina, uma pasta com todas as ordens
 * feitas — é o que se consulta quando ela volta com a mesma avaria, e o que
 * se mostra a um comprador ou a um auditor. Este documento é essa pasta de
 * uma vez: todas as ordens (o que foi feito, quando, aos quantos km, por
 * quem, com que peças) e as tarefas de plano executadas. Os valores só saem
 * para quem os pode ver.
 */
@Service
public class AssetHistoryPdfService {

    public record Doc(
            String tag, String name, String type, String plate, String serialNumber,
            String manufacturer, String model, String meter, String location,
            boolean showMoney, int orderCount, String totalCost, String totalDowntime,
            List<Order> orders, List<Completion> completions) {}

    public record Order(
            String number, String title, String type, String status, String openedAt,
            String completedAt, String meterAt, String closingMeter, String assignedTo,
            String description, String resolution, String rootCause,
            String laborHours, String downtimeHours, String totalCost,
            List<String> tasks, List<Line> parts, List<Line> services) {}

    public record Line(String description, String quantity, String cost) {}

    public record Completion(String completedAt, String title, String meterValue,
                             String performedBy, String notes) {}

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final WorkOrderRepository workOrders;
    private final PlanTaskCompletionRepository completions;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public AssetHistoryPdfService(AssetRepository assets, AssetMeterRepository meters,
            WorkOrderRepository workOrders, PlanTaskCompletionRepository completions,
            Letterhead letterhead, PdfRenderer renderer) {
        this.assets = assets;
        this.meters = meters;
        this.workOrders = workOrders;
        this.completions = completions;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String assetId, boolean showMoney) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));

        AssetMeter primary = meters.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
        String unidade = primary != null ? primary.getUnit() : "";
        String medidor = primary != null ? numero(primary.getCurrentValue(), 0) + " " + unidade : null;

        List<Order> ordens = new ArrayList<>();
        BigDecimal custoTotal = BigDecimal.ZERO;
        BigDecimal paragemTotal = BigDecimal.ZERO;
        // Todas as ordens, da mais antiga para a mais recente: é assim que se lê uma pasta.
        List<WorkOrder> todas = new ArrayList<>(
                workOrders.findByAssetIdOrderByOpenedAtDesc(a.getId(), PageRequest.of(0, 2000)).getContent());
        java.util.Collections.reverse(todas);
        for (WorkOrder w : todas) {
            if (w.getStatus() == WorkOrderStatus.CANCELLED) {
                continue; // uma ordem cancelada não é manutenção feita
            }
            if (w.getTotalCost() != null) {
                custoTotal = custoTotal.add(w.getTotalCost());
            }
            if (w.getDowntimeHours() != null) {
                paragemTotal = paragemTotal.add(w.getDowntimeHours());
            }
            List<String> tarefas = new ArrayList<>();
            for (WorkOrderTask t : w.getTasks()) {
                tarefas.add((t.isDone() ? "✓ " : "○ ") + t.getTitle()
                        + (t.getSystemName() != null ? " (" + t.getSystemName() + ")" : ""));
            }
            List<Line> pecas = new ArrayList<>();
            for (WorkOrderPart p : w.getParts()) {
                String nome = p.getPart() != null ? p.getPart().getName() : p.getPartName();
                BigDecimal custo = p.getUnitCost() != null && p.getQuantity() != null
                        ? p.getUnitCost().multiply(p.getQuantity()) : null;
                pecas.add(new Line(nome, numero(p.getQuantity(), 0), showMoney ? numero(custo, 2) : null));
            }
            List<Line> servicos = new ArrayList<>();
            for (WorkOrderExternalService s : w.getServices()) {
                servicos.add(new Line(
                        (s.getSupplier() != null ? s.getSupplier() + " — " : "") + s.getDescription(),
                        null, showMoney ? numero(s.getCost(), 2) : null));
            }
            ordens.add(new Order(
                    w.getNumber(), w.getTitle(), w.getType().label(), w.getStatus().label(),
                    data(w.getOpenedAt()), data(w.getCompletedAt()),
                    w.getMeterValue() != null ? numero(w.getMeterValue(), 0) + " " + unidade : null,
                    w.getClosingMeterValue() != null ? numero(w.getClosingMeterValue(), 0) + " " + unidade : null,
                    w.getAssignedTo() != null ? w.getAssignedTo().getName() : w.getAssignedToLabel(),
                    w.getDescription(), w.getResolution(), w.getRootCause(),
                    numero(w.getTotalLaborHours(), 1), numero(w.getDowntimeHours(), 1),
                    showMoney ? numero(w.getTotalCost(), 2) : null,
                    tarefas, pecas, servicos));
        }

        List<Completion> execucoes = new ArrayList<>();
        for (PlanTaskCompletion c : completions.findByAssetIdOrderByCompletedAtDesc(
                a.getId(), PageRequest.of(0, 500))) {
            execucoes.add(new Completion(data(c.getCompletedAt()), c.getTitle(),
                    c.getMeterValue() != null ? numero(c.getMeterValue(), 0) + " " + unidade : null,
                    c.getPerformedByLabel(), c.getNotes()));
        }

        Doc doc = new Doc(
                a.getTag(), a.getName(),
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getPlate(), a.getSerialNumber(), a.getManufacturer(), a.getModel(),
                medidor, a.getLocation() != null ? a.getLocation().getName() : null,
                showMoney, ordens.size(),
                showMoney ? numero(custoTotal, 2) : null,
                numero(paragemTotal, 1),
                ordens, execucoes);

        return renderer.render("asset-history", letterhead.of(a.getOrganization()), "h", doc);
    }
}
