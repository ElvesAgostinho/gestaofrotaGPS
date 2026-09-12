package ao.autocare.modules.plan.pdf;

import java.util.List;

/** Modelo pré-formatado para o template do PDF "Plano de Manutenção Preventiva". */
public record PlanPdfModel(
        String productName,
        String generatedAt,
        Asset asset,
        Criticality criticality,
        Checklist checklist,
        Plan plan,
        List<Kpi> kpis,
        List<Predictive> predictive,
        List<PartGroup> parts,
        String observations) {

    public record Asset(
            String tag, String name, String model, String serialNumber, String modelYear,
            String location, String responsible, String objective) {}

    public record Criticality(
            int production, int safety, int financial, String overall, String overallLabel) {

        public String stars(int n) {
            return "★★★★★".substring(0, n) + "☆☆☆☆☆".substring(0, 5 - n);
        }
    }

    public record ChecklistItem(String text, String verification) {}

    public record Checklist(String name, Integer estimatedMinutes, List<ChecklistItem> items) {}

    public record LubeTask(int hours, String title, String tools) {}

    public record PlanRow(String system, List<String> col250, List<String> col500,
                          List<String> col1000, List<String> col2000) {}

    public record Plan(String name, String notes, LubeTask lube, List<PlanRow> rows,
                       List<Integer> intervals) {}

    public record Kpi(String name, String target, String formula) {}

    public record Predictive(String frequency, String technique, String components, String goal) {}

    public record PartGroup(String system, List<String> parts) {}
}
