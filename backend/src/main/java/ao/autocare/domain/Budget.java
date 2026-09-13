package ao.autocare.domain;

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

/** Um valor anual previsto para um âmbito (frota, filial, viatura) e uma categoria de custo. */
@Getter
@Setter
@Entity
@Table(name = "budgets")
public class Budget extends TimestampedEntity {

    public enum Scope { ORG, LOCATION, ASSET }

    public enum Category {
        MAINTENANCE, FUEL, TOTAL;

        public String label() {
            return switch (this) {
                case MAINTENANCE -> "Manutenção";
                case FUEL -> "Combustível";
                case TOTAL -> "Manutenção + combustível";
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "fiscal_year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Scope scope = Scope.ORG;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category = Category.TOTAL;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(length = 500)
    private String notes;

    @Column(name = "warned_at_pct")
    private Integer warnedAtPct;
}
