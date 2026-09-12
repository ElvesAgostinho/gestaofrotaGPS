package ao.autocare.modules.plan;

import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Calcula quando uma tarefa de plano vence, aplicando a regra "o que ocorrer
 * primeiro" entre os gatilhos por medidor e por calendário. Lógica pura e testável.
 */
public final class PlanScheduleCalculator {

    private PlanScheduleCalculator() {}

    public record TriggerSpec(
            PlanTriggerType type, MeterKind meterKind,
            BigDecimal interval, BigDecimal tolerance) {}

    public record Projection(
            Instant nextDueAt,
            BigDecimal nextDueMeter,
            MeterKind nextDueMeterKind,
            BigDecimal remainingMeter,
            Integer remainingDays,
            PlanTaskStatus status) {}

    /** Tolerância por omissão de um gatilho por medidor: 10% do intervalo. */
    private static BigDecimal defaultMeterTolerance(BigDecimal interval) {
        return interval.multiply(new BigDecimal("0.10"));
    }

    private static final int DEFAULT_CALENDAR_TOLERANCE_DAYS = 7;

    public static Projection project(
            List<TriggerSpec> triggers,
            Instant lastDoneAt,
            BigDecimal lastDoneMeter,
            BigDecimal currentMeter,
            MeterKind primaryMeterKind,
            BigDecimal dailyAverage,
            Instant now) {

        Instant baseAt = lastDoneAt != null ? lastDoneAt : now;
        BigDecimal baseMeter = lastDoneMeter != null ? lastDoneMeter
                : (currentMeter != null ? currentMeter : BigDecimal.ZERO);

        Candidate best = null;

        for (TriggerSpec t : triggers) {
            Candidate c = switch (t.type()) {
                case METER_INTERVAL -> meterCandidate(t, baseMeter, currentMeter,
                        primaryMeterKind, dailyAverage, now);
                case CALENDAR_DAYS -> calendarCandidate(t, baseAt, now);
            };
            if (c == null) continue;
            if (best == null || c.rankDays() < best.rankDays()) {
                best = c;
            }
        }

        if (best == null) {
            return new Projection(null, null, null, null, null, PlanTaskStatus.OK);
        }
        return new Projection(best.dueAt, best.dueMeter, best.dueMeterKind,
                best.remainingMeter, best.remainingDays, best.status);
    }

    // ------------------------------------------------------------------
    private record Candidate(
            Instant dueAt, BigDecimal dueMeter, MeterKind dueMeterKind,
            BigDecimal remainingMeter, Integer remainingDays, PlanTaskStatus status) {

        /** Menor valor = vence primeiro. Sem dias conhecidos → muito longe (mas vencidas ganham). */
        double rankDays() {
            if (remainingDays != null) return remainingDays;
            if (remainingMeter != null && remainingMeter.signum() <= 0) return -1;
            return Double.MAX_VALUE;
        }
    }

    private static Candidate meterCandidate(
            TriggerSpec t, BigDecimal baseMeter, BigDecimal currentMeter,
            MeterKind primaryMeterKind, BigDecimal dailyAverage, Instant now) {

        if (currentMeter == null) return null;
        if (t.meterKind() != null && primaryMeterKind != null && t.meterKind() != primaryMeterKind) {
            return null; // gatilho para um medidor que este ativo não tem como principal
        }
        BigDecimal dueMeter = baseMeter.add(t.interval());
        BigDecimal remaining = dueMeter.subtract(currentMeter);

        Integer days = null;
        Instant dueAt = null;
        if (dailyAverage != null && dailyAverage.signum() > 0) {
            double d = remaining.doubleValue() / dailyAverage.doubleValue();
            days = (int) Math.floor(d);
            dueAt = now.plus(Duration.ofMinutes((long) (d * 1440)));
        }

        BigDecimal tolerance = t.tolerance() != null ? t.tolerance() : defaultMeterTolerance(t.interval());
        PlanTaskStatus status;
        if (remaining.signum() <= 0) {
            status = PlanTaskStatus.OVERDUE;
        } else if (remaining.compareTo(tolerance) <= 0) {
            status = PlanTaskStatus.DUE_SOON;
        } else {
            status = PlanTaskStatus.OK;
        }
        return new Candidate(dueAt, dueMeter,
                t.meterKind() != null ? t.meterKind() : primaryMeterKind,
                remaining.setScale(2, RoundingMode.HALF_UP), days, status);
    }

    private static Candidate calendarCandidate(TriggerSpec t, Instant baseAt, Instant now) {
        long intervalDays = t.interval().longValue();
        Instant dueAt = baseAt.plus(Duration.ofDays(intervalDays));
        long remaining = Duration.between(now, dueAt).toDays();
        int toleranceDays = t.tolerance() != null ? t.tolerance().intValue() : DEFAULT_CALENDAR_TOLERANCE_DAYS;

        PlanTaskStatus status;
        if (remaining <= 0) {
            status = PlanTaskStatus.OVERDUE;
        } else if (remaining <= toleranceDays) {
            status = PlanTaskStatus.DUE_SOON;
        } else {
            status = PlanTaskStatus.OK;
        }
        return new Candidate(dueAt, null, null, null, (int) remaining, status);
    }
}
