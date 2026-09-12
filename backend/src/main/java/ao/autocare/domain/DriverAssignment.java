package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Quem conduziu que ativo, e entre que datas.
 *
 * <p>Existe como tabela, e não como um campo "motorista" no ativo, porque um
 * campo responderia apenas "quem conduz hoje" — e passaria a mentir sobre o
 * passado assim que houvesse uma troca. Sem histórico não há forma honesta de
 * imputar uma viagem, uma infração ou um abastecimento a alguém.
 */
@Getter
@Setter
@Entity
@Table(name = "driver_assignments")
public class DriverAssignment extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Nulo enquanto a atribuição estiver em vigor. */
    @Column(name = "ended_at")
    private Instant endedAt;

    /**
     * Condutor principal do ativo neste período.
     *
     * <p>Um camião pode ter um motorista titular e ajudantes que o conduzem
     * pontualmente. Quando é preciso escolher um responsável, é o titular.
     */
    @Column(name = "is_primary", nullable = false)
    private boolean primaryDriver = true;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    public boolean isOpen() {
        return endedAt == null;
    }

    /** Cobre este instante? Aberta cobre tudo o que vem depois do início. */
    public boolean covers(Instant moment) {
        if (moment == null || startedAt == null || moment.isBefore(startedAt)) {
            return false;
        }
        return endedAt == null || moment.isBefore(endedAt);
    }
}
