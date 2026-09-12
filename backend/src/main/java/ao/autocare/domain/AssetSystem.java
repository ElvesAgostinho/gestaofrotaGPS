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
 * Sistema de um tipo de ativo (motor, hidráulico, transmissão, ...).
 * As tarefas do plano de manutenção são agrupadas por sistema (Fase 3).
 */
@Getter
@Setter
@Entity
@Table(name = "asset_systems")
public class AssetSystem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    /** Código estável do sistema (ex.: ENGINE, HYDRAULIC). Ver {@code Enums.AssetSystemCode}. */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
