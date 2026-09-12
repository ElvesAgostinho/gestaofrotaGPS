package ao.autocare.modules.fuel.dto;

import ao.autocare.domain.FuelRecord;
import ao.autocare.domain.enums.Enums.FuelSource;
import ao.autocare.domain.enums.Enums.FuelType;
import ao.autocare.domain.enums.Enums.MeterKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedidos e respostas dos abastecimentos. */
public final class FuelDtos {

    private FuelDtos() {}

    public record SaveFuelRecordRequest(
            @NotNull(message = "Indique quantos litros foram abastecidos.")
            @Positive(message = "Os litros têm de ser maiores do que zero.")
            BigDecimal liters,
            Instant filledAt,
            BigDecimal pricePerLiter,
            /** Se vier vazio é calculado a partir do preço por litro. */
            BigDecimal totalCost,
            @Size(max = 3) String currency,
            /** Leitura do horímetro/hodómetro. Sem ela não há consumo. */
            BigDecimal meterValue,
            /** Depósito cheio. Só entre enchimentos completos há consumo fiável. */
            Boolean fullTank,
            @Size(max = 160) String station,
            @Size(max = 120) String driverLabel,
            @Size(max = 40) String paymentMethod,
            String receiptFileId,
            @Size(max = 2000) String notes,

            // ---- Controlo de consumo (Fatia 15) ------------------------------
            /** Motorista a quem imputar o abastecimento. */
            String driverId,
            /** Filial que suporta o custo. */
            String branchId,
            /**
             * Onde o abastecimento aconteceu. É o que permite confrontar com a
             * posição real da viatura — sem isto, um cartão usado a 40 km de
             * distância é indistinguível de um abastecimento normal.
             */
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 40) String cardNumber,
            @Size(max = 60) String invoiceNumber,
            FuelType fuelType) {}

    public record FuelRecordView(
            String id,
            String assetId,
            String assetTag,
            Instant filledAt,
            BigDecimal liters,
            BigDecimal pricePerLiter,
            BigDecimal totalCost,
            String currency,
            BigDecimal meterValue,
            MeterKind meterKind,
            boolean fullTank,
            String station,
            String driverLabel,
            String paymentMethod,
            String receiptUrl,
            FuelSource source,
            /** Consumo face ao enchimento completo anterior; nulo se não deu para calcular. */
            BigDecimal consumption,
            String consumptionUnit,
            BigDecimal distanceOrHours,
            String notes,

            String driverId,
            String driverName,
            String branchId,
            String branchName,
            BigDecimal latitude,
            BigDecimal longitude,
            String cardNumber,
            String invoiceNumber,
            FuelType fuelType,
            /**
             * Verificação contra a posição real da viatura.
             *
             * <p>Ausente significa <b>não foi possível verificar</b> — sem
             * aparelho, ou sem posição próxima daquela hora. É diferente de
             * "verificado e está bem", e a diferença tem de chegar ao ecrã.
             */
            Boolean gpsVerified,
            BigDecimal gpsDistanceM,
            BigDecimal gpsDistanceKm,
            String gpsLabel) {

        public static FuelRecordView of(FuelRecord r, String receiptUrl) {
            return new FuelRecordView(
                    r.getId(), r.getAsset().getId(), r.getAsset().getTag(),
                    r.getFilledAt(), r.getLiters(), r.getPricePerLiter(), r.getTotalCost(),
                    r.getCurrency(), r.getMeterValue(), r.getMeterKind(), r.isFullTank(),
                    r.getStation(), r.getDriverLabel(), r.getPaymentMethod(), receiptUrl,
                    r.getSource(), r.getConsumption(), r.getConsumptionUnit(),
                    r.getDistanceOrHours(), r.getNotes(),
                    r.getDriver() != null ? r.getDriver().getId() : null,
                    r.getDriver() != null ? r.getDriver().getName() : null,
                    r.getBranch() != null ? r.getBranch().getId() : null,
                    r.getBranch() != null ? r.getBranch().getName() : null,
                    r.getLatitude(), r.getLongitude(),
                    r.getCardNumber(), r.getInvoiceNumber(), r.getFuelType(),
                    r.getGpsVerified(), r.getGpsDistanceM(), r.getGpsDistanceKm(),
                    gpsLabel(r));
        }

        private static String gpsLabel(FuelRecord r) {
            if (r.getGpsVerified() == null) {
                return "Não foi possível verificar com o GPS.";
            }
            if (r.getGpsVerified()) {
                return "A viatura estava no local do abastecimento.";
            }
            BigDecimal km = r.getGpsDistanceM() != null
                    ? r.getGpsDistanceM().divide(BigDecimal.valueOf(1000), 1,
                            java.math.RoundingMode.HALF_UP)
                    : null;
            return "A viatura estava" + (km != null ? " a " + km + " km" : " longe")
                    + " do local do abastecimento.";
        }
    }

    /**
     * Resumo de consumo de um ativo.
     *
     * @param reliable        {@code false} enquanto não houver abastecimentos
     *                        completos suficientes para a média valer alguma coisa
     * @param disclaimer      texto para mostrar no ecrã, a dizer o que o sistema
     *                        faz e o que não faz — não é decorativo
     */
    public record ConsumptionSummary(
            String assetId,
            String assetTag,
            String unit,
            BigDecimal last,
            BigDecimal average,
            BigDecimal best,
            BigDecimal worst,
            int samples,
            boolean reliable,
            String disclaimer) {}
}
