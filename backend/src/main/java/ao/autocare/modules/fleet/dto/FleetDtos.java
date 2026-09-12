package ao.autocare.modules.fleet.dto;

import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.Location;
import ao.autocare.domain.Route;
import ao.autocare.domain.RouteWaypoint;
import ao.autocare.domain.enums.Enums.DriverStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FleetDtos {

    private FleetDtos() {}

    // ===== Motoristas ======================================================
    public record SaveDriverRequest(
            @NotBlank(message = "Indique o nome do motorista.")
            @Size(max = 160) String name,
            @Size(max = 40) String employeeNumber,
            @Size(max = 40) String phone,
            @Email(message = "O email indicado não é válido.")
            @Size(max = 190) String email,
            @Size(max = 60) String nationalId,
            LocalDate birthDate,
            LocalDate hiredAt,
            String userId,
            String branchId,
            @Size(max = 60) String licenseNumber,
            @Size(max = 60) String licenseCategories,
            LocalDate licenseIssuedAt,
            LocalDate licenseExpiresAt,
            @Size(max = 60) String licenseCountry,
            DriverStatus status,
            @Size(max = 2000) String notes,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    /**
     * Um motorista, como aparece numa lista.
     *
     * <p>{@code licenseExpiresInDays} vem calculado do servidor de propósito: se
     * cada ecrã fizesse a sua conta a partir da data, bastava um fuso horário
     * diferente para dois ecrãs discordarem sobre se uma carta está válida.
     */
    public record DriverView(
            String id,
            String name,
            String employeeNumber,
            String phone,
            String email,
            String nationalId,
            LocalDate birthDate,
            LocalDate hiredAt,
            String userId,
            String branchId,
            String branchName,
            String licenseNumber,
            String licenseCategories,
            LocalDate licenseIssuedAt,
            LocalDate licenseExpiresAt,
            String licenseCountry,
            Long licenseExpiresInDays,
            boolean licenseExpired,
            boolean canDrive,
            DriverStatus status,
            String statusLabel,
            String notes,
            List<AssignmentView> currentAssets,
            Long version) {

        public static DriverView of(Driver d, List<AssignmentView> assets) {
            Location branch = d.getBranch();
            return new DriverView(
                    d.getId(), d.getName(), d.getEmployeeNumber(), d.getPhone(), d.getEmail(),
                    d.getNationalId(), d.getBirthDate(), d.getHiredAt(),
                    d.getUser() != null ? d.getUser().getId() : null,
                    branch != null ? branch.getId() : null,
                    branch != null ? branch.getName() : null,
                    d.getLicenseNumber(), d.getLicenseCategories(),
                    d.getLicenseIssuedAt(), d.getLicenseExpiresAt(), d.getLicenseCountry(),
                    d.daysUntilLicenseExpiry(), d.isLicenseExpired(), d.canDrive(),
                    d.getStatus(), statusLabel(d.getStatus()), d.getNotes(), assets,
                    d.getVersion());
        }

        public static String statusLabel(DriverStatus status) {
            return switch (status) {
                case ACTIVE -> "Ativo";
                case SUSPENDED -> "Suspenso";
                case INACTIVE -> "Inativo";
            };
        }
    }

    // ===== Atribuições =====================================================
    public record AssignDriverRequest(
            @NotBlank(message = "Indique o motorista.") String driverId,
            @NotBlank(message = "Indique o ativo.") String assetId,
            Instant startedAt,
            Boolean primaryDriver,
            @Size(max = 500) String notes) {}

    public record AssignmentView(
            String id,
            String driverId,
            String driverName,
            String assetId,
            String assetTag,
            String assetName,
            Instant startedAt,
            Instant endedAt,
            boolean primaryDriver,
            boolean open,
            String notes) {

        public static AssignmentView of(DriverAssignment a) {
            return new AssignmentView(
                    a.getId(),
                    a.getDriver().getId(), a.getDriver().getName(),
                    a.getAsset().getId(), a.getAsset().getTag(), a.getAsset().getName(),
                    a.getStartedAt(), a.getEndedAt(), a.isPrimaryDriver(), a.isOpen(),
                    a.getNotes());
        }
    }

    // ===== Rotas ===========================================================
    public record SaveRouteRequest(
            @NotBlank(message = "Indique o nome da rota.")
            @Size(max = 200) String name,
            @Size(max = 40) String code,
            String originLocationId,
            String destinationLocationId,
            @Size(max = 200) String originLabel,
            @Size(max = 200) String destinationLabel,
            BigDecimal expectedDistanceKm,
            Integer expectedDurationMinutes,
            BigDecimal expectedFuelLiters,
            BigDecimal tolerancePercent,
            Boolean active,
            @Size(max = 2000) String notes,
            /** MANUAL, ENGINE ou STRAIGHT. Ausente mantém o que lá estava. */
            @Size(max = 20) String distanceSource,
            String pathGeojson,
            List<WaypointRequest> waypoints,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    /**
     * Pedido de cálculo de percurso.
     *
     * <p>Cada ponto vem como um local registado <b>ou</b> como coordenadas
     * marcadas no mapa. Nunca escritas à mão — é precisamente esse o erro que
     * este ecrã existe para apagar.
     */
    public record CalculateRouteRequest(
            @NotNull(message = "Indique os pontos do percurso.")
            @Size(min = 2, message = "São precisos pelo menos dois pontos.")
            List<RoutePointRequest> points,
            /** Consumo médio da viatura, L/100 km, para prever o combustível. */
            BigDecimal litersPer100Km) {}

    public record RoutePointRequest(
            String locationId, BigDecimal latitude, BigDecimal longitude) {}

    /**
     * O que o motor devolveu.
     *
     * @param source ENGINE quando veio das estradas reais, STRAIGHT quando é
     *     uma estimativa em linha reta — e nesse caso {@code warning} diz porquê
     */
    public record RouteCalculationView(
            BigDecimal distanceKm,
            Integer durationMinutes,
            BigDecimal fuelLiters,
            String geojson,
            String source,
            String warning) {}

    public record WaypointRequest(
            @NotBlank(message = "Cada ponto de passagem precisa de nome.")
            @Size(max = 200) String label,
            String locationId,
            BigDecimal latitude,
            BigDecimal longitude) {}

    public record RouteView(
            String id,
            String code,
            String name,
            String originLocationId,
            String destinationLocationId,
            String originName,
            String destinationName,
            BigDecimal expectedDistanceKm,
            Integer expectedDurationMinutes,
            BigDecimal expectedFuelLiters,
            BigDecimal tolerancePercent,
            boolean active,
            String notes,
            String distanceSource,
            String pathGeojson,
            List<WaypointView> waypoints,
            Long version) {

        public static RouteView of(Route r, List<WaypointView> waypoints) {
            return new RouteView(
                    r.getId(), r.getCode(), r.getName(),
                    r.getOriginLocation() != null ? r.getOriginLocation().getId() : null,
                    r.getDestinationLocation() != null
                            ? r.getDestinationLocation().getId() : null,
                    r.originName(), r.destinationName(),
                    r.getExpectedDistanceKm(), r.getExpectedDurationMinutes(),
                    r.getExpectedFuelLiters(), r.getTolerancePercent(),
                    r.isActive(), r.getNotes(),
                    r.getDistanceSource(), r.getPathGeojson(), waypoints,
                    r.getVersion());
        }
    }

    public record WaypointView(
            String id, String label, String locationId,
            BigDecimal latitude, BigDecimal longitude, int sortOrder) {

        public static WaypointView of(RouteWaypoint w) {
            return new WaypointView(w.getId(), w.getLabel(),
                    w.getLocation() != null ? w.getLocation().getId() : null,
                    w.getLatitude(), w.getLongitude(), w.getSortOrder());
        }
    }

    /** Resumo para o painel de frota. */
    public record DriverSummary(
            long active,
            long suspended,
            long inactive,
            long licensesExpired,
            long licensesExpiringSoon,
            long withoutAsset) {}

    @NotNull
    public record EndAssignmentRequest(Instant endedAt, @Size(max = 500) String notes) {}
}
