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
                new Modelo("LIGHT_VEHICLE",
                        "Ligeiro de passageiros ou mercadorias",
                        "Revisões por quilometragem: 10.000, 20.000, 40.000 e 60.000 km, "
                                + "mais o que se faz por tempo e não por quilómetros.",
                        lightVehicleTasks().size()),
                new Modelo("GENERATOR",
                        "Gerador diesel",
                        "Ensaio semanal, revisões às 250, 500 e 1000 horas.",
                        generatorTasks().size()));
    }

    /**
     * A família do catálogo a que um ativo pertence.
     *
     * <p>A categoria diz «viatura», mas uma viatura tanto é um camião com
     * quinta roda como um Hilux: o que os separa é o nome do tipo de
     * equipamento que a empresa criou. Esta é a única regra do sistema para
     * essa decisão — a inspeção diária, os programas preditivos e o catálogo
     * usam todos esta, para não haver duas respostas diferentes à mesma
     * pergunta.
     */
    public static String codigoPara(String categoria, String tipoNome) {
        String n = tipoNome == null ? "" : java.text.Normalizer
                .normalize(tipoNome.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if ("GENERATOR".equals(categoria) || n.contains("gerador") || n.contains("electrog")
                || n.contains("eletrog") || n.contains("generator")) {
            return "GENERATOR";
        }
        if ("VEHICLE".equals(categoria)) {
            boolean ligeiro = n.contains("ligeir") || n.contains("carro") || n.contains("autom")
                    || n.contains("pick") || n.contains("jipe") || n.contains("suv")
                    || n.contains("carrinha") || n.contains("van") || n.contains("sedan");
            return ligeiro ? "LIGHT_VEHICLE" : "TRUCK_HEAVY";
        }
        return "RETROESCAVADORA";
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
            case "LIGHT_VEHICLE" -> new SavePlanRequest(
                    "Ligeiro — plano preventivo",
                    assetTypeId,
                    "Revisões por quilometragem para ligeiro de passageiros ou de mercadorias.",
                    "Num ligeiro não há pontos de massa: os rolamentos e as juntas vêm "
                            + "selados de fábrica. O que o mata é o que se adia — travões, "
                            + "distribuição e o filtro de ar, que em estrada de terra dura "
                            + "muito menos do que o manual europeu supõe.",
                    "Manter a viatura disponível e segura ao menor custo por quilómetro, "
                            + "sem adiar o que é de segurança.",
                    "Intervalos habituais de frota ligeira. Confirmar sempre com o manual "
                            + "do fabricante, sobretudo a correia de distribuição.",
                    "Departamento de Manutenção",
                    lightVehicleTasks());
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

    /**
     * O plano do camião, sistema a sistema, por quilometragem.
     *
     * <p>Um pesado não se trata por «revisão A, B, C»: trata-se por sistemas,
     * como a máquina — só que o contador é o odómetro. Os intervalos são os
     * habituais de frota pesada em estrada de terra batida e poeira, que é
     * onde estes camiões andam; o manual de cada fabricante manda sempre.
     */
    private static List<TaskInput> truckTasks() {
        List<TaskInput> t = new ArrayList<>();

        // --- 10 000 km -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — óleo, filtros e correias (10.000 km)",
                "· Trocar o óleo do motor e o filtro de óleo\n"
                        + "· Substituir o filtro de ar (ou limpar, se o indicador ainda o permitir)\n"
                        + "· Inspecionar correias: tensão, fendas e desfiamento\n"
                        + "· Verificar níveis e procurar fugas por baixo da viatura\n\n"
                        + "Registar a quantidade e a especificação do óleo na ordem.",
                120, "Chave de filtros, recipiente de recolha, funil, EPIs",
                km(10_000), List.of(
                        peca("Filtro de óleo", "1", "un"),
                        peca("Filtro de ar", "1", "un"),
                        peca("Óleo do motor 15W-40", "38", "L"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — drenar separador de água (10.000 km)",
                "Drenar o separador de água e inspecionar as linhas de combustível. "
                        + "Em Angola a água no gasóleo é a causa mais comum de avaria de injeção.",
                20, "Recipiente de recolha, EPIs", km(10_000), List.of()));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — fluido, circuito e purga dos reservatórios (10.000 km)",
                "· Verificar o nível do fluido e procurar fugas\n"
                        + "· Purgar a água dos reservatórios de ar\n"
                        + "· Confirmar o curso do pedal e o tempo de enchimento",
                40, "Lanterna, recipiente, EPIs", km(10_000), List.of()));

        t.add(tarefa("RODADO", "Pneus e rodado",
                "Rodado — pressões, piso e aperto de rodas (10.000 km)",
                "· Medir a pressão a frio em cada posição\n"
                        + "· Medir a profundidade do piso e registar por posição\n"
                        + "· Reapertar as porcas de roda ao binário do fabricante",
                60, "Manómetro, medidor de piso, chave dinamométrica",
                km(10_000), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — bateria, terminais e iluminação (10.000 km)",
                "Verificar a tensão em repouso (12,4–12,9 V por bateria), limpar terminais e "
                        + "confirmar toda a iluminação e sinalização, incluindo a do reboque.",
                30, "Multímetro, escova de terminais", km(10_000), List.of()));

        // --- 20 000 km -------------------------------------------------------
        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — pastilhas, discos e tambores (20.000 km)",
                "Medir e registar a espessura das pastilhas ou maxilas e o estado de discos e "
                        + "tambores em cada eixo. Ajustar o curso e ensaiar a travagem.",
                150, "Paquímetro, macaco, EPIs",
                km(20_000), List.of(peca("Pastilhas de travão", "1", "jogo"))));

        t.add(tarefa("SUSPENSAO", "Suspensão e direção",
                "Suspensão — molas, jumelos, amortecedores e folgas (20.000 km)",
                "· Lubrificar pinos de mola e jumelos\n"
                        + "· Procurar molas partidas e fugas nos amortecedores\n"
                        + "· Verificar folgas na direção e nas rótulas",
                90, "Bomba de massa, pé de cabra, EPIs",
                km(20_000), List.of(peca("Massa lubrificante EP2", "1", "kg"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — filtros primário e secundário (20.000 km)",
                "Substituir os filtros primário e secundário. Purgar o circuito e confirmar "
                        + "que não há entradas de ar.",
                60, "Chave de filtros, recipiente", km(20_000), List.of(
                        peca("Filtro de combustível primário", "1", "un"),
                        peca("Filtro de combustível secundário", "1", "un"))));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — níveis, cruzetas e veio (20.000 km)",
                "Verificar o nível da caixa, lubrificar cruzetas e junta deslizante e "
                        + "procurar folgas no veio de transmissão.",
                60, "Bomba de massa, funil", km(20_000), List.of()));

        t.add(tarefa("RODADO", "Pneus e rodado",
                "Rodado — rotação e alinhamento (20.000 km)",
                "Rodar os pneus conforme o desgaste medido e verificar a convergência. "
                        + "Desgaste irregular é direção desalinhada — e são pneus a menos.",
                120, "Máquina de alinhamento, medidor de piso", km(20_000), List.of()));

        // --- 40 000 km -------------------------------------------------------
        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — óleo da caixa e embraiagem (40.000 km)",
                "Trocar o óleo da caixa, verificar o curso e a folga da embraiagem e o "
                        + "estado do cilindro auxiliar.",
                180, "Chave de bujões, funil, macaco",
                km(40_000), List.of(peca("Óleo da caixa", "14", "L"))));

        t.add(tarefa("EIXOS", "Eixos e diferenciais",
                "Eixos — óleo dos diferenciais e rolamentos de cubo (40.000 km)",
                "Trocar o óleo dos diferenciais e inspecionar rolamentos e retentores dos cubos.",
                180, "Chave de bujões, funil",
                km(40_000), List.of(peca("Óleo do diferencial SAE 80W-90", "18", "L"))));

        t.add(tarefa("MOTOR", "Motor", "Motor — turbo, intercooler e arrefecimento (40.000 km)",
                "Inspecionar o turbo (folga e fugas), as mangueiras do intercooler e o "
                        + "radiador. Verificar a concentração do líquido de arrefecimento.",
                90, "Lanterna, refratómetro", km(40_000), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — arranque, alternador e chicotes (40.000 km)",
                "Medir o consumo do motor de arranque e a carga do alternador (13,8–14,6 V). "
                        + "Inspecionar chicotes, fichas e passagens por arestas.",
                90, "Multímetro, pinça amperimétrica", km(40_000), List.of()));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — circuito pneumático e válvulas (40.000 km)",
                "Ensaiar a estanquidade do circuito, inspecionar válvulas, secador de ar e "
                        + "tubagens. Registar o tempo de queda de pressão.",
                120, "Manómetro, água com sabão", km(40_000), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — chassi, fixações e quinta roda (40.000 km)",
                "Verificar travessas, fixações da caixa e soldas. Limpar e lubrificar o prato "
                        + "da quinta roda e medir a folga do travamento.",
                90, "Chave dinamométrica, bomba de massa", km(40_000), List.of()));

        // --- 80 000 km -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — revisão maior (80.000 km)",
                "· Regular válvulas\n"
                        + "· Inspecionar e ensaiar injetores\n"
                        + "· Substituir correias\n"
                        + "· Substituir o líquido de arrefecimento",
                480, "Apalpa-folgas, chave dinamométrica",
                km(80_000), List.of(
                        peca("Correias", "1", "jogo"),
                        peca("Líquido de refrigeração", "40", "L"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — depósito, bomba e retorno (80.000 km)",
                "Limpar o depósito, ensaiar a bomba e medir o retorno dos bicos.",
                240, "Kit de ensaio de injeção, recipiente", km(80_000), List.of()));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — revisão geral (80.000 km)",
                "Revisão da caixa e do kit de embraiagem conforme o desgaste medido.",
                480, "Macaco de caixa, chave dinamométrica", km(80_000), List.of()));

        t.add(tarefa("SUSPENSAO", "Suspensão e direção",
                "Suspensão e direção — revisão completa (80.000 km)",
                "Substituir amortecedores e buchas conforme o estado, rever a caixa de "
                        + "direção e ensaiar o comportamento em estrada.",
                300, "Banco de ensaio, macaco", km(80_000), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e chassi",
                "Estrutura — inspeção estrutural completa (80.000 km)",
                "Inspeção de soldas, trincas e corrosão em todo o chassi e na caixa. "
                        + "Registar fotografias do que for reparado.",
                240, "Lanterna, líquidos penetrantes", km(80_000), List.of()));

        return t;
    }

    // ==== Ligeiro ==========================================================

    /**
     * O plano do ligeiro, por quilometragem e por tempo.
     *
     * <p>Um ligeiro de frota faz quilómetros a sério e ninguém repara nele até
     * deixar alguém a pé. Duas coisas não seguem o odómetro e vão aqui à parte:
     * o líquido dos travões, que envelhece por absorver humidade, e o ar
     * condicionado — que em Angola é condição de trabalho, não conforto.
     */
    private static List<TaskInput> lightVehicleTasks() {
        List<TaskInput> t = new ArrayList<>();

        // --- 10 000 km -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — óleo e filtro (10.000 km)",
                "· Trocar o óleo do motor e o filtro de óleo\n"
                        + "· Verificar níveis e procurar fugas\n"
                        + "· Inspecionar a correia de acessórios",
                60, "Chave de filtros, recipiente, funil",
                km(10_000), List.of(
                        peca("Filtro de óleo", "1", "un"),
                        peca("Óleo do motor 5W-30", "5", "L"))));

        t.add(tarefa("RODADO", "Pneus e rodado",
                "Rodado — pressões e piso (10.000 km)",
                "Medir a pressão a frio e a profundidade do piso nas quatro posições, "
                        + "incluindo o sobresselente. Mínimo legal: 1,6 mm.",
                30, "Manómetro, medidor de piso", km(10_000), List.of()));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — nível, fugas e curso do pedal (10.000 km)",
                "Verificar o nível do fluido, procurar fugas no circuito e confirmar o "
                        + "curso do pedal e o travão de mão.",
                30, "Lanterna, EPIs", km(10_000), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — bateria, luzes e escovas (10.000 km)",
                "Medir a tensão em repouso (12,4–12,9 V), limpar terminais, confirmar toda "
                        + "a iluminação e o estado das escovas do limpa-vidros.",
                30, "Multímetro, escova de terminais", km(10_000), List.of()));

        // --- 20 000 km -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — filtro de ar e de habitáculo (20.000 km)",
                "Substituir o filtro de ar e o filtro do habitáculo. Com a poeira das "
                        + "estradas de terra, ambos duram menos do que o manual indica.",
                45, "Chave de fendas", km(20_000), List.of(
                        peca("Filtro de ar", "1", "un"),
                        peca("Filtro de habitáculo", "1", "un"))));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — pastilhas e discos dianteiros (20.000 km)",
                "Medir e registar a espessura das pastilhas e dos discos dianteiros. "
                        + "Substituir o que estiver no limite.",
                90, "Paquímetro, macaco",
                km(20_000), List.of(peca("Pastilhas de travão dianteiras", "1", "jogo"))));

        t.add(tarefa("SUSPENSAO", "Suspensão e direção",
                "Suspensão — amortecedores, apoios e rótulas (20.000 km)",
                "Procurar fugas nos amortecedores, folgas nas rótulas e nos apoios, e "
                        + "ruídos em lombas. Verificar as proteções dos semieixos.",
                60, "Pé de cabra, macaco", km(20_000), List.of()));

        t.add(tarefa("RODADO", "Pneus e rodado",
                "Rodado — rotação e alinhamento (20.000 km)",
                "Rodar os pneus conforme o desgaste e verificar a convergência. Desgaste "
                        + "irregular é alinhamento — e são pneus a menos.",
                60, "Máquina de alinhamento", km(20_000), List.of()));

        // --- 40 000 km -------------------------------------------------------
        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — filtro e linhas (40.000 km)",
                "Substituir o filtro de combustível e inspecionar as linhas. Nos diesel, "
                        + "drenar a água do separador.",
                60, "Chave de filtros, recipiente",
                km(40_000), List.of(peca("Filtro de combustível", "1", "un"))));

        t.add(tarefa("MOTOR", "Motor", "Motor — velas ou injetores (40.000 km)",
                "Nos motores a gasolina, substituir as velas de ignição. Nos diesel, "
                        + "ensaiar os injetores e verificar as velas de pré-aquecimento.",
                90, "Chave de velas, kit de ensaio",
                km(40_000), List.of(peca("Velas de ignição", "1", "jogo"))));

        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — traseiros e travão de mão (40.000 km)",
                "Inspecionar pastilhas ou maxilas traseiras, tambores ou discos, e afinar "
                        + "o travão de mão.",
                90, "Paquímetro, macaco", km(40_000), List.of()));

        t.add(tarefa("TRANSMISSAO", "Sistema de transmissão",
                "Transmissão — óleo e embraiagem (40.000 km)",
                "Verificar o nível (ou substituir, conforme o fabricante) o óleo da caixa e "
                        + "confirmar o curso e a folga da embraiagem.",
                90, "Chave de bujões, funil", km(40_000), List.of()));

        // --- 60 000 km -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — distribuição e arrefecimento (60.000 km)",
                "Substituir a correia (ou verificar a corrente) de distribuição conforme o "
                        + "manual do fabricante, a bomba de água e o líquido de arrefecimento. "
                        + "É a intervenção que, adiada, parte o motor.",
                360, "Chave dinamométrica, pinos de calagem",
                km(60_000), List.of(
                        peca("Kit de distribuição", "1", "kit"),
                        peca("Líquido de refrigeração", "6", "L"))));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — alternador, arranque e bateria (60.000 km)",
                "Medir a carga do alternador (13,8–14,6 V), o consumo do motor de arranque "
                        + "e ensaiar a bateria em carga.",
                60, "Multímetro, pinça amperimétrica, testador de baterias",
                km(60_000), List.of()));

        t.add(tarefa("SUSPENSAO", "Suspensão e direção",
                "Suspensão e direção — revisão (60.000 km)",
                "Rever amortecedores, buchas, rolamentos de roda e a direção assistida. "
                        + "Ensaiar o comportamento em estrada.",
                180, "Macaco, banco de ensaio", km(60_000), List.of()));

        // --- Por tempo, não por quilómetros ----------------------------------
        t.add(tarefa("TRAVAGEM", "Sistema de travagem",
                "Travagem — substituir o líquido (24 meses)",
                "O líquido dos travões absorve humidade e perde ponto de ebulição mesmo "
                        + "com a viatura parada: numa descida longa, ferve e o pedal vai ao "
                        + "fundo. Medir o teor de água antes e registar.",
                90, "Kit de purga, medidor de humidade",
                List.of(new TriggerInput(PlanTriggerType.CALENDAR_DAYS, null,
                        BigDecimal.valueOf(730), BigDecimal.valueOf(30))),
                List.of(peca("Líquido de travões DOT 4", "1", "L"))));

        t.add(tarefa("CLIMATIZACAO", "Climatização",
                "Ar condicionado — higienização e carga de gás (12 meses)",
                "Verificar a carga de gás, o funcionamento do compressor e higienizar o "
                        + "evaporador. Num ligeiro de frota em Angola, o ar condicionado é "
                        + "condição de trabalho, não conforto.",
                90, "Máquina de recuperação de gás, produto de higienização",
                List.of(new TriggerInput(PlanTriggerType.CALENDAR_DAYS, null,
                        BigDecimal.valueOf(365), BigDecimal.valueOf(15))),
                List.of()));

        return t;
    }

    // ==== Gerador ==========================================================

    /**
     * O plano do gerador, sistema a sistema, por horas de funcionamento.
     *
     * <p>Um grupo electrogéneo tem duas vidas: as horas que trabalha e os meses
     * em que está parado à espera de fazer falta. Por isso o plano começa no
     * ensaio semanal em carga — o único que prova que ele arranca — e só depois
     * segue a matriz por horas. O que o distingue de um motor qualquer é a parte
     * elétrica: alternador, quadro e transferência automática.
     */
    private static List<TaskInput> generatorTasks() {
        List<TaskInput> t = new ArrayList<>();

        t.add(tarefa("ENSAIO", "Ensaio", "Ensaio semanal em carga",
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
                List.of()));

        // --- 250 horas -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — óleo e filtros (250 h)",
                "· Trocar o óleo do motor e o filtro de óleo\n"
                        + "· Substituir o filtro de combustível\n"
                        + "· Inspecionar correias e mangueiras",
                120, "Chave de filtros, recipiente, funil",
                horas(250), List.of(
                        peca("Filtro de óleo", "1", "un"),
                        peca("Filtro de combustível", "1", "un"),
                        peca("Óleo do motor 15W-40", "18", "L"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — separador de água e linhas (250 h)",
                "Drenar o separador de água, verificar as linhas e confirmar o nível do "
                        + "depósito diário e do depósito principal.",
                30, "Recipiente de recolha, EPIs", horas(250), List.of()));

        t.add(tarefa("ARREFECIMENTO", "Sistema de arrefecimento",
                "Arrefecimento — nível e concentração (250 h)",
                "Verificar o nível do líquido, medir a concentração anticongelante e "
                        + "inspecionar mangueiras e abraçadeiras.",
                30, "Refratómetro, lanterna", horas(250), List.of()));

        t.add(tarefa("ELETRICO", "Sistema elétrico",
                "Elétrico — bateria de arranque (250 h)",
                "Medir a tensão em repouso, limpar e proteger os terminais e confirmar que "
                        + "o carregador de manutenção está a funcionar.",
                30, "Multímetro, escova de terminais, massa de proteção",
                horas(250), List.of()));

        // --- 500 horas -------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — filtro de ar e estanquidade (500 h)",
                "Substituir o filtro de ar, verificar o indicador de restrição e procurar "
                        + "fugas de óleo, água e gases de escape.",
                60, "Chave de filtros, lanterna",
                horas(500), List.of(peca("Filtro de ar", "1", "un"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — filtros primário e secundário (500 h)",
                "Substituir ambos os filtros e purgar o circuito.",
                60, "Chave de filtros, recipiente", horas(500), List.of(
                        peca("Filtro de combustível primário", "1", "un"),
                        peca("Filtro de combustível secundário", "1", "un"))));

        t.add(tarefa("ALTERNADOR", "Alternador",
                "Alternador — ligações de potência e ventilação (500 h)",
                "Reapertar as ligações de potência ao binário, inspecionar o isolamento "
                        + "visível dos cabos e limpar as grelhas de ventilação do alternador.",
                90, "Chave dinamométrica, aspirador industrial",
                horas(500), List.of()));

        t.add(tarefa("QUADRO", "Quadro e transferência",
                "Quadro — ensaio da transferência automática (500 h)",
                "Simular falha de rede e cronometrar a comutação. Confirmar temporizações, "
                        + "alarmes e o regresso automático à rede.",
                90, "Multímetro, cronómetro", horas(500), List.of()));

        // --- 1000 horas ------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — válvulas e análise de óleo (1000 h)",
                "Regular as folgas de válvulas e recolher amostra de óleo para análise "
                        + "(desgaste interno e contaminação).",
                240, "Apalpa-folgas, kit de amostra", horas(1000), List.of()));

        t.add(tarefa("ARREFECIMENTO", "Sistema de arrefecimento",
                "Arrefecimento — radiador, bomba e termostato (1000 h)",
                "Limpar o radiador por fora, ensaiar o termostato e verificar folga e fugas "
                        + "na bomba de água.",
                120, "Máquina de lavar a baixa pressão, termómetro",
                horas(1000), List.of()));

        t.add(tarefa("ALTERNADOR", "Alternador",
                "Alternador — resistência de isolamento (1000 h)",
                "Medir a resistência de isolamento dos enrolamentos com megóhmetro e "
                        + "registar o valor. A tendência ao longo do tempo é o que interessa: "
                        + "um valor a descer avisa antes de o alternador queimar.",
                120, "Megóhmetro", horas(1000), List.of()));

        t.add(tarefa("QUADRO", "Quadro e transferência",
                "Quadro — termografia e aperto de ligações (1000 h)",
                "Termografar o quadro em carga e reapertar as ligações que aquecerem. "
                        + "Uma ligação frouxa aquece muito antes de arder.",
                90, "Câmara termográfica, chave dinamométrica",
                horas(1000), List.of()));

        t.add(tarefa("ESTRUTURA", "Estrutura e ventilação",
                "Estrutura — escape, ventilação e depósito (1000 h)",
                "Inspecionar escape e silenciador, confirmar que as grelhas de entrada e "
                        + "saída de ar estão desobstruídas e verificar fixações e fugas no depósito.",
                90, "Lanterna, chave dinamométrica", horas(1000), List.of()));

        // --- 2000 horas ------------------------------------------------------
        t.add(tarefa("MOTOR", "Motor", "Motor — revisão maior (2000 h)",
                "· Inspecionar e ensaiar injetores\n"
                        + "· Verificar o turbo\n"
                        + "· Substituir correias e líquido de arrefecimento",
                480, "Kit de ensaio de injeção, chave dinamométrica",
                horas(2000), List.of(
                        peca("Correias", "1", "jogo"),
                        peca("Líquido de refrigeração", "25", "L"))));

        t.add(tarefa("COMBUSTIVEL", "Sistema de combustível",
                "Combustível — limpeza do depósito e do circuito (2000 h)",
                "Limpar o depósito (borras e água acumuladas), verificar o retorno dos bicos "
                        + "e a bomba de transferência.",
                240, "Bomba de trasfega, recipiente", horas(2000), List.of()));

        t.add(tarefa("ALTERNADOR", "Alternador",
                "Alternador — revisão completa (2000 h)",
                "Rever rolamentos, regulador de tensão (AVR) e ponte rectificadora; medir "
                        + "novamente o isolamento no fim.",
                300, "Megóhmetro, extractor de rolamentos", horas(2000), List.of()));

        t.add(tarefa("QUADRO", "Quadro e transferência",
                "Quadro — revisão completa (2000 h)",
                "Rever contactores, disjuntores, sensores e a unidade de comando; ensaiar "
                        + "todos os alarmes e paragens de emergência.",
                240, "Multímetro, mala de ensaio", horas(2000), List.of()));

        t.add(tarefa("ENSAIO", "Ensaio", "Ensaio de carga prolongado (2000 h)",
                "Quatro horas em carga a pelo menos 75 % da potência nominal, com registo "
                        + "de temperaturas, tensões e consumo. É o ensaio que valida a revisão.",
                300, "Banco de carga, pinça amperimétrica, termómetro",
                horas(2000), List.of()));

        return t;
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
            case "LIGHT_VEHICLE" -> new SaveTemplateRequest(
                    "Inspeção diária — ligeiro",
                    assetTypeId,
                    "A fazer antes de sair, todos os dias. Leva cinco minutos e evita "
                            + "ficar a pé a 200 km de casa.",
                    8,
                    List.of(
                            item("Nível do óleo do motor", VERIFY, true),
                            item("Nível do líquido de arrefecimento", VERIFY, true),
                            item("Pressão e piso dos pneus, incluindo o sobresselente", INSPECT, true),
                            item("Manchas de óleo ou água por baixo da viatura", INSPECT, true),
                            item("Luzes, piscas, stops e buzina", TEST, true),
                            item("Travões e travão de mão", TEST, true),
                            item("Água do limpa-vidros e estado das escovas", VERIFY, false),
                            item("Triângulo, colete e macaco a bordo", VERIFY, true),
                            item("Documentos da viatura e do condutor", VERIFY, true)));
            case "GENERATOR" -> new SaveTemplateRequest(
                    "Inspeção diária — gerador",
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
            case "LIGHT_VEHICLE" -> List.of(
                    programa(PredictiveTechnique.OIL_ANALYSIS, 12,
                            "Motor",
                            "Avaliar desgaste interno sem abrir o motor"),
                    programa(PredictiveTechnique.ALIGNMENT, 6,
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
            case "LIGHT_VEHICLE" -> List.of(
                    parte("Filtro de óleo", "MOTOR", "un", "2"),
                    parte("Filtro de ar", "MOTOR", "un", "2"),
                    parte("Filtro de habitáculo", "MOTOR", "un", "2"),
                    parte("Filtro de combustível", "COMBUSTIVEL", "un", "1"),
                    parte("Pastilhas de travão dianteiras", "TRAVAGEM", "jogo", "1"),
                    parte("Líquido de travões DOT 4", "TRAVAGEM", "L", "2"),
                    parte("Escovas do limpa-vidros", "ELETRICO", "jogo", "1"),
                    parte("Lâmpadas", "ELETRICO", "un", "4"));
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
            case "LIGHT_VEHICLE" -> new CriticalityRequest(3, 4, 2, null,
                    "Ligeiro de frota: a paragem resolve-se com outra viatura, mas o risco "
                            + "rodoviário de quem a conduz é o mesmo de um pesado.");
            case "GENERATOR" -> new CriticalityRequest(5, 4, 4, null,
                    "Energia de emergência: quando falha, falha tudo o que dela depende.");
            default -> null;
        };
    }
}
