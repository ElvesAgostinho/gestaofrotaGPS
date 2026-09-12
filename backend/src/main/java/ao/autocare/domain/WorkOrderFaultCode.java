package ao.autocare.domain;

import ao.autocare.domain.enums.Enums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Código de avaria lido no equipamento.
 *
 * <p>Um camião moderno e um gerador dizem o que têm: J1939 (SPN/FMI) num
 * pesado, OBD-II num ligeiro, o painel nos restantes. Guardar o código é o que
 * permite, um ano depois, ver que o mesmo SPN voltou três vezes — e que o que
 * se andou a fazer foi apagar o sintoma.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_fault_codes")
public class WorkOrderFaultCode extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.FaultCodeSource source = Enums.FaultCodeSource.J1939;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 300)
    private String description;

    @Column
    private Integer occurrences;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.FaultCodeStatus status = Enums.FaultCodeStatus.ACTIVE;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(length = 500)
    private String note;
}
