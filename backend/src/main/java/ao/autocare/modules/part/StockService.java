package ao.autocare.modules.part;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Part;
import ao.autocare.domain.StockItem;
import ao.autocare.domain.StockMovement;
import ao.autocare.domain.Warehouse;
import ao.autocare.domain.enums.Enums.StockMovementType;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.part.dto.PartDtos.MovementRequest;
import ao.autocare.modules.part.dto.PartDtos.MovementView;
import ao.autocare.modules.part.dto.PartDtos.SavePartRequest;
import ao.autocare.modules.part.dto.PartDtos.SaveWarehouseRequest;
import ao.autocare.modules.part.dto.PartDtos.PartView;
import ao.autocare.modules.part.dto.PartDtos.TransferRequest;
import ao.autocare.modules.part.dto.PartDtos.WarehouseView;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PartRepository;
import ao.autocare.repo.StockItemRepository;
import ao.autocare.repo.StockMovementRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WarehouseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final PartRepository parts;
    private final WarehouseRepository warehouses;
    private final StockItemRepository stockItems;
    private final StockMovementRepository movements;
    private final OrganizationRepository organizations;
    private final LocationRepository locations;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public StockService(
            PartRepository parts,
            WarehouseRepository warehouses,
            StockItemRepository stockItems,
            StockMovementRepository movements,
            OrganizationRepository organizations,
            LocationRepository locations,
            UserRepository users,
            NotificationService notifications,
            AuditService audit) {
        this.parts = parts;
        this.warehouses = warehouses;
        this.stockItems = stockItems;
        this.movements = movements;
        this.organizations = organizations;
        this.locations = locations;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ==== Peças ======================================================
    @Transactional(readOnly = true)
    public List<PartView> listParts(String orgId) {
        return parts.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(p -> PartView.summary(p, stockItems.totalQuantityForPart(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartView getPart(String orgId, String id) {
        Part p = requirePart(orgId, id);
        return PartView.of(p, stockItems.totalQuantityForPart(id), stockItems.findByPartId(id));
    }

    @Transactional(readOnly = true)
    public List<PartView> lowStock(String orgId) {
        return stockItems.findLowStock(orgId).stream()
                .map(StockItem::getPart)
                .distinct()
                .map(p -> PartView.summary(p, stockItems.totalQuantityForPart(p.getId())))
                .toList();
    }

    @Transactional
    public PartView createPart(String orgId, String userId, SavePartRequest req) {
        if (req.partNumber() != null && !req.partNumber().isBlank()
                && parts.existsByOrganizationIdAndPartNumberIgnoreCase(orgId, req.partNumber().trim())) {
            throw ApiException.conflict("Já existe uma peça com esta referência.");
        }
        Part p = new Part();
        p.setOrganization(organizations.getReferenceById(orgId));
        applyPart(p, req);
        parts.save(p);
        audit.record(orgId, userId, "part.create", "Part", p.getId(), p.getName());
        return PartView.of(p, BigDecimal.ZERO, List.of());
    }

    @Transactional
    public PartView updatePart(String orgId, String userId, String id, SavePartRequest req) {
        Part p = requirePart(orgId, id);
        applyPart(p, req);
        audit.record(orgId, userId, "part.update", "Part", p.getId(), p.getName());
        return PartView.of(p, stockItems.totalQuantityForPart(id), stockItems.findByPartId(id));
    }

    @Transactional
    public void deletePart(String orgId, String userId, String id) {
        Part p = requirePart(orgId, id);
        if (!stockItems.findByPartId(id).isEmpty()) {
            throw ApiException.conflict("Esta peça tem stock registado. Zere o stock primeiro.");
        }
        parts.delete(p);
        audit.record(orgId, userId, "part.delete", "Part", id, p.getName());
    }

    // ==== Armazéns ===================================================
    @Transactional(readOnly = true)
    public List<WarehouseView> listWarehouses(String orgId) {
        return warehouses.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(WarehouseView::of).toList();
    }

    @Transactional
    public WarehouseView createWarehouse(String orgId, String userId, SaveWarehouseRequest req) {
        Warehouse w = new Warehouse();
        w.setOrganization(organizations.getReferenceById(orgId));
        w.setName(req.name().trim());
        if (req.locationId() != null && !req.locationId().isBlank()) {
            w.setLocation(locations.findByIdAndOrganizationId(req.locationId(), orgId)
                    .orElseThrow(() -> ApiException.badRequest("Local inválido.")));
        }
        warehouses.save(w);
        audit.record(orgId, userId, "warehouse.create", "Warehouse", w.getId(), w.getName());
        return WarehouseView.of(w);
    }

    @Transactional
    public WarehouseView updateWarehouse(String orgId, String userId, String id, SaveWarehouseRequest req) {
        Warehouse w = warehouses.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Armazém não encontrado."));
        if (req.name() != null && !req.name().isBlank()) w.setName(req.name().trim());
        if (req.locationId() != null) {
            w.setLocation(req.locationId().isBlank() ? null
                    : locations.findByIdAndOrganizationId(req.locationId(), orgId)
                        .orElseThrow(() -> ApiException.badRequest("Local inválido.")));
        }
        audit.record(orgId, userId, "warehouse.update", "Warehouse", w.getId(), w.getName());
        return WarehouseView.of(w);
    }

    // ==== Movimentos ================================================
    @Transactional(readOnly = true)
    public PagedResponse<MovementView> movements(String orgId, String partId, Pageable pageable) {
        if (partId != null && !partId.isBlank()) {
            requirePart(orgId, partId);
            return PagedResponse.of(movements.findByPartIdOrderByCreatedAtDesc(partId, pageable)
                    .map(MovementView::of));
        }
        return PagedResponse.of(movements.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                .map(MovementView::of));
    }

    @Transactional
    public MovementView recordMovement(String orgId, String userId, MovementRequest req) {
        Part part = requirePart(orgId, req.partId());
        Warehouse wh = requireWarehouse(orgId, req.warehouseId());

        BigDecimal signed = switch (req.type()) {
            case IN, TRANSFER_IN, ADJUSTMENT -> req.quantity();
            case OUT_WORK_ORDER, OUT_OTHER, TRANSFER_OUT -> req.quantity().negate();
        };
        StockMovement m = apply(orgId, userId, part, wh, req.type(), signed,
                req.unitCost(), req.reference(), null, req.notes());
        audit.record(orgId, userId, "stock.movement", "Part", part.getId(),
                req.type() + " " + req.quantity() + " " + part.getUnit() + " · " + part.getName());
        return MovementView.of(m);
    }

    @Transactional
    public List<MovementView> transfer(String orgId, String userId, TransferRequest req) {
        if (req.fromWarehouseId().equals(req.toWarehouseId())) {
            throw ApiException.badRequest("Os armazéns de origem e destino têm de ser diferentes.");
        }
        Part part = requirePart(orgId, req.partId());
        Warehouse from = requireWarehouse(orgId, req.fromWarehouseId());
        Warehouse to = requireWarehouse(orgId, req.toWarehouseId());
        String ref = "Transferência " + from.getName() + " → " + to.getName();

        StockMovement out = apply(orgId, userId, part, from, StockMovementType.TRANSFER_OUT,
                req.quantity().negate(), null, ref, null, req.notes());
        StockMovement in = apply(orgId, userId, part, to, StockMovementType.TRANSFER_IN,
                req.quantity(), part.getAverageCost(), ref, null, req.notes());
        audit.record(orgId, userId, "stock.transfer", "Part", part.getId(), ref);
        return List.of(MovementView.of(out), MovementView.of(in));
    }

    /** Consumo de peça por uma Ordem de Manutenção (chamado pelo módulo de OM). */
    @Transactional
    public void consumeForWorkOrder(
            String orgId, String userId, String partId, String warehouseId,
            BigDecimal quantity, String workOrderId, String reference) {
        Part part = requirePart(orgId, partId);
        Warehouse wh = requireWarehouse(orgId, warehouseId);
        apply(orgId, userId, part, wh, StockMovementType.OUT_WORK_ORDER,
                quantity.negate(), part.getAverageCost(), reference, workOrderId, null);
    }

    // ------------------------------------------------------------------
    private StockMovement apply(
            String orgId, String userId, Part part, Warehouse wh, StockMovementType type,
            BigDecimal signedQty, BigDecimal unitCost, String reference,
            String workOrderId, String notes) {

        StockItem item = stockItems.findByPartIdAndWarehouseId(part.getId(), wh.getId())
                .orElseGet(() -> {
                    StockItem fresh = new StockItem();
                    fresh.setPart(part);
                    fresh.setWarehouse(wh);
                    fresh.setQuantity(BigDecimal.ZERO);
                    return fresh;
                });

        BigDecimal oldQty = item.getQuantity();
        BigDecimal newQty;
        BigDecimal recordedQty;
        if (type == StockMovementType.ADJUSTMENT) {
            newQty = signedQty;              // ajuste define o valor absoluto
            recordedQty = newQty.subtract(oldQty); // delta informativo no movimento
        } else {
            newQty = oldQty.add(signedQty);
            recordedQty = signedQty;
        }
        if (newQty.signum() < 0) {
            throw ApiException.conflict("Stock insuficiente de \"" + part.getName()
                    + "\" em " + wh.getName() + " (disponível: " + oldQty + ").");
        }
        item.setQuantity(newQty.setScale(2, RoundingMode.HALF_UP));
        stockItems.save(item);
        warnIfCrossedMinimum(orgId, part, oldQty, item.getQuantity(), wh.getName());

        // Custo médio ponderado nas entradas
        if ((type == StockMovementType.IN) && unitCost != null && unitCost.signum() > 0) {
            recalcAverageCost(part, signedQty, unitCost);
        }

        StockMovement m = new StockMovement();
        m.setOrganization(organizations.getReferenceById(orgId));
        m.setPart(part);
        m.setWarehouse(wh);
        m.setMovementType(type);
        m.setQuantity(recordedQty);
        m.setBalanceAfter(item.getQuantity());
        m.setUnitCost(unitCost);
        m.setReference(reference);
        m.setWorkOrderId(workOrderId);
        m.setNotes(notes);
        if (userId != null) m.setPerformedBy(users.getReferenceById(userId));
        return movements.save(m);
    }

    /**
     * Avisa quando o stock <b>atravessa</b> o mínimo, não sempre que está abaixo.
     * Sem esta distinção, cada saída de uma peça já em falta geraria um aviso
     * novo e a caixa de notificações passava a ser ignorada.
     *
     * <p>Como a origem do aviso é a peça, um segundo cruzamento só volta a
     * avisar depois de alguém ter reposto o stock acima do mínimo — o registo
     * anterior é apagado nesse momento.
     */
    private void warnIfCrossedMinimum(
            String orgId, Part part, BigDecimal before, BigDecimal after, String warehouseName) {

        BigDecimal minimum = part.getMinQuantity();
        if (minimum == null || minimum.signum() <= 0) {
            return;
        }
        boolean wasAbove = before.compareTo(minimum) >= 0;
        boolean nowBelow = after.compareTo(minimum) < 0;
        if (!nowBelow) {
            // Stock reposto: o aviso deixa de fazer sentido e sai do caminho,
            // para que um novo esgotamento volte a avisar.
            notifications.resolve("part_low_stock", part.getId());
            return;
        }
        if (!wasAbove) {
            return; // já estava em falta; não se repete o aviso a cada saída
        }
        notifications.notifyManagers(NotificationService.Draft.of(
                orgId,
                ao.autocare.domain.enums.Enums.AlertCategory.STOCK,
                ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                "Stock no mínimo — " + part.getName(),
                part.getName() + " em " + warehouseName + ": " + after
                        + " (mínimo " + minimum + ").",
                "part_low_stock", part.getId(),
                "/pecas/" + part.getId()));
    }

    private void recalcAverageCost(Part part, BigDecimal inQty, BigDecimal unitCost) {
        BigDecimal existingQty = stockItems.totalQuantityForPart(part.getId());
        BigDecimal oldAvg = part.getAverageCost() != null ? part.getAverageCost() : unitCost;
        BigDecimal oldValue = existingQty.subtract(inQty).max(BigDecimal.ZERO).multiply(oldAvg);
        BigDecimal inValue = inQty.multiply(unitCost);
        BigDecimal totalQty = existingQty;
        if (totalQty.signum() > 0) {
            part.setAverageCost(oldValue.add(inValue).divide(totalQty, 2, RoundingMode.HALF_UP));
        }
    }

    private void applyPart(Part p, SavePartRequest req) {
        p.setName(req.name().trim());
        p.setPartNumber(blankToNull(req.partNumber()));
        p.setSystemCode(blankToNull(req.systemCode()));
        if (req.category() != null) p.setCategory(req.category());
        if (req.unit() != null && !req.unit().isBlank()) p.setUnit(req.unit().trim());
        if (req.minQuantity() != null) p.setMinQuantity(req.minQuantity());
        if (req.averageCost() != null) p.setAverageCost(req.averageCost());
        if (req.currency() != null && !req.currency().isBlank()) {
            p.setCurrency(req.currency().trim().toUpperCase());
        }
        p.setNotes(blankToNull(req.notes()));
    }

    private Part requirePart(String orgId, String id) {
        return parts.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Peça não encontrada."));
    }

    private Warehouse requireWarehouse(String orgId, String id) {
        return warehouses.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Armazém não encontrado."));
    }

    /** Usado por outros módulos (ex.: Ordens de Manutenção). */
    public Part requirePartInternal(String orgId, String id) {
        return requirePart(orgId, id);
    }

    public Warehouse requireWarehouseInternal(String orgId, String id) {
        return requireWarehouse(orgId, id);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
