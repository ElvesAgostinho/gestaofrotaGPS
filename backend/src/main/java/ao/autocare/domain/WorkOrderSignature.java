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
 * Assinatura de quem executou, supervisionou ou recebeu a viatura.
 *
 * <p>O PDF já tinha linhas para assinar em papel. Isto guarda quem assinou de
 * facto: sem a aceitação do operador, «a viatura já vinha assim» e
 * «estragaram-na na oficina» continuam duas afirmações igualmente
 * indemonstráveis.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_signatures")
public class WorkOrderSignature extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.SignatureRole role;

    @Column(name = "person_name", nullable = false, length = 150)
    private String personName;

    @Column(name = "person_document", length = 60)
    private String personDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Falso quando quem recebe assina com reservas. */
    @Column(nullable = false)
    private boolean accepted = true;

    @Column(length = 500)
    private String note;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;
}
