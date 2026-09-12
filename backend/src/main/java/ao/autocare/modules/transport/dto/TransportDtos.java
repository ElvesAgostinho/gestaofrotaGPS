package ao.autocare.modules.transport.dto;

import ao.autocare.domain.TransportNote;
import ao.autocare.domain.TransportNoteItem;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedidos e vistas da guia de transporte. */
public final class TransportDtos {

    private TransportDtos() {}

    public record ItemRequest(
            @Size(max = 300) String description,
            @Size(max = 60) String reference,
            BigDecimal quantity,
            @Size(max = 20) String unit,
            BigDecimal weightKg,
            BigDecimal volumeM3,
            Integer packages,
            @Size(max = 500) String notes) {}

    public record SaveNoteRequest(
            String assetId,
            @Size(max = 30) String trailerPlate,
            String driverId,
            @Size(max = 150) String driverLabel,
            @Size(max = 60) String driverLicense,

            String originBranchId,
            @Size(max = 300) String originLabel,
            @Size(max = 400) String originAddress,
            @Size(max = 300) String destinationLabel,
            @Size(max = 400) String destinationAddress,
            String routeId,

            @Size(max = 200) String customerName,
            @Size(max = 40) String customerTaxId,
            @Size(max = 120) String customerContact,

            @Size(max = 1000) String cargoDescription,
            @Size(max = 40) String hazardClass,
            @Size(max = 2000) String notes,

            List<ItemRequest> items) {}

    public record DepartRequest(BigDecimal meter) {}

    public record DeliverRequest(
            @Size(max = 150) String receivedByName,
            @Size(max = 60) String receivedByDocument,
            Boolean accepted,
            BigDecimal arrivalMeter,
            @Size(max = 1000) String notes) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record ItemView(
            String id, String description, String reference,
            BigDecimal quantity, String unit,
            BigDecimal weightKg, BigDecimal volumeM3, Integer packages, String notes) {

        public static ItemView of(TransportNoteItem i) {
            return new ItemView(i.getId(), i.getDescription(), i.getReference(),
                    i.getQuantity(), i.getUnit(),
                    i.getWeightKg(), i.getVolumeM3(), i.getPackages(), i.getNotes());
        }
    }

    /** Linha da lista. Leve de propósito: a ficha é que traz a carga toda. */
    public record NoteSummary(
            String id, String number, String status, String statusLabel,
            String assetTag, String driverName,
            String originLabel, String destinationLabel, String customerName,
            BigDecimal totalWeightKg, Integer totalPackages,
            Instant issuedAt, Instant deliveredAt) {

        public static NoteSummary of(TransportNote n) {
            return new NoteSummary(
                    n.getId(), n.getNumber(), n.getStatus().name(), n.getStatus().label(),
                    n.getAsset() != null ? n.getAsset().getTag() : null,
                    n.getDriver() != null ? n.getDriver().getName() : n.getDriverLabel(),
                    n.getOriginLabel(), n.getDestinationLabel(), n.getCustomerName(),
                    n.getTotalWeightKg(), n.getTotalPackages(),
                    n.getIssuedAt(), n.getDeliveredAt());
        }
    }

    public record NoteView(
            String id, String number, int noteYear, String status, String statusLabel,
            String assetId, String assetTag, String assetName, String trailerPlate,
            String driverId, String driverName, String driverLicense,
            String originBranchId, String originLabel, String originAddress,
            String destinationLabel, String destinationAddress, String routeId,
            String customerName, String customerTaxId, String customerContact,
            Instant issuedAt, Instant departedAt, Instant deliveredAt,
            BigDecimal departureMeter, BigDecimal arrivalMeter, BigDecimal distanceKm,
            BigDecimal totalWeightKg, BigDecimal totalVolumeM3, Integer totalPackages,
            String cargoDescription, String hazardClass,
            String receivedByName, String receivedByDocument,
            Boolean deliveryAccepted, String deliveryNotes,
            String cancellationReason, String notes,
            List<ItemView> items, List<String> nextActions) {

        public static NoteView of(TransportNote n) {
            return new NoteView(
                    n.getId(), n.getNumber(), n.getNoteYear(),
                    n.getStatus().name(), n.getStatus().label(),
                    n.getAsset() != null ? n.getAsset().getId() : null,
                    n.getAsset() != null ? n.getAsset().getTag() : null,
                    n.getAsset() != null ? n.getAsset().getName() : null,
                    n.getTrailerPlate(),
                    n.getDriver() != null ? n.getDriver().getId() : null,
                    n.getDriver() != null ? n.getDriver().getName() : n.getDriverLabel(),
                    n.getDriverLicense(),
                    n.getOriginBranch() != null ? n.getOriginBranch().getId() : null,
                    n.getOriginLabel(), n.getOriginAddress(),
                    n.getDestinationLabel(), n.getDestinationAddress(), n.getRouteId(),
                    n.getCustomerName(), n.getCustomerTaxId(), n.getCustomerContact(),
                    n.getIssuedAt(), n.getDepartedAt(), n.getDeliveredAt(),
                    n.getDepartureMeter(), n.getArrivalMeter(), n.getDistanceKm(),
                    n.getTotalWeightKg(), n.getTotalVolumeM3(), n.getTotalPackages(),
                    n.getCargoDescription(), n.getHazardClass(),
                    n.getReceivedByName(), n.getReceivedByDocument(),
                    n.getDeliveryAccepted(), n.getDeliveryNotes(),
                    n.getCancellationReason(), n.getNotes(),
                    n.getItems().stream().map(ItemView::of).toList(),
                    proximas(n));
        }

        /**
         * O que se pode fazer a seguir.
         *
         * <p>Vem do servidor para o ecrã não ter de repetir as regras do
         * percurso — e ficar a divergir delas quando mudarem.
         */
        private static List<String> proximas(TransportNote n) {
            return switch (n.getStatus()) {
                case DRAFT -> List.of("ISSUE", "CANCEL");
                case ISSUED -> List.of("DEPART", "DELIVER", "CANCEL");
                case IN_TRANSIT -> List.of("DELIVER", "CANCEL");
                case DELIVERED, CANCELLED -> List.of();
            };
        }
    }
}
