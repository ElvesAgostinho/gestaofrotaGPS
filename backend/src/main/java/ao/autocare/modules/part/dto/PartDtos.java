package ao.autocare.modules.part.dto;

import ao.autocare.domain.Part;
import ao.autocare.domain.StockItem;
import ao.autocare.domain.StockMovement;
import ao.autocare.domain.Warehouse;
import ao.autocare.domain.enums.Enums.PartCategory;
import ao.autocare.domain.enums.Enums.StockMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PartDtos {

    private PartDtos() {}

    // ---- Peças ------------------------------------------------------
    public record SavePartRequest(
            @NotBlank(message = "Indique o nome da peça.") @Size(max = 200) String name,
            @Size(max = 80) String partNumber,
            @Size(max = 30) String systemCode,
            PartCategory category,
            @Size(max = 20) String unit,
            BigDecimal minQuantity,
            BigDecimal averageCost,
            @Size(max = 3) String currency,
            @Size(max = 4000) String notes) {}

    public record StockLine(String warehouseId, String warehouseName, BigDecimal quantity,
                            BigDecimal minQuantity) {
        public static StockLine of(StockItem s) {
            return new StockLine(s.getWarehouse().getId(), s.getWarehouse().getName(),
                    s.getQuantity(),
                    s.getMinQuantity() != null ? s.getMinQuantity() : s.getPart().getMinQuantity());
        }
    }

    public record PartView(
            String id, String name, String partNumber, String systemCode, String category,
            String unit, BigDecimal minQuantity, BigDecimal averageCost, String currency,
            String notes, boolean active,
            BigDecimal totalQuantity, boolean lowStock, List<StockLine> stock) {

        public static PartView of(Part p, BigDecimal total, List<StockItem> items) {
            BigDecimal totalQ = total != null ? total : BigDecimal.ZERO;
            boolean low = p.getMinQuantity() != null
                    && p.getMinQuantity().signum() > 0
                    && totalQ.compareTo(p.getMinQuantity()) <= 0;
            return new PartView(
                    p.getId(), p.getName(), p.getPartNumber(), p.getSystemCode(),
                    p.getCategory().name(), p.getUnit(), p.getMinQuantity(), p.getAverageCost(),
                    p.getCurrency(), p.getNotes(), p.isActive(),
                    totalQ, low, items.stream().map(StockLine::of).toList());
        }

        public static PartView summary(Part p, BigDecimal total) {
            return of(p, total, List.of());
        }
    }

    // ---- Armazéns -------------------------------------------------
    public record SaveWarehouseRequest(
            @NotBlank(message = "Indique o nome do armazém.") @Size(max = 160) String name,
            String locationId) {}

    public record WarehouseView(String id, String name, String locationId, String locationName,
                                boolean active) {
        public static WarehouseView of(Warehouse w) {
            return new WarehouseView(w.getId(), w.getName(),
                    w.getLocation() != null ? w.getLocation().getId() : null,
                    w.getLocation() != null ? w.getLocation().getName() : null,
                    w.isActive());
        }
    }

    // ---- Movimentos ---------------------------------------------
    public record MovementRequest(
            @NotBlank String partId,
            @NotBlank String warehouseId,
            @NotNull StockMovementType type,
            @NotNull @Positive BigDecimal quantity,
            BigDecimal unitCost,
            @Size(max = 200) String reference,
            @Size(max = 500) String notes) {}

    public record TransferRequest(
            @NotBlank String partId,
            @NotBlank String fromWarehouseId,
            @NotBlank String toWarehouseId,
            @NotNull @Positive BigDecimal quantity,
            @Size(max = 500) String notes) {}

    public record MovementView(
            String id, String partId, String partName, String warehouseId, String warehouseName,
            String type, BigDecimal quantity, BigDecimal balanceAfter, BigDecimal unitCost,
            String reference, Instant createdAt) {

        public static MovementView of(StockMovement m) {
            return new MovementView(
                    m.getId(), m.getPart().getId(), m.getPart().getName(),
                    m.getWarehouse().getId(), m.getWarehouse().getName(),
                    m.getMovementType().name(), m.getQuantity(), m.getBalanceAfter(),
                    m.getUnitCost(), m.getReference(), m.getCreatedAt());
        }
    }
}
