package ao.autocare.modules.assettype.dto;

import ao.autocare.domain.AssetSystem;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.MeterKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AssetTypeDtos {

    private AssetTypeDtos() {}

    public record SystemInput(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 80) String name,
            Integer sortOrder) {}

    public record CreateAssetTypeRequest(
            @NotBlank(message = "Indique o nome do tipo de ativo.")
            @Size(max = 80) String name,
            AssetCategory category,
            MeterKind primaryMeter,
            MeterKind secondaryMeter,
            @Size(max = 40) String icon,
            /** Se verdadeiro (por omissão), cria os 8 sistemas padrão do documento de referência. */
            Boolean useStandardSystems,
            /** Sistemas explícitos — substituem os padrão quando fornecidos. */
            List<SystemInput> systems) {}

    public record UpdateAssetTypeRequest(
            @Size(max = 80) String name,
            AssetCategory category,
            MeterKind primaryMeter,
            MeterKind secondaryMeter,
            @Size(max = 40) String icon,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    public record SystemView(String id, String code, String name, int sortOrder) {
        public static SystemView of(AssetSystem s) {
            return new SystemView(s.getId(), s.getCode(), s.getName(), s.getSortOrder());
        }
    }

    public record AssetTypeView(
            String id,
            String name,
            String category,
            /** Rótulo da família, vindo do servidor: o ecrã não repete a lista. */
            String categoryLabel,
            String primaryMeter,
            String secondaryMeter,
            String icon,
            long assetCount,
            List<SystemView> systems,
            Long version) {

        public static AssetTypeView of(AssetType t, long assetCount) {
            return new AssetTypeView(
                    t.getId(), t.getName(),
                    t.getCategory().name(), t.getCategory().label(),
                    t.getPrimaryMeter().name(),
                    t.getSecondaryMeter() != null ? t.getSecondaryMeter().name() : null,
                    t.getIcon(), assetCount,
                    t.getSystems().stream().map(SystemView::of).toList(),
                    t.getVersion());
        }
    }
}
