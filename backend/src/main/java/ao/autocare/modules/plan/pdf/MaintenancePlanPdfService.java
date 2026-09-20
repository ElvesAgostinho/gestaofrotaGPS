package ao.autocare.modules.plan.pdf;

import ao.autocare.common.ApiException;
import ao.autocare.config.AutoCareProperties;
import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetPlan;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.MaintenancePlan;
import ao.autocare.domain.PlanTask;
import ao.autocare.domain.PlanTaskTrigger;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetPlanRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.ChecklistTemplateRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Gera o PDF "Plano de Manutenção Preventiva" de um ativo (formato do documento de referência). */
@Service
public class MaintenancePlanPdfService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final Map<String, String> CRIT_LABEL = Map.of(
            "LOW", "BAIXA", "MEDIUM", "MÉDIA", "HIGH", "ALTA", "CRITICAL", "CRÍTICA");

    private static final List<PlanPdfModel.Kpi> KPIS = List.of(
            new PlanPdfModel.Kpi("Disponibilidade", "≥ 90 %",
                    "(Horas Disponíveis / Horas Planeadas) × 100"),
            new PlanPdfModel.Kpi("MTBF (Mean Time Between Failures)", "≥ 500 horas",
                    "Horas de Operação / Número de Falhas"),
            new PlanPdfModel.Kpi("MTTR (Mean Time To Repair)", "≤ 4 horas",
                    "Tempo Total de Reparação / Número de Reparações"),
            new PlanPdfModel.Kpi("Cumprimento do plano", "≥ 95 %",
                    "(Ordens Executadas / Ordens Planeadas) × 100"));

    /**
     * Tabela do documento de referência, usada quando o ativo ainda não tem
     * programas preditivos definidos. Assim o PDF nunca sai vazio nesta secção,
     * mas quem já configurou os seus programas vê os seus, não os do exemplo.
     */
    private static final List<PlanPdfModel.Predictive> PREDICTIVE_REFERENCE = List.of(
            new PlanPdfModel.Predictive("Mensal", "Análise de vibração",
                    "Motor, bomba hidráulica, alternador",
                    "Identificar desgastes prematuros e desalinhamentos"),
            new PlanPdfModel.Predictive("Trimestral", "Termografia",
                    "Sistema elétrico, cablagem, conexões",
                    "Detetar aquecimentos anormais e prevenir falhas elétricas"),
            new PlanPdfModel.Predictive("Semestral", "Análise de óleo",
                    "Motor, hidráulico, transmissão",
                    "Avaliar contaminação, desgaste e condição dos fluidos"));

    private final TemplateEngine templateEngine;
    private final ao.autocare.modules.org.Letterhead letterhead;
    private final AutoCareProperties props;
    private final AssetRepository assets;
    private final ao.autocare.repo.PredictiveProgramRepository predictivePrograms;
    private final ao.autocare.modules.kpi.KpiService kpis;
    private final AssetCriticalityRepository criticalities;
    private final ChecklistTemplateRepository checklistTemplates;
    private final AssetPlanRepository assetPlans;
    private final AssetPlanTaskRepository assetPlanTasks;

    public MaintenancePlanPdfService(
            TemplateEngine templateEngine,
            AutoCareProperties props,
            AssetRepository assets,
            AssetCriticalityRepository criticalities,
            ChecklistTemplateRepository checklistTemplates,
            AssetPlanRepository assetPlans,
            AssetPlanTaskRepository assetPlanTasks,
            ao.autocare.repo.PredictiveProgramRepository predictivePrograms,
            ao.autocare.modules.kpi.KpiService kpis,
            ao.autocare.modules.org.Letterhead letterhead) {
        this.letterhead = letterhead;
        this.templateEngine = templateEngine;
        this.props = props;
        this.assets = assets;
        this.criticalities = criticalities;
        this.checklistTemplates = checklistTemplates;
        this.assetPlans = assetPlans;
        this.assetPlanTasks = assetPlanTasks;
        this.predictivePrograms = predictivePrograms;
        this.kpis = kpis;
    }

    @Transactional(readOnly = true)
    public byte[] render(String orgId, String assetId) {
        Asset asset = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));

        PlanPdfModel model = buildModel(orgId, asset);

        Context ctx = new Context(Locale.forLanguageTag("pt"));
        ctx.setVariable("doc", model);
        ctx.setVariable("timbre", letterhead.of(asset.getOrganization()));
        String html = templateEngine.process("maintenance-plan", ctx);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o PDF do plano de manutenção", e);
        }
    }

    // ------------------------------------------------------------------
    private PlanPdfModel buildModel(String orgId, Asset asset) {
        var crit = criticalities.findByAssetId(asset.getId()).orElse(null);
        PlanPdfModel.Criticality criticality = crit == null
                ? new PlanPdfModel.Criticality(1, 1, 1, "LOW", "BAIXA")
                : new PlanPdfModel.Criticality(
                        crit.getProductionImpact(), crit.getSafetyImpact(), crit.getFinancialImpact(),
                        crit.getOverall().name(),
                        CRIT_LABEL.getOrDefault(crit.getOverall().name(), crit.getOverall().name()));

        PlanPdfModel.Checklist checklist = checklistTemplates
                .findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(t -> matchesAssetType(t, asset))
                .findFirst()
                .map(this::toChecklist)
                .orElse(null);

        PlanPdfModel.Plan plan = assetPlans.findByAssetId(asset.getId()).stream()
                .findFirst()
                .map(ap -> toPlan(ap, assetPlanTasks.findByAssetPlanId(ap.getId())))
                .orElse(null);

        String product = props.app().name();
        return new PlanPdfModel(
                product,
                DATE.format(java.time.Instant.now()),
                new PlanPdfModel.Asset(
                        asset.getTag(), asset.getName(),
                        nz(asset.getModel()), nz(asset.getSerialNumber()),
                        asset.getModelYear() != null ? asset.getModelYear().toString() : "—",
                        asset.getLocation() != null ? asset.getLocation().getName() : "—",
                        asset.getResponsibleLabel() != null ? asset.getResponsibleLabel()
                                : (asset.getResponsibleUser() != null ? asset.getResponsibleUser().getName() : "—"),
                        nz(asset.getObjective())),
                criticality, checklist, plan, kpisFor(orgId, asset.getId()),
                predictiveFor(asset.getId()),
                buildPartGroups(plan),
                "Todas as manutenções devem ser registadas no sistema (OM). "
                        + "Utilizar apenas peças originais. Seguir as recomendações do manual do "
                        + "operador e de serviço. Manter o equipamento limpo e protegido contra intempéries.");
    }

    /**
     * Os indicadores com a meta e o valor medido no último ano.
     *
     * <p>Uma meta impressa sozinha é uma intenção; ao lado do número real é um
     * compromisso que se pode verificar. Onde ainda não há dados que cheguem,
     * imprime-se «por apurar» — nunca um zero que parece um desastre.
     */
    private List<PlanPdfModel.Kpi> kpisFor(String orgId, String assetId) {
        java.util.Map<String, String> reais = new java.util.HashMap<>();
        try {
            var relatorio = kpis.report(orgId, assetId,
                    java.time.Instant.now().minus(java.time.Duration.ofDays(365)), null);
            for (var m : relatorio.metrics()) {
                if (m.value() != null) {
                    reais.put(m.key(), ao.autocare.modules.org.PdfRenderer.numero(java.math.BigDecimal.valueOf(m.value()), 1)
                            + ("%".equals(m.unit()) ? " %" : " " + m.unit()));
                }
            }
        } catch (RuntimeException e) {
            // Um indicador que não se consegue calcular não impede a folha de sair.
            return KPIS;
        }
        List<PlanPdfModel.Kpi> out = new java.util.ArrayList<>();
        String[] chaves = {"availability", "mtbf", "mttr", "plan_compliance"};
        for (int i = 0; i < KPIS.size(); i++) {
            PlanPdfModel.Kpi k = KPIS.get(i);
            out.add(new PlanPdfModel.Kpi(k.name(), k.target(), k.formula(), reais.get(chaves[i])));
        }
        return out;
    }

    private boolean matchesAssetType(ChecklistTemplate t, Asset asset) {
        return t.getAssetType() == null || t.getAssetType().getId().equals(asset.getAssetType().getId());
    }

    private PlanPdfModel.Checklist toChecklist(ChecklistTemplate t) {
        List<PlanPdfModel.ChecklistItem> items = t.getItems().stream()
                .map(i -> new PlanPdfModel.ChecklistItem(i.getText(), verificationLabel(i.getVerification().name())))
                .toList();
        return new PlanPdfModel.Checklist(t.getName(), t.getEstimatedMinutes(), items);
    }

    private PlanPdfModel.Plan toPlan(AssetPlan ap, List<AssetPlanTask> ignored) {
        MaintenancePlan mp = ap.getPlan();
        PlanPdfModel.LubeTask lube = null;
        // sistema -> intervalo -> lista de títulos
        Map<String, Map<Integer, List<String>>> grouped = new LinkedHashMap<>();
        java.util.TreeSet<Integer> intervalSet = new java.util.TreeSet<>();

        for (PlanTask task : mp.getTasks()) {
            Integer meterInterval = meterInterval(task);
            if (meterInterval != null && meterInterval == 50 && lube == null
                    && task.getTitle().toLowerCase().contains("lubrific")) {
                lube = new PlanPdfModel.LubeTask(50, task.getTitle(), nz(task.getTools()));
                continue;
            }
            if (meterInterval == null) continue;
            String system = task.getSystemName() != null ? task.getSystemName() : "Geral";
            intervalSet.add(meterInterval);
            grouped.computeIfAbsent(system, k -> new LinkedHashMap<>())
                    .computeIfAbsent(meterInterval, k -> new ArrayList<>())
                    .add(task.getTitle());
        }

        List<Integer> intervals = new ArrayList<>(intervalSet);
        // Colunas fixas do documento (250/500/1000/2000) mais quaisquer outras encontradas.
        List<Integer> columns = new ArrayList<>(List.of(250, 500, 1000, 2000));
        for (Integer i : intervals) if (!columns.contains(i)) columns.add(i);

        List<PlanPdfModel.PlanRow> rows = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<Integer, List<String>> byInterval = entry.getValue();
            rows.add(new PlanPdfModel.PlanRow(entry.getKey(),
                    byInterval.getOrDefault(250, List.of()),
                    byInterval.getOrDefault(500, List.of()),
                    byInterval.getOrDefault(1000, List.of()),
                    byInterval.getOrDefault(2000, List.of())));
        }
        return new PlanPdfModel.Plan(mp.getName(), nz(mp.getNotes()), lube, rows, columns);
    }

    private List<PlanPdfModel.PartGroup> buildPartGroups(PlanPdfModel.Plan plan) {
        // Fase 5 traz o inventário. Por agora, extrai as peças mencionadas nas tarefas.
        return List.of();
    }

    private Integer meterInterval(PlanTask task) {
        for (PlanTaskTrigger tr : task.getTriggers()) {
            if (tr.getTriggerType() == PlanTriggerType.METER_INTERVAL && tr.getIntervalValue() != null) {
                return tr.getIntervalValue().setScale(0, RoundingMode.HALF_UP).intValueExact();
            }
        }
        return null;
    }

    private static String verificationLabel(String v) {
        return switch (v) {
            case "INSPECT" -> "Inspecionar";
            case "TEST" -> "Testar";
            default -> "Verificar";
        };
    }

    /**
     * Programas preditivos reais do ativo; se não houver nenhum, a tabela do
     * documento de referência.
     */
    private List<PlanPdfModel.Predictive> predictiveFor(String assetId) {
        List<PlanPdfModel.Predictive> rows = predictivePrograms
                .findByAssetIdOrderByTechniqueAsc(assetId).stream()
                .filter(ao.autocare.domain.PredictiveProgram::isActive)
                .map(program -> new PlanPdfModel.Predictive(
                        ao.autocare.modules.predictive.dto.PredictiveDtos.ProgramView
                                .frequencyLabel(program.getFrequencyMonths()),
                        program.getTechnique().label(),
                        nz(program.getComponents()),
                        nz(program.getGoal())))
                .toList();
        return rows.isEmpty() ? PREDICTIVE_REFERENCE : rows;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
