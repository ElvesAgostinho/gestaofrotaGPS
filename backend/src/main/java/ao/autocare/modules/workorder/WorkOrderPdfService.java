package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderExternalService;
import ao.autocare.domain.WorkOrderLabor;
import ao.autocare.domain.WorkOrderPart;
import ao.autocare.domain.WorkOrderTask;
import ao.autocare.domain.enums.Enums.WorkOrderExecution;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.WorkOrderRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * A ordem de manutenção em papel.
 *
 * <p>Este documento sai da oficina assinado, ou não serve de nada. Por isso
 * leva três assinaturas — quem executou, quem verificou e quem autorizou a
 * despesa — que são três responsabilidades diferentes e raramente da mesma
 * pessoa.
 *
 * <p>Quando quem pede não pode ver custos, o PDF sai <b>sem a secção de
 * valores</b> e di-lo no rodapé. Um documento que parece completo mas omite
 * números em silêncio é pior do que um que assume o que não mostra.
 */
@Service
public class WorkOrderPdfService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Luanda");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
    private static final DateTimeFormatter DATETIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);

    private final WorkOrderRepository workOrders;
    private final AssetMeterRepository meters;
    private final TemplateEngine templateEngine;
    private final ao.autocare.modules.org.Letterhead letterhead;

    public WorkOrderPdfService(
            WorkOrderRepository workOrders,
            AssetMeterRepository meters,
            TemplateEngine templateEngine,
            ao.autocare.modules.org.Letterhead letterhead) {
        this.workOrders = workOrders;
        this.meters = meters;
        this.templateEngine = templateEngine;
        this.letterhead = letterhead;
    }

    /** Modelo do documento. Tudo já formatado: o template não faz contas. */
    public record Doc(
            String number, String title, String statusLabel, String typeLabel,
            String priorityLabel, String executionLabel,
            String assetTag, String assetName, String plate, String model, String serial,
            String meter, String systemCode, String branch, String driver, String supplier,
            String openedAt, String dueAt, String startedAt, String completedAt,
            boolean requiresShutdown, String safetyNotes,
            boolean hasDiagnosis, String symptom, String diagnosis, String probableCause,
            String recommendedAction, String diagnosedBy, String diagnosedAt,
            List<Line> tasks, List<LaborLine> labor, List<PartLine> parts,
            List<ServiceLine> services,
            boolean showMoney, String laborCost, String partsCost, String externalCost,
            String warrantyRecovered, String totalCost, String currency,
            String approvedAmount, String approvedBy, String approvedAt,
            String downtimeHours, String downtimeCost,
            String resolution, String rootCause, String correctiveAction,
            String assignedTo, String verifiedBy) {}

    public record Line(String title, String system, boolean done) {}

    public record LaborLine(String who, String hours, String rate, String total) {}

    public record PartLine(String name, String quantity, String unitCost, String total) {}

    public record ServiceLine(String supplier, String description, String invoice, String cost) {}

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String workOrderId, boolean showMoney) {
        WorkOrder w = workOrders.findByIdAndOrganizationId(workOrderId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem não encontrada."));

        Context ctx = new Context(Locale.forLanguageTag("pt"));
        ctx.setVariable("w", build(w, showMoney));
        ctx.setVariable("org", w.getOrganization().getName());
        ctx.setVariable("timbre", letterhead.of(w.getOrganization()));
        ctx.setVariable("generatedAt", DATETIME.format(Instant.now()));

        String html = templateEngine.process("work-order", ctx);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o PDF da ordem de manutenção", e);
        }
    }

    private Doc build(WorkOrder w, boolean showMoney) {
        Asset a = w.getAsset();

        String medidor = meters.findByAssetId(a.getId()).stream()
                .filter(m -> m.isPrimary())
                .findFirst()
                .map(m -> money(m.getCurrentValue()) + " " + m.getUnit())
                .orElse(null);

        List<Line> tarefas = new ArrayList<>();
        for (WorkOrderTask t : w.getTasks()) {
            tarefas.add(new Line(t.getTitle(), t.getSystemName(), t.isDone()));
        }

        List<LaborLine> mao = new ArrayList<>();
        for (WorkOrderLabor l : w.getLabor()) {
            BigDecimal total = l.getHourlyRate() != null
                    ? l.getHourlyRate().multiply(l.getHours()) : null;
            mao.add(new LaborLine(
                    l.getTechnicianLabel(), money(l.getHours()),
                    money(l.getHourlyRate()), money(total)));
        }

        List<PartLine> pecas = new ArrayList<>();
        for (WorkOrderPart p : w.getParts()) {
            BigDecimal total = p.getUnitCost() != null
                    ? p.getUnitCost().multiply(p.getQuantity()) : null;
            pecas.add(new PartLine(
                    p.getPartName(), money(p.getQuantity()),
                    money(p.getUnitCost()), money(total)));
        }

        List<ServiceLine> servicos = new ArrayList<>();
        for (WorkOrderExternalService s : w.getServices()) {
            servicos.add(new ServiceLine(
                    s.getSupplier(), s.getDescription(), s.getInvoiceNumber(),
                    money(s.getCost())));
        }

        return new Doc(
                w.getNumber(), w.getTitle(), w.getStatus().label(), w.getType().label(),
                w.getPriority().name(),
                w.getExecution() == WorkOrderExecution.EXTERNAL
                        ? "Oficina externa" : "Equipa interna",
                a.getTag(), a.getName(), a.getPlate(),
                join(a.getManufacturer(), a.getModel()), a.getSerialNumber(),
                medidor, w.getSystemCode(),
                w.getBranch() != null ? w.getBranch().getName() : null,
                w.getDriver() != null ? w.getDriver().getName() : null,
                w.getSupplier() != null ? w.getSupplier().getName() : null,
                date(w.getOpenedAt()), date(w.getDueAt()),
                date(w.getStartedAt()), date(w.getCompletedAt()),
                w.isRequiresShutdown(), w.getSafetyNotes(),
                w.hasDiagnosis(), w.getSymptom(), w.getDiagnosis(),
                w.getProbableCause(), w.getRecommendedAction(),
                w.getDiagnosedByLabel(), date(w.getDiagnosedAt()),
                tarefas, mao, pecas, servicos,
                showMoney,
                money(w.getTotalLaborCost()), money(w.getTotalPartsCost()),
                money(w.getTotalExternalCost()), money(w.getWarrantyRecovered()),
                money(w.getTotalCost()), w.getCurrency(),
                money(w.getApprovedAmount()),
                w.getApprovedBy() != null ? w.getApprovedBy().getName() : null,
                date(w.getApprovedAt()),
                money(w.getDowntimeHours()), money(w.getDowntimeCost()),
                w.getResolution(), w.getRootCause(), w.getCorrectiveAction(),
                w.getAssignedToLabel(),
                w.getVerifiedBy() != null ? w.getVerifiedBy().getName() : null);
    }

    /** Formato angolano: ponto para milhares, vírgula para decimais. */
    private static String money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        DecimalFormatSymbols pt = new DecimalFormatSymbols(Locale.forLanguageTag("pt-PT"));
        pt.setGroupingSeparator('.');
        pt.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", pt)
                .format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String date(Instant at) {
        return at == null ? null : DATETIME.format(at);
    }

    private static String join(String a, String b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        return b == null ? a : a + " " + b;
    }
}
