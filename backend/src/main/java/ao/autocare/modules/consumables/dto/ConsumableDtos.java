package ao.autocare.modules.consumables.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * O que o painel de pneus, óleos e filtros devolve.
 *
 * <p>Tudo aqui responde a uma única pergunta de gestão: <b>o que tenho de
 * trocar nos próximos dias, em que equipamento, com que peça — e tenho-a em
 * armazém?</b> Por isso cada linha carrega sempre três coisas juntas: o que
 * falta (sulco, quilómetros, dias), de onde vem a conta (medições ou vida
 * declarada) e o que isso obriga a comprar.
 */
public final class ConsumableDtos {

    private ConsumableDtos() {}

    /** Estados usados em todo o painel, do mais grave para o menos. */
    public static final String CRITICO = "CRITICO";
    public static final String AVISO = "AVISO";
    public static final String OK = "OK";
    public static final String SEM_DADOS = "SEM_DADOS";

    /** O painel completo. */
    public record Painel(
            int horizonteDias,
            Resumo resumo,
            List<PneuLinha> pneus,
            List<ServicoLinha> oleos,
            List<ServicoLinha> filtros,
            List<CompraLinha> compras) {}

    /**
     * Os números do topo do painel.
     *
     * <p><b>semMedicao</b> e <b>semPlano</b> não são defeitos do sistema: são
     * a parte da frota que ninguém está a controlar. Mostrá-los é metade do
     * valor do painel — é o que faz o gestor perceber que tem 14 pneus sem
     * uma única medição de sulco.
     */
    public record Resumo(
            int pneusCriticos,
            int pneusAviso,
            int pneusMontados,
            int pneusSemMedicao,
            int oleosVencidos,
            int oleosAVencer,
            int filtrosVencidos,
            int filtrosAVencer,
            int pecasEmFalta,
            int encomendasUrgentes,
            int equipamentosSemPlano,
            BigDecimal custoEstimado,
            String moeda) {}

    /** Um pneu montado, com a vida que lhe resta. */
    public record PneuLinha(
            String tyreId,
            String assetId,
            String assetTag,
            String assetName,
            String position,
            String brand,
            String model,
            String size,
            String serialNumber,
            BigDecimal treadMm,
            BigDecimal minTreadMm,
            BigDecimal pressure,
            BigDecimal targetPressure,
            Integer pressaoPct,
            BigDecimal kmRodados,
            BigDecimal desgastePor1000Km,
            BigDecimal kmRestantes,
            Integer diasRestantes,
            LocalDate dataPrevista,
            Integer vidaUsadaPct,
            BigDecimal custoPorKm,
            Integer medidoHaDias,
            String base,
            String estado,
            String motivo,
            String acao) {}

    /** Uma troca de óleo ou de filtro que está a chegar. */
    public record ServicoLinha(
            String taskId,
            String assetId,
            String assetTag,
            String assetName,
            String titulo,
            String sistema,
            String tipo,
            String medidor,
            String unidadeMedidor,
            BigDecimal ultimoMedidor,
            Instant ultimaData,
            BigDecimal proximoMedidor,
            Instant proximaData,
            BigDecimal restanteMedidor,
            Integer restanteDias,
            Integer diasProjetados,
            LocalDate dataPrevista,
            Integer vidaUsadaPct,
            BigDecimal litros,
            String consumoAnormal,
            List<PecaLinha> pecas,
            String estado,
            String motivo) {}

    /** Uma peça que a tarefa pede, confrontada com o armazém. */
    public record PecaLinha(
            String partId,
            String nome,
            String partNumber,
            String especificacao,
            String categoria,
            BigDecimal quantidade,
            String unidade,
            BigDecimal stock,
            boolean noCatalogo,
            boolean suficiente,
            Integer prazoDias) {}

    /** Uma linha da lista de compras: o somatório de tudo o que vence. */
    public record CompraLinha(
            String partId,
            String nome,
            String partNumber,
            String especificacao,
            String categoria,
            String unidade,
            BigDecimal necessario,
            BigDecimal stock,
            BigDecimal aComprar,
            BigDecimal custoEstimado,
            String moeda,
            Integer prazoDias,
            LocalDate precisaEm,
            LocalDate encomendarAte,
            boolean urgente,
            boolean noCatalogo,
            List<String> equipamentos) {}
}
