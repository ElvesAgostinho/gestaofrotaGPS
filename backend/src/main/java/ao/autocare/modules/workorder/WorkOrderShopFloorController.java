package ao.autocare.modules.workorder;

import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FailureCodingRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FaultCodeRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FaultCodeView;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FluidRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FluidView;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.MeasurementRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.MeasurementTemplate;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.MeasurementView;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.NextServiceRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.SafetyRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.SignatureRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.SignatureView;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.TestRequest;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O chão de oficina de uma ordem de manutenção.
 *
 * <p>Tudo aqui exige, no mínimo, o papel de técnico: quem consulta não regista
 * medições nem assina trabalho.
 */
@Tag(name = "Ordens — chão de oficina")
@RestController
public class WorkOrderShopFloorController {

    private final WorkOrderShopFloorService shopFloor;
    private final OrgContext orgContext;

    public WorkOrderShopFloorController(
            WorkOrderShopFloorService shopFloor, OrgContext orgContext) {
        this.shopFloor = shopFloor;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ==== Medições =========================================================

    @Operation(summary = "Registar uma medição",
            description = "Espessura de pastilha, piso de pneu, tensão, pressão, prova de carga. "
                    + "O veredicto é calculado a partir dos limites de serviço.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/measurements")
    public List<MeasurementView> addMeasurement(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody MeasurementRequest req) {
        return shopFloor.addMeasurement(org(p), p.id(), id, req).stream()
                .map(MeasurementView::of).toList();
    }

    @Operation(summary = "Remover uma medição")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @DeleteMapping("/api/v1/work-orders/{id}/measurements/{measurementId}")
    public Map<String, String> removeMeasurement(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String measurementId) {
        shopFloor.removeMeasurement(org(p), p.id(), id, measurementId);
        return Map.of("message", "Medição removida.");
    }

    @Operation(summary = "O que se mede em cada tipo de equipamento",
            description = "Modelos com os limites de serviço habituais já preenchidos. "
                    + "O manual do fabricante manda sempre — por isso são editáveis.")
    @GetMapping("/api/v1/work-orders/measurement-templates")
    public List<MeasurementTemplate> templates(
            @RequestParam(defaultValue = "HEAVY") String kind) {
        return shopFloor.templates(kind);
    }

    // ==== Fluidos ==========================================================

    @Operation(summary = "Registar um fluido ou lubrificante",
            description = "Especificação, quantidade, filtro trocado e lote.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/fluids")
    public List<FluidView> addFluid(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody FluidRequest req) {
        return shopFloor.addFluid(org(p), p.id(), id, req).stream()
                .map(FluidView::of).toList();
    }

    @Operation(summary = "Remover um fluido")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @DeleteMapping("/api/v1/work-orders/{id}/fluids/{fluidId}")
    public Map<String, String> removeFluid(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String fluidId) {
        shopFloor.removeFluid(org(p), p.id(), id, fluidId);
        return Map.of("message", "Fluido removido.");
    }

    // ==== Códigos de avaria ================================================

    @Operation(summary = "Registar um código de avaria lido no equipamento",
            description = "J1939 (SPN/FMI) num pesado, OBD-II num ligeiro, painel num gerador.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/fault-codes")
    public List<FaultCodeView> addFaultCode(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody FaultCodeRequest req) {
        return shopFloor.addFaultCode(org(p), p.id(), id, req).stream()
                .map(FaultCodeView::of).toList();
    }

    @Operation(summary = "Marcar um código como apagado")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/fault-codes/{codeId}/clear")
    public List<FaultCodeView> clearFaultCode(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String codeId) {
        return shopFloor.clearFaultCode(org(p), p.id(), id, codeId).stream()
                .map(FaultCodeView::of).toList();
    }

    @Operation(summary = "Remover um código de avaria")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @DeleteMapping("/api/v1/work-orders/{id}/fault-codes/{codeId}")
    public Map<String, String> removeFaultCode(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @PathVariable String codeId) {
        shopFloor.removeFaultCode(org(p), p.id(), id, codeId);
        return Map.of("message", "Código removido.");
    }

    // ==== Assinaturas ======================================================

    @Operation(summary = "Assinar a ordem",
            description = "Uma assinatura por papel. Quem assina com reservas tem de dizer quais.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PostMapping("/api/v1/work-orders/{id}/signatures")
    public List<SignatureView> sign(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SignatureRequest req) {
        return shopFloor.sign(org(p), p.id(), id, req).stream()
                .map(SignatureView::of).toList();
    }

    // ==== Segurança, ensaio, próxima intervenção ===========================

    @Operation(summary = "Bloqueio, etiquetagem e equipamento de proteção",
            description = "A hora é posta pelo servidor: numa auditoria, uma hora escrita "
                    + "pelo próprio operador não prova nada.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PutMapping("/api/v1/work-orders/{id}/safety")
    public Map<String, Object> safety(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody SafetyRequest req) {
        var w = shopFloor.saveSafety(org(p), p.id(), id, req);
        return Map.of(
                "lotoApplied", Boolean.TRUE.equals(w.getLotoApplied()),
                "message", "Registo de segurança atualizado.");
    }

    @Operation(summary = "Registar o ensaio final")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PutMapping("/api/v1/work-orders/{id}/test")
    public Map<String, String> test(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody TestRequest req) {
        shopFloor.saveTest(org(p), p.id(), id, req);
        return Map.of("message", "Ensaio registado.");
    }

    @Operation(summary = "Marcar a próxima intervenção recomendada")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PutMapping("/api/v1/work-orders/{id}/next-service")
    public Map<String, String> nextService(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody NextServiceRequest req) {
        shopFloor.saveNextService(org(p), p.id(), id, req);
        return Map.of("message", "Próxima intervenção marcada.");
    }

    @Operation(summary = "Codificar a avaria (norma ISO 14224)",
            description = "Componente, modo de falha e causa — para somar avarias iguais "
                    + "em toda a frota.")
    @RequirePermission(Permission.WORKORDERS_MANAGE)
    @PutMapping("/api/v1/work-orders/{id}/failure-coding")
    public Map<String, String> failureCoding(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @Valid @RequestBody FailureCodingRequest req) {
        shopFloor.saveFailureCoding(org(p), p.id(), id, req);
        return Map.of("message", "Avaria codificada.");
    }
}
