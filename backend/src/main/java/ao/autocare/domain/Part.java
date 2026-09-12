package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.PartCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Peça no catálogo da organização. */
@Getter
@Setter
@Entity
@Table(name = "parts")
public class Part extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "part_number", length = 80)
    private String partNumber;

    /** Sistema a que pertence (ENGINE, HYDRAULIC…). Livre. */
    @Column(name = "system_code", length = 30)
    private String systemCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PartCategory category = PartCategory.GENERAL;

    @Column(nullable = false, length = 20)
    private String unit = "un";

    @Column(name = "min_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal minQuantity = BigDecimal.ZERO;

    @Column(name = "average_cost", precision = 14, scale = 2)
    private BigDecimal averageCost;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
