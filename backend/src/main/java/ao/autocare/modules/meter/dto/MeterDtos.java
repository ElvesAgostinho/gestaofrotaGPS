package ao.autocare.modules.meter.dto;

import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.MeterReading;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class MeterDtos {

    private MeterDtos() {}

    public record AddReadingRequest(
            @NotNull(message = "Indique o valor da leitura.")
            @PositiveOrZero(message = "O valor não pode ser negativo.")
            BigDecimal value,
            /** Momento da leitura. Se vazio, usa agora. */
            Instant readingAt,
            @Size(max = 300) String note) {}

    public record MeterView(
            String id, String assetId, String kind, String unit,
            BigDecimal currentValue, BigDecimal dailyAverage,
            Instant lastReadingAt, boolean primary) {

        public static MeterView of(AssetMeter m) {
            return new MeterView(
                    m.getId(), m.getAsset().getId(), m.getKind().name(), m.getUnit(),
                    m.getCurrentValue(), m.getDailyAverage(), m.getLastReadingAt(), m.isPrimary());
        }
    }

    public record ReadingView(
            String id,
            BigDecimal value,
            Instant readingAt,
            String source,
            BigDecimal delta,
            boolean flagged,
            String flagReason,
            String note,
            Instant createdAt) {

        public static ReadingView of(MeterReading r) {
            return new ReadingView(
                    r.getId(), r.getValue(), r.getReadingAt(), r.getSource().name(),
                    r.getDelta(), r.isFlagged(), r.getFlagReason(), r.getNote(), r.getCreatedAt());
        }
    }

    public record AddReadingResponse(MeterView meter, ReadingView reading) {}
}
