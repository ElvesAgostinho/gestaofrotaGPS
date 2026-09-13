package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma infração de um motorista: multa, acidente, excesso de velocidade
 * confirmado, uso indevido. Tem pontos (a política da empresa) e, se houver,
 * o valor da multa e se já foi paga.
 */
@Getter
@Setter
@Entity
@Table(name = "driver_infractions")
public class DriverInfraction extends BaseEntity {

    public enum Kind {
        SPEEDING, ACCIDENT, FINE, MISUSE, DOCUMENT, OTHER;

        public String label() {
            return switch (this) {
                case SPEEDING -> "Excesso de velocidade";
                case ACCIDENT -> "Acidente";
                case FINE -> "Multa";
                case MISUSE -> "Uso indevido da viatura";
                case DOCUMENT -> "Documentação em falta";
                case OTHER -> "Outra";
            };
        }
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.OTHER;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int points;

    @Column(name = "fine_amount", precision = 14, scale = 2)
    private BigDecimal fineAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(nullable = false)
    private boolean paid;

    /** Número do auto, do processo, da apólice. */
    @Column(length = 80)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (occurredAt == null) occurredAt = createdAt;
    }
}
