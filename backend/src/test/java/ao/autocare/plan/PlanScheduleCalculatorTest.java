package ao.autocare.plan;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.domain.enums.Enums.PlanTaskStatus;
import ao.autocare.domain.enums.Enums.PlanTriggerType;
import ao.autocare.modules.plan.PlanScheduleCalculator;
import ao.autocare.modules.plan.PlanScheduleCalculator.TriggerSpec;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanScheduleCalculatorTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private TriggerSpec meter(double interval) {
        return new TriggerSpec(PlanTriggerType.METER_INTERVAL, MeterKind.HOURMETER,
                BigDecimal.valueOf(interval), null);
    }

    private TriggerSpec calendar(int days) {
        return new TriggerSpec(PlanTriggerType.CALENDAR_DAYS, null, BigDecimal.valueOf(days), null);
    }

    @Test
    void meterTrigger_projectsDueMeterAndDaysFromDailyAverage() {
        // óleo a cada 250 h; última troca às 1000 h; agora nas 1180 h; usa 8 h/dia
        var p = PlanScheduleCalculator.project(
                List.of(meter(250)),
                now, new BigDecimal("1000"),
                new BigDecimal("1180"), MeterKind.HOURMETER, new BigDecimal("8"), now);

        assertThat(p.nextDueMeter()).isEqualByComparingTo("1250");
        assertThat(p.remainingMeter()).isEqualByComparingTo("70");   // 1250 - 1180
        assertThat(p.remainingDays()).isEqualTo(8);                   // 70 / 8 ≈ 8.75 -> floor 8
        assertThat(p.status()).isEqualTo(PlanTaskStatus.OK);
    }

    @Test
    void meterTrigger_overdueWhenCurrentPastDueMeter() {
        var p = PlanScheduleCalculator.project(
                List.of(meter(250)),
                now, new BigDecimal("1000"),
                new BigDecimal("1300"), MeterKind.HOURMETER, new BigDecimal("8"), now);
        assertThat(p.remainingMeter()).isEqualByComparingTo("-50");
        assertThat(p.status()).isEqualTo(PlanTaskStatus.OVERDUE);
    }

    @Test
    void meterTrigger_dueSoonWithinTenPercentTolerance() {
        // faltam 20 h de um intervalo de 250 h (tolerância 25 h) -> DUE_SOON
        var p = PlanScheduleCalculator.project(
                List.of(meter(250)),
                now, new BigDecimal("1000"),
                new BigDecimal("1230"), MeterKind.HOURMETER, new BigDecimal("8"), now);
        assertThat(p.remainingMeter()).isEqualByComparingTo("20");
        assertThat(p.status()).isEqualTo(PlanTaskStatus.DUE_SOON);
    }

    @Test
    void calendarTrigger_projectsFromLastDone() {
        var p = PlanScheduleCalculator.project(
                List.of(calendar(30)),
                now.minus(Duration.ofDays(20)), null,
                null, null, null, now);
        assertThat(p.remainingDays()).isEqualTo(10);
        assertThat(p.status()).isEqualTo(PlanTaskStatus.OK);
    }

    @Test
    void whicheverComesFirst_meterVsCalendar() {
        // 250 h a 8 h/dia ≈ 31 dias; ou 15 dias de calendário -> vence o calendário
        var p = PlanScheduleCalculator.project(
                List.of(meter(250), calendar(15)),
                now, new BigDecimal("1000"),
                new BigDecimal("1000"), MeterKind.HOURMETER, new BigDecimal("8"), now);
        assertThat(p.remainingDays()).isEqualTo(15);
        assertThat(p.nextDueMeter()).isNull(); // ganhou o gatilho de calendário
    }

    @Test
    void meterTrigger_withoutDailyAverage_stillGivesRemainingMeter() {
        var p = PlanScheduleCalculator.project(
                List.of(meter(500)),
                now, new BigDecimal("0"),
                new BigDecimal("120"), MeterKind.HOURMETER, null, now);
        assertThat(p.remainingMeter()).isEqualByComparingTo("380");
        assertThat(p.remainingDays()).isNull();
        assertThat(p.status()).isEqualTo(PlanTaskStatus.OK);
    }

    @Test
    void noTriggers_returnsOk() {
        var p = PlanScheduleCalculator.project(
                List.of(), now, null, new BigDecimal("100"), MeterKind.HOURMETER, null, now);
        assertThat(p.status()).isEqualTo(PlanTaskStatus.OK);
        assertThat(p.nextDueAt()).isNull();
    }
}
