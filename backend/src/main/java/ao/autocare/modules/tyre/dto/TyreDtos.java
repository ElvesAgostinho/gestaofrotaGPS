package ao.autocare.modules.tyre.dto;

import ao.autocare.domain.Tyre;
import ao.autocare.domain.TyreReading;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TyreDtos {

    private TyreDtos() {}

    /** Montar um pneu numa posição (ou pô-lo em stock, sem posição). */
    public record SaveTyreRequest(
            @Size(max = 12) String position,
            @Size(max = 80) String brand,
            @Size(max = 80) String model,
            @Size(max = 40) String size,
            @Size(max = 80) String serialNumber,
            BigDecimal cost,
            @Size(max = 3) String currency,
            Instant installedAt,
            /** Contador na montagem; vazio = o contador atual do ativo. */
            BigDecimal installedMeter,
            BigDecimal targetPressure,
            BigDecimal minTreadMm,
            BigDecimal lastTreadMm,
            @Size(max = 1000) String notes) {}

    public record ReadingRequest(
            Instant measuredAt,
            BigDecimal meterValue,
            BigDecimal pressure,
            BigDecimal treadMm,
            @Size(max = 300) String note) {}

    /** Desmontar: por desgaste, furo, dano, rotação, recauchutagem. */
    public record RemoveRequest(
            Tyre.RemovalReason reason,
            Instant removedAt,
            BigDecimal removedMeter,
            /** Verdadeiro = fim de vida; falso = fica em stock (reserva, recauchutar). */
            Boolean retire,
            @Size(max = 300) String note) {}

    /** Trocar dois pneus de posição na mesma viatura. */
    public record RotateRequest(String otherTyreId) {}

    public record ReadingView(String id, Instant measuredAt, BigDecimal meterValue,
                              BigDecimal pressure, BigDecimal treadMm, String note) {

        public static ReadingView of(TyreReading r) {
            return new ReadingView(r.getId(), r.getMeasuredAt(), r.getMeterValue(),
                    r.getPressure(), r.getTreadMm(), r.getNote());
        }
    }

    public record TyreView(
            String id, String assetId, String assetTag, String position, String brand, String model,
            String size, String serialNumber, String status, BigDecimal cost, String currency,
            Instant installedAt, BigDecimal installedMeter, Instant removedAt, BigDecimal removedMeter,
            String removalReason, BigDecimal targetPressure, BigDecimal lastPressure,
            BigDecimal lastTreadMm, BigDecimal minTreadMm, Instant lastMeasuredAt, String notes,
            /** Km/h percorridos nesta montagem, com o contador atual do ativo. */
            BigDecimal distanceRun,
            /** Custo por km/h (nulo sem custo ou sem distância). */
            BigDecimal costPerUnit,
            /** Frase de alerta (sulco baixo, pressão baixa) ou nulo. */
            String alert) {

        public static TyreView of(Tyre t, BigDecimal currentMeter, boolean showMoney) {
            return new TyreView(
                    t.getId(),
                    t.getAsset() != null ? t.getAsset().getId() : null,
                    t.getAsset() != null ? t.getAsset().getTag() : null,
                    t.getPosition(), t.getBrand(), t.getModel(), t.getSize(), t.getSerialNumber(),
                    t.getStatus().name(),
                    showMoney ? t.getCost() : null, t.getCurrency(),
                    t.getInstalledAt(), t.getInstalledMeter(), t.getRemovedAt(), t.getRemovedMeter(),
                    t.getRemovalReason() != null ? t.getRemovalReason().name() : null,
                    t.getTargetPressure(), t.getLastPressure(), t.getLastTreadMm(), t.getMinTreadMm(),
                    t.getLastMeasuredAt(), t.getNotes(),
                    t.distanceRun(currentMeter),
                    showMoney ? t.costPerUnit(currentMeter) : null,
                    t.alerta());
        }
    }

    public record TyreDetail(TyreView tyre, List<ReadingView> readings) {}
}
