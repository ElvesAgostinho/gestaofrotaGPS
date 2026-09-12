package ao.autocare.modules.location.dto;

import ao.autocare.domain.Location;
import ao.autocare.domain.enums.Enums.LocationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class LocationDtos {

    private LocationDtos() {}

    public record CreateLocationRequest(
            @NotBlank(message = "Indique o nome do local.")
            @Size(max = 160) String name,
            @Size(max = 40) String code,
            LocationKind kind,
            String parentId,
            @Size(max = 4000) String notes,
            BigDecimal latitude,
            BigDecimal longitude,
            // Filial (kind = BRANCH). Ignorados nos outros tipos de local.
            @Size(max = 40) String costCenter,
            String managerUserId,
            @Size(max = 300) String address,
            @Size(max = 120) String city,
            @Size(max = 120) String province,
            @Size(max = 40) String phone,
            Integer radiusMeters) {}

    public record UpdateLocationRequest(
            @Size(max = 160) String name,
            @Size(max = 40) String code,
            LocationKind kind,
            String parentId,
            Boolean active,
            @Size(max = 4000) String notes,
            BigDecimal latitude,
            BigDecimal longitude,
            // Filial (kind = BRANCH). Ignorados nos outros tipos de local.
            @Size(max = 40) String costCenter,
            String managerUserId,
            @Size(max = 300) String address,
            @Size(max = 120) String city,
            @Size(max = 120) String province,
            @Size(max = 40) String phone,
            Integer radiusMeters,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    public record LocationView(
            String id,
            String name,
            String code,
            String kind,
            String parentId,
            String parentName,
            boolean active,
            String notes,
            BigDecimal latitude,
            BigDecimal longitude,
            String costCenter,
            String managerUserId,
            String address,
            String city,
            String province,
            String phone,
            Integer radiusMeters,
            long assetCount,
            Long version) {

        public static LocationView of(Location l, long assetCount) {
            Location parent = l.getParent();
            return new LocationView(
                    l.getId(), l.getName(), l.getCode(), l.getKind().name(),
                    parent != null ? parent.getId() : null,
                    parent != null ? parent.getName() : null,
                    l.isActive(), l.getNotes(), l.getLatitude(), l.getLongitude(),
                    l.getCostCenter(), l.getManagerUserId(), l.getAddress(),
                    l.getCity(), l.getProvince(), l.getPhone(), l.getRadiusMeters(),
                    assetCount,
                    l.getVersion());
        }
    }

    public record LocationNode(
            String id,
            String name,
            String code,
            String kind,
            boolean active,
            BigDecimal latitude,
            BigDecimal longitude,
            List<LocationNode> children) {

        public static LocationNode of(Location l) {
            return new LocationNode(l.getId(), l.getName(), l.getCode(),
                    l.getKind().name(), l.isActive(), l.getLatitude(), l.getLongitude(),
                    new ArrayList<>());
        }
    }
}
