package ao.autocare.modules.checklist;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ItemView;
import ao.autocare.modules.org.DocumentSealService;
import ao.autocare.modules.org.Letterhead;
import ao.autocare.modules.org.PdfRenderer;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A ficha do posto: a folha que fica na cabina da máquina.
 *
 * <p>É o documento que toda a gente conhece do manual do fabricante — a
 * inspeção diária a fazer antes do arranque, os pontos de lubrificação com o
 * intervalo, e as ferramentas e materiais que a tarefa exige — mas impresso
 * com o timbre da empresa e com os dados desta máquina em concreto.
 *
 * <p>Serve para ser plastificado e pendurado. O operador não vai abrir um
 * sistema às seis da manhã; vai olhar para a folha. O sistema existe para que
 * a folha esteja certa, para que a execução volte para dentro (pelo
 * telemóvel) e para que ninguém tenha de a escrever à mão.
 */
@Service
public class OperatorSheetPdfService {

    public record Linha(String texto, String verificacao, boolean critico) {}

    public record Lubrificacao(String titulo, List<String> accoes, List<String> materiais) {}

    public record Doc(
            String tag, String name, String type, String plate, String meter, String location,
            String inspectionTitle, String inspectionNote, Integer minutes, String inspectionSource,
            List<Linha> inspection,
            List<Lubrificacao> lubrication,
            List<String> planTasks) {}

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final AssetPlanTaskRepository planTasks;
    private final DailyInspectionService daily;
    private final Letterhead letterhead;
    private final PdfRenderer renderer;

    public OperatorSheetPdfService(AssetRepository assets, AssetMeterRepository meters,
            AssetPlanTaskRepository planTasks, DailyInspectionService daily,
            Letterhead letterhead, PdfRenderer renderer) {
        this.assets = assets;
        this.meters = meters;
        this.planTasks = planTasks;
        this.daily = daily;
        this.letterhead = letterhead;
        this.renderer = renderer;
    }

    /** Os rótulos de verificação, como no manual: verificar, inspecionar, testar. */
    private static String verificacao(String tipo) {
        return switch (tipo == null ? "VERIFY" : tipo) {
            case "INSPECT" -> "Inspecionar";
            case "TEST" -> "Testar";
            default -> "Verificar";
        };
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String assetId, DocumentSealService.Selo selo) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));

        AssetMeter principal = meters.findByAssetId(a.getId()).stream()
                .filter(AssetMeter::isPrimary).findFirst().orElse(null);
        String unidade = principal != null ? principal.getUnit() : "";

        DailyInspectionService.Ficha ficha = daily.forAsset(orgId, assetId);
        List<Linha> inspecao = new ArrayList<>();
        if (ficha != null) {
            for (ItemView i : ficha.items()) {
                inspecao.add(new Linha(i.text(), verificacao(i.verification()), i.critical()));
            }
        }

        // Lubrificação e tarefas do plano, agrupadas pelo intervalo — é assim que
        // o manual as organiza, e é assim que o mecânico as executa: de uma vez.
        java.util.Map<String, List<String>> porIntervalo = new java.util.LinkedHashMap<>();
        Set<String> materiais = new LinkedHashSet<>();
        List<String> todas = new ArrayList<>();
        for (var t : planTasks.findForAsset(orgId, a.getId())) {
            String intervalo = "Sem intervalo definido";
            if (t.getTask() != null && !t.getTask().getTriggers().isEmpty()) {
                var g = t.getTask().getTriggers().get(0);
                boolean horas = g.getMeterKind() == ao.autocare.domain.enums.Enums.MeterKind.HOURMETER;
                intervalo = g.getTriggerType() == ao.autocare.domain.enums.Enums.PlanTriggerType.METER_INTERVAL
                        ? "A cada " + PdfRenderer.numero(g.getIntervalValue(), 0) + (horas ? " horas" : " km")
                        : "A cada " + PdfRenderer.numero(g.getIntervalValue(), 0) + " dias";
            }
            porIntervalo.computeIfAbsent(intervalo, k -> new ArrayList<>()).add(t.getTitle());
            todas.add(t.getTitle() + " — " + intervalo.toLowerCase());
            if (t.getTask() != null) {
                if (t.getTask().getTools() != null && !t.getTask().getTools().isBlank()) {
                    materiais.add(t.getTask().getTools().trim());
                }
                t.getTask().getParts().forEach(p -> materiais.add(p.getPartName()));
            }
        }
        List<Lubrificacao> blocos = new ArrayList<>();
        porIntervalo.forEach((intervalo, accoes) ->
                blocos.add(new Lubrificacao(intervalo, accoes, new ArrayList<>(materiais))));

        Doc doc = new Doc(
                a.getTag(), a.getName(),
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getPlate(),
                principal != null ? PdfRenderer.numero(principal.getCurrentValue(), 0) + " " + unidade : null,
                a.getLocation() != null ? a.getLocation().getName() : null,
                ficha != null ? ficha.name() : "Inspeção diária (antes do arranque)",
                ficha != null ? ficha.description() : null,
                ficha != null ? ficha.estimatedMinutes() : null,
                ficha != null ? ficha.source() : null,
                inspecao, blocos, todas);

        return renderer.render("operator-sheet", letterhead.of(a.getOrganization()), "f", doc, selo);
    }
}
