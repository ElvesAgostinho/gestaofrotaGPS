package ao.autocare.modules.part;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.part.dto.PartDtos.MovementRequest;
import ao.autocare.modules.part.dto.PartDtos.MovementView;
import ao.autocare.modules.part.dto.PartDtos.PartView;
import ao.autocare.modules.part.dto.PartDtos.SavePartRequest;
import ao.autocare.modules.part.dto.PartDtos.SaveWarehouseRequest;
import ao.autocare.modules.part.dto.PartDtos.TransferRequest;
import ao.autocare.modules.part.dto.PartDtos.WarehouseView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Peças e stock")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class PartController {

    private final StockService stock;
    private final OrgContext orgContext;

    public PartController(StockService stock, OrgContext orgContext) {
        this.stock = stock;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ---- Peças ------------------------------------------------------
    @Operation(summary = "Listar peças")
    @GetMapping("/api/v1/parts")
    public List<PartView> listParts(@AuthenticationPrincipal AuthPrincipal p) {
        return stock.listParts(org(p));
    }

    @Operation(summary = "Peças abaixo do stock mínimo")
    @GetMapping("/api/v1/parts/low-stock")
    public List<PartView> lowStock(@AuthenticationPrincipal AuthPrincipal p) {
        return stock.lowStock(org(p));
    }

    @Operation(summary = "Obter uma peça (com stock por armazém)")
    @GetMapping("/api/v1/parts/{id}")
    public PartView getPart(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return stock.getPart(org(p), id);
    }

    @Operation(summary = "Criar uma peça")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/parts")
    @ResponseStatus(HttpStatus.CREATED)
    public PartView createPart(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SavePartRequest req) {
        return stock.createPart(org(p), p.id(), req);
    }

    @Operation(summary = "Atualizar uma peça")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PatchMapping("/api/v1/parts/{id}")
    public PartView updatePart(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SavePartRequest req) {
        return stock.updatePart(org(p), p.id(), id, req);
    }

    @Operation(summary = "Eliminar uma peça")
    @RequirePermission(Permission.PARTS_MANAGE)
    @DeleteMapping("/api/v1/parts/{id}")
    public Map<String, String> deletePart(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        stock.deletePart(org(p), p.id(), id);
        return Map.of("message", "Peça eliminada.");
    }

    // ---- Armazéns -------------------------------------------------
    @Operation(summary = "Listar armazéns")
    @GetMapping("/api/v1/warehouses")
    public List<WarehouseView> listWarehouses(@AuthenticationPrincipal AuthPrincipal p) {
        return stock.listWarehouses(org(p));
    }

    @Operation(summary = "Criar um armazém")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseView createWarehouse(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SaveWarehouseRequest req) {
        return stock.createWarehouse(org(p), p.id(), req);
    }

    @Operation(summary = "Atualizar um armazém")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PatchMapping("/api/v1/warehouses/{id}")
    public WarehouseView updateWarehouse(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SaveWarehouseRequest req) {
        return stock.updateWarehouse(org(p), p.id(), id, req);
    }

    // ---- Movimentos ---------------------------------------------
    @Operation(summary = "Histórico de movimentos de stock")
    @GetMapping("/api/v1/stock/movements")
    public PagedResponse<MovementView> movements(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) String partId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return stock.movements(org(p), partId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Registar um movimento (entrada, saída, ajuste)")
    @RequirePermission(Permission.STOCK_MOVE)
    @PostMapping("/api/v1/stock/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementView recordMovement(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody MovementRequest req) {
        return stock.recordMovement(org(p), p.id(), req);
    }

    @Operation(summary = "Transferir stock entre armazéns")
    @RequirePermission(Permission.PARTS_MANAGE)
    @PostMapping("/api/v1/stock/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MovementView> transfer(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody TransferRequest req) {
        return stock.transfer(org(p), p.id(), req);
    }
}
