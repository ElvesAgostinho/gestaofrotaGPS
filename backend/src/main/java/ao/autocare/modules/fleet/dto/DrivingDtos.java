package ao.autocare.modules.fleet.dto;

import ao.autocare.domain.DriverScore;
import ao.autocare.domain.DrivingEvent;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.DrivingEventKind;
import ao.autocare.domain.enums.Enums.ScoreBand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DrivingDtos {

    private DrivingDtos() {}

    /**
     * Uma infração.
     *
     * <p>Leva sempre o valor medido <b>e</b> o limiar ultrapassado. Um motorista
     * tem o direito de perguntar porque é que aquilo contou, e a resposta tem de
     * caber no ecrã — não em código que ninguém fora da equipa consegue ler.
     */
    public record DrivingEventView(
            String id,
            DrivingEventKind kind,
            String kindLabel,
            AlertSeverity severity,
            Instant occurredAt,
            Instant endedAt,
            String assetId,
            String assetTag,
            String driverId,
            String driverName,
            String tripId,
            BigDecimal measuredValue,
            BigDecimal thresholdValue,
            String unit,
            BigDecimal speedKph,
            BigDecimal latitude,
            BigDecimal longitude,
            String placeName,
            BigDecimal penaltyPoints,
            String description,
            boolean dismissed,
            Instant dismissedAt,
            String dismissReason) {

        public static DrivingEventView of(DrivingEvent e) {
            return new DrivingEventView(
                    e.getId(), e.getKind(), e.getKind().label(), e.getSeverity(),
                    e.getOccurredAt(), e.getEndedAt(),
                    e.getAsset().getId(), e.getAsset().getTag(),
                    e.getDriver() != null ? e.getDriver().getId() : null,
                    e.getDriver() != null ? e.getDriver().getName() : null,
                    e.getTrip() != null ? e.getTrip().getId() : null,
                    e.getMeasuredValue(), e.getThresholdValue(), e.getUnit(), e.getSpeedKph(),
                    e.getLatitude(), e.getLongitude(), e.getPlaceName(),
                    e.getPenaltyPoints(), e.getDescription(),
                    e.isDismissed(), e.getDismissedAt(), e.getDismissReason());
        }
    }

    public record DismissRequest(
            @NotBlank(message = "Escreva a razão para anular esta infração.")
            @Size(max = 400) String reason) {}

    /**
     * Pontuação de um motorista num período.
     *
     * <p>{@code formula} vai no JSON de propósito: uma pontuação que afeta a
     * vida de alguém tem de poder ser refeita à mão com os números do ecrã.
     */
    public record DriverScoreView(
            String driverId,
            String driverName,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal distanceKm,
            int drivingMinutes,
            int tripCount,
            int overspeedCount,
            int harshBrakeCount,
            int harshAccelCount,
            int harshCornerCount,
            int idlingCount,
            int nightCount,
            BigDecimal totalPenalty,
            BigDecimal score,
            ScoreBand band,
            String bandLabel,
            boolean insufficientData,
            String explanation,
            String formula,
            Instant computedAt) {

        public static DriverScoreView of(DriverScore s) {
            return new DriverScoreView(
                    s.getDriver().getId(), s.getDriver().getName(),
                    s.getPeriodStart(), s.getPeriodEnd(),
                    s.getDistanceKm(), s.getDrivingMinutes(), s.getTripCount(),
                    s.getOverspeedCount(), s.getHarshBrakeCount(), s.getHarshAccelCount(),
                    s.getHarshCornerCount(), s.getIdlingCount(), s.getNightCount(),
                    s.getTotalPenalty(), s.getScore(), s.getBand(),
                    s.getBand() != null ? s.getBand().label() : null,
                    s.isInsufficientData(),
                    explain(s),
                    "pontuação = 100 − (penalizações ÷ km × 100)",
                    s.getComputedAt());
        }

        private static String explain(DriverScore s) {
            if (s.isInsufficientData()) {
                return "Percorreu apenas " + s.getDistanceKm() + " km no período. "
                        + "Abaixo de 50 km a pontuação não significa nada — dar 100 a quem "
                        + "conduziu pouco poria essa pessoa acima de quem conduziu muito "
                        + "com duas infrações.";
            }
            return s.getTotalPenalty() + " pontos de penalização em " + s.getDistanceKm()
                    + " km, ao longo de " + s.getTripCount() + " viagem(ns).";
        }
    }

    /** Painel de condução da frota. */
    public record DrivingSummary(
            Instant periodStart,
            Instant periodEnd,
            int totalEvents,
            int overspeed,
            int harshBrake,
            int harshAcceleration,
            int harshCornering,
            int idling,
            int nightDriving,
            List<DriverScoreView> ranking) {}
}
