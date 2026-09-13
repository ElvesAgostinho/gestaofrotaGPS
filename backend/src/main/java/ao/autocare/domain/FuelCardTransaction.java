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

/** Uma linha do extrato do cartão de combustível: um abastecimento que foi mesmo pago. */
@Getter
@Setter
@Entity
@Table(name = "fuel_card_transactions")
public class FuelCardTransaction extends BaseEntity {

    public enum Status { MATCHED, UNMATCHED, IGNORED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "card_number", length = 40)
    private String cardNumber;

    @Column(name = "transacted_at", nullable = false)
    private Instant transactedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal liters;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Column(length = 160)
    private String station;

    @Column(length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.UNMATCHED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_record_id")
    private FuelRecord fuelRecord;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @Column(name = "import_batch", nullable = false, length = 36)
    private String importBatch;

    @PrePersist
    void onCreate() {
        if (importedAt == null) importedAt = Instant.now();
    }
}
