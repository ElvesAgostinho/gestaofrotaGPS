package ao.autocare.common;

import ao.autocare.domain.enums.Enums.AlertSeverity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Estado de caducidade de documentos (secção 17 do produto).
 *
 * Severidade por omissão:
 *   > 30 dias   -> INFO
 *   8..30 dias  -> WARNING
 *   <= 7 dias ou caducado -> CRITICAL
 */
public final class ExpiryCalculator {

    private ExpiryCalculator() {}

    public record ExpiryStatus(long daysRemaining, boolean expired, AlertSeverity severity, String label) {}

    public static ExpiryStatus status(Instant expiry, Instant now) {
        if (expiry == null) {
            return null;
        }
        LocalDate end = expiry.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, end);

        if (days < 0) {
            long ago = Math.abs(days);
            String label = ago == 1 ? "Caducou ontem" : "Caducado há " + ago + " dias";
            return new ExpiryStatus(days, true, AlertSeverity.CRITICAL, label);
        }

        AlertSeverity severity;
        if (days <= 7) {
            severity = AlertSeverity.CRITICAL;
        } else if (days <= 30) {
            severity = AlertSeverity.WARNING;
        } else {
            severity = AlertSeverity.INFO;
        }

        String label;
        if (days == 0) {
            label = "Caduca hoje";
        } else if (days == 1) {
            label = "Caduca amanhã";
        } else {
            label = "Caduca em " + days + " dias";
        }
        return new ExpiryStatus(days, false, severity, label);
    }

    /** Datas em que se deve notificar, dada a validade e os dias de antecedência. */
    public static List<Instant> notificationDates(Instant expiry, int[] leadDays) {
        List<Instant> dates = new ArrayList<>();
        for (int d : leadDays) {
            dates.add(expiry.minus(d, ChronoUnit.DAYS));
        }
        dates.sort(Instant::compareTo);
        return dates;
    }
}
