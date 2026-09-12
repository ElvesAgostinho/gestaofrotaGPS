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
 * Fluido ou lubrificante aplicado numa intervenção.
 *
 * <p>Uma peça diz «filtro de óleo»; não diz que se meteram 38 litros de
 * 15W-40 CH-4. Numa frota pesada o lubrificante é uma rubrica de custo por si
 * só, e a especificação errada estraga o motor que se queria proteger.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_fluids")
public class WorkOrderFluid extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Enums.FluidKind kind;

    /** Especificação técnica: «15W-40 CH-4», «SAE 80W-90 GL-5», «ELC». */
    @Column(length = 80)
    private String spec;

    @Column(length = 80)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.FluidAction action = Enums.FluidAction.REPLACED;

    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, length = 12)
    private String unit = "L";

    @Column(name = "filter_changed", nullable = false)
    private boolean filterChanged = false;

    /** O número de peça é o que permite repetir a compra sem enganos. */
    @Column(name = "filter_part_number", length = 60)
    private String filterPartNumber;

    @Column(length = 60)
    private String batch;

    @Column(name = "unit_cost", precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 18, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 500)
    private String note;
}
