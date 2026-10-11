package ao.autocare.modules.consumables;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetPlanTask;
import ao.autocare.domain.Part;
import ao.autocare.domain.PlanTaskPart;
import ao.autocare.domain.Tyre;
import ao.autocare.domain.TyreReading;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.modules.consumables.dto.ConsumableDtos.CompraLinha;
import ao.autocare.modules.consumables.dto.ConsumableDtos.Painel;
import ao.autocare.modules.consumables.dto.ConsumableDtos.PecaLinha;
import ao.autocare.modules.consumables.dto.ConsumableDtos.PneuLinha;
import ao.autocare.modules.consumables.dto.ConsumableDtos.Resumo;
import ao.autocare.modules.consumables.dto.ConsumableDtos.ServicoLinha;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetPlanTaskRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.FluidTopUpRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.PlanTaskPartRepository;
import ao.autocare.repo.StockItemRepository;
import ao.autocare.repo.TyreReadingRepository;
import ao.autocare.repo.TyreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pneus, óleos e filtros: o controlo que faz ou desfaz uma frota.
 *
 * <p>É o sítio mais caro de uma empresa de transportes depois do combustível, e
 * o mais fácil de perder de vista: um pneu troca-se quando «já está mau», o
 * óleo quando «parece que já foi há muito», o filtro quando alguém se lembra. O
 * resultado é sempre o mesmo — motores abertos antes do tempo e pneus
 * deitados fora com metade da borracha por usar.
 *
 * <p>Os sistemas grandes resolvem isto de uma única forma: <b>cada consumível é
 * um componente com vida própria</b>. Tem uma vida esperada, uma vida já
 * consumida e uma taxa a que se gasta — e com essas três coisas prevê-se a
 * data de troca em vez de se esperar pela avaria. Este serviço faz exactamente
 * isso:
 *
 * <ul>
 *   <li><b>Pneus</b> — a taxa de desgaste sai das medições de sulco: milímetros
 *       perdidos por mil quilómetros. Daí tiram-se os quilómetros que faltam
 *       até ao sulco mínimo e, com a média diária do contador, a data.</li>
 *   <li><b>Óleos e filtros</b> — o relógio de cada tarefa do plano já diz
 *       quanto falta em horas ou quilómetros; o que faltava era passar isso a
 *       <i>data</i> e confrontá-lo com o armazém.</li>
 *   <li><b>Lista de compras</b> — tudo o que vence no horizonte, somado por
 *       artigo, menos o que já existe em armazém, recuado pelo prazo de entrega
 *       do fornecedor. Em Angola é esta conta que decide se a máquina espera
 *       três semanas por um filtro.</li>
 * </ul>
 *
 * <p>O que o painel não sabe, diz: um pneu sem medições de sulco aparece como
 * «sem dados» e não como «em ordem». Essa distinção é o que impede o painel de
 * mentir a quem o lê.
 */
@Service
public class ConsumablesService {

    /** Horizonte por omissão: o mês que o gestor tem à frente. */
    private static final int HORIZONTE_PADRAO = 30;

    /** Abaixo disto um pneu entra em aviso mesmo sem a data estar perto. */
    private static final BigDecimal KM_AVISO_PNEU = new BigDecimal("2000");

    /** Dias sem medir um pneu a partir dos quais o controlo deixou de existir. */
    private static final int DIAS_SEM_MEDIR = 45;

    private static final BigDecimal MIL = new BigDecimal("1000");
    private static final BigDecimal CEM = new BigDecimal("100");

    // Um texto é de óleo se falar de óleo, lubrificante ou massa — depois de se
    // tirar de lá os «filtros de óleo», que são outra coisa e outra peça.
    private static final Pattern FILTRO_DE_OLEO =
            Pattern.compile("(?i)filtros?\\s+(de\\s+|do\\s+|da\\s+)?[óo]leo");
    private static final Pattern OLEO = Pattern.compile(
            "(?i)[óo]leo|lubrific|massa\\s+consist|massa\\s+lubrific|graxa|fluido\\s+hidr|atf");
    private static final Pattern FILTRO = Pattern.compile(
            "(?i)filtro|elemento\\s+filtrante|secador\\s+de\\s+ar|separador\\s+de\\s+[áa]gua");

    private final TyreRepository tyres;
    private final TyreReadingRepository readings;
    private final AssetPlanTaskRepository planTasks;
    private final PlanTaskPartRepository planTaskParts;
    private final AssetMeterRepository meters;
    private final PartRepository parts;
    private final StockItemRepository stock;
    private final FluidTopUpRepository fluids;
    private final AssetRepository assets;
    private final ao.autocare.repo.AssetPlanRepository assetPlans;

    public ConsumablesService(
            TyreRepository tyres,
            TyreReadingRepository readings,
            AssetPlanTaskRepository planTasks,
            PlanTaskPartRepository planTaskParts,
            AssetMeterRepository meters,
            PartRepository parts,
            StockItemRepository stock,
            FluidTopUpRepository fluids,
            AssetRepository assets,
            ao.autocare.repo.AssetPlanRepository assetPlans) {
        this.tyres = tyres;
        this.readings = readings;
        this.planTasks = planTasks;
        this.planTaskParts = planTaskParts;
        this.meters = meters;
        this.parts = parts;
        this.stock = stock;
        this.fluids = fluids;
        this.assets = assets;
        this.assetPlans = assetPlans;
    }

    // ==== O painel ==========================================================

    /**
     * O painel inteiro para uma empresa.
     *
     * @param horizonteDias quantos dias para a frente contam como «a vencer»
     * @param custos se quem pede pode ver dinheiro (custo por km, lista valorizada)
     */
    @Transactional(readOnly = true)
    public Painel painel(String orgId, Integer horizonteDias, boolean custos) {
        int horizonte = horizonteDias == null || horizonteDias <= 0
                ? HORIZONTE_PADRAO
                : Math.min(horizonteDias, 365);
        LocalDate hoje = LocalDate.now(ZoneOffset.UTC);

        Map<String, AssetMeter> contadores = new HashMap<>();
        for (AssetMeter m : meters.findPrimaryForOrg(orgId)) {
            contadores.put(m.getAsset().getId(), m);
        }
        Map<String, BigDecimal> stockPorPeca = new HashMap<>();
        for (Object[] r : stock.totalsByPartForOrg(orgId)) {
            stockPorPeca.put((String) r[0], (BigDecimal) r[1]);
        }
        List<Part> catalogo = parts.findByOrganizationIdOrderByNameAsc(orgId);
        Map<String, Part> porNome = new HashMap<>();
        for (Part p : catalogo) {
            porNome.putIfAbsent(chave(p.getName()), p);
        }

        List<PneuLinha> pneus = pneus(orgId, contadores, horizonte, hoje, custos);
        List<ServicoLinha> oleos = new ArrayList<>();
        List<ServicoLinha> filtros = new ArrayList<>();
        servicos(orgId, contadores, stockPorPeca, porNome, horizonte, hoje, oleos, filtros);

        List<CompraLinha> compras =
                compras(oleos, filtros, pneus, catalogo, stockPorPeca, porNome, hoje, custos);

        return new Painel(horizonte, resumo(pneus, oleos, filtros, compras, orgId, custos),
                pneus, oleos, filtros, compras);
    }

    /**
     * Liga as peças dos planos aos artigos do armazém, pelo nome.
     *
     * <p>Os planos do catálogo escrevem «Filtro de óleo do motor» em texto; o
     * armazém tem um artigo com esse nome. Enquanto os dois não estiverem
     * ligados, o painel consegue mostrar a peça mas não o stock — e é o stock
     * que decide se a máquina para. Isto fecha essa ligação de uma vez para
     * toda a empresa, sem obrigar ninguém a reescrever os planos.
     *
     * @return quantas linhas de plano ficaram ligadas
     */
    @Transactional
    public int ligarAoArmazem(String orgId) {
        Map<String, Part> porNome = new HashMap<>();
        for (Part p : parts.findByOrganizationIdOrderByNameAsc(orgId)) {
            porNome.putIfAbsent(chave(p.getName()), p);
        }
        if (porNome.isEmpty()) {
            return 0;
        }
        int ligadas = 0;
        List<PlanTaskPart> soltas = planTaskParts.findUnlinkedForOrg(orgId);
        for (PlanTaskPart p : soltas) {
            Part encontrada = porNome.get(chave(p.getPartName()));
            if (encontrada != null) {
                p.setPart(encontrada);
                ligadas++;
            }
        }
        if (ligadas > 0) {
            planTaskParts.saveAll(soltas);
        }
        return ligadas;
    }

    // ==== Pneus =============================================================

    private List<PneuLinha> pneus(
            String orgId,
            Map<String, AssetMeter> contadores,
            int horizonte,
            LocalDate hoje,
            boolean custos) {
        List<Tyre> montados = tyres.findByOrganizationIdOrderByStatusAscUpdatedAtDesc(orgId).stream()
                .filter(t -> t.getStatus() == Tyre.Status.INSTALLED)
                .filter(t -> t.getAsset() != null && !t.getAsset().isArchived())
                .toList();
        if (montados.isEmpty()) {
            return List.of();
        }

        Map<String, List<TyreReading>> historico = new HashMap<>();
        for (TyreReading r : readings.findByTyreIds(montados.stream().map(Tyre::getId).toList())) {
            historico.computeIfAbsent(r.getTyre().getId(), k -> new ArrayList<>()).add(r);
        }

        List<PneuLinha> linhas = new ArrayList<>();
        for (Tyre t : montados) {
            Asset a = t.getAsset();
            AssetMeter m = contadores.get(a.getId());
            BigDecimal atual = m != null ? m.getCurrentValue() : null;
            BigDecimal rodados = t.distanceRun(atual);
            List<TyreReading> hist = historico.getOrDefault(t.getId(), List.of());

            Desgaste d = desgaste(t, hist, rodados);
            Integer dias = projetarDias(d.kmRestantes(), m);
            LocalDate prevista = dias == null ? null : hoje.plusDays(dias);
            Integer medidoHa = t.getLastMeasuredAt() == null
                    ? null
                    : (int) ChronoUnit.DAYS.between(t.getLastMeasuredAt(), Instant.now());
            Integer pressaoPct = percentagemDaPressao(t);

            Avaliacao av = avaliarPneu(t, d, dias, medidoHa, pressaoPct, horizonte);
            linhas.add(new PneuLinha(
                    t.getId(), a.getId(), a.getTag(), a.getName(), t.getPosition(),
                    t.getBrand(), t.getModel(), t.getSize(), t.getSerialNumber(),
                    t.getLastTreadMm(), t.getMinTreadMm(),
                    t.getLastPressure(), t.getTargetPressure(), pressaoPct,
                    rodados, d.por1000Km(), d.kmRestantes(), dias, prevista, d.vidaUsadaPct(),
                    custos ? t.costPerUnit(atual) : null,
                    medidoHa, d.base(), av.estado(), av.motivo(), av.acao()));
        }
        linhas.sort(Comparator
                .comparingInt((PneuLinha l) -> gravidade(l.estado()))
                .thenComparing(l -> l.kmRestantes() == null ? BigDecimal.valueOf(Long.MAX_VALUE)
                        : l.kmRestantes()));
        return linhas;
    }

    /** O que se sabe sobre a vida de um pneu, e de onde se soube. */
    private record Desgaste(
            BigDecimal por1000Km, BigDecimal kmRestantes, Integer vidaUsadaPct, String base) {}

    /**
     * A taxa de desgaste de um pneu.
     *
     * <p>Duas medições de sulco com o contador anotado bastam para saber
     * quantos milímetros se perdem por mil quilómetros — e isso é
     * imediatamente mais verdadeiro do que qualquer vida declarada, porque já
     * inclui a estrada, a carga e o pé de quem conduz. Sem duas medições
     * usa-se a vida do fabricante; sem ela, não se inventa.
     */
    private Desgaste desgaste(Tyre t, List<TyreReading> hist, BigDecimal rodados) {
        List<TyreReading> uteis = hist.stream()
                .filter(r -> r.getTreadMm() != null && r.getMeterValue() != null)
                .toList();
        BigDecimal taxaPorKm = null;
        if (uteis.size() >= 2) {
            TyreReading nova = uteis.get(0);
            TyreReading velha = uteis.get(uteis.size() - 1);
            BigDecimal perdaSulco = velha.getTreadMm().subtract(nova.getTreadMm());
            BigDecimal distancia = nova.getMeterValue().subtract(velha.getMeterValue());
            if (perdaSulco.signum() > 0 && distancia.signum() > 0) {
                taxaPorKm = perdaSulco.divide(distancia, 10, RoundingMode.HALF_UP);
            }
        }

        BigDecimal restantes = null;
        String base = null;
        if (taxaPorKm != null && t.getLastTreadMm() != null && t.getMinTreadMm() != null) {
            BigDecimal sulcoUtil = t.getLastTreadMm().subtract(t.getMinTreadMm());
            restantes = sulcoUtil.signum() <= 0
                    ? BigDecimal.ZERO
                    : sulcoUtil.divide(taxaPorKm, 0, RoundingMode.DOWN);
            base = "medições de sulco";
        } else if (t.getExpectedLifeKm() != null && t.getExpectedLifeKm().signum() > 0
                && rodados != null) {
            BigDecimal falta = t.getExpectedLifeKm().subtract(rodados);
            restantes = falta.signum() <= 0 ? BigDecimal.ZERO : falta.setScale(0, RoundingMode.DOWN);
            base = "vida declarada pelo fabricante";
        }

        Integer usada = null;
        if (t.getExpectedLifeKm() != null && t.getExpectedLifeKm().signum() > 0 && rodados != null) {
            usada = rodados.multiply(CEM)
                    .divide(t.getExpectedLifeKm(), 0, RoundingMode.HALF_UP).intValue();
        } else if (restantes != null && rodados != null) {
            BigDecimal total = rodados.add(restantes);
            if (total.signum() > 0) {
                usada = rodados.multiply(CEM).divide(total, 0, RoundingMode.HALF_UP).intValue();
            }
        }

        BigDecimal por1000 = taxaPorKm == null
                ? null
                : taxaPorKm.multiply(MIL).setScale(2, RoundingMode.HALF_UP);
        return new Desgaste(por1000, restantes, usada == null ? null : Math.min(usada, 999), base);
    }

    private record Avaliacao(String estado, String motivo, String acao) {}

    private Avaliacao avaliarPneu(
            Tyre t, Desgaste d, Integer dias, Integer medidoHa, Integer pressaoPct, int horizonte) {
        String sulco = numero(t.getLastTreadMm());
        String minimo = numero(t.getMinTreadMm());

        if (t.getLastTreadMm() != null && t.getMinTreadMm() != null
                && t.getLastTreadMm().compareTo(t.getMinTreadMm()) < 0) {
            return new Avaliacao("CRITICO",
                    "Sulco a " + sulco + " mm, abaixo do mínimo de " + minimo + " mm",
                    "Substituir antes da próxima viagem");
        }
        if (pressaoPct != null && pressaoPct < 80) {
            return new Avaliacao("CRITICO",
                    "Pressão a " + pressaoPct + "% do alvo — risco de rebentamento e "
                            + "desgaste irrecuperável do ombro",
                    "Calibrar hoje e procurar a fuga");
        }
        if (d.kmRestantes() != null && d.kmRestantes().signum() <= 0) {
            return new Avaliacao("CRITICO",
                    "Vida esgotada pela " + d.base(),
                    "Medir o sulco e substituir");
        }
        if (t.getLastTreadMm() == null) {
            return new Avaliacao("SEM_DADOS",
                    "Sem nenhuma medição de sulco — não há como prever a troca",
                    "Medir sulco e pressão e registar no sistema");
        }
        if (d.kmRestantes() != null
                && (d.kmRestantes().compareTo(KM_AVISO_PNEU) <= 0
                        || (dias != null && dias <= horizonte))) {
            return new Avaliacao("AVISO",
                    "Faltam " + numero(d.kmRestantes()) + " km até ao sulco mínimo"
                            + (dias == null ? "" : " (cerca de " + dias + " dias)"),
                    "Encomendar o pneu e marcar a substituição");
        }
        if (pressaoPct != null && pressaoPct < 90) {
            return new Avaliacao("AVISO",
                    "Pressão a " + pressaoPct + "% do alvo — gasta combustível e come o ombro",
                    "Calibrar à pressão de serviço");
        }
        if (medidoHa != null && medidoHa > DIAS_SEM_MEDIR) {
            return new Avaliacao("AVISO",
                    "Sem medição há " + medidoHa + " dias",
                    "Medir sulco e pressão");
        }
        if (d.kmRestantes() == null) {
            return new Avaliacao("SEM_DADOS",
                    "Uma medição só não dá taxa de desgaste, e não há vida declarada",
                    "Medir outra vez daqui a algumas semanas, ou indicar a vida esperada");
        }
        return new Avaliacao("OK", null, null);
    }

    private static Integer percentagemDaPressao(Tyre t) {
        if (t.getLastPressure() == null || t.getTargetPressure() == null
                || t.getTargetPressure().signum() <= 0) {
            return null;
        }
        return t.getLastPressure().multiply(CEM)
                .divide(t.getTargetPressure(), 0, RoundingMode.HALF_UP).intValue();
    }

    /** Passa unidades de contador a dias, pela média diária do próprio ativo. */
    private static Integer projetarDias(BigDecimal unidades, AssetMeter m) {
        if (unidades == null || m == null || m.getDailyAverage() == null
                || m.getDailyAverage().signum() <= 0) {
            return null;
        }
        if (unidades.signum() <= 0) {
            return 0;
        }
        return unidades.divide(m.getDailyAverage(), 0, RoundingMode.DOWN).intValue();
    }

    // ==== Óleos e filtros ===================================================

    private void servicos(
            String orgId,
            Map<String, AssetMeter> contadores,
            Map<String, BigDecimal> stockPorPeca,
            Map<String, Part> porNome,
            int horizonte,
            LocalDate hoje,
            List<ServicoLinha> oleos,
            List<ServicoLinha> filtros) {
        List<AssetPlanTask> tarefas = planTasks.findActiveForOrg(orgId);
        if (tarefas.isEmpty()) {
            return;
        }

        Set<String> idsDeTarefa = new HashSet<>();
        for (AssetPlanTask t : tarefas) {
            if (t.getTask() != null) {
                idsDeTarefa.add(t.getTask().getId());
            }
        }
        Map<String, List<PlanTaskPart>> pecasPorTarefa = new HashMap<>();
        if (!idsDeTarefa.isEmpty()) {
            for (PlanTaskPart p : planTaskParts.findByTaskIds(idsDeTarefa)) {
                pecasPorTarefa.computeIfAbsent(p.getTask().getId(), k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, String> consumoDeOleo = consumoAnormalDeOleo(orgId);

        for (AssetPlanTask t : tarefas) {
            List<PlanTaskPart> pecas = t.getTask() == null
                    ? List.of()
                    : pecasPorTarefa.getOrDefault(t.getTask().getId(), List.of());
            String texto = texto(t, pecas);
            boolean ehOleo = ehOleo(texto, pecas);
            boolean ehFiltro = ehFiltro(texto, pecas);
            if (!ehOleo && !ehFiltro) {
                continue;
            }

            Asset a = t.getAssetPlan().getAsset();
            AssetMeter m = contadores.get(a.getId());
            Integer projetados = projetarDias(t.getRemainingMeter(), m);
            String estado = estadoDoServico(t, projetados, horizonte);
            if (estado == null) {
                continue;
            }

            if (ehOleo) {
                oleos.add(linhaDeServico(t, a, m, pecas, stockPorPeca, porNome, projetados, hoje,
                        estado, tipoDeOleo(texto), consumoDeOleo.get(a.getId())));
            }
            if (ehFiltro) {
                filtros.add(linhaDeServico(t, a, m, pecas, stockPorPeca, porNome, projetados, hoje,
                        estado, tipoDeFiltro(texto), null));
            }
        }
        oleos.sort(ordemDosServicos());
        filtros.sort(ordemDosServicos());
    }

    private static Comparator<ServicoLinha> ordemDosServicos() {
        return Comparator
                .comparingInt((ServicoLinha l) -> gravidade(l.estado()))
                .thenComparingInt(l -> {
                    Integer d = l.diasProjetados() != null ? l.diasProjetados() : l.restanteDias();
                    return d == null ? Integer.MAX_VALUE : d;
                });
    }

    private ServicoLinha linhaDeServico(
            AssetPlanTask t,
            Asset a,
            AssetMeter m,
            List<PlanTaskPart> pecas,
            Map<String, BigDecimal> stockPorPeca,
            Map<String, Part> porNome,
            Integer projetados,
            LocalDate hoje,
            String estado,
            String tipo,
            String consumoAnormal) {
        List<PecaLinha> linhas = new ArrayList<>();
        BigDecimal litros = BigDecimal.ZERO;
        for (PlanTaskPart p : pecas) {
            Part catalogada = p.getPart() != null ? p.getPart() : porNome.get(chave(p.getPartName()));
            BigDecimal existe = catalogada == null
                    ? BigDecimal.ZERO
                    : stockPorPeca.getOrDefault(catalogada.getId(), BigDecimal.ZERO);
            BigDecimal pedida = p.getQuantity() != null ? p.getQuantity() : BigDecimal.ONE;
            if (emLitros(p.getUnit())) {
                litros = litros.add(pedida);
            }
            linhas.add(new PecaLinha(
                    catalogada == null ? null : catalogada.getId(),
                    p.getPartName(),
                    catalogada == null ? null : catalogada.getPartNumber(),
                    catalogada == null ? null : catalogada.getSpecification(),
                    catalogada == null ? null : catalogada.getCategory().name(),
                    pedida,
                    p.getUnit() != null ? p.getUnit() : (catalogada == null ? null : catalogada.getUnit()),
                    existe,
                    catalogada != null,
                    catalogada != null && existe.compareTo(pedida) >= 0,
                    catalogada == null ? null : catalogada.getLeadTimeDays()));
        }

        LocalDate prevista = dataPrevista(t, projetados, hoje);
        return new ServicoLinha(
                t.getId(), a.getId(), a.getTag(), a.getName(),
                t.getTitle(), t.getSystemName(), tipo,
                m == null ? null : m.getKind().name(),
                m == null ? null : unidade(m.getKind()),
                t.getLastDoneMeter(), t.getLastDoneAt(),
                t.getNextDueMeter(), t.getNextDueAt(),
                t.getRemainingMeter(), t.getRemainingDays(), projetados, prevista,
                vidaUsada(t, m),
                litros.signum() > 0 ? litros : null,
                consumoAnormal,
                linhas,
                estado,
                motivoDoServico(t, m, projetados, estado));
    }

    /**
     * Quando é que isto vence, em data.
     *
     * <p>Uma tarefa pode vencer por quilómetros e por tempo ao mesmo tempo —
     * e vence pelo que chegar primeiro. É por isso que se compara a data do
     * calendário com a data a que o contador chega lá.
     */
    private static LocalDate dataPrevista(AssetPlanTask t, Integer projetados, LocalDate hoje) {
        LocalDate porTempo = t.getNextDueAt() == null
                ? null
                : t.getNextDueAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate porContador = projetados == null ? null : hoje.plusDays(projetados);
        if (porTempo == null) {
            return porContador;
        }
        if (porContador == null) {
            return porTempo;
        }
        return porContador.isBefore(porTempo) ? porContador : porTempo;
    }

    /**
     * Devolve o estado, ou {@code null} quando a tarefa ainda está longe e não
     * pertence ao painel.
     */
    private static String estadoDoServico(AssetPlanTask t, Integer projetados, int horizonte) {
        boolean vencidaPorContador = t.getRemainingMeter() != null && t.getRemainingMeter().signum() < 0;
        boolean vencidaPorTempo = t.getRemainingDays() != null && t.getRemainingDays() < 0;
        if (vencidaPorContador || vencidaPorTempo || t.getStatus() == PlanTaskStatus.OVERDUE) {
            return "CRITICO";
        }
        // Há duas maneiras de estar «perto»: faltar pouco tempo — e para isso
        // conta o horizonte que o gestor pediu — ou faltar pouco contador. A
        // segunda não precisa de média de utilização nenhuma, e é a única que
        // serve numa frota que ainda não tem histórico: é a tolerância do
        // próprio plano, a mesma que o resto do sistema usa.
        boolean perto = t.getStatus() == PlanTaskStatus.DUE_SOON
                || (t.getRemainingDays() != null && t.getRemainingDays() <= horizonte)
                || (projetados != null && projetados <= horizonte);
        return perto ? "AVISO" : null;
    }

    private static String motivoDoServico(
            AssetPlanTask t, AssetMeter m, Integer projetados, String estado) {
        String un = m == null ? "" : " " + unidade(m.getKind());
        if ("CRITICO".equals(estado)) {
            if (t.getRemainingMeter() != null && t.getRemainingMeter().signum() < 0) {
                return "Passou " + numero(t.getRemainingMeter().abs()) + un + " do intervalo";
            }
            if (t.getRemainingDays() != null && t.getRemainingDays() < 0) {
                return "Vencida há " + Math.abs(t.getRemainingDays()) + " dias";
            }
            return "Vencida";
        }
        if (t.getRemainingMeter() != null && projetados != null) {
            return "Faltam " + numero(t.getRemainingMeter()) + un
                    + " — cerca de " + projetados + " dias ao ritmo actual";
        }
        if (t.getRemainingMeter() != null) {
            return "Faltam " + numero(t.getRemainingMeter()) + un;
        }
        if (t.getRemainingDays() != null) {
            return "Faltam " + t.getRemainingDays() + " dias";
        }
        return null;
    }

    /** Quanto do intervalo já foi consumido, em percentagem. */
    private static Integer vidaUsada(AssetPlanTask t, AssetMeter m) {
        if (t.getNextDueMeter() != null && t.getLastDoneMeter() != null && m != null
                && m.getCurrentValue() != null) {
            BigDecimal intervalo = t.getNextDueMeter().subtract(t.getLastDoneMeter());
            if (intervalo.signum() > 0) {
                BigDecimal feito = m.getCurrentValue().subtract(t.getLastDoneMeter());
                if (feito.signum() < 0) {
                    feito = BigDecimal.ZERO;
                }
                return Math.min(feito.multiply(CEM)
                        .divide(intervalo, 0, RoundingMode.HALF_UP).intValue(), 999);
            }
        }
        if (t.getNextDueAt() != null && t.getLastDoneAt() != null) {
            long total = ChronoUnit.DAYS.between(t.getLastDoneAt(), t.getNextDueAt());
            if (total > 0) {
                long feitos = ChronoUnit.DAYS.between(t.getLastDoneAt(), Instant.now());
                return (int) Math.min(Math.max(feitos, 0) * 100 / total, 999);
            }
        }
        return null;
    }

    /**
     * Quem está a comer óleo entre mudanças.
     *
     * <p>Um motor saudável não precisa de atesto entre serviços. Se o motorista
     * atestou três litros em três meses, o óleo está a sair por algum lado — ou
     * a ser queimado — e isso vale mais do que a data da próxima mudança.
     */
    private Map<String, String> consumoAnormalDeOleo(String orgId) {
        Instant desde = Instant.now().minus(90, ChronoUnit.DAYS);
        Map<String, String> fora = new HashMap<>();
        for (Object[] r : fluids.totalsSince(orgId, desde)) {
            if (r[1] != ao.autocare.domain.FluidTopUp.Kind.ENGINE_OIL) {
                continue;
            }
            BigDecimal litros = r[2] == null ? BigDecimal.ZERO : (BigDecimal) r[2];
            long vezes = ((Number) r[3]).longValue();
            if (litros.compareTo(new BigDecimal("3")) >= 0 || vezes >= 3) {
                fora.put((String) r[0], "Atestou " + numero(litros) + " L de óleo em "
                        + vezes + " vezes nos últimos 90 dias — consumo acima do normal; "
                        + "verificar vedantes, turbo e fumo de escape");
            }
        }
        return fora;
    }

    // ==== Lista de compras ==================================================

    /**
     * O que há a comprar, e até quando.
     *
     * <p>Soma por artigo tudo o que as tarefas do horizonte pedem, desconta o
     * que já existe em armazém e recua a data pelo prazo de entrega: é essa
     * subtracção — e só ela — que transforma uma lista de peças numa ordem de
     * compra com data. Um filtro que demora 21 dias a chegar e é preciso daqui
     * a 15 já está atrasado hoje, e o painel di-lo.
     */
    private List<CompraLinha> compras(
            List<ServicoLinha> oleos,
            List<ServicoLinha> filtros,
            List<PneuLinha> pneus,
            List<Part> catalogo,
            Map<String, BigDecimal> stockPorPeca,
            Map<String, Part> porNome,
            LocalDate hoje,
            boolean custos) {
        /** Acumulador por artigo. */
        record Acc(List<BigDecimal> quantidades, Set<String> equipamentos,
                   List<LocalDate> datas, PecaLinha exemplo) {}

        Map<String, Acc> porArtigo = new LinkedHashMap<>();
        Set<String> tarefasJaContadas = new HashSet<>();
        for (ServicoLinha s : juntar(oleos, filtros)) {
            // Uma tarefa que muda óleo e filtro aparece nos dois separadores,
            // mas compra-se uma vez.
            if (!tarefasJaContadas.add(s.taskId())) {
                continue;
            }
            for (PecaLinha p : s.pecas()) {
                String chave = p.partId() != null ? "id:" + p.partId() : "nome:" + chave(p.nome());
                Acc acc = porArtigo.computeIfAbsent(chave,
                        k -> new Acc(new ArrayList<>(), new HashSet<>(), new ArrayList<>(), p));
                acc.quantidades().add(p.quantidade() == null ? BigDecimal.ONE : p.quantidade());
                if (s.assetTag() != null) {
                    acc.equipamentos().add(s.assetTag());
                }
                if (s.dataPrevista() != null) {
                    acc.datas().add(s.dataPrevista());
                }
            }
        }

        List<CompraLinha> linhas = new ArrayList<>();
        for (Acc acc : porArtigo.values()) {
            PecaLinha p = acc.exemplo();
            BigDecimal necessario = acc.quantidades().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Part catalogada = p.partId() != null
                    ? catalogo.stream().filter(c -> c.getId().equals(p.partId())).findFirst().orElse(null)
                    : porNome.get(chave(p.nome()));
            BigDecimal existe = catalogada == null
                    ? BigDecimal.ZERO
                    : stockPorPeca.getOrDefault(catalogada.getId(), BigDecimal.ZERO);
            BigDecimal comprar = necessario.subtract(existe);
            if (comprar.signum() <= 0) {
                comprar = BigDecimal.ZERO;
            }
            LocalDate precisaEm = acc.datas().stream().min(LocalDate::compareTo).orElse(null);
            Integer prazo = catalogada == null ? null : catalogada.getLeadTimeDays();
            LocalDate encomendarAte = precisaEm == null
                    ? null
                    : precisaEm.minusDays(prazo == null ? 0 : prazo);
            BigDecimal custo = null;
            if (custos && catalogada != null && catalogada.getAverageCost() != null) {
                custo = catalogada.getAverageCost().multiply(comprar).setScale(2, RoundingMode.HALF_UP);
            }
            linhas.add(new CompraLinha(
                    catalogada == null ? null : catalogada.getId(),
                    p.nome(),
                    catalogada == null ? null : catalogada.getPartNumber(),
                    catalogada == null ? null : catalogada.getSpecification(),
                    catalogada == null ? null : catalogada.getCategory().name(),
                    p.unidade(),
                    necessario, existe, comprar, custo,
                    catalogada == null ? "AOA" : catalogada.getCurrency(),
                    prazo, precisaEm, encomendarAte,
                    comprar.signum() > 0 && encomendarAte != null && !encomendarAte.isAfter(hoje),
                    catalogada != null,
                    acc.equipamentos().stream().sorted().limit(12).toList()));
        }

        linhas.addAll(comprasDePneus(pneus, catalogo, stockPorPeca, hoje, custos));
        linhas.sort(Comparator
                .comparing(CompraLinha::urgente, Comparator.reverseOrder())
                .thenComparing(l -> l.encomendarAte() == null ? LocalDate.MAX : l.encomendarAte()));
        return linhas;
    }

    /** Os pneus que já não dão para mais uma viagem também são uma compra. */
    private List<CompraLinha> comprasDePneus(
            List<PneuLinha> pneus,
            List<Part> catalogo,
            Map<String, BigDecimal> stockPorPeca,
            LocalDate hoje,
            boolean custos) {
        Map<String, List<PneuLinha>> porMedida = new LinkedHashMap<>();
        for (PneuLinha l : pneus) {
            if (!"CRITICO".equals(l.estado()) && !"AVISO".equals(l.estado())) {
                continue;
            }
            porMedida.computeIfAbsent(l.size() == null ? "medida não indicada" : l.size(),
                    k -> new ArrayList<>()).add(l);
        }

        List<CompraLinha> linhas = new ArrayList<>();
        for (Map.Entry<String, List<PneuLinha>> e : porMedida.entrySet()) {
            String medida = e.getKey();
            List<PneuLinha> grupo = e.getValue();
            Part catalogada = catalogo.stream()
                    .filter(p -> p.getCategory() == ao.autocare.domain.enums.Enums.PartCategory.TIRE)
                    .filter(p -> combina(p, medida))
                    .findFirst().orElse(null);
            BigDecimal existe = catalogada == null
                    ? BigDecimal.ZERO
                    : stockPorPeca.getOrDefault(catalogada.getId(), BigDecimal.ZERO);
            BigDecimal necessario = BigDecimal.valueOf(grupo.size());
            BigDecimal comprar = necessario.subtract(existe).max(BigDecimal.ZERO);
            LocalDate precisaEm = grupo.stream()
                    .map(PneuLinha::dataPrevista).filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(grupo.stream().anyMatch(l -> "CRITICO".equals(l.estado())) ? hoje : null);
            Integer prazo = catalogada == null ? null : catalogada.getLeadTimeDays();
            LocalDate encomendarAte = precisaEm == null
                    ? null
                    : precisaEm.minusDays(prazo == null ? 0 : prazo);
            BigDecimal custo = null;
            if (custos && catalogada != null && catalogada.getAverageCost() != null) {
                custo = catalogada.getAverageCost().multiply(comprar).setScale(2, RoundingMode.HALF_UP);
            }
            linhas.add(new CompraLinha(
                    catalogada == null ? null : catalogada.getId(),
                    "Pneu " + medida,
                    catalogada == null ? null : catalogada.getPartNumber(),
                    catalogada == null ? null : catalogada.getSpecification(),
                    "TIRE", "un",
                    necessario, existe, comprar, custo,
                    catalogada == null ? "AOA" : catalogada.getCurrency(),
                    prazo, precisaEm, encomendarAte,
                    comprar.signum() > 0 && encomendarAte != null && !encomendarAte.isAfter(hoje),
                    catalogada != null,
                    grupo.stream().map(PneuLinha::assetTag).filter(Objects::nonNull)
                            .distinct().sorted().limit(12).toList()));
        }
        return linhas;
    }

    private static boolean combina(Part p, String medida) {
        String m = chave(medida);
        return chave(p.getName()).contains(m)
                || (p.getSpecification() != null && chave(p.getSpecification()).contains(m));
    }

    // ==== Resumo ============================================================

    private Resumo resumo(
            List<PneuLinha> pneus,
            List<ServicoLinha> oleos,
            List<ServicoLinha> filtros,
            List<CompraLinha> compras,
            String orgId,
            boolean custos) {
        int pneusCriticos = (int) pneus.stream().filter(p -> "CRITICO".equals(p.estado())).count();
        int pneusAviso = (int) pneus.stream().filter(p -> "AVISO".equals(p.estado())).count();
        int semMedicao = (int) pneus.stream().filter(p -> p.treadMm() == null).count();
        int oleosVencidos = (int) oleos.stream().filter(s -> "CRITICO".equals(s.estado())).count();
        int filtrosVencidos = (int) filtros.stream().filter(s -> "CRITICO".equals(s.estado())).count();
        int emFalta = (int) compras.stream().filter(c -> c.aComprar().signum() > 0).count();
        int urgentes = (int) compras.stream().filter(CompraLinha::urgente).count();

        // Equipamento sem plano é equipamento sem controlo nenhum de óleos e
        // filtros: não aparece no painel porque não há nada a vencer, e é
        // precisamente esse o problema. Conta-se pelos planos atribuídos, não
        // pelas linhas do painel — senão uma frota em ordem parecia uma frota
        // esquecida.
        Set<String> comPlano = new HashSet<>(assetPlans.findAssetIdsWithActivePlan(orgId));
        int semPlano = 0;
        for (Asset a : assets.findByOrganizationId(orgId)) {
            if (!a.isArchived() && a.getStatus() != AssetStatus.RETIRED && !comPlano.contains(a.getId())) {
                semPlano++;
            }
        }

        BigDecimal custo = null;
        if (custos) {
            custo = compras.stream()
                    .map(c -> c.custoEstimado() == null ? BigDecimal.ZERO : c.custoEstimado())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return new Resumo(
                pneusCriticos, pneusAviso, pneus.size(), semMedicao,
                oleosVencidos, oleos.size() - oleosVencidos,
                filtrosVencidos, filtros.size() - filtrosVencidos,
                emFalta, urgentes, semPlano, custo, "AOA");
    }

    // ==== Classificação =====================================================

    private static String texto(AssetPlanTask t, List<PlanTaskPart> pecas) {
        StringBuilder sb = new StringBuilder();
        if (t.getTitle() != null) {
            sb.append(t.getTitle()).append(' ');
        }
        if (t.getSystemName() != null) {
            sb.append(t.getSystemName()).append(' ');
        }
        for (PlanTaskPart p : pecas) {
            sb.append(p.getPartName()).append(' ');
        }
        return sb.toString();
    }

    private static boolean ehOleo(String texto, List<PlanTaskPart> pecas) {
        for (PlanTaskPart p : pecas) {
            if (p.getPart() != null
                    && p.getPart().getCategory() == ao.autocare.domain.enums.Enums.PartCategory.LUBRICANT) {
                return true;
            }
        }
        // Um «filtro de óleo» fala de óleo mas é um filtro: tira-se da frase
        // antes de perguntar se o que sobra é uma mudança de óleo.
        return OLEO.matcher(FILTRO_DE_OLEO.matcher(texto).replaceAll(" ")).find();
    }

    private static boolean ehFiltro(String texto, List<PlanTaskPart> pecas) {
        for (PlanTaskPart p : pecas) {
            if (p.getPart() != null
                    && p.getPart().getCategory() == ao.autocare.domain.enums.Enums.PartCategory.FILTER) {
                return true;
            }
        }
        return FILTRO.matcher(texto).find();
    }

    private static String tipoDeOleo(String texto) {
        String t = chave(texto);
        if (t.contains("hidraul")) {
            return "HIDRAULICO";
        }
        if (t.contains("transmiss") || t.contains("caixa") || t.contains("atf")) {
            return "TRANSMISSAO";
        }
        if (t.contains("diferenc") || t.contains("eixo")) {
            return "DIFERENCIAL";
        }
        if (t.contains("direc")) {
            return "DIRECAO";
        }
        if (t.contains("massa") || t.contains("graxa") || t.contains("lubrific")) {
            return "MASSA";
        }
        return "MOTOR";
    }

    private static String tipoDeFiltro(String texto) {
        String t = chave(texto);
        if (t.contains("habitacul") || t.contains("cabina") || t.contains("polen")) {
            return "HABITACULO";
        }
        if (t.contains("combustivel") || t.contains("gasoleo") || t.contains("diesel")) {
            return "COMBUSTIVEL";
        }
        if (t.contains("hidraul")) {
            return "HIDRAULICO";
        }
        if (t.contains("transmiss")) {
            return "TRANSMISSAO";
        }
        if (t.contains("secador")) {
            return "SECADOR DE AR";
        }
        if (t.contains("separador") || t.contains("agua")) {
            return "SEPARADOR DE AGUA";
        }
        if (t.contains("oleo")) {
            return "OLEO";
        }
        return "AR";
    }

    // ==== Utilitários =======================================================

    private static List<ServicoLinha> juntar(List<ServicoLinha> a, List<ServicoLinha> b) {
        List<ServicoLinha> todos = new ArrayList<>(a);
        todos.addAll(b);
        return todos;
    }

    private static int gravidade(String estado) {
        return switch (estado == null ? "" : estado) {
            case "CRITICO" -> 0;
            case "AVISO" -> 1;
            case "SEM_DADOS" -> 2;
            default -> 3;
        };
    }

    private static boolean emLitros(String unidade) {
        if (unidade == null) {
            return false;
        }
        String u = chave(unidade);
        return u.equals("l") || u.startsWith("litro");
    }

    private static String unidade(MeterKind kind) {
        return kind == MeterKind.HOURMETER ? "h" : "km";
    }

    /** Nome normalizado: sem acentos, sem maiúsculas, sem espaços a mais. */
    private static String chave(String s) {
        if (s == null) {
            return "";
        }
        String sem = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sem.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static String numero(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        return v.setScale(v.scale() > 0 && v.stripTrailingZeros().scale() > 0 ? 1 : 0,
                        RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }
}
