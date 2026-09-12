package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.RiskLevel;
import ao.autocare.domain.enums.Enums.TestKind;
import ao.autocare.domain.enums.Enums.TestResult;
import ao.autocare.domain.enums.Enums.WorkOrderExecution;
import ao.autocare.domain.enums.Enums.WorkOrderPriority;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import ao.autocare.domain.enums.Enums.WorkOrderType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Ordem de Manutenção. */
@Getter
@Setter
@Entity
@Table(name = "work_orders")
public class WorkOrder extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 15)
    private WorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkOrderPriority priority = WorkOrderPriority.NORMAL;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column(name = "assigned_to_label", length = 120)
    private String assignedToLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "meter_value", precision = 14, scale = 2)
    private BigDecimal meterValue;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "downtime_start")
    private Instant downtimeStart;

    @Column(name = "downtime_end")
    private Instant downtimeEnd;

    @Column(name = "total_labor_hours", precision = 10, scale = 2)
    private BigDecimal totalLaborHours;

    @Column(name = "total_parts_cost", precision = 14, scale = 2)
    private BigDecimal totalPartsCost;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<WorkOrderTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderLabor> labor = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderExternalService> services = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderAttachment> attachments = new ArrayList<>();

    // ---- Prazo (Fatia 16) --------------------------------------------------
    @Column(name = "due_at")
    private Instant dueAt;

    /**
     * Cumpriu o prazo.
     *
     * <p>Guardado no fecho, e nao calculado na leitura: mudar a regra de prazos
     * amanha reescreveria a historia de cumprimento de ontem, e os indicadores
     * de um ano fechado deixariam de bater certo com o que foi reportado.
     */
    @Column(name = "sla_met")
    private Boolean slaMet;

    // ---- Previsto e realizado ---------------------------------------------
    @Column(name = "estimated_hours", precision = 10, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "estimated_cost", precision = 16, scale = 2)
    private BigDecimal estimatedCost;

    // ---- Custo -------------------------------------------------------------
    @Column(name = "total_labor_cost", precision = 16, scale = 2)
    private BigDecimal totalLaborCost;

    @Column(name = "total_external_cost", precision = 16, scale = 2)
    private BigDecimal totalExternalCost;

    @Column(name = "total_cost", precision = 16, scale = 2)
    private BigDecimal totalCost;

    // ---- Paragem -----------------------------------------------------------
    @Column(name = "downtime_hours", precision = 10, scale = 2)
    private BigDecimal downtimeHours;

    @Column(name = "downtime_cost", precision = 16, scale = 2)
    private BigDecimal downtimeCost;

    // ---- Garantia, causa e desfecho ---------------------------------------
    @Column(name = "under_warranty", nullable = false)
    private boolean underWarranty;

    @Column(name = "warranty_reference", length = 120)
    private String warrantyReference;

    @Column(name = "warranty_recovered", precision = 16, scale = 2)
    private BigDecimal warrantyRecovered;

    @Column(name = "root_cause", length = 2000)
    private String rootCause;

    @Column(name = "corrective_action", length = 2000)
    private String correctiveAction;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    // ---- Contexto ----------------------------------------------------------
    @Column(name = "closing_meter_value", precision = 14, scale = 2)
    private BigDecimal closingMeterValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Location branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /** Ordem que deu origem a esta -- tipicamente uma inspecao que achou algo. */
    @Column(name = "parent_work_order_id", length = 36)
    private String parentWorkOrderId;

    @Column(name = "system_code", length = 30)
    private String systemCode;

    /** Obriga a parar o ativo para ser executada. */
    @Column(name = "requires_shutdown", nullable = false)
    private boolean requiresShutdown;

    @Column(name = "safety_notes", length = 2000)
    private String safetyNotes;

    // ---- Modulo de manutencao (Fatia 17) ----------------------------------
    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderQuote> quotes = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt asc")
    private List<WorkOrderStatusHistory> statusHistory = new ArrayList<>();

    /**
     * Diagnostico em quatro campos, nao num so de texto livre.
     *
     * <p>"Vibra ao travar" (o que o condutor sente), "discos empenados" (o que
     * o tecnico ve), "travagem prolongada em descida" (porque aconteceu) e
     * "substituir discos" (o que fazer) sao coisas diferentes -- e so separadas
     * permitem perceber, meses depois, que o problema volta sempre pela mesma
     * razao.
     */
    @Column(length = 2000)
    private String symptom;

    @Column(length = 2000)
    private String diagnosis;

    @Column(name = "probable_cause", length = 2000)
    private String probableCause;

    @Column(name = "recommended_action", length = 2000)
    private String recommendedAction;

    @Column(name = "diagnosed_by", length = 36)
    private String diagnosedBy;

    @Column(name = "diagnosed_by_label", length = 160)
    private String diagnosedByLabel;

    @Column(name = "diagnosed_at")
    private Instant diagnosedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderExecution execution = WorkOrderExecution.INTERNAL;

    /** Valor aprovado. Gastar acima disto precisa de nova aprovacao. */
    /**
     * Quando foi aprovada.
     *
     * <p>O "quem" existia desde o inicio; o "quando" faltava. Numa aprovacao de
     * despesa, saber quem autorizou sem saber em que momento nao serve de muito.
     */
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_amount", precision = 16, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "approval_note", length = 1000)
    private String approvalNote;

    @Column(name = "rejected_by", length = 36)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 36)
    private String closedBy;

    /** Ano da numeracao: OM-2026-000001. */
    @Column(name = "order_year")
    private Integer orderYear;

    public void addQuote(WorkOrderQuote q) {
        q.setWorkOrder(this);
        quotes.add(q);
    }

    public void addStatusChange(WorkOrderStatusHistory h) {
        h.setWorkOrder(this);
        statusHistory.add(h);
    }

    /** Ja tem diagnostico registado. */
    public boolean hasDiagnosis() {
        return (diagnosis != null && !diagnosis.isBlank())
                || (symptom != null && !symptom.isBlank());
    }

    /** Orcamento escolhido, se houver. */
    public java.util.Optional<WorkOrderQuote> selectedQuote() {
        return quotes.stream().filter(WorkOrderQuote::isSelected).findFirst();
    }

    public void addService(WorkOrderExternalService s) {
        s.setWorkOrder(this);
        services.add(s);
    }

    public void addAttachment(WorkOrderAttachment a) {
        a.setWorkOrder(this);
        attachments.add(a);
    }

    /**
     * Custo do que ficou por fazer face ao previsto, em percentagem.
     *
     * <p>Nulo quando nao houve estimativa: zero sugeriria que se acertou em
     * cheio, e nao ha nada com que comparar.
     */
    public BigDecimal costOverrunPercent() {
        if (estimatedCost == null || estimatedCost.signum() <= 0 || totalCost == null) {
            return null;
        }
        return totalCost.subtract(estimatedCost)
                .multiply(BigDecimal.valueOf(100))
                .divide(estimatedCost, 1, java.math.RoundingMode.HALF_UP);
    }

    public void addTask(WorkOrderTask t) {
        t.setWorkOrder(this);
        tasks.add(t);
    }

    public void addLabor(WorkOrderLabor l) {
        l.setWorkOrder(this);
        labor.add(l);
    }

    public void addPart(WorkOrderPart p) {
        p.setWorkOrder(this);
        parts.add(p);
    }

    // ==== Chao de oficina (Fatia 19) ========================================

    /**
     * Bloqueio e etiquetagem.
     *
     * <p>{@code requiresShutdown} e {@code safetyNotes} ja existiam, mas em
     * texto livre. Numa intervencao com risco, quem bloqueou e quem
     * desbloqueou tem de ficar registado com nome e hora, ou o procedimento
     * nao vale nada numa auditoria de seguranca.
     */
    @Column(name = "loto_applied")
    private Boolean lotoApplied = Boolean.FALSE;

    @Column(name = "loto_tag_number", length = 40)
    private String lotoTagNumber;

    @Column(name = "loto_applied_by_label", length = 120)
    private String lotoAppliedByLabel;

    @Column(name = "loto_applied_at")
    private Instant lotoAppliedAt;

    @Column(name = "loto_removed_by_label", length = 120)
    private String lotoRemovedByLabel;

    @Column(name = "loto_removed_at")
    private Instant lotoRemovedAt;

    @Column(name = "work_permit_number", length = 40)
    private String workPermitNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "ppe_required", length = 400)
    private String ppeRequired;

    /**
     * Segundo medidor.
     *
     * <p>Um gerador conta-se em horas e um camiao em quilometros, mas um
     * camiao com tomada de forca tem os dois, e a revisao depende de ambos.
     */
    @Column(name = "hour_meter_value", precision = 18, scale = 2)
    private BigDecimal hourMeterValue;

    @Column(name = "closing_hour_meter_value", precision = 18, scale = 2)
    private BigDecimal closingHourMeterValue;

    /** Ensaio final. Entregar sem provar devolve o problema ao condutor. */
    @Column(name = "test_performed")
    private Boolean testPerformed = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_kind", length = 20)
    private TestKind testKind;

    @Column(name = "test_distance_km", precision = 12, scale = 2)
    private BigDecimal testDistanceKm;

    @Column(name = "test_duration_minutes")
    private Integer testDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_result", length = 20)
    private TestResult testResult;

    @Column(name = "test_notes", length = 2000)
    private String testNotes;

    /**
     * Proxima intervencao recomendada.
     *
     * <p>O plano preventivo trata do ciclo; isto guarda o que a oficina
     * recomendou nesta intervencao concreta.
     */
    @Column(name = "next_service_meter", precision = 18, scale = 2)
    private BigDecimal nextServiceMeter;

    @Column(name = "next_service_hour_meter", precision = 18, scale = 2)
    private BigDecimal nextServiceHourMeter;

    @Column(name = "next_service_at")
    private Instant nextServiceAt;

    @Column(name = "next_service_note", length = 1000)
    private String nextServiceNote;

    /**
     * Codificacao da avaria (norma ISO 14224).
     *
     * <p>{@code systemCode} ja dizia onde. Isto diz o que falhou e porque, em
     * codigo, que e o que permite somar avarias iguais em toda a frota.
     */
    @Column(name = "component_code", length = 40)
    private String componentCode;

    @Column(name = "failure_mode", length = 40)
    private String failureMode;

    @Column(name = "failure_cause", length = 40)
    private String failureCause;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, recordedAt asc")
    private List<WorkOrderMeasurement> measurements = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<WorkOrderFluid> fluids = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<WorkOrderFaultCode> faultCodes = new ArrayList<>();

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("signedAt asc")
    private List<WorkOrderSignature> signatures = new ArrayList<>();
}
