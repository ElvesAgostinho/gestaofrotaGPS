package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma linha da carga de uma guia de transporte.
 *
 * <p>Uma guia com um só campo de texto para a mercadoria não serve para
 * conferir nada na entrega: é preciso saber o que são vinte volumes e quanto
 * pesa cada lote.
 */
@Getter
@Setter
@Entity
@Table(name = "transport_note_items")
public class TransportNoteItem extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_note_id", nullable = false)
    private TransportNote transportNote;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(length = 60)
    private String reference;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit = "un";

    @Column(name = "weight_kg", precision = 14, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "volume_m3", precision = 14, scale = 3)
    private BigDecimal volumeM3;

    @Column
    private Integer packages;

    @Column(length = 500)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
