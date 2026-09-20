package ao.autocare.modules.asset.dto;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetCriticality;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.domain.enums.Enums.CriticalityLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AssetDtos {

    private AssetDtos() {}

    public record CreateAssetRequest(
            @NotBlank(message = "Indique o código interno do ativo (etiqueta).")
            @Size(max = 40) String tag,
            @NotBlank(message = "Indique o nome do ativo.")
            @Size(max = 160) String name,
            @NotBlank(message = "Indique o tipo de ativo.")
            String assetTypeId,
            String locationId,
            String responsibleUserId,
            @Size(max = 80) String manufacturer,
            @Size(max = 120) String model,
            @Size(max = 120) String serialNumber,
            Integer modelYear,
            @Size(max = 20) String plate,
            @Size(max = 120) String responsibleLabel,
            Instant acquisitionDate,
            BigDecimal acquisitionValue,
            @Size(max = 3) String currency,
            @Size(max = 500) String photoUrl,
            @Size(max = 8000) String objective,
            @Size(max = 8000) String notes,
            AssetStatus status,
            BigDecimal latitude,
            BigDecimal longitude,
            /** Limite de velocidade deste ativo, km/h. Vazio = usa o da empresa. */
            BigDecimal speedLimitKph,
            /** Valor inicial do horímetro/hodómetro principal. */
            BigDecimal initialMeterValue,
            /**
             * Capacidade do depósito, em litros.
             *
             * <p>Sem ela o sistema não consegue dizer que um abastecimento não
             * cabia na viatura — que é uma das formas mais simples de litros
             * faturados irem parar a outro lado.
             */
            BigDecimal tankCapacityLiters,
            /**
             * Quanto custa uma hora com este ativo parado.
             *
             * <p>Sem isto, "esteve catorze horas parado" é uma frase; com isto é
             * um número que entra na decisão de reparar ou substituir.
             */
            BigDecimal downtimeCostPerHour) {}

    public record UpdateAssetRequest(
            @Size(max = 40) String tag,
            @Size(max = 160) String name,
            String assetTypeId,
            String locationId,
            String responsibleUserId,
            @Size(max = 80) String manufacturer,
            @Size(max = 120) String model,
            @Size(max = 120) String serialNumber,
            Integer modelYear,
            @Size(max = 20) String plate,
            @Size(max = 120) String responsibleLabel,
            Instant acquisitionDate,
            BigDecimal acquisitionValue,
            @Size(max = 3) String currency,
            @Size(max = 500) String photoUrl,
            @Size(max = 8000) String objective,
            @Size(max = 8000) String notes,
            AssetStatus status,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedLimitKph,
            BigDecimal tankCapacityLiters,
            BigDecimal downtimeCostPerHour,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    public record CriticalityRequest(
            @Min(1) @Max(5) int productionImpact,
            @Min(1) @Max(5) int safetyImpact,
            @Min(1) @Max(5) int financialImpact,
            /** Sobreposição manual da criticidade geral (opcional). */
            CriticalityLevel overall,
            @Size(max = 500) String notes) {}

    public record MeterView(
            String id, String kind, String unit,
            BigDecimal currentValue, BigDecimal dailyAverage,
            Instant lastReadingAt, boolean primary) {

        public static MeterView of(AssetMeter m) {
            return new MeterView(m.getId(), m.getKind().name(), m.getUnit(),
                    m.getCurrentValue(), m.getDailyAverage(), m.getLastReadingAt(), m.isPrimary());
        }
    }

    public record CriticalityView(
            int productionImpact, int safetyImpact, int financialImpact,
            String overall, boolean overallManual, Instant assessedAt, String notes) {

        public static CriticalityView of(AssetCriticality c) {
            if (c == null) {
                return new CriticalityView(1, 1, 1, "LOW", false, null, null);
            }
            return new CriticalityView(
                    c.getProductionImpact(), c.getSafetyImpact(), c.getFinancialImpact(),
                    c.getOverall().name(), c.isOverallManual(), c.getAssessedAt(), c.getNotes());
        }
    }

    public record AssetSummary(
            String id, String tag, String name, String assetTypeName,
            /** Familia do ativo, para agrupar a lista em vez de a misturar. */
            String category, String categoryLabel, int categoryOrder,
            String locationName, String status, String criticality,
            boolean archived, String primaryPhotoUrl, long photoCount,
            BigDecimal latitude, BigDecimal longitude,
            List<MeterView> meters,
            /** A tarefa de manutenção que vence primeiro, ou nulo se não há plano. */
            NextMaintenance nextMaintenance) {}

    /**
     * «Próxima manutenção: revisão geral em 1 250 km». O que o gestor quer ver
     * na lista sem abrir a ficha.
     */
    public record NextMaintenance(
            String title, String status,
            BigDecimal remainingMeter, String meterKind, Integer remainingDays,
            BigDecimal nextDueMeter, Instant nextDueAt) {

        public static NextMaintenance of(ao.autocare.domain.AssetPlanTask t) {
            return new NextMaintenance(t.getTitle(), t.getStatus().name(),
                    t.getRemainingMeter(),
                    t.getNextDueMeterKind() != null ? t.getNextDueMeterKind().name() : null,
                    t.getRemainingDays(), t.getNextDueMeter(), t.getNextDueAt());
        }

        /** Vencida antes de a vencer, a vencer antes de em dia; depois, a que tem menos margem. */
        public static int urgencia(ao.autocare.domain.AssetPlanTask t) {
            return switch (t.getStatus()) {
                case OVERDUE -> 0;
                case DUE_SOON -> 1;
                default -> 2;
            };
        }
    }

    public record AssetView(
            String id,
            String tag,
            String name,
            String assetTypeId,
            String assetTypeName,
            /** A família do catálogo: RETROESCAVADORA, TRUCK_HEAVY, LIGHT_VEHICLE ou GENERATOR. */
            String family,
            String locationId,
            String locationName,
            String responsibleUserId,
            String responsibleLabel,
            String manufacturer,
            String model,
            String serialNumber,
            Integer modelYear,
            String plate,
            Instant acquisitionDate,
            BigDecimal acquisitionValue,
            String currency,
            String photoUrl,
            String objective,
            String notes,
            String status,
            boolean archived,
            Instant createdAt,
            String primaryPhotoUrl,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant positionAt,
            String positionSource,
            BigDecimal speedLimitKph,
            BigDecimal tankCapacityLiters,
            BigDecimal downtimeCostPerHour,
            List<MeterView> meters,
            CriticalityView criticality,
            List<AssetPhotoDtos.AssetPhotoView> photos,
            Long version,
            /** Último nível do depósito pelo sensor do GPS, em litros. */
            BigDecimal fuelLevelLiters,
            Instant fuelLevelAt) {

        /**
         * A mesma ficha sem os valores financeiros.
         *
         * <p>Para quem nao tem permissao de custos. Os campos ficam a nulo em
         * vez de a zero: zero seria um valor, e um valor errado.
         */
        public AssetView withoutMoney() {
            return new AssetView(id, tag, name, assetTypeId, assetTypeName, family, locationId,
                    locationName, responsibleUserId, responsibleLabel, manufacturer, model,
                    serialNumber, modelYear, plate, acquisitionDate, null, currency, photoUrl,
                    objective, notes, status, archived, createdAt, primaryPhotoUrl, latitude,
                    longitude, positionAt, positionSource, speedLimitKph, tankCapacityLiters,
                    null, meters, criticality, photos,
                    version, fuelLevelLiters, fuelLevelAt);
        }

        public static AssetView of(
                Asset a, List<AssetMeter> meters, AssetCriticality crit,
                List<AssetPhotoDtos.AssetPhotoView> photos) {
            String primary = photos.stream().filter(AssetPhotoDtos.AssetPhotoView::primary)
                    .map(AssetPhotoDtos.AssetPhotoView::url).findFirst()
                    .orElse(photos.isEmpty() ? null : photos.get(0).url());
            return new AssetView(
                    a.getId(), a.getTag(), a.getName(),
                    a.getAssetType().getId(), a.getAssetType().getName(),
                    ao.autocare.modules.plan.PlanCatalog.codigoPara(
                            a.getAssetType().getCategory() != null
                                    ? a.getAssetType().getCategory().name() : null,
                            a.getAssetType().getName()),
                    a.getLocation() != null ? a.getLocation().getId() : null,
                    a.getLocation() != null ? a.getLocation().getName() : null,
                    a.getResponsibleUser() != null ? a.getResponsibleUser().getId() : null,
                    a.getResponsibleLabel(),
                    a.getManufacturer(), a.getModel(), a.getSerialNumber(), a.getModelYear(),
                    a.getPlate(), a.getAcquisitionDate(), a.getAcquisitionValue(), a.getCurrency(),
                    a.getPhotoUrl(), a.getObjective(), a.getNotes(),
                    a.getStatus().name(), a.isArchived(), a.getCreatedAt(),
                    primary,
                    a.getLatitude(), a.getLongitude(), a.getPositionAt(),
                    a.getPositionSource() != null ? a.getPositionSource().name() : null,
                    a.getSpeedLimitKph(), a.getTankCapacityLiters(),
                    a.getDowntimeCostPerHour(),
                    meters.stream().map(MeterView::of).toList(),
                    CriticalityView.of(crit),
                    photos,
                    a.getVersion(), a.getFuelLevelLiters(), a.getFuelLevelAt());
        }
    }
}
