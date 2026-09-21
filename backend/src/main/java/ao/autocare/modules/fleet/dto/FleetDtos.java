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
            Long version,
            @Size(max = 60) String cardNumber,
            LocalDate cardExpiresAt,
            LocalDate medicalExpiresAt) {}

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
            Long version,
            String cardNumber,
            LocalDate cardExpiresAt,
            LocalDate medicalExpiresAt,
            /** «Carta caduca em 12 dias», «Exame médico caducado há 3 dias»… */
            List<String> warnings) {

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
                    d.getVersion(),
                    d.getCardNumber(), d.getCardExpiresAt(), d.getMedicalExpiresAt(), d.avisos());
        }

        public static String statusLabel(DriverStatus status) {
            return switch (status) {
                case ACTIVE -> "Ativo";
                case SUSPENDED -> "Suspenso";
                case INACTIVE -> "Inativo";
            };
        }
    }

    // ===== Infrações e escala ===============================================
    public record SaveInfractionRequest(
            @NotNull ao.autocare.domain.DriverInfraction.Kind kind,
            Instant occurredAt,
            String assetId,
            @Size(max = 1000) String description,
            Integer points,
            BigDecimal fineAmount,
            Boolean paid,
            @Size(max = 80) String reference) {}

    public record InfractionView(String id, String driverId, String driverName, String assetId, String assetTag,
                                 Instant occurredAt, String kind, String kindLabel, String description, int points,
                                 BigDecimal fineAmount, String currency, boolean paid, String reference) {

        public static InfractionView of(ao.autocare.domain.DriverInfraction i, boolean showMoney) {
            return new InfractionView(i.getId(), i.getDriver().getId(), i.getDriver().getName(),
                    i.getAsset() != null ? i.getAsset().getId() : null,
                    i.getAsset() != null ? i.getAsset().getTag() : null,
                    i.getOccurredAt(), i.getKind().name(), i.getKind().label(), i.getDescription(),
                    i.getPoints(), showMoney ? i.getFineAmount() : null, i.getCurrency(), i.isPaid(),
                    i.getReference());
        }
    }

    public record SaveShiftRequest(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            String assetId,
            ao.autocare.domain.DriverShift.Kind kind,
            @Size(max = 500) String notes) {}

    public record ShiftView(String id, String driverId, String driverName, String assetId, String assetTag,
                            Instant startsAt, Instant endsAt, String kind, String kindLabel, String notes) {

        public static ShiftView of(ao.autocare.domain.DriverShift s) {
            return new ShiftView(s.getId(), s.getDriver().getId(), s.getDriver().getName(),
                    s.getAsset() != null ? s.getAsset().getId() : null,
                    s.getAsset() != null ? s.getAsset().getTag() : null,
                    s.getStartsAt(), s.getEndsAt(), s.getKind().name(), s.getKind().label(), s.getNotes());
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
            /** Largura do corredor, em metros; fora dele avisa-se quem gere. */
            Integer corridorMeters,
            Boolean active,
            @Size(max = 2000) String notes,
            /** MANUAL, ENGINE ou STRAIGHT. Ausente mantém o que lá estava. */
            @Size(max = 20) String distanceSource,
            String pathGeojson,
            List<WaypointRequest> waypoints,
            /**
             * Viatura que vai fazer a rota. <b>Obrigatória ao criar</b>: uma rota
             * sem viatura é um percurso de que ninguém é responsável. Ao alterar,
             * ausente mantém as atribuições que já existem.
             */
            String assetId,
            /** Motorista previsto (opcional). */
            String driverId,
            /** Dia previsto (opcional); vazio = a viatura faz a rota de forma recorrente. */
            java.time.LocalDate plannedFor,
            /** A versão que o ecrã leu. Ausente: não se verifica. */
            Long version) {}

    /** Atribuir (mais) uma viatura a uma rota já criada. */
    public record AssignRouteRequest(
            @NotBlank(message = "Indique a viatura.") String assetId,
            String driverId,
            java.time.LocalDate plannedFor,
            @Size(max = 500) String notes) {}

    public record RouteAssignmentView(
            String id, String routeId, String routeName,
            String assetId, String assetTag, String assetName,
            String driverId, String driverName,
            java.time.LocalDate plannedFor, String notes, java.time.Instant createdAt) {

        public static RouteAssignmentView of(ao.autocare.domain.RouteAssignment a) {
            return new RouteAssignmentView(a.getId(), a.getRoute().getId(), a.getRoute().getName(),
                    a.getAsset().getId(), a.getAsset().getTag(), a.getAsset().getName(),
                    a.getDriver() != null ? a.getDriver().getId() : null,
                    a.getDriver() != null ? a.getDriver().getName() : null,
                    a.getPlannedFor(), a.getNotes(), a.getCreatedAt());
        }
    }

    /**
     * Uma viatura a fazer uma rota agora: onde vai, quanto falta, a que horas
     * chega e se está fora do corredor.
     */
    public record RouteLiveView(
            String routeId, String routeName, String assetId, String assetTag, String assetName,
            /** A família do catálogo: o mapa desenha o veículo certo. */
            String assetFamily,
            String driverName,
            java.time.Instant positionAt, BigDecimal latitude, BigDecimal longitude,
            BigDecimal speedKph,
            /** 0..1 do percurso já feito. */
            BigDecimal progress,
            BigDecimal doneKm, BigDecimal remainingKm,
            /** Metros a que está da estrada prevista. */
            Integer offRouteMeters,
            boolean offRoute,
            Integer corridorMeters,
            /** Hora prevista de chegada; nula quando não há velocidade nem duração prevista. */
            java.time.Instant eta,
            /** Minutos de atraso sobre o previsto; negativo = adiantado. */
            Integer delayMinutes,
            /** Como se calculou o ETA: GPS (velocidade atual), PLANO (duração prevista) ou nulo. */
            String etaSource) {}

    /**
     * O previsto contra o andado: o traçado da rota, o percurso real de uma
     * viagem que a fez, e a diferença em km e minutos.
     */
    public record RouteVsRealView(
            String routeId, String routeName, String pathGeojson,
            BigDecimal expectedDistanceKm, Integer expectedDurationMinutes,
            String tripId, String assetId, String assetTag,
            java.time.Instant startedAt, java.time.Instant endedAt,
            BigDecimal actualDistanceKm, Integer actualDurationMinutes,
            /** Km a mais (positivo) ou a menos do que o previsto; nulo sem previsto. */
            BigDecimal distanceDeltaKm,
            Integer durationDeltaMinutes,
            /** Verdadeiro quando a diferença passa a tolerância da rota. */
            Boolean outOfTolerance,
            List<double[]> track) {}

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
            Integer corridorMeters,
            boolean active,
            String notes,
            String distanceSource,
            String pathGeojson,
            List<WaypointView> waypoints,
            /** Viaturas atribuídas a esta rota; nunca vazia numa rota criada pelo ecrã. */
            List<RouteAssignmentView> assignments,
            Long version) {

        public static RouteView of(Route r, List<WaypointView> waypoints) {
            return of(r, waypoints, List.of());
        }

        public static RouteView of(Route r, List<WaypointView> waypoints,
                List<RouteAssignmentView> assignments) {
            return new RouteView(
                    r.getId(), r.getCode(), r.getName(),
                    r.getOriginLocation() != null ? r.getOriginLocation().getId() : null,
                    r.getDestinationLocation() != null
                            ? r.getDestinationLocation().getId() : null,
                    r.originName(), r.destinationName(),
                    r.getExpectedDistanceKm(), r.getExpectedDurationMinutes(),
                    r.getExpectedFuelLiters(), r.getTolerancePercent(), r.getCorridorMeters(),
                    r.isActive(), r.getNotes(),
                    r.getDistanceSource(), r.getPathGeojson(), waypoints, assignments,
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
