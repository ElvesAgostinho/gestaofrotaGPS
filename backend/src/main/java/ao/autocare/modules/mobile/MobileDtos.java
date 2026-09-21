package ao.autocare.modules.mobile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MobileDtos {

    private MobileDtos() {}

    /** Uma viatura para escolher no telemovel; «mine» = atribuida a quem esta a ver. */
    public record AssetPick(String id, String tag, String name, String plate, String status, boolean mine) {}

    /**
      * A viatura como o motorista precisa de a ver: o contador, o que falta
      * para a próxima manutenção e se a inspeção de hoje já foi feita.
      */
    public record MinhaViatura(
            String id,
            String tag,
            String name,
            String plate,
            String status,
            /** «128 400 km» ou «1 240 h», já escrito. */
            String meterLabel,
            String meterUnit,
            BigDecimal meterValue,
            /** O título da manutenção que vem a seguir, ou nulo se não houver plano. */
            String nextMaintenance,
            /** «em 320 km», «em 6 h», «em 4 dias» — a margem que resta. */
            String nextMaintenanceIn,
            /** OVERDUE, DUE_SOON, OK. */
            String nextMaintenanceStatus,
            boolean inspectionDoneToday,
            Instant lastInspectionAt,
            BigDecimal fuelLevelLiters) {}

    /** A rota que o gestor marcou para hoje. */
    public record RotaHoje(
            String assignmentId,
            String routeId,
            String name,
            String code,
            String assetId,
            String assetTag,
            BigDecimal distanceKm,
            Integer expectedMinutes,
            /** Verdadeiro quando o gestor desenhou o percurso: dá para seguir no mapa. */
            boolean hasPath) {}

    /** Um ponto do percurso, para desenhar no mapa do telemóvel. */
    public record PontoRota(String label, BigDecimal latitude, BigDecimal longitude, String tipo) {}

    /**
     * A rota com tudo o que o telemóvel precisa para a desenhar.
     *
     * <p>Leva o traçado que o gestor definiu ({@code pathGeojson}) e, à parte,
     * a origem e o destino — para a aplicação poder oferecer as duas vistas: o
     * percurso do gestor, ou o caminho calculado de A até B.
     */
    public record RotaDetalhe(
            String routeId,
            String name,
            String code,
            String originLabel,
            String destinationLabel,
            BigDecimal distanceKm,
            Integer expectedMinutes,
            Integer corridorMeters,
            String pathGeojson,
            List<PontoRota> points,
            String assetId,
            String assetTag,
            String notes) {}

    public record HomeView(
            String userName,
            List<AssetPick> assets,
            long myOpenOrders,
            boolean hasAssignedAssets,
            /** As viaturas que são mesmo dele, com o detalhe que interessa hoje. */
            List<MinhaViatura> myAssets,
            /** A rota de hoje, se houver. */
            RotaHoje route,
            /** O que está mal ou a vencer nas viaturas dele, em linguagem simples. */
            List<String> warnings) {}
}
