package ao.autocare.modules.workorder.dto;

import ao.autocare.domain.Failure;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderLabor;
import ao.autocare.domain.WorkOrderPart;
import ao.autocare.domain.WorkOrderTask;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WorkOrderDtos {

    private WorkOrderDtos() {}

    // ---- Criar ------------------------------------------------------
    public record TaskInput(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 80) String systemName,
            @Size(max = 8000) String instructions) {}

    public record FailureInput(
            @NotBlank @Size(max = 500) String description,
            @Size(max = 500) String cause,
            @Size(max = 30) String systemCode,
            Boolean causedDowntime) {}

    public record CreateWorkOrderRequest(
            @NotBlank(message = "Indique o ativo.") String assetId,
            @NotNull WorkOrderType type,
            @NotBlank(message = "Indique um título.") @Size(max = 200) String title,
            @Size(max = 8000) String description,
            WorkOrderPriority priority,
            Instant scheduledFor,
            /** Membro da equipa responsável. Preenche também o nome mostrado. */
            String assignedToUserId,
            @Size(max = 120) String assignedToLabel,
            List<TaskInput> tasks,
            /** Para OM corretivas: a avaria que a originou. */
            FailureInput failure,

            // ---- Ficha completa (Fatia 16) -----------------------------------
            /**
             * Prazo. Vazio usa o prazo da prioridade — sem prazo nenhum, toda a
             * ordem está a horas e o cumprimento é sempre 100%.
             */
            Instant dueAt,
            BigDecimal estimatedHours,
            BigDecimal estimatedCost,
            /** Sistema do ativo (motor, hidráulico...), para ver avarias repetidas. */
            @Size(max = 30) String systemCode,
            String branchId,
            String driverId,
            String parentWorkOrderId,
            Boolean requiresShutdown,
            @Size(max = 2000) String safetyNotes,
            Boolean underWarranty,
            @Size(max = 120) String warrantyReference) {}

    public record FromDueRequest(
            @NotBlank String assetId,
            /** Estados a incluir; por omissão OVERDUE + DUE_SOON. */
            List<String> statuses,
            WorkOrderPriority priority,
            Instant scheduledFor,
            String assignedToUserId) {}

    public record UpdateWorkOrderRequest(
            @Size(max = 200) String title,
            @Size(max = 8000) String description,
            WorkOrderPriority priority,
            Instant scheduledFor,
            String assignedToUserId,
            @Size(max = 120) String assignedToLabel,
            Instant dueAt,
            BigDecimal estimatedHours,
            BigDecimal estimatedCost,
            @Size(max = 30) String systemCode,
            String branchId,
            String driverId,
            Boolean requiresShutdown,
            @Size(max = 2000) String safetyNotes,
            Boolean underWarranty,
            @Size(max = 120) String warrantyReference,
            BigDecimal warrantyRecovered,
            @Size(max = 2000) String rootCause,
            @Size(max = 2000) String correctiveAction) {}

    public record StartRequest(Instant startedAt, Boolean stopAsset) {}

    public record CompleteRequest(
            @Size(max = 8000) String resolution,
            Instant completedAt,
            Instant downtimeEnd,
            BigDecimal meterValue,
            /** Repor o ativo a operacional ao concluir. */
            Boolean assetBackToOperational,
            /** Leitura do medidor no fecho — diferente da de abertura. */
            BigDecimal closingMeterValue,
            @Size(max = 2000) String rootCause,
            @Size(max = 2000) String correctiveAction,
            BigDecimal warrantyRecovered) {}

    public record CancelRequest(
            @NotBlank(message = "Explique porque é que a ordem foi anulada.")
            @Size(max = 500) String reason) {}

    /** Serviço feito por uma oficina de fora. */
    public record ExternalServiceInput(
            @NotBlank(message = "Indique o fornecedor.") @Size(max = 200) String supplier,
            @NotBlank(message = "Descreva o serviço.") @Size(max = 500) String description,
            @Size(max = 60) String invoiceNumber,
            @NotNull @Positive BigDecimal cost,
            @Size(max = 3) String currency,
            Instant performedAt,
            Integer warrantyMonths,
            @Size(max = 500) String notes) {}

    public record ExternalServiceView(
            String id, String supplier, String description, String invoiceNumber,
            BigDecimal cost, String currency, Instant performedAt,
            Integer warrantyMonths, Instant warrantyUntil, boolean underWarrantyNow,
            String notes) {

        public static ExternalServiceView of(ao.autocare.domain.WorkOrderExternalService s) {
            return new ExternalServiceView(
                    s.getId(), s.getSupplier(), s.getDescription(), s.getInvoiceNumber(),
                    s.getCost(), s.getCurrency(), s.getPerformedAt(),
                    s.getWarrantyMonths(), s.getWarrantyUntil(),
                    s.isUnderWarranty(Instant.now()), s.getNotes());
        }
    }

    /** Um aviso que a ordem dá por si. Nunca bloqueia nada. */
    public record InsightView(String code, String severity, String title, String detail) {}

    public record LaborInput(
            @Size(max = 120) String technicianLabel,
            @NotNull @Positive BigDecimal hours,
            BigDecimal hourlyRate,
            Instant workedOn,
            @Size(max = 300) String notes) {}

    public record PartInput(
            String partId,
            @Size(max = 200) String partName,
            String warehouseId,
            @NotNull @Positive BigDecimal quantity,
            BigDecimal unitCost) {}

    // ---- Modulo de manutencao (Fatia 17) ---------------------------------
    /**
     * Diagnostico em quatro campos.
     *
     * <p>Separar o sintoma (o que o condutor sente) do diagnostico (o que o
     * tecnico ve), da causa e da solucao e o que permite responder, meses
     * depois, a "quantas vezes este problema foi causado pela mesma coisa?".
     */
    public record DiagnosisRequest(
            @Size(max = 2000) String symptom,
            @Size(max = 2000) String diagnosis,
            @Size(max = 2000) String probableCause,
            @Size(max = 2000) String recommendedAction,
            @Size(max = 160) String technicianLabel) {}

    public record QuoteRequest(
            String supplierId,
            @Size(max = 200) String supplierLabel,
            @Size(max = 60) String quoteNumber,
            Instant quotedAt,
            Instant validUntil,
            BigDecimal partsAmount,
            BigDecimal laborAmount,
            BigDecimal otherAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            String fileId,
            @Size(max = 2000) String notes) {}

    public record QuoteView(
            String id,
            String supplierId,
            String supplierLabel,
            String quoteNumber,
            Instant quotedAt,
            Instant validUntil,
            boolean expired,
            BigDecimal partsAmount,
            BigDecimal laborAmount,
            BigDecimal otherAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            boolean selected,
            String notes) {

        public static QuoteView of(ao.autocare.domain.WorkOrderQuote q) {
            return new QuoteView(
                    q.getId(),
                    q.getSupplier() != null ? q.getSupplier().getId() : null,
                    q.getSupplierLabel(), q.getQuoteNumber(),
                    q.getQuotedAt(), q.getValidUntil(), q.isExpired(Instant.now()),
                    q.getPartsAmount(), q.getLaborAmount(), q.getOtherAmount(),
                    q.getDiscountAmount(), q.getTaxAmount(), q.getTotalAmount(),
                    q.getCurrency(), q.isSelected(), q.getNotes());
        }
    }

    public record ApprovalRequest(BigDecimal amount, @Size(max = 1000) String note) {}

    public record StatusChangeRequest(
            @NotNull(message = "Indique o estado de destino.")
            ao.autocare.domain.enums.Enums.WorkOrderStatus status,
            @Size(max = 1000) String note) {}

    /** Uma transicao de estado, para a linha do tempo da ordem. */
    public record StatusHistoryView(
            String fromStatus,
            String fromLabel,
            String toStatus,
            String toLabel,
            String changedByLabel,
            String note,
            Integer minutesInPrevious,
            Instant changedAt) {

        public static StatusHistoryView of(ao.autocare.domain.WorkOrderStatusHistory h) {
            return new StatusHistoryView(
                    h.getFromStatus() != null ? h.getFromStatus().name() : null,
                    h.getFromStatus() != null ? h.getFromStatus().label() : null,
                    h.getToStatus().name(), h.getToStatus().label(),
                    h.getChangedByLabel(), h.getNote(),
                    h.getMinutesInPrevious(), h.getChangedAt());
        }
    }

    // ---- Vistas ---------------------------------------------------
    public record TaskView(String id, String title, String systemName, String instructions,
                           boolean done, Instant doneAt, String notes) {
        public static TaskView of(WorkOrderTask t) {
            return new TaskView(t.getId(), t.getTitle(), t.getSystemName(), t.getInstructions(),
                    t.isDone(), t.getDoneAt(), t.getNotes());
        }
    }

    public record LaborView(String id, String technicianLabel, BigDecimal hours,
                            BigDecimal hourlyRate, Instant workedOn, String notes) {
        public static LaborView of(WorkOrderLabor l) {
            return new LaborView(l.getId(), l.getTechnicianLabel(), l.getHours(),
                    l.getHourlyRate(), l.getWorkedOn(), l.getNotes());
        }
    }

    public record PartView(String id, String partId, String partName, String warehouseName,
                           BigDecimal quantity, BigDecimal unitCost, boolean consumed) {
        public static PartView of(WorkOrderPart p) {
            return new PartView(p.getId(),
                    p.getPart() != null ? p.getPart().getId() : null, p.getPartName(),
                    p.getWarehouse() != null ? p.getWarehouse().getName() : null,
                    p.getQuantity(), p.getUnitCost(), p.isConsumed());
        }
    }

    /**
     * Linha da lista de ordens.
     *
     * <p>Leva os rótulos já traduzidos além dos códigos. O ecrã tinha o seu
     * próprio mapa de estados e ficou a faltar-lhe metade quando o módulo de
     * manutenção acrescentou estados novos — uma ordem em orçamento aparecia
     * como «QUOTING». O servidor é quem sabe os estados que existem.
     */
    public record WorkOrderSummary(
            String id, String number, String assetId, String assetTag, String assetName,
            String type, String status, String priority, String title,
            String statusLabel, String typeLabel, String priorityLabel,
            Instant openedAt, Instant scheduledFor, Instant completedAt,
            String assignedTo, String assignedToUserId, int taskCount, int tasksDone) {

        public static WorkOrderSummary of(WorkOrder w) {
            int done = (int) w.getTasks().stream().filter(WorkOrderTask::isDone).count();
            return new WorkOrderSummary(
                    w.getId(), w.getNumber(), w.getAsset().getId(), w.getAsset().getTag(),
                    w.getAsset().getName(), w.getType().name(), w.getStatus().name(),
                    w.getPriority().name(), w.getTitle(),
                    w.getStatus().label(), w.getType().label(), w.getPriority().label(),
                    w.getOpenedAt(), w.getScheduledFor(),
                    w.getCompletedAt(),
                    w.getAssignedToLabel() != null ? w.getAssignedToLabel()
                            : (w.getAssignedTo() != null ? w.getAssignedTo().getName() : null),
                    w.getAssignedTo() != null ? w.getAssignedTo().getId() : null,
                    w.getTasks().size(), done);
        }
    }

    public record WorkOrderView(
            String id, String number, String assetId, String assetTag, String assetName,
            String type, String status, String priority, String title, String description,
            String resolution, String assignedToLabel, String assignedToUserId,
            BigDecimal meterValue, Instant scheduledFor, Instant openedAt, Instant startedAt,
            Instant completedAt, Instant verifiedAt, Instant downtimeStart, Instant downtimeEnd,
            BigDecimal totalLaborHours, BigDecimal totalPartsCost, String currency,
            List<TaskView> tasks, List<LaborView> labor, List<PartView> parts,
            List<ExternalServiceView> externalServices,

            // ---- Ficha completa (Fatia 16) -----------------------------------
            Instant dueAt,
            Boolean slaMet,
            BigDecimal estimatedHours,
            BigDecimal estimatedCost,
            BigDecimal totalLaborCost,
            BigDecimal totalExternalCost,
            /** Mão de obra + peças + oficina externa, menos o que a garantia devolveu. */
            BigDecimal totalCost,
            /** Nulo sem orçamento: zero sugeriria que se acertou em cheio. */
            BigDecimal costOverrunPercent,
            BigDecimal downtimeHours,
            /** Nulo quando o ativo não tem custo de paragem definido. */
            BigDecimal downtimeCost,
            boolean underWarranty,
            String warrantyReference,
            BigDecimal warrantyRecovered,
            String rootCause,
            String correctiveAction,
            String cancellationReason,
            BigDecimal closingMeterValue,
            String systemCode,
            String branchId,
            String branchName,
            String driverId,
            String driverName,
            String parentWorkOrderId,
            boolean requiresShutdown,
            String safetyNotes,
            String verifiedByName,
            /** Avisos que a ordem dá por si. Nunca bloqueiam nada. */
            // ---- Chao de oficina (Fatia 19) ----
            List<ShopFloorDtos.MeasurementView> measurements,
            List<ShopFloorDtos.FluidView> fluids,
            List<ShopFloorDtos.FaultCodeView> faultCodes,
            List<ShopFloorDtos.SignatureView> signatures,
            Boolean lotoApplied, String lotoTagNumber,
            String lotoAppliedByLabel, Instant lotoAppliedAt,
            String lotoRemovedByLabel, Instant lotoRemovedAt,
            String workPermitNumber, String riskLevel, String riskLevelLabel,
            String ppeRequired,
            BigDecimal hourMeterValue, BigDecimal closingHourMeterValue,
            Boolean testPerformed, String testKind, String testKindLabel,
            BigDecimal testDistanceKm, Integer testDurationMinutes,
            String testResult, String testResultLabel, String testNotes,
            BigDecimal nextServiceMeter, BigDecimal nextServiceHourMeter,
            Instant nextServiceAt, String nextServiceNote,
            String componentCode, String failureMode, String failureCause,
            List<InsightView> insights,

            // ---- Modulo de manutencao (Fatia 17) -----------------------------
            String statusLabel,
            String typeLabel,
            String execution,
            String supplierId,
            String supplierName,
            String symptom,
            String diagnosis,
            String probableCause,
            String recommendedAction,
            String diagnosedByLabel,
            Instant diagnosedAt,
            BigDecimal approvedAmount,
            String approvalNote,
            String approvedByName,
            Instant approvedAt,
            String rejectionReason,
            Instant rejectedAt,
            Instant closedAt,
            Integer orderYear,
            List<QuoteView> quotes,
            List<StatusHistoryView> statusHistory,
            /** Estados para onde esta ordem pode seguir a partir de onde esta. */
            List<String> nextStatuses,
            /** Cronómetros a correr agora: quem está a trabalhar nesta ordem e desde quando. */
            List<TimerView> timers) {

        public static WorkOrderView of(WorkOrder w) {
            return of(w, List.of(), List.of());
        }

        public static WorkOrderView of(WorkOrder w, List<InsightView> insights) {
            return of(w, insights, List.of());
        }

        public static WorkOrderView of(
                WorkOrder w, List<InsightView> insights, List<String> nextStatuses) {
            return of(w, insights, nextStatuses, List.of());
        }

        public static WorkOrderView of(
                WorkOrder w, List<InsightView> insights, List<String> nextStatuses, List<TimerView> timers) {
            return new WorkOrderView(
                    w.getId(), w.getNumber(), w.getAsset().getId(), w.getAsset().getTag(),
                    w.getAsset().getName(), w.getType().name(), w.getStatus().name(),
                    w.getPriority().name(), w.getTitle(), w.getDescription(), w.getResolution(),
                    w.getAssignedToLabel(),
                    w.getAssignedTo() != null ? w.getAssignedTo().getId() : null,
                    w.getMeterValue(), w.getScheduledFor(), w.getOpenedAt(), w.getStartedAt(),
                    w.getCompletedAt(), w.getVerifiedAt(), w.getDowntimeStart(), w.getDowntimeEnd(),
                    w.getTotalLaborHours(), w.getTotalPartsCost(), w.getCurrency(),
                    w.getTasks().stream().map(TaskView::of).toList(),
                    w.getLabor().stream().map(LaborView::of).toList(),
                    w.getParts().stream().map(PartView::of).toList(),
                    w.getServices().stream().map(ExternalServiceView::of).toList(),
                    w.getDueAt(), w.getSlaMet(),
                    w.getEstimatedHours(), w.getEstimatedCost(),
                    w.getTotalLaborCost(), w.getTotalExternalCost(), w.getTotalCost(),
                    w.costOverrunPercent(),
                    w.getDowntimeHours(), w.getDowntimeCost(),
                    w.isUnderWarranty(), w.getWarrantyReference(), w.getWarrantyRecovered(),
                    w.getRootCause(), w.getCorrectiveAction(), w.getCancellationReason(),
                    w.getClosingMeterValue(), w.getSystemCode(),
                    w.getBranch() != null ? w.getBranch().getId() : null,
                    w.getBranch() != null ? w.getBranch().getName() : null,
                    w.getDriver() != null ? w.getDriver().getId() : null,
                    w.getDriver() != null ? w.getDriver().getName() : null,
                    w.getParentWorkOrderId(),
                    w.isRequiresShutdown(), w.getSafetyNotes(),
                    w.getVerifiedBy() != null ? w.getVerifiedBy().getName() : null,
                    w.getMeasurements().stream().map(ShopFloorDtos.MeasurementView::of).toList(),
                w.getFluids().stream().map(ShopFloorDtos.FluidView::of).toList(),
                w.getFaultCodes().stream().map(ShopFloorDtos.FaultCodeView::of).toList(),
                w.getSignatures().stream().map(ShopFloorDtos.SignatureView::of).toList(),
                w.getLotoApplied(), w.getLotoTagNumber(),
                w.getLotoAppliedByLabel(), w.getLotoAppliedAt(),
                w.getLotoRemovedByLabel(), w.getLotoRemovedAt(),
                w.getWorkPermitNumber(),
                w.getRiskLevel() != null ? w.getRiskLevel().name() : null,
                w.getRiskLevel() != null ? w.getRiskLevel().label() : null,
                w.getPpeRequired(),
                w.getHourMeterValue(), w.getClosingHourMeterValue(),
                w.getTestPerformed(),
                w.getTestKind() != null ? w.getTestKind().name() : null,
                w.getTestKind() != null ? w.getTestKind().label() : null,
                w.getTestDistanceKm(), w.getTestDurationMinutes(),
                w.getTestResult() != null ? w.getTestResult().name() : null,
                w.getTestResult() != null ? w.getTestResult().label() : null,
                w.getTestNotes(),
                w.getNextServiceMeter(), w.getNextServiceHourMeter(),
                w.getNextServiceAt(), w.getNextServiceNote(),
                w.getComponentCode(), w.getFailureMode(), w.getFailureCause(),
                insights,
                    w.getStatus().label(), w.getType().label(), w.getExecution().name(),
                    w.getSupplier() != null ? w.getSupplier().getId() : null,
                    w.getSupplier() != null ? w.getSupplier().getName() : null,
                    w.getSymptom(), w.getDiagnosis(), w.getProbableCause(),
                    w.getRecommendedAction(), w.getDiagnosedByLabel(), w.getDiagnosedAt(),
                    w.getApprovedAmount(), w.getApprovalNote(),
                    w.getApprovedBy() != null ? w.getApprovedBy().getName() : null,
                    w.getApprovedAt(),
                    w.getRejectionReason(), w.getRejectedAt(), w.getClosedAt(),
                    w.getOrderYear(),
                    w.getQuotes().stream().map(QuoteView::of).toList(),
                    w.getStatusHistory().stream().map(StatusHistoryView::of).toList(),
                    nextStatuses, timers);
        }
    }

    /** Um cronómetro a correr. */
    public record TimerView(String userId, String userName, Instant startedAt, long minutes) {
        public static TimerView of(ao.autocare.domain.WorkOrderTimer t) {
            return new TimerView(t.getUser().getId(), t.getUser().getName(), t.getStartedAt(),
                    java.time.Duration.between(t.getStartedAt(), Instant.now()).toMinutes());
        }
    }

    /**
     * A mesma ordem, sem os valores financeiros.
     *
     * <p>O documento pede que quem nao tem permissao de custos nao veja
     * dinheiro. Esconder no ecra nao chega -- os numeros continuavam a viajar
     * na resposta e bastava abrir as ferramentas do browser. Aqui sao
     * <b>removidos</b> antes de sair do servidor.
     */
    public static WorkOrderView withoutMoney(WorkOrderView v) {
        return new WorkOrderView(
                v.id(), v.number(), v.assetId(), v.assetTag(), v.assetName(),
                v.type(), v.status(), v.priority(), v.title(), v.description(),
                v.resolution(), v.assignedToLabel(), v.assignedToUserId(),
                v.meterValue(), v.scheduledFor(), v.openedAt(), v.startedAt(),
                v.completedAt(), v.verifiedAt(), v.downtimeStart(), v.downtimeEnd(),
                v.totalLaborHours(), null, v.currency(),
                v.tasks(), v.labor(), v.parts(), v.externalServices(),
                v.dueAt(), v.slaMet(), v.estimatedHours(), null,
                null, null, null, null,
                v.downtimeHours(), null,
                v.underWarranty(), v.warrantyReference(), null,
                v.rootCause(), v.correctiveAction(), v.cancellationReason(),
                v.closingMeterValue(), v.systemCode(), v.branchId(), v.branchName(),
                v.driverId(), v.driverName(), v.parentWorkOrderId(),
                v.requiresShutdown(), v.safetyNotes(), v.verifiedByName(),
                // O chao de oficina nao e dinheiro: um tecnico sem permissao de
                // custos continua a precisar das medicoes, dos codigos de avaria
                // e das assinaturas para fazer o trabalho. So o custo dos
                // fluidos sai.
                v.measurements(), semCustoNosFluidos(v.fluids()),
                v.faultCodes(), v.signatures(),
                v.lotoApplied(), v.lotoTagNumber(),
                v.lotoAppliedByLabel(), v.lotoAppliedAt(),
                v.lotoRemovedByLabel(), v.lotoRemovedAt(),
                v.workPermitNumber(), v.riskLevel(), v.riskLevelLabel(),
                v.ppeRequired(),
                v.hourMeterValue(), v.closingHourMeterValue(),
                v.testPerformed(), v.testKind(), v.testKindLabel(),
                v.testDistanceKm(), v.testDurationMinutes(),
                v.testResult(), v.testResultLabel(), v.testNotes(),
                v.nextServiceMeter(), v.nextServiceHourMeter(),
                v.nextServiceAt(), v.nextServiceNote(),
                v.componentCode(), v.failureMode(), v.failureCause(),
                v.insights(), v.statusLabel(), v.typeLabel(), v.execution(),
                v.supplierId(), v.supplierName(),
                v.symptom(), v.diagnosis(), v.probableCause(), v.recommendedAction(),
                v.diagnosedByLabel(), v.diagnosedAt(),
                null, v.approvalNote(), v.approvedByName(), v.approvedAt(),
                v.rejectionReason(), v.rejectedAt(), v.closedAt(), v.orderYear(),
                List.of(), v.statusHistory(), v.nextStatuses(), v.timers());
    }


    /** Os mesmos fluidos, sem o que custaram. */
    private static List<ShopFloorDtos.FluidView> semCustoNosFluidos(
            List<ShopFloorDtos.FluidView> fluidos) {
        if (fluidos == null) {
            return List.of();
        }
        return fluidos.stream()
                .map(f -> new ShopFloorDtos.FluidView(
                        f.id(), f.kind(), f.kindLabel(), f.spec(), f.brand(),
                        f.action(), f.actionLabel(), f.quantity(), f.unit(),
                        f.filterChanged(), f.filterPartNumber(), f.batch(),
                        null, null, f.note()))
                .toList();
    }

    public record FailureCreated(String failureId) {}

    public record FailureView(String id, String assetId, String systemCode, String description,
                              String cause, Instant detectedAt, BigDecimal meterValue,
                              boolean causedDowntime, String workOrderId) {
        public static FailureView of(Failure f) {
            return new FailureView(f.getId(), f.getAsset().getId(), f.getSystemCode(),
                    f.getDescription(), f.getCause(), f.getDetectedAt(), f.getMeterValue(),
                    f.isCausedDowntime(), f.getWorkOrder() != null ? f.getWorkOrder().getId() : null);
        }
    }
}
