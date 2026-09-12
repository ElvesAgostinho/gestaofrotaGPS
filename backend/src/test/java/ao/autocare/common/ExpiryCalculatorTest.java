package ao.autocare.common;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.common.ExpiryCalculator.ExpiryStatus;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpiryCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private Instant inDays(long days) {
        return NOW.plus(days, ChronoUnit.DAYS);
    }

    @Test
    void nullExpiryReturnsNull() {
        assertThat(ExpiryCalculator.status(null, NOW)).isNull();
    }

    @Test
    void moreThan30DaysIsInfo() {
        ExpiryStatus s = ExpiryCalculator.status(inDays(54), NOW);
        assertThat(s.expired()).isFalse();
        assertThat(s.severity()).isEqualTo(AlertSeverity.INFO);
        assertThat(s.daysRemaining()).isEqualTo(54);
    }

    @Test
    void between8And30DaysIsWarning() {
        ExpiryStatus s = ExpiryCalculator.status(inDays(17), NOW);
        assertThat(s.severity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(s.label()).isEqualTo("Caduca em 17 dias");
    }

    @Test
    void sevenDaysOrLessIsCritical() {
        assertThat(ExpiryCalculator.status(inDays(7), NOW).severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(ExpiryCalculator.status(inDays(3), NOW).severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void todayAndTomorrowLabels() {
        assertThat(ExpiryCalculator.status(inDays(0), NOW).label()).isEqualTo("Caduca hoje");
        assertThat(ExpiryCalculator.status(inDays(1), NOW).label()).isEqualTo("Caduca amanhã");
    }

    @Test
    void expiredDocument() {
        ExpiryStatus s = ExpiryCalculator.status(inDays(-35), NOW);
        assertThat(s.expired()).isTrue();
        assertThat(s.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(s.label()).isEqualTo("Caducado há 35 dias");
    }

    @Test
    void notificationDatesAreSorted() {
        Instant expiry = Instant.parse("2026-12-01T00:00:00Z");
        List<Instant> dates = ExpiryCalculator.notificationDates(expiry, new int[] {30, 7, 0});
        assertThat(dates).containsExactly(
                Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-11-24T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"));
    }
}
