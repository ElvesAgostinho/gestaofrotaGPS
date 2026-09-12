package ao.autocare.config;

import java.util.List;

/**
 * Conteúdo do documento de referência "Plano de Manutenção Preventiva" da
 * de fabricante para retroescavadora — usado pelo seeder de demonstração.
 * Uma linha por (sistema, intervalo em horas, descrição da tarefa).
 */
final class ReferencePlanData {

    private ReferencePlanData() {}

    record PlanTaskRow(String systemCode, String systemName, int hours, String title, String tools) {}

    record ChecklistItemRow(String text, String verification, boolean critical) {}

    static final int LUBE_HOURS = 50;

    static final String LUBE_TITLE =
            "Lubrificação geral — pinos da lança, pinos da concha, articulações, "
            + "cilindros hidráulicos e eixo dianteiro";

    static final String LUBE_TOOLS =
            "Bomba de massa · Massa lubrificante EP2 · Panos de limpeza · EPIs";

    /** Inspeção diária (antes do arranque) — 15 minutos. */
    static final List<ChecklistItemRow> DAILY_CHECKLIST = List.of(
            new ChecklistItemRow("Nível do óleo do motor", "VERIFY", false),
            new ChecklistItemRow("Nível do líquido de arrefecimento", "VERIFY", false),
            new ChecklistItemRow("Nível do óleo hidráulico", "VERIFY", false),
            new ChecklistItemRow("Estado dos pneus", "VERIFY", false),
            new ChecklistItemRow("Vazamentos (óleo, combustível, hidráulico)", "INSPECT", true),
            new ChecklistItemRow("Luzes e sinalização", "TEST", false),
            new ChecklistItemRow("Alarme de marcha atrás", "TEST", true),
            new ChecklistItemRow("Travões", "TEST", true),
            new ChecklistItemRow("Buzina", "TEST", false),
            new ChecklistItemRow("Limpeza do radiador", "VERIFY", false),
            new ChecklistItemRow("Extintor de incêndio", "VERIFY", true));

    /** Plano de manutenção por horas — 8 sistemas × 250/500/1000/2000 h. */
    static final List<PlanTaskRow> PLAN_TASKS = List.of(
            // MOTOR
            row("ENGINE", "Motor", 250, "Trocar óleo do motor; trocar filtro de óleo; inspecionar correias; verificar sistema de arrefecimento"),
            row("ENGINE", "Motor", 500, "Verificar sistema de injeção; verificar nível do óleo; verificar mangueiras e conexões"),
            row("ENGINE", "Motor", 1000, "Analisar gases de escape; verificar motor de arranque; verificar alternador"),
            row("ENGINE", "Motor", 2000, "Regular válvulas; inspecionar injetores; verificar turboalimentador"),
            // SISTEMA HIDRÁULICO
            row("HYDRAULIC", "Sistema Hidráulico", 250, "Verificar mangueiras, cilindros e conexões"),
            row("HYDRAULIC", "Sistema Hidráulico", 500, "Trocar filtro hidráulico de retorno; limpar respiro do reservatório"),
            row("HYDRAULIC", "Sistema Hidráulico", 1000, "Analisar óleo hidráulico; verificar pressão da bomba; verificar válvulas"),
            row("HYDRAULIC", "Sistema Hidráulico", 2000, "Trocar óleo hidráulico; limpar reservatório hidráulico"),
            // SISTEMA DE COMBUSTÍVEL
            row("FUEL", "Sistema de Combustível", 250, "Drenar separador de água; verificar linhas de combustível"),
            row("FUEL", "Sistema de Combustível", 500, "Trocar filtro primário; trocar filtro secundário"),
            row("FUEL", "Sistema de Combustível", 1000, "Verificar bomba de combustível; verificar bicos injetores"),
            row("FUEL", "Sistema de Combustível", 2000, "Limpeza do tanque de combustível; verificar retorno dos bicos"),
            // SISTEMA DE TRANSMISSÃO
            row("TRANSMISSION", "Sistema de Transmissão", 250, "Verificar nível do óleo; verificar vazamentos"),
            row("TRANSMISSION", "Sistema de Transmissão", 500, "Trocar filtro da transmissão; verificar embreagem"),
            row("TRANSMISSION", "Sistema de Transmissão", 1000, "Analisar óleo da transmissão; verificar pressões"),
            row("TRANSMISSION", "Sistema de Transmissão", 2000, "Trocar óleo da transmissão; revisão geral do sistema"),
            // EIXOS E DIFERENCIAIS
            row("AXLES", "Eixos e Diferenciais", 250, "Verificar nível do óleo; verificar vazamentos"),
            row("AXLES", "Eixos e Diferenciais", 500, "Verificar folgas e rolamentos"),
            row("AXLES", "Eixos e Diferenciais", 1000, "Analisar óleo dos eixos"),
            row("AXLES", "Eixos e Diferenciais", 2000, "Trocar óleo dos eixos"),
            // SISTEMA ELÉTRICO
            row("ELECTRICAL", "Sistema Elétrico", 250, "Verificar bateria, terminais e alternador"),
            row("ELECTRICAL", "Sistema Elétrico", 500, "Verificar motor de arranque; verificar chicotes elétricos"),
            row("ELECTRICAL", "Sistema Elétrico", 1000, "Verificar fusíveis e relés; verificar sensores"),
            row("ELECTRICAL", "Sistema Elétrico", 2000, "Revisão completa do sistema elétrico"),
            // SISTEMA DE TRAVAGEM
            row("BRAKES", "Sistema de Travagem", 250, "Verificar nível do fluido; verificar vazamentos"),
            row("BRAKES", "Sistema de Travagem", 500, "Inspecionar discos; inspecionar pastilhas"),
            row("BRAKES", "Sistema de Travagem", 1000, "Verificar acumuladores; testar sistema de travagem"),
            row("BRAKES", "Sistema de Travagem", 2000, "Revisão completa do sistema de travagem"),
            // ESTRUTURA E CHASSI
            row("STRUCTURE", "Estrutura e Chassi", 250, "Verificar aperto de parafusos; verificar soldas e trincas"),
            row("STRUCTURE", "Estrutura e Chassi", 500, "Verificar pinos e buchas; verificar desgaste"),
            row("STRUCTURE", "Estrutura e Chassi", 1000, "Inspeção estrutural completa"),
            row("STRUCTURE", "Estrutura e Chassi", 2000, "Revisão geral da estrutura"));

    private static PlanTaskRow row(String code, String name, int hours, String title) {
        return new PlanTaskRow(code, name, hours, title, null);
    }
}
