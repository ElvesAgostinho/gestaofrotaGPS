package ao.autocare.modules.workorder;

import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderExternalService;
import ao.autocare.domain.WorkOrderLabor;
import ao.autocare.domain.WorkOrderPart;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * O que a ordem de manutenção sabe dizer por si.
 *
 * <p>Duas coisas separadas vivem aqui. As <b>contas</b> — custo total, paragem,
 * cumprimento de prazo — que têm de fechar e ser reproduzíveis. E os
 * <b>avisos</b>, que apontam o que costuma passar despercebido: a mesma avaria
 * a repetir-se, uma reparação que já foi paga e ainda está em garantia, uma
 * ordem que triplicou o orçamento sem ninguém dar por isso.
 *
 * <p>Nenhum aviso bloqueia nada. Uma ordem tem de poder fechar mesmo quando
 * está tudo errado — impedir o registo não corrige a realidade, apenas faz com
 * que ela deixe de ser registada.
 */
@Component
public class WorkOrderIntelligence {

    /**
     * Prazos por omissão, por prioridade.
     *
     * <p>Existem para que "atrasado" queira dizer alguma coisa. Sem prazo, toda
     * a ordem está a horas e o indicador de cumprimento é sempre 100%.
     */
    static final Duration SLA_URGENT = Duration.ofHours(24);
    static final Duration SLA_HIGH = Duration.ofDays(3);
    static final Duration SLA_NORMAL = Duration.ofDays(7);
    static final Duration SLA_LOW = Duration.ofDays(30);

    /** Janela em que uma avaria no mesmo sistema conta como repetição. */
    static final int REPEAT_WINDOW_DAYS = 90;

    /** Desvio ao orçamento a partir do qual se assinala. */
    static final BigDecimal OVERRUN_THRESHOLD = new BigDecimal("25");

    private final WorkOrderRepository workOrders;

    public WorkOrderIntelligence(WorkOrderRepository workOrders) {
        this.workOrders = workOrders;
    }

    /** Um aviso sobre a ordem, para mostrar a quem a está a ver. */
    public record Insight(String code, String severity, String title, String detail) {}

    // ==== Contas ===========================================================
    public Instant defaultDueDate(WorkOrderPriority priority, Instant from) {
        Duration prazo = switch (priority) {
            case URGENT -> SLA_URGENT;
            case HIGH -> SLA_HIGH;
            case NORMAL -> SLA_NORMAL;
            case LOW -> SLA_LOW;
        };
        return from.plus(prazo);
    }

    /**
     * Recalcula as contas da ordem.
     *
     * <p>O custo total só fica certo com a <b>mão de obra valorizada</b> e com a
     * oficina externa lá dentro. Sem isso, o "custo de manutenção" de um ativo
     * era uma fração do que a empresa pagou — e a decisão de reparar ou
     * substituir era tomada com o número errado.
     */
    public void recalculate(WorkOrder w) {
        BigDecimal horas = BigDecimal.ZERO;
        BigDecimal custoMaoObra = BigDecimal.ZERO;
        for (WorkOrderLabor l : w.getLabor()) {
            horas = horas.add(l.getHours());
            if (l.getHourlyRate() != null) {
                custoMaoObra = custoMaoObra.add(l.getHourlyRate().multiply(l.getHours()));
            }
        }
        w.setTotalLaborHours(horas.setScale(2, RoundingMode.HALF_UP));
        w.setTotalLaborCost(custoMaoObra.setScale(2, RoundingMode.HALF_UP));

        BigDecimal custoPecas = BigDecimal.ZERO;
        for (WorkOrderPart p : w.getParts()) {
            if (p.getUnitCost() != null) {
                custoPecas = custoPecas.add(p.getUnitCost().multiply(p.getQuantity()));
            }
        }
        w.setTotalPartsCost(custoPecas.setScale(2, RoundingMode.HALF_UP));

        BigDecimal custoExterno = BigDecimal.ZERO;
        for (WorkOrderExternalService s : w.getServices()) {
            custoExterno = custoExterno.add(s.getCost());
        }
        w.setTotalExternalCost(custoExterno.setScale(2, RoundingMode.HALF_UP));

        BigDecimal total = custoMaoObra.add(custoPecas).add(custoExterno);
        if (w.getWarrantyRecovered() != null) {
            // O que a garantia devolveu não é custo da empresa.
            total = total.subtract(w.getWarrantyRecovered());
        }
        w.setTotalCost(total.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        recalculateDowntime(w);
    }

    /**
     * Horas de paragem e o que custaram.
     *
     * <p>"Esteve catorze horas parado" é uma frase; com o custo à frente passa a
     * ser um número que entra na decisão. Fica nulo quando o ativo não tem custo
     * de paragem definido — inventar um valor daria ar de rigor a um palpite.
     */
    void recalculateDowntime(WorkOrder w) {
        if (w.getDowntimeStart() == null || w.getDowntimeEnd() == null) {
            return;
        }
        double horas = Math.max(
                Duration.between(w.getDowntimeStart(), w.getDowntimeEnd()).toMillis()
                        / 3_600_000.0, 0.0);
        BigDecimal valor = BigDecimal.valueOf(horas).setScale(2, RoundingMode.HALF_UP);
        w.setDowntimeHours(valor);

        BigDecimal porHora = w.getAsset().getDowntimeCostPerHour();
        w.setDowntimeCost(porHora != null
                ? valor.multiply(porHora).setScale(2, RoundingMode.HALF_UP) : null);
    }

    /**
     * Fixa o cumprimento do prazo no momento do fecho.
     *
     * <p>Guardado, e não calculado na leitura: mudar a regra de prazos amanhã
     * reescreveria a história de cumprimento de ontem, e os indicadores de um
     * ano já fechado deixavam de bater certo com o que foi reportado.
     */
    public void stampSla(WorkOrder w, Instant closedAt) {
        if (w.getDueAt() == null) {
            w.setSlaMet(null);
            return;
        }
        w.setSlaMet(!closedAt.isAfter(w.getDueAt()));
    }

    // ==== Avisos ===========================================================
    public List<Insight> insights(WorkOrder w) {
        List<Insight> avisos = new ArrayList<>();

        repeatFailure(w).ifPresent(avisos::add);
        stillUnderExternalWarranty(w).ifPresent(avisos::add);
        budgetOverrun(w).ifPresent(avisos::add);
        missingRootCause(w).ifPresent(avisos::add);
        overdue(w).ifPresent(avisos::add);
        expensiveDowntime(w).ifPresent(avisos::add);

        return avisos;
    }

    /** A mesma avaria a repetir-se: reparar outra vez não vai resolver. */
    private java.util.Optional<Insight> repeatFailure(WorkOrder w) {
        if (w.getSystemCode() == null || w.getType() != WorkOrderType.CORRECTIVE) {
            return java.util.Optional.empty();
        }
        Instant desde = w.getOpenedAt().minus(REPEAT_WINDOW_DAYS, ChronoUnit.DAYS);
        long anteriores = workOrders.countCorrectiveOnSystemSince(
                w.getAsset().getId(), w.getSystemCode(), desde, w.getId());
        if (anteriores == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Insight(
                "REPEAT_FAILURE", "WARNING",
                "Avaria repetida no mesmo sistema",
                "Este ativo já teve " + anteriores + " ordem(ns) corretiva(s) no sistema \""
                        + w.getSystemCode() + "\" nos últimos " + REPEAT_WINDOW_DAYS
                        + " dias. Reparar outra vez trata o sintoma; vale a pena procurar "
                        + "a causa antes de gastar mais."));
    }

    /** Já foi pago a uma oficina e ainda está dentro da garantia dela. */
    private java.util.Optional<Insight> stillUnderExternalWarranty(WorkOrder w) {
        Instant momento = w.getOpenedAt();
        for (WorkOrderExternalService s : w.getServices()) {
            if (s.isUnderWarranty(momento)) {
                return java.util.Optional.of(new Insight(
                        "EXTERNAL_WARRANTY", "INFO",
                        "Serviço ainda em garantia",
                        "O serviço de " + s.getSupplier() + " está em garantia até "
                                + s.getWarrantyUntil() + ". Se a avaria for a mesma, não "
                                + "deve ser paga duas vezes."));
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<Insight> budgetOverrun(WorkOrder w) {
        BigDecimal desvio = w.costOverrunPercent();
        if (desvio == null || desvio.compareTo(OVERRUN_THRESHOLD) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Insight(
                "BUDGET_OVERRUN", "WARNING",
                "Custo " + desvio + "% acima do orçamentado",
                "Orçamentada em " + w.getEstimatedCost() + " " + w.getCurrency()
                        + " e já vai em " + w.getTotalCost() + ". Se a diferença tiver "
                        + "explicação, escreva-a — a próxima estimativa deste tipo de "
                        + "trabalho depende disso."));
    }

    /** Uma corretiva sem causa apurada volta a acontecer. */
    private java.util.Optional<Insight> missingRootCause(WorkOrder w) {
        if (w.getType() != WorkOrderType.CORRECTIVE
                || w.getCompletedAt() == null
                || (w.getRootCause() != null && !w.getRootCause().isBlank())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Insight(
                "NO_ROOT_CAUSE", "INFO",
                "Corretiva fechada sem causa apurada",
                "Sem causa registada, esta avaria vai voltar e ninguém saberá porquê. "
                        + "Ainda dá para escrever."));
    }

    private java.util.Optional<Insight> overdue(WorkOrder w) {
        if (w.getDueAt() == null || w.getCompletedAt() != null) {
            return java.util.Optional.empty();
        }
        if (Instant.now().isBefore(w.getDueAt())) {
            return java.util.Optional.empty();
        }
        long dias = Duration.between(w.getDueAt(), Instant.now()).toDays();
        return java.util.Optional.of(new Insight(
                "OVERDUE", "WARNING",
                "Fora do prazo há " + dias + " dia(s)",
                "O prazo era " + w.getDueAt() + ". Se já não é realista, mude-o em vez de "
                        + "o deixar vencido — um prazo que toda a gente ignora não serve "
                        + "para medir nada."));
    }

    private java.util.Optional<Insight> expensiveDowntime(WorkOrder w) {
        if (w.getDowntimeCost() == null || w.getTotalCost() == null
                || w.getDowntimeCost().compareTo(w.getTotalCost()) <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Insight(
                "DOWNTIME_DOMINATES", "WARNING",
                "A paragem custou mais do que a reparação",
                "A reparação custou " + w.getTotalCost() + " " + w.getCurrency()
                        + " e as " + w.getDowntimeHours() + " horas de paragem valeram "
                        + w.getDowntimeCost() + ". Em casos assim, pagar mais por uma "
                        + "reparação rápida costuma sair mais barato."));
    }
}
