package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Percurso previsto entre dois pontos.
 *
 * <p>Serve para comparar o previsto com o realizado: quilometros a mais, tempo
 * a mais, combustivel a mais. Sem um previsto credivel, dizer que um consumo e
 * "alto" nao tem termo de comparacao nenhum -- e uma frota inteira pode andar
 * anos a gastar 30% a mais sem que isso apareca em lado nenhum.
 */
@Getter
@Setter
@Entity
@Table(name = "routes")
public class Route extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_location_id")
    private Location originLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    /** Nome livre, para origens e destinos que nao sao locais registados. */
    @Column(name = "origin_label", length = 200)
    private String originLabel;

    @Column(name = "destination_label", length = 200)
    private String destinationLabel;

    @Column(name = "expected_distance_km", precision = 10, scale = 2)
    private BigDecimal expectedDistanceKm;

    @Column(name = "expected_duration_minutes")
    private Integer expectedDurationMinutes;

    /**
     * Litros previstos. Fica nulo enquanto nao houver base de consumo do ativo
     * -- preencher com um palpite daria ar de rigor a um numero inventado.
     */
    @Column(name = "expected_fuel_liters", precision = 10, scale = 2)
    private BigDecimal expectedFuelLiters;

    /** Desvio aceite antes de a viagem ser assinalada, em percentagem. */
    @Column(name = "tolerance_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal tolerancePercent = new BigDecimal("15");

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(length = 2000)
    private String notes;

    /**
     * De onde veio a distância que aqui está.
     *
     * <p>Sem isto, um número calculado pelo motor e um número que alguém
     * escreveu ficam iguais na tabela, e quem lê o relatório não sabe em qual
     * pode confiar. MANUAL, ENGINE ou STRAIGHT.
     */
    @Column(name = "distance_source", length = 20)
    private String distanceSource = "MANUAL";

    /** O traçado devolvido pelo motor, em GeoJSON, para o mapa desenhar. */
    @Column(name = "path_geojson", columnDefinition = "TEXT")
    private String pathGeojson;

    @Column(name = "computed_at")
    private java.time.Instant computedAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<RouteWaypoint> waypoints = new ArrayList<>();

    public void addWaypoint(RouteWaypoint w) {
        w.setRoute(this);
        waypoints.add(w);
    }

    /** Como se escreve a origem, venha ela de um local registado ou de texto. */
    public String originName() {
        if (originLocation != null) {
            return originLocation.getName();
        }
        return originLabel;
    }

    public String destinationName() {
        if (destinationLocation != null) {
            return destinationLocation.getName();
        }
        return destinationLabel;
    }
}
