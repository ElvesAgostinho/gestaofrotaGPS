package ao.autocare.modules.plan;

import ao.autocare.domain.enums.Enums.PredictiveTechnique;
import ao.autocare.domain.enums.Enums.VerificationType;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ItemInput;
import ao.autocare.modules.checklist.dto.ChecklistDtos.SaveTemplateRequest;
import ao.autocare.modules.part.dto.PartDtos.SavePartRequest;
import ao.autocare.modules.predictive.dto.PredictiveDtos.SaveProgramRequest;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.asset.dto.AssetDtos.CriticalityRequest;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import ao.autocare.modules.plan.dto.PlanDtos.PartInput;
import ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest;
import ao.autocare.modules.plan.dto.PlanDtos.TaskInput;
import ao.autocare.modules.plan.dto.PlanDtos.TriggerInput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Planos de manutenção prontos, para não se escrever tudo à mão.
 *
 * <p>Escrever trinta e tal tarefas com intervalos, ferramentas e peças em cada
 * máquina é o que faz uma empresa desistir do plano preventivo e voltar a
 * reparar só o que parte. Estes modelos trazem o plano completo; depois
 * edita-se o que for preciso.
 *
 * <p>O plano da retroescavadora segue um documento de manutenção preventiva de
 * fabricante fornecido pelo cliente, para máquina de 4 toneladas: inspeção diária,
 * lubrificação às 50 h, e o plano por sistema às 250, 500, 1000 e 2000 horas.
 * Os intervalos são os do documento; as ferramentas e as peças vêm das listas
 * que ele traz no fim.
 *
 * <p><b>Os valores são um ponto de partida.</b> O manual do operador de cada
 * máquina manda sempre, e por isso tudo isto fica editável depois de criado.
 */
public final class PlanCatalog {

    private PlanCatalog() {}

    /** Um modelo disponível no catálogo. */
    public record Modelo(String code, String name, String description, int taskCount) {}

    public static List<Modelo> disponiveis() {
        return List.of(
                new Modelo("RETROESCAVADORA",
                        "Retroescavadora — plano do fabricante",
                        "Plano preventivo completo: lubrificação às 50 h e intervenções por "
                                + "sistema às 250, 500, 1000 e 2000 horas.",
                        backhoeTasks().size()),
                new Modelo("TRUCK_HEAVY",
                        "Camião pesado",
                        "Revisões por quilometragem: 10.000, 20.000, 40.000 e 80.000 km.",
                        truckTasks().size()),
                new Modelo("GENERATOR",
                        "Gerador diesel",
                        "Ensaio semanal, revisões às 250, 500 e 1000 horas.",
                        generatorTasks().size()));
    }

    /** Monta o pedido de criação de um plano a partir do código do modelo. */
    public static SavePlanRequest build(String code, String assetTypeId) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "RETROESCAVADORA" -> new SavePlanRequest(
                    "Retroescavadora — plano preventivo",
                    assetTypeId,
                    "Plano de manutenção preventiva para retroescavadora, "
                            + "segundo o documento do fabricante. Criticidade: CRÍTICA "
                            + "(impacto na segurança e financeiro máximos).",
                    "Utilizar apenas peças originais do fabricante. Seguir as recomendações do "
                            + "manual do operador e de serviço. Manter o equipamento limpo e "
                            + "protegido contra intempéries. Todas as intervenções devem ficar "
                            + "registadas em ordem de manutenção.",
                    // O objetivo, tal como o documento o escreve. Fica separado da
                    // descrição porque é a primeira coisa que uma auditoria lê.
                    "Estabelecer as atividades de manutenção preventiva com a finalidade de "
                            + "garantir a máxima disponibilidade, confiabilidade e vida útil da "
                            + "retroescavadora, reduzindo falhas inesperadas, custos de reparação "
                            + "e tempos de paragem.",
                    "Plano de manutenção preventiva do fabricante do equipamento. "
                            + "Manual do operador e de serviço do fabricante.",
                    "Departamento de Manutenção",
                    backhoeTasks());
            case "TRUCK_HEAVY" -> new SavePlanRequest(
                    "Camião pesado — plano preventivo",
                    assetTypeId,
                    "Revisões por quilometragem para veículo pesado de mercadorias.",
                    null,
                    "Manter a viatura disponível e em conformidade legal, reduzindo avarias "
                            + "em estrada e o custo por quilómetro.",
                    "Intervalos habituais de frota pesada. Confirmar com o manual do "
                            + "fabricante de cada viatura.",
                    "Departamento de Manutenção",
                    truckTasks());
            case "GENERATOR" -> new SavePlanRequest(
                    "Gerador diesel — plano preventivo",
                    assetTypeId,
                    "Ensaio periódico e revisões por horas de funcionamento.",
                    "O ensaio em carga é o único que prova que o gerador arranca quando "
                            + "faltar a energia. Um gerador que só se liga sem carga dá a "
                            + "ilusão de estar operacional.",
                    "Garantir que o grupo arranca e assume a carga no momento em que a "
                            + "energia da rede falhar.",
                    "Intervalos habituais de grupos electrogéneos diesel. Confirmar com o "
                            + "manual do fabricante.",
                    "Departamento de Manutenção",
                    generatorTasks());
            default -> throw ao.autocare.common.ApiException.badRequest(
                    "Modelo de plano desconhecido: " + code);
        };
    }

    // ==== Retroescavadora ======================================

    private static List<TaskInput> backhoeTasks() {
        List<TaskInput> t = new ArrayList<>();

        // --- Lubrificação geral, a cada 50 horas -----------------------------
        t.add(tarefa("LUBRIFICACAO", "Lubrificação",
                "Lubrificação geral dos pontos de articulação",
                """
                Lubrificar todos os pontos de massa da máquina:
                · pinos da lança
                · pinos da concha
                · articulações
                · cilindros hidráulicos
                · eixo dianteiro

                Aplicar massa até sair massa limpa pela folga. Limpar o excesso —
                a massa que fica fora atrai areia e transforma o ponto lubrificado
                num ponto abrasivo.
                """,
                30, "Bomba de massa, panos de limpeza, EPIs",
                horas(50), List.of(peca("Massa lubrificante EP2", "1", "kg"))));

        // --- 250 horas -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — mudança de óleo e filtro (250 h)",
                """
                · Trocar o óleo do motor
                · Trocar o filtro de óleo
                · Inspecionar as correias (tensão e estado)
                · Verificar o sistema de arrefecimento

                Registar a quantidade e a especificação do óleo na ordem: o
                lubrificante errado protege menos do que nenhum.
                """,
                90, "Chave de filtros, recipiente de recolha, funil, EPIs",
                horas(250), List.of(
                        peca("Filtro de óleo do motor", "1", "un"),
                        peca("Óleo do motor 15W-40", "12", "L"))));

        t.add(tarefa("HIDRAULICO", "Sistema hidráulico",
                "Hidráulico — inspeção de mangueiras e cilindros (250 h)",
                "Verificar mangueiras, cilindros e conexões: fugas, esfregamento, "
                        + "abaulamentos e fissuras. Uma mangueira a abaular rebenta com pressão "
                        + "e a fuga de alta pressão é um risco de segurança, não só de avaria.",
                45, "Lanterna, espelho de inspeção, EPIs", horas(250), List.of()));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — drenar separador de água (250 h)",
                "Drenar o separador de água e verificar as linhas de combustível. "
                        + "Em Angola a água no gasóleo é a causa mais comum de avaria do "
                        + "sistema de injeção.",
                20, "Recipiente de recolha, EPIs", horas(250), List.of()));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — verificar nível e fugas (250 h)",
                "Verificar o nível do óleo da transmissão e procurar vazamentos.",
                20, "Pano, funil", horas(250), List.of()));

        t.add(tarefa("EIXOS", "Eixos e diferenciais",
                "Eixos — verificar nível e fugas (250 h)",
                "Verificar o nível do óleo dos eixos e diferenciais e procurar vazamentos.",
                25, "Chave de bujões, funil", horas(250), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — bateria, terminais e alternador (250 h)",
                "Verificar a bateria (tensão e densidade), limpar os terminais e "
                        + "confirmar a carga do alternador. Medir e registar as tensões: em "
                        + "repouso 12,4–12,9 V; em carga 13,8–14,6 V.",
                25, "Multímetro, escova de terminais, massa de proteção",
                horas(250), List.of()));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — nível do fluido e vazamentos (250 h)",
                "Verificar o nível do fluido dos travões e procurar vazamentos no circuito.",
                20, "Lanterna, EPIs", horas(250), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — aperto de parafusos, soldas e trincas (250 h)",
                "Verificar o aperto dos parafusos estruturais e inspecionar soldas e "
                        + "trincas. Uma trinca encontrada cedo solda-se; encontrada tarde "
                        + "substitui-se a peça inteira.",
                40, "Chave dinamométrica, lanterna, EPIs", horas(250), List.of()));

        // --- 500 horas -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — injeção, mangueiras e conexões (500 h)",
                "· Verificar o sistema de injeção\n"
                        + "· Verificar o nível do óleo\n"
                        + "· Verificar mangueiras e conexões",
                60, "Ferramenta de diagnóstico, EPIs", horas(500), List.of()));

        t.add(tarefa("HIDRAULICO", "Sistema hidráulico",
                "Hidráulico — filtro de retorno e respiro (500 h)",
                "Trocar o filtro hidráulico de retorno e limpar o respiro do reservatório. "
                        + "Um respiro entupido põe o reservatório em depressão e arrasta "
                        + "sujidade pelos vedantes.",
                60, "Chave de filtros, recipiente, EPIs",
                horas(500), List.of(peca("Filtro hidráulico de retorno", "1", "un"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — filtros primário e secundário (500 h)",
                "Trocar o filtro primário e o filtro secundário de combustível.",
                45, "Chave de filtros, recipiente, EPIs",
                horas(500), List.of(
                        peca("Filtro de combustível primário", "1", "un"),
                        peca("Filtro de combustível secundário", "1", "un"))));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — filtro e embraiagem (500 h)",
                "Trocar o filtro da transmissão e verificar a embraiagem.",
                75, "Chave de filtros, EPIs",
                horas(500), List.of(peca("Filtro da transmissão", "1", "un"))));

        t.add(tarefa("EIXOS", "Eixos e diferenciais",
                "Eixos — folgas e rolamentos (500 h)",
                "Verificar folgas e o estado dos rolamentos dos eixos.",
                60, "Comparador, macaco, EPIs", horas(500), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — motor de arranque e chicotes (500 h)",
                "Verificar o motor de arranque e o estado dos chicotes elétricos "
                        + "(isolamento, fixação, pontos de esfregamento).",
                45, "Multímetro, fita isoladora, abraçadeiras", horas(500), List.of()));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — discos e pastilhas (500 h)",
                "Inspecionar discos e pastilhas. Medir e registar a espessura de cada "
                        + "pastilha na ordem: sem o número não há forma de prever quando "
                        + "chegam ao limite.",
                60, "Paquímetro, macaco, EPIs", horas(500), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — pinos, buchas e desgaste (500 h)",
                "Verificar pinos e buchas e medir o desgaste. Substituir o que estiver "
                        + "fora de tolerância.",
                75, "Paquímetro, extrator de pinos, EPIs",
                horas(500), List.of(peca("Pinos e buchas", "1", "kit"))));

        // --- 1000 horas ------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — escape, arranque e alternador (1000 h)",
                "· Analisar os gases de escape\n"
                        + "· Verificar o motor de arranque\n"
                        + "· Verificar o alternador",
                90, "Analisador de gases, multímetro", horas(1000), List.of()));

        t.add(tarefa("HIDRAULICO", "Sistema hidráulico",
                "Hidráulico — análise do óleo, pressão e válvulas (1000 h)",
                "· Recolher amostra e analisar o óleo hidráulico\n"
                        + "· Verificar a pressão da bomba\n"
                        + "· Verificar as válvulas\n\n"
                        + "Registar a amostra no módulo de manutenção preditiva.",
                90, "Kit de recolha de amostra, manómetro de alta pressão",
                horas(1000), List.of()));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — bomba e bicos injetores (1000 h)",
                "Verificar a bomba de combustível e os bicos injetores.",
                120, "Banco de ensaio de injetores", horas(1000), List.of()));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — análise do óleo e pressões (1000 h)",
                "Analisar o óleo da transmissão e verificar as pressões de trabalho.",
                90, "Kit de amostra, manómetro", horas(1000), List.of()));

        t.add(tarefa("EIXOS", "Eixos e diferenciais",
                "Eixos — análise do óleo (1000 h)",
                "Recolher amostra e analisar o óleo dos eixos.",
                45, "Kit de recolha de amostra", horas(1000), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — fusíveis, relés e sensores (1000 h)",
                "Verificar fusíveis e relés e testar os sensores.",
                60, "Multímetro, jogo de fusíveis",
                horas(1000), List.of(
                        peca("Fusíveis", "1", "kit"),
                        peca("Relés", "1", "kit"))));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — acumuladores e ensaio do sistema (1000 h)",
                "Verificar os acumuladores e testar o sistema de travagem em condições "
                        + "de carga. Registar o resultado do ensaio na ordem.",
                90, "Manómetro, pista de ensaio, EPIs", horas(1000), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — inspeção estrutural completa (1000 h)",
                "Inspeção estrutural completa: chassi, lança, braço e ligações.",
                150, "Líquidos penetrantes, lanterna, EPIs", horas(1000), List.of()));

        // --- 2000 horas ------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — válvulas, injetores e turbo (2000 h)",
                "· Regular as válvulas\n"
                        + "· Inspecionar os injetores\n"
                        + "· Verificar o turboalimentador\n\n"
                        + "Registar as folgas medidas na ordem.",
                240, "Apalpa-folgas, chave dinamométrica, EPIs", horas(2000), List.of()));

        t.add(tarefa("HIDRAULICO", "Sistema hidráulico",
                "Hidráulico — mudança de óleo e limpeza do reservatório (2000 h)",
                "Trocar o óleo hidráulico e limpar o reservatório. Aproveitar para "
                        + "inspecionar o interior do reservatório: o que lá está no fundo diz "
                        + "muito sobre o desgaste da bomba.",
                240, "Bomba de transferência, recipiente, panos, EPIs",
                horas(2000), List.of(
                        peca("Óleo hidráulico", "80", "L"),
                        peca("Kit de vedantes", "1", "kit"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — limpeza do depósito e retorno dos bicos (2000 h)",
                "Limpar o depósito de combustível e verificar o retorno dos bicos injetores.",
                180, "Bomba de transferência, recipiente, EPIs", horas(2000), List.of()));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — mudança de óleo e revisão geral (2000 h)",
                "Trocar o óleo da transmissão e fazer a revisão geral do sistema.",
                240, "Chave de bujões, funil, EPIs",
                horas(2000), List.of(peca("Óleo da transmissão", "20", "L"))));

        t.add(tarefa("EIXOS", "Eixos e diferenciais",
                "Eixos — mudança de óleo (2000 h)",
                "Trocar o óleo dos eixos e diferenciais.",
                120, "Chave de bujões, funil",
                horas(2000), List.of(peca("Óleo dos eixos SAE 80W-90", "16", "L"))));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — revisão completa (2000 h)",
                "Revisão completa do sistema elétrico.",
                180, "Multímetro, osciloscópio, EPIs",
                horas(2000), List.of(peca("Bateria", "1", "un"))));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — revisão completa (2000 h)",
                "Revisão completa do sistema de travagem, incluindo substituição de "
                        + "componentes de desgaste.",
                240, "Ferramenta de travões, macaco, EPIs", horas(2000), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — revisão geral (2000 h)",
                "Revisão geral da estrutura, incluindo material de desgaste.",
                300, "Equipamento de soldadura, EPIs",
                horas(2000), List.of(
                        peca("Dentes da concha", "1", "kit"),
                        peca("Lâmina de desgaste", "1", "un"))));

        return t;
    }

    // ==== Camião pesado ====================================================

    private static List<TaskInput> truckTasks() {
        return List.of(
                tarefa("MOTOR", "Motor", "Revisão A — óleo e filtros (10.000 km)",
                        "Trocar óleo do motor, filtro de óleo e filtro de ar. "
                                + "Verificar níveis e procurar fugas.",
                        120, "Chave de filtros, recipiente, funil",
                        km(10_000), List.of(
                                peca("Filtro de óleo", "1", "un"),
                                peca("Filtro de ar", "1", "un"),
                                peca("Óleo do motor 15W-40", "38", "L"))),
                tarefa("TRAVAGEM", "Travagem", "Revisão B — travagem e pneus (20.000 km)",
                        "Medir e registar a espessura das pastilhas e o piso dos pneus em "
                                + "cada posição. Verificar pressões e fazer rotação se o "
                                + "desgaste o justificar.",
                        150, "Paquímetro, medidor de piso, manómetro",
                        km(20_000), List.of(peca("Pastilhas de travão", "1", "jogo"))),
                tarefa("TRANSMISSAO", "Transmissão", "Revisão C — transmissão e eixos (40.000 km)",
                        "Trocar óleo da caixa e dos diferenciais. Verificar folgas dos "
                                + "veios e o estado dos apoios.",
                        240, "Chave de bujões, funil, macaco",
                        km(40_000), List.of(
                                peca("Óleo da caixa", "14", "L"),
                                peca("Óleo do diferencial SAE 80W-90", "18", "L"))),
                tarefa("MOTOR", "Motor", "Revisão D — revisão maior (80.000 km)",
                        "Regulação de válvulas, inspeção do turbo, substituição de correias "
                                + "e revisão do sistema de arrefecimento.",
                        480, "Apalpa-folgas, chave dinamométrica",
                        km(80_000), List.of(
                                peca("Correias", "1", "jogo"),
                                peca("Líquido de refrigeração", "40", "L"))));
    }

    // ==== Gerador ==========================================================

    private static List<TaskInput> generatorTasks() {
        return List.of(
                tarefa("ENSAIO", "Ensaio", "Ensaio semanal em carga",
                        """
                        Arrancar o gerador e mantê-lo em carga durante pelo menos 30 minutos.

                        Registar na ordem: potência atingida, tensão em cada fase,
                        frequência, pressão de óleo e temperatura da água.

                        Um gerador que só se liga sem carga dá a ilusão de estar
                        operacional — e falha no dia em que for preciso.
                        """,
                        45, "Banco de carga, multímetro, pinça amperimétrica",
                        List.of(new TriggerInput(PlanTriggerType.CALENDAR_DAYS, null,
                                BigDecimal.valueOf(7), BigDecimal.ONE)),
                        List.of()),
                tarefa("MOTOR", "Motor", "Motor — óleo e filtros (250 h)",
                        "Trocar o óleo do motor e os filtros de óleo e combustível. "
                                + "Drenar a água do separador.",
                        120, "Chave de filtros, recipiente, funil",
                        horas(250), List.of(
                                peca("Filtro de óleo", "1", "un"),
                                peca("Filtro de combustível", "1", "un"),
                                peca("Óleo do motor 15W-40", "18", "L"))),
                tarefa("ELETRICO", "Elétrico", "Elétrico — bateria e quadro (500 h)",
                        "Verificar a bateria de arranque, limpar terminais e inspecionar o "
                                + "quadro elétrico e o quadro de transferência automática.",
                        90, "Multímetro, escova de terminais", horas(500), List.of()),
                tarefa("MOTOR", "Motor", "Revisão maior (1000 h)",
                        "Regulação de válvulas, análise de óleo, medição da resistência de "
                                + "isolamento do alternador e revisão do sistema de "
                                + "arrefecimento.",
                        360, "Apalpa-folgas, megóhmetro, kit de amostra",
                        horas(1000), List.of(
                                peca("Líquido de refrigeração", "25", "L"),
                                peca("Correias", "1", "jogo"))));
    }

    // ==== Auxiliares =======================================================

    private static TaskInput tarefa(
            String systemCode, String systemName, String title, String instructions,
            int minutos, String ferramentas,
            List<TriggerInput> gatilhos, List<PartInput> pecas) {
        return new TaskInput(systemCode, systemName, title, instructions,
                minutos, ferramentas, gatilhos, pecas);
    }

    /** Gatilho por horas de funcionamento, com 10 % de tolerância. */
    private static List<TriggerInput> horas(int h) {
        return List.of(new TriggerInput(PlanTriggerType.METER_INTERVAL, MeterKind.HOURMETER,
                BigDecimal.valueOf(h), BigDecimal.valueOf(Math.max(5, h / 10))));
    }

    /** Gatilho por quilómetros, com 10 % de tolerância. */
    private static List<TriggerInput> km(int km) {
        return List.of(new TriggerInput(PlanTriggerType.METER_INTERVAL, MeterKind.ODOMETER,
                BigDecimal.valueOf(km), BigDecimal.valueOf(km / 10)));
    }

    private static PartInput peca(String nome, String quantidade, String unidade) {
        return new PartInput(nome, new BigDecimal(quantidade), unidade);
    }

    // ==== Inspecao diaria ==================================================

    /**
     * A inspeccao de quinze minutos antes do arranque.
     *
     * <p>E a parte mais importante de um plano preventivo e a que mais se
     * esquece. Nao substitui revisoes: apanha fugas, niveis e travoes no dia em
     * que aparecem, que e onde as avarias caras comecam. Um dos onze pontos da
     * retroescavadora -- o alarme de marcha-atras -- nao e manutencao nenhuma,
     * e seguranca de quem trabalha atras da maquina.
     *
     * <p>Devolve {@code null} quando o modelo nao preve inspeccao diaria.
     */
    public static SaveTemplateRequest dailyChecklist(String code, String assetTypeId) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "RETROESCAVADORA" -> new SaveTemplateRequest(
                    "Inspeção diária — retroescavadora",
                    assetTypeId,
                    "A fazer antes do arranque, todos os dias. Qualquer ponto reprovado "
                            + "impede a máquina de sair até ser resolvido.",
                    15,
                    List.of(
                            item("Nível do óleo do motor", VERIFY, true),
                            item("Nível do líquido de arrefecimento", VERIFY, true),
                            item("Nível do óleo hidráulico", VERIFY, true),
                            item("Estado dos pneus (pressão, cortes, desgaste)", INSPECT, true),
                            item("Vazamentos de óleo, combustível ou hidráulico", INSPECT, true),
                            item("Luzes e sinalização", TEST, false),
                            item("Alarme de marcha-atrás", TEST, true),
                            item("Travões", TEST, true),
                            item("Buzina", TEST, false),
                            item("Limpeza do radiador", VERIFY, false),
                            item("Extintor de incêndio (carga e validade)", VERIFY, true)));
            case "TRUCK_HEAVY" -> new SaveTemplateRequest(
                    "Inspeção diária — camião pesado",
                    assetTypeId,
                    "A fazer antes de sair, todos os dias.",
                    12,
                    List.of(
                            item("Nível do óleo do motor", VERIFY, true),
                            item("Nível do líquido de arrefecimento", VERIFY, true),
                            item("Pressão e estado dos pneus", INSPECT, true),
                            item("Vazamentos por baixo da viatura", INSPECT, true),
                            item("Luzes, piscas e stops", TEST, true),
                            item("Travões e travão de mão", TEST, true),
                            item("Buzina e espelhos", TEST, false),
                            item("Documentos a bordo e extintor", VERIFY, true),
                            item("Fixação da carga", INSPECT, true)));
            case "GENERATOR" -> new SaveTemplateRequest(
                    "Inspecao diaria — gerador",
                    assetTypeId,
                    "A fazer todos os dias, mesmo quando o gerador não trabalhou: "
                            + "o dia em que faltar a energia não é dia de descobrir problemas.",
                    10,
                    List.of(
                            item("Nível do óleo do motor", VERIFY, true),
                            item("Nível do líquido de arrefecimento", VERIFY, true),
                            item("Nível de combustível no depósito", VERIFY, true),
                            item("Vazamentos de óleo, água ou combustível", INSPECT, true),
                            item("Estado e terminais da bateria de arranque", INSPECT, true),
                            item("Quadro de transferência em automático", VERIFY, true),
                            item("Ventilação e escape desobstruídos", INSPECT, true),
                            item("Extintor junto ao grupo", VERIFY, true)));
            default -> null;
        };
    }

    // ==== Monitorizacao preditiva ==========================================

    /**
     * Os programas de monitorizacao que o documento preve.
     *
     * <p>Sao por equipamento e nao por plano: a vibracao mede-se numa maquina
     * concreta, nao num modelo. Por isso so se criam quando o catalogo e
     * aplicado a um ativo.
     */
    public static List<SaveProgramRequest> predictivePrograms(String code) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "RETROESCAVADORA" -> List.of(
                    programa(PredictiveTechnique.VIBRATION, 1,
                            "Motor, bomba hidráulica, alternador",
                            "Identificar desgastes prematuros e desalinhamentos"),
                    programa(PredictiveTechnique.THERMOGRAPHY, 3,
                            "Sistema elétrico, cablagem, conexões",
                            "Detetar aquecimentos anormais e prevenir falhas elétricas"),
                    programa(PredictiveTechnique.OIL_ANALYSIS, 6,
                            "Motor, hidráulico, transmissão",
                            "Avaliar contaminação, desgaste e condição dos fluidos"));
            case "TRUCK_HEAVY" -> List.of(
                    programa(PredictiveTechnique.OIL_ANALYSIS, 6,
                            "Motor, caixa, diferenciais",
                            "Avaliar desgaste interno sem abrir o motor"),
                    programa(PredictiveTechnique.ALIGNMENT, 12,
                            "Direção e rodado",
                            "Evitar desgaste irregular dos pneus"));
            case "GENERATOR" -> List.of(
                    programa(PredictiveTechnique.INSULATION, 12,
                            "Alternador e cablagem de potência",
                            "Confirmar que o isolamento aguenta a carga"),
                    programa(PredictiveTechnique.THERMOGRAPHY, 3,
                            "Quadro elétrico e quadro de transferência",
                            "Apanhar ligações a aquecer antes de arderem"),
                    programa(PredictiveTechnique.OIL_ANALYSIS, 6,
                            "Motor",
                            "Avaliar contaminação e desgaste"));
            default -> List.of();
        };
    }

    // ==== Pecas de armazem =================================================

    /**
     * O que tem de estar em armazem para o plano se cumprir.
     *
     * <p>Um plano preventivo sem pecas no armazem transforma-se em manutencao
     * correctiva no dia em que a peca falta: a maquina fica parada a espera de
     * uma encomenda que demora semanas a chegar a Angola.
     */
    public static List<SavePartRequest> spareParts(String code) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "RETROESCAVADORA" -> List.of(
                    parte("Filtro de óleo do motor", "MOTOR", "un", "2"),
                    parte("Filtro de combustível primário", "COMBUSTIVEL", "un", "2"),
                    parte("Filtro de combustível secundário", "COMBUSTIVEL", "un", "2"),
                    parte("Filtro de ar", "MOTOR", "un", "2"),
                    parte("Filtro hidráulico de retorno", "HIDRAULICO", "un", "2"),
                    parte("Filtro da transmissão", "TRANSMISSAO", "un", "1"),
                    parte("Correias do motor", "MOTOR", "jogo", "1"),
                    parte("Sensor de temperatura", "MOTOR", "un", "1"),
                    parte("Mangueiras hidráulicas", "HIDRAULICO", "un", "2"),
                    parte("Kit de vedantes hidráulicos", "HIDRAULICO", "kit", "1"),
                    parte("Válvulas hidráulicas", "HIDRAULICO", "un", "1"),
                    parte("Bateria", "ELETRICO", "un", "1"),
                    parte("Fusíveis", "ELETRICO", "kit", "2"),
                    parte("Relés", "ELETRICO", "kit", "1"),
                    parte("Lâmpadas", "ELETRICO", "un", "4"),
                    parte("Dentes da concha", "IMPLEMENTO", "un", "6"),
                    parte("Pinos e buchas", "ESTRUTURA", "kit", "1"),
                    parte("Parafusos estruturais", "ESTRUTURA", "kit", "1"),
                    parte("Lâmina de desgaste", "IMPLEMENTO", "un", "1"),
                    parte("Massa lubrificante EP2", "LUBRIFICACAO", "kg", "10"));
            case "TRUCK_HEAVY" -> List.of(
                    parte("Filtro de óleo", "MOTOR", "un", "2"),
                    parte("Filtro de ar", "MOTOR", "un", "2"),
                    parte("Filtro de combustível", "COMBUSTIVEL", "un", "2"),
                    parte("Pastilhas de travao", "TRAVAGEM", "jogo", "1"),
                    parte("Correias", "MOTOR", "jogo", "1"));
            case "GENERATOR" -> List.of(
                    parte("Filtro de óleo", "MOTOR", "un", "2"),
                    parte("Filtro de combustível", "COMBUSTIVEL", "un", "2"),
                    parte("Filtro de ar", "MOTOR", "un", "1"),
                    parte("Bateria de arranque", "ELETRICO", "un", "1"),
                    parte("Correias", "MOTOR", "jogo", "1"));
            default -> List.of();
        };
    }

    // ==== Auxiliares dos blocos acima ======================================

    private static final VerificationType VERIFY = VerificationType.VERIFY;
    private static final VerificationType INSPECT = VerificationType.INSPECT;
    private static final VerificationType TEST = VerificationType.TEST;

    private static ItemInput item(String texto, VerificationType tipo, boolean critico) {
        return new ItemInput(texto, tipo, critico);
    }

    private static SaveProgramRequest programa(
            PredictiveTechnique tecnica, int meses, String componentes, String objectivo) {
        return new SaveProgramRequest(tecnica, meses, componentes, objectivo,
                null, null, Boolean.TRUE, null);
    }

    private static SavePartRequest parte(
            String nome, String sistema, String unidade, String minimo) {
        return new SavePartRequest(nome, null, sistema, null, unidade,
                new BigDecimal(minimo), null, "AOA", null);
    }

    // ==== Criticidade ======================================================

    /**
     * A criticidade que o documento do fabricante atribui ao equipamento.
     *
     * <p>Vem em três eixos, como no documento: impacto na produção, na
     * segurança e financeiro, de 1 a 5. A retroescavadora do documento leva
     * 4/5/5 — quatro estrelas na produção, cinco na segurança e cinco no
     * financeiro — o que dá criticidade <b>crítica</b>.
     *
     * <p>Não é um número decorativo: é o que decide a ordem por que as avarias
     * são atendidas quando há três máquinas paradas e um mecânico.
     *
     * <p>Devolve {@code null} quando o modelo não traz avaliação.
     */
    public static CriticalityRequest criticality(String code) {
        return switch (code == null ? "" : code.toUpperCase()) {
            case "RETROESCAVADORA" -> new CriticalityRequest(4, 5, 5, null,
                    "Avaliação do plano de manutenção preventiva do fabricante: "
                            + "impacto na produção 4/5, na segurança 5/5, financeiro 5/5.");
            case "TRUCK_HEAVY" -> new CriticalityRequest(4, 4, 4, null,
                    "Veículo pesado de mercadorias: paragem afeta entregas e "
                            + "envolve risco rodoviário.");
            case "GENERATOR" -> new CriticalityRequest(5, 4, 4, null,
                    "Energia de emergência: quando falha, falha tudo o que dela depende.");
            default -> null;
        };
    }
}
