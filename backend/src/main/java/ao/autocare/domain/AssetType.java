package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.MeterKind;
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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Tipo de ativo (ex.: "Retroescavadora", "Gerador diesel", "Camião"). */
@Getter
@Setter
@Entity
@Table(name = "asset_types")
public class AssetType extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetCategory category = AssetCategory.MACHINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_meter", nullable = false, length = 20)
    private MeterKind primaryMeter = MeterKind.HOURMETER;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_meter", length = 20)
    private MeterKind secondaryMeter;

    @Column(length = 40)
    private String icon;

    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AssetSystem> systems = new ArrayList<>();

    public void addSystem(AssetSystem system) {
        system.setAssetType(this);
        systems.add(system);
    }
}
