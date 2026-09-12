package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.TransportNoteStatus;
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

/**
 * Guia de transporte — o documento que acompanha a carga.
 *
 * <p>É o que a autoridade pede na estrada e o que o cliente assina na entrega.
 * Para a frota, é a peça que ligava uma viagem a uma carga, a um cliente e a um
 * motorista — e que não existia.
 *
 * <p>Os totais de peso, volume e volumes são <b>sempre</b> recalculados a
 * partir das linhas. Um total que não bate com o que vai na carga é uma
 * discussão à espera, na estrada ou na entrega.
 */
@Getter
@Setter
@Entity
@Table(name = "transport_notes")
public class TransportNote extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "note_year", nullable = false)
    private int noteYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportNoteStatus status = TransportNoteStatus.DRAFT;

    // ---- Quem transporta --------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "trailer_plate", length = 30)
    private String trailerPlate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "driver_label", length = 150)
    private String driverLabel;

    @Column(name = "driver_license", length = 60)
    private String driverLicense;

    // ---- De onde e para onde ---------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_branch_id")
    private Location originBranch;

    @Column(name = "origin_label", nullable = false, length = 300)
    private String originLabel;

    @Column(name = "origin_address", length = 400)
    private String originAddress;

    @Column(name = "destination_label", nullable = false, length = 300)
    private String destinationLabel;

    @Column(name = "destination_address", length = 400)
    private String destinationAddress;

    @Column(name = "route_id", length = 36)
    private String routeId;

    // ---- Cliente ----------------------------------------------------------

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_tax_id", length = 40)
    private String customerTaxId;

    @Column(name = "customer_contact", length = 120)
    private String customerContact;

    // ---- Tempos e medidores ----------------------------------------------

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "departure_meter", precision = 18, scale = 2)
    private BigDecimal departureMeter;

    @Column(name = "arrival_meter", precision = 18, scale = 2)
    private BigDecimal arrivalMeter;

    @Column(name = "distance_km", precision = 12, scale = 2)
    private BigDecimal distanceKm;

    // ---- Carga ------------------------------------------------------------

    @Column(name = "total_weight_kg", precision = 14, scale = 3)
    private BigDecimal totalWeightKg;

    @Column(name = "total_volume_m3", precision = 14, scale = 3)
    private BigDecimal totalVolumeM3;

    @Column(name = "total_packages")
    private Integer totalPackages;

    @Column(name = "cargo_description", length = 1000)
    private String cargoDescription;

    @Column(name = "hazard_class", length = 40)
    private String hazardClass;

    // ---- Entrega ----------------------------------------------------------

    @Column(name = "received_by_name", length = 150)
    private String receivedByName;

    @Column(name = "received_by_document", length = 60)
    private String receivedByDocument;

    @Column(name = "delivery_accepted")
    private Boolean deliveryAccepted;

    @Column(name = "delivery_notes", length = 1000)
    private String deliveryNotes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @OneToMany(mappedBy = "transportNote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<TransportNoteItem> items = new ArrayList<>();

    /**
     * Recalcula os totais a partir das linhas.
     *
     * <p>Chamado sempre que a carga muda. Aceitar totais do pedido deixaria a
     * guia dizer 12 toneladas com 8 na caixa — e é a guia que vai na estrada.
     */
    public void recalculate() {
        BigDecimal peso = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        int volumes = 0;
        for (TransportNoteItem i : items) {
            if (i.getWeightKg() != null) {
                peso = peso.add(i.getWeightKg());
            }
            if (i.getVolumeM3() != null) {
                volume = volume.add(i.getVolumeM3());
            }
            if (i.getPackages() != null) {
                volumes += i.getPackages();
            }
        }
        // Zero e diferente de "nao se pesou": sem linhas com peso, fica nulo.
        totalWeightKg = peso.signum() > 0 ? peso : null;
        totalVolumeM3 = volume.signum() > 0 ? volume : null;
        totalPackages = volumes > 0 ? volumes : null;
    }

    /** Ja nao se mexe: entregue ou anulada. */
    public boolean isTerminal() {
        return status == TransportNoteStatus.DELIVERED
                || status == TransportNoteStatus.CANCELLED;
    }
}
