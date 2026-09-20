package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Que viatura faz esta rota, com que motorista e em que dia.
 *
 * <p>A rota é o percurso e reutiliza-se — «Luanda → Lobito» é uma só, não uma
 * por camião. O que muda de viagem para viagem é quem a faz: é isso que esta
 * atribuição guarda. Ao criar uma rota exige-se logo a primeira, para não
 * ficarem percursos no sistema sem ninguém responsável por eles.
 */
@Entity
@Table(name = "route_assignments")
@Getter
@Setter
public class RouteAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** Motorista previsto; pode ficar por definir e preencher-se depois. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /** Dia previsto; vazio quando a viatura faz a rota de forma recorrente. */
    @Column(name = "planned_for")
    private LocalDate plannedFor;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant agora = Instant.now();
        if (createdAt == null) createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
