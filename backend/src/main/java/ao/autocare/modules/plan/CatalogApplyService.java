package ao.autocare.modules.plan;

import ao.autocare.common.ApiException;
import ao.autocare.domain.enums.Enums.PredictiveTechnique;
import ao.autocare.modules.checklist.ChecklistTemplateService;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ItemInput;
import ao.autocare.modules.checklist.dto.ChecklistDtos.SaveTemplateRequest;
import ao.autocare.modules.part.StockService;
import ao.autocare.modules.part.dto.PartDtos.SavePartRequest;
import ao.autocare.modules.plan.dto.AssetPlanDtos.AssignPlanRequest;
import ao.autocare.modules.predictive.PredictiveService;
import ao.autocare.modules.predictive.dto.PredictiveDtos.SaveProgramRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Aplica um plano de catálogo por inteiro.
 *
 * <p>O plano de manutenção de um fabricante não é só a tabela das horas. Traz
 * também a inspeção diária, a monitorização preditiva e a lista de peças que
 * têm de estar em armazém — e um plano aplicado sem essas três partes deixa a
 * empresa a pensar que está coberta quando não está.
 *
 * <p>A inspeção diária é a peça mais importante e a mais esquecida: quinze
 * minutos antes do arranque apanham fugas, níveis e travões, que é onde as
 * avarias caras começam.
 */
@Service
public class CatalogApplyService {

    private static final Logger log = LoggerFactory.getLogger(CatalogApplyService.class);

    private final PlanService plans;
    private final AssetPlanService assetPlans;
    private final ChecklistTemplateService checklists;
    private final PredictiveService predictive;
    private final StockService stock;
    private final ao.autocare.modules.asset.AssetService assetService;
    private final ao.autocare.repo.AssetRepository assets;

    public CatalogApplyService(
            PlanService plans,
            AssetPlanService assetPlans,
            ChecklistTemplateService checklists,
            PredictiveService predictive,
            StockService stock,
            ao.autocare.modules.asset.AssetService assetService,
            ao.autocare.repo.AssetRepository assets) {
        this.plans = plans;
        this.assetPlans = assetPlans;
        this.checklists = checklists;
        this.predictive = predictive;
        this.stock = stock;
        this.assetService = assetService;
        this.assets = assets;
    }

    /** O que foi criado, para o ecrã poder dizê-lo sem adivinhar. */
    public record Resultado(
            String planId,
            String planName,
            int taskCount,
            String checklistTemplateId,
            int checklistItemCount,
            int predictiveProgramCount,
            int partCount,
            /** Criticidade atribuída ao ativo, quando o modelo a traz. */
            String criticality,
            List<String> avisos) {}

    /*
     * Sem @Transactional de proposito.
     *
     * Cada servico chamado aqui tem transacao propria. Quando um deles recusa
     * -- uma peca que ja existe em armazem, o caso normal ao aplicar o plano a
     * uma segunda maquina igual -- essa transacao interna marca a de fora para
     * reversao. Apanhar a excecao nao desfaz a marca: o metodo corria ate ao
     * fim e rebentava no commit com "transaction silently rolled back",
     * deitando fora o plano inteiro por causa de um filtro repetido.
     *
     * E o mesmo erro que ja tinha aparecido na importacao de abastecimentos.
     * Sem transacao a envolver tudo, cada peca falha por si e o resto entra --
     * que e o que a lista de avisos devolvida promete.
     */
    public Resultado apply(
            String orgId, String userId, String code,
            String assetTypeId, String assetId,
            boolean comChecklist, boolean comPreditiva, boolean comPecas) {

        List<String> avisos = new ArrayList<>();

        // ---- o plano de horas ---------------------------------------------
        //
        // A idade do equipamento entra aqui. Um camião de 2005 não se mantém
        // com os intervalos de um de 2023: as mangueiras, as correias e o
        // líquido de travões envelhecem no tempo, e não nos quilómetros. Quando
        // a viatura passa dos dez anos, esses sistemas são encurtados — e o
        // motivo fica escrito no plano, para ninguém pensar que foi engano.
        var pedido = PlanCatalog.build(code, assetTypeId);
        Integer ano = null;
        if (assetId != null && !assetId.isBlank()) {
            ano = assets.findByIdAndOrganizationId(assetId, orgId)
                    .map(a -> a.getModelYear()).orElse(null);
        }
        String notaIdade = VehicleTaxonomy.notaDeIdade(ano);
        if (notaIdade != null) {
            pedido = comIntervalosDeIdade(pedido, VehicleTaxonomy.factorDeIdade(ano), notaIdade);
            avisos.add(notaIdade);
        }
        var plano = plans.create(orgId, userId, pedido);

        // Atribuir ao ativo, se foi indicado. Sem isto o plano existe mas não
        // conta a ninguém — e é aí que as empresas ficam com planos no papel.
        if (assetId != null && !assetId.isBlank()) {
            try {
                assetPlans.assign(orgId, userId, assetId,
                        new AssignPlanRequest(plano.id(), Boolean.TRUE, null, null));
            } catch (ApiException e) {
                avisos.add("O plano foi criado mas não ficou atribuído: " + e.getMessage());
            }
        }

        // ---- inspeção diária ----------------------------------------------
        String checklistId = null;
        int itens = 0;
        if (comChecklist) {
            var modelo = PlanCatalog.dailyChecklist(code, assetTypeId);
            if (modelo != null) {
                try {
                    var criado = checklists.create(orgId, userId, modelo);
                    checklistId = criado.id();
                    itens = modelo.items().size();
                } catch (ApiException e) {
                    avisos.add("Inspeção diária não criada: " + e.getMessage());
                }
            }
        }

        // ---- monitorização preditiva ---------------------------------------
        int programas = 0;
        if (comPreditiva) {
            if (assetId == null || assetId.isBlank()) {
                avisos.add("A monitorização preditiva é por equipamento — "
                        + "escolha o ativo para a criar.");
            } else {
                for (SaveProgramRequest p : PlanCatalog.predictivePrograms(code)) {
                    try {
                        predictive.create(orgId, userId, assetId, p);
                        programas++;
                    } catch (ApiException e) {
                        avisos.add("Programa " + p.technique().label() + ": " + e.getMessage());
                    }
                }
            }
        }

        // ---- peças de armazém ----------------------------------------------
        int pecas = 0;
        if (comPecas) {
            for (SavePartRequest p : PlanCatalog.spareParts(code)) {
                try {
                    stock.createPart(orgId, userId, p);
                    pecas++;
                } catch (ApiException e) {
                    // Peça repetida é o caso normal ao aplicar o plano a uma
                    // segunda máquina igual: não é erro, é o armazém já ter.
                    log.debug("Peça '{}' não criada: {}", p.name(), e.getMessage());
                }
            }
        }

        // ---- criticidade ----------------------------------------------------
        // Não é um número decorativo: decide a ordem por que as avarias são
        // atendidas quando há três máquinas paradas e um mecânico.
        String criticidade = null;
        var avaliacao = PlanCatalog.criticality(code);
        if (avaliacao != null && assetId != null && !assetId.isBlank()) {
            try {
                var c = assetService.setCriticality(orgId, userId, assetId, avaliacao);
                criticidade = c.overall();
            } catch (ApiException e) {
                avisos.add("Criticidade não atribuída: " + e.getMessage());
            }
        }

        return new Resultado(plano.id(), plano.name(), plano.tasks().size(),
                checklistId, itens, programas, pecas, criticidade, avisos);
    }

    /** As técnicas que o catálogo prevê, para o ecrã as poder mostrar antes. */
    public static List<PredictiveTechnique> tecnicasDe(String code) {
        return PlanCatalog.predictivePrograms(code).stream()
                .map(SaveProgramRequest::technique).toList();
    }

    static SaveTemplateRequest modeloChecklist(
            String nome, String assetTypeId, String descricao, int minutos, List<ItemInput> itens) {
        return new SaveTemplateRequest(nome, assetTypeId, descricao, minutos, itens);
    }

    /**
     * Encurta os intervalos do que envelhece com o tempo.
     *
     * <p>Só travagem, arrefecimento, mangueiras e correias — e nunca alarga
     * nada. Uma viatura velha não pode esperar mais do que uma nova; pode ter
     * de esperar menos.
     */
    private static ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest comIntervalosDeIdade(
            ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest pedido, double factor, String nota) {

        java.util.Set<String> sensiveis = java.util.Set.of(
                "TRAVAGEM", "ARREFECIMENTO", "MOTOR", "COMBUSTIVEL");

        java.util.List<ao.autocare.modules.plan.dto.PlanDtos.TaskInput> tarefas = new ArrayList<>();
        for (var tarefa : pedido.tasks()) {
            if (!sensiveis.contains(tarefa.systemCode()) || tarefa.triggers() == null) {
                tarefas.add(tarefa);
                continue;
            }
            var gatilhos = new ArrayList<ao.autocare.modules.plan.dto.PlanDtos.TriggerInput>();
            for (var g : tarefa.triggers()) {
                java.math.BigDecimal intervalo = g.interval();
                if (intervalo != null) {
                    intervalo = intervalo.multiply(java.math.BigDecimal.valueOf(factor))
                            .setScale(0, java.math.RoundingMode.HALF_UP);
                }
                gatilhos.add(new ao.autocare.modules.plan.dto.PlanDtos.TriggerInput(
                        g.type(), g.meterKind(), intervalo, g.tolerance()));
            }
            tarefas.add(new ao.autocare.modules.plan.dto.PlanDtos.TaskInput(
                    tarefa.systemCode(), tarefa.systemName(), tarefa.title(),
                    tarefa.instructions(), tarefa.estimatedMinutes(), tarefa.tools(),
                    gatilhos, tarefa.parts()));
        }
        String notas = (pedido.notes() == null ? "" : pedido.notes() + "\n\n") + nota;
        return new ao.autocare.modules.plan.dto.PlanDtos.SavePlanRequest(pedido.name(), pedido.assetTypeId(), pedido.description(),
                notas, pedido.objective(), pedido.sourceReference(), pedido.preparedByLabel(),
                tarefas);
    }
}
