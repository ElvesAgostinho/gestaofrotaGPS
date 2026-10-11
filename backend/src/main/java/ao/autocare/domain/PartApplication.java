package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Onde é que esta peça se monta.
 *
 * <p>Cada campo vazio quer dizer «qualquer». Uma linha só com a família serve
 * toda a família: um filtro de ar marcado como {@code TRUCK_HEAVY} serve
 * qualquer camião da frota. Uma linha com marca, modelo, motor e intervalo de
 * anos serve exactamente aquela viatura e mais nenhuma. Uma peça pode ter
 * várias aplicações, e serve o equipamento se <b>qualquer</b> uma delas
 * encaixar — é assim que funciona um catálogo de peças a sério, e é a única
 * forma de o sistema poder avisar «este filtro não é para este camião».
 *
 * <p>É de propósito que isto não é uma lista fechada de viaturas. Em Angola
 * compra-se o que aparece: entra um camião de uma marca que ninguém tinha, e o
 * armazém tem de o acomodar no mesmo dia, sem esperar por uma base de dados de
 * fabricante que não existe para metade da frota.
 */
@Getter
@Setter
@Entity
@Table(name = "part_applications")
public class PartApplication extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    /** Família do catálogo: TRUCK_HEAVY, PICKUP, GENERATOR… Vazio = qualquer. */
    @Column(length = 30)
    private String family;

    @Column(length = 80)
    private String make;

    @Column(length = 120)
    private String model;

    /** O motor é o que decide de verdade num filtro, numa correia ou numa vela. */
    @Column(length = 120)
    private String engine;

    @Column(name = "year_from")
    private Integer yearFrom;

    @Column(name = "year_to")
    private Integer yearTo;

    @Column(length = 300)
    private String note;

    /** Uma linha sem nenhum critério serve tudo — e é bom que se veja. */
    public boolean universal() {
        return isBlank(family) && isBlank(make) && isBlank(model) && isBlank(engine)
                && yearFrom == null && yearTo == null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
