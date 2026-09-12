package ao.autocare.modules.workorder.dto;

import ao.autocare.domain.WorkOrderFaultCode;
import ao.autocare.domain.WorkOrderFluid;
import ao.autocare.domain.WorkOrderMeasurement;
import ao.autocare.domain.WorkOrderSignature;
import ao.autocare.domain.enums.Enums.FaultCodeSource;
import ao.autocare.domain.enums.Enums.FaultCodeStatus;
import ao.autocare.domain.enums.Enums.FluidAction;
import ao.autocare.domain.enums.Enums.FluidKind;
import ao.autocare.domain.enums.Enums.MeasurementVerdict;
import ao.autocare.domain.enums.Enums.SignatureRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedidos e vistas do chão de oficina. */
public final class ShopFloorDtos {

    private ShopFloorDtos() {}

    // ==== Medições =========================================================

    public record MeasurementRequest(
            @Size(max = 60) String groupName,
            @Size(max = 120) String name,
            @Size(max = 60) String position,
            BigDecimal valueNum,
            @Size(max = 200) String valueText,
            @Size(max = 20) String unit,
            BigDecimal minValue,
            BigDecimal maxValue,
            /** Só vale quando não há limites: aí é juízo do técnico, não conta. */
            MeasurementVerdict verdict,
            @Size(max = 500) String note,
            Integer sortOrder,
            Instant recordedAt) {}

    public record MeasurementView(
            String id, String groupName, String name, String position,
            BigDecimal valueNum, String valueText, String unit,
            BigDecimal minValue, BigDecimal maxValue,
            String verdict, String verdictLabel, boolean needsAction,
            String note, int sortOrder,
            String recordedByLabel, Instant recordedAt) {

        public static MeasurementView of(WorkOrderMeasurement m) {
            return new MeasurementView(
                    m.getId(), m.getGroupName(), m.getName(), m.getPosition(),
                    m.getValueNum(), m.getValueText(), m.getUnit(),
                    m.getMinValue(), m.getMaxValue(),
                    m.getVerdict().name(), m.getVerdict().label(), m.getVerdict().needsAction(),
                    m.getNote(), m.getSortOrder(),
                    m.getRecordedByLabel(), m.getRecordedAt());
        }
    }

    /** Linha de um modelo: o que medir e entre que limites. */
    public record MeasurementTemplate(
            String groupName, String name, String position, String unit,
            BigDecimal minValue, BigDecimal maxValue) {}

    // ==== Fluidos ==========================================================

    public record FluidRequest(
            @NotNull(message = "Indique o tipo de fluido.") FluidKind kind,
            @Size(max = 80) String spec,
            @Size(max = 80) String brand,
            FluidAction action,
            BigDecimal quantity,
            @Size(max = 12) String unit,
            Boolean filterChanged,
            @Size(max = 60) String filterPartNumber,
            @Size(max = 60) String batch,
            BigDecimal unitCost,
            @Size(max = 500) String note) {}

    public record FluidView(
            String id, String kind, String kindLabel, String spec, String brand,
            String action, String actionLabel, BigDecimal quantity, String unit,
            boolean filterChanged, String filterPartNumber, String batch,
            BigDecimal unitCost, BigDecimal totalCost, String note) {

        public static FluidView of(WorkOrderFluid f) {
            return new FluidView(
                    f.getId(), f.getKind().name(), f.getKind().label(),
                    f.getSpec(), f.getBrand(),
                    f.getAction().name(), f.getAction().label(),
                    f.getQuantity(), f.getUnit(),
                    f.isFilterChanged(), f.getFilterPartNumber(), f.getBatch(),
                    f.getUnitCost(), f.getTotalCost(), f.getNote());
        }
    }

    // ==== Códigos de avaria ================================================

    public record FaultCodeRequest(
            FaultCodeSource source,
            @Size(max = 40) String code,
            @Size(max = 300) String description,
            Integer occurrences,
            FaultCodeStatus status,
            Instant firstSeenAt,
            @Size(max = 500) String note) {}

    public record FaultCodeView(
            String id, String source, String sourceLabel, String code, String description,
            Integer occurrences, String status, String statusLabel,
            Instant firstSeenAt, Instant clearedAt, String note) {

        public static FaultCodeView of(WorkOrderFaultCode c) {
            return new FaultCodeView(
                    c.getId(), c.getSource().name(), c.getSource().label(),
                    c.getCode(), c.getDescription(), c.getOccurrences(),
                    c.getStatus().name(), c.getStatus().label(),
                    c.getFirstSeenAt(), c.getClearedAt(), c.getNote());
        }
    }

    // ==== Assinaturas ======================================================

    public record SignatureRequest(
            @NotNull(message = "Indique em que papel assina.") SignatureRole role,
            @Size(max = 150) String personName,
            @Size(max = 60) String personDocument,
            Boolean accepted,
            @Size(max = 500) String note) {}

    public record SignatureView(
            String id, String role, String roleLabel, String personName,
            String personDocument, boolean accepted, String note, Instant signedAt) {

        public static SignatureView of(WorkOrderSignature s) {
            return new SignatureView(
                    s.getId(), s.getRole().name(), s.getRole().label(),
                    s.getPersonName(), s.getPersonDocument(),
                    s.isAccepted(), s.getNote(), s.getSignedAt());
        }
    }

    // ==== Segurança, ensaio e próxima intervenção ==========================

    /** Bloqueio e etiquetagem: quem bloqueou, com que etiqueta, e quando. */
    public record SafetyRequest(
            Boolean lotoApplied,
            @Size(max = 40) String lotoTagNumber,
            @Size(max = 120) String appliedByLabel,
            @Size(max = 120) String removedByLabel,
            @Size(max = 40) String workPermitNumber,
            String riskLevel,
            @Size(max = 400) String ppeRequired,
            @Size(max = 2000) String safetyNotes) {}

    /** Ensaio final. Entregar sem provar devolve o problema ao condutor. */
    public record TestRequest(
            String kind,
            BigDecimal distanceKm,
            Integer durationMinutes,
            String result,
            @Size(max = 2000) String notes) {}

    public record NextServiceRequest(
            BigDecimal meter,
            BigDecimal hourMeter,
            Instant at,
            @Size(max = 1000) String note) {}

    /** Codificação da avaria (norma ISO 14224), para somar avarias iguais. */
    public record FailureCodingRequest(
            @Size(max = 40) String componentCode,
            @Size(max = 40) String failureMode,
            @Size(max = 40) String failureCause) {}
}
