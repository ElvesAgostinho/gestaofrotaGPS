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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Um atesto de fluido: água, óleo, hidráulico ou líquido de travões.
 *
 * <p>Parece o registo mais banal do sistema e é um dos mais reveladores. Numa
 * frota, atestar água é um gesto tão comum que ninguém o conta — e é por
 * ninguém o contar que uma fuga pequena vive meses até acabar em motor gripado
 * ou em incêndio. Contado, passa a ter uma tendência: «esta viatura levou nove
 * litros em trinta dias» é uma frase que obriga alguém a ir ver.
 */
@Entity
@Table(name = "fluid_topups")
@Getter
@Setter
public class FluidTopUp extends BaseEntity {

    /** O que se atestou. */
    public enum Kind {
        /** Água ou líquido de arrefecimento. */
        COOLANT,
        ENGINE_OIL,
        HYDRAULIC,
        /** Num circuito fechado não desaparece: se houve atesto, há fuga. */
        BRAKE,
        TRANSMISSION,
        OTHER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.COOLANT;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal liters;

    @Column(name = "meter_value", precision = 12, scale = 2)
    private BigDecimal meterValue;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
