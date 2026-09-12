package ao.autocare.modules.telemetry;

import ao.autocare.domain.Asset;
import ao.autocare.domain.Geofence;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.GpsPosition;
import ao.autocare.domain.TelemetryAlert;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.TelemetryAlertRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Vigia a velocidade e abre um alerta quando o limite aplicável é excedido.
 *
 * <p>O limite não é um valor global: resolve-se pela ordem
 * <b>zona &gt; ativo &gt; empresa</b>. Uma obra pode impor 20 km/h a tudo o que
 * lá entra, independentemente do que o camião faz na estrada; um ativo pode ter
 * limite próprio; e a empresa define um valor de recurso. Se nenhum dos três
 * estiver definido, <b>não há vigilância</b> — não se inventa um limite que a
 * empresa nunca escolheu.
 *
 * <p>O alerta é um episódio: enquanto o excesso dura, o mesmo registo é
 * atualizado com o pico atingido. Fecha quando a velocidade volta ao limite.
 */
@Component
public class SpeedWatch {

    /**
     * Margem antes de alertar. Absorve o erro do GPS na medição de velocidade e
     * a diferença entre o velocímetro e o aparelho — sem isto, um veículo a
     * andar exatamente no limite geraria alertas intermitentes.
     */
    private static final BigDecimal TOLERANCE_KPH = new BigDecimal("5");

    private final TelemetryAlertRepository alerts;
    private final NotificationService notifications;

    public SpeedWatch(TelemetryAlertRepository alerts, NotificationService notifications) {
        this.alerts = alerts;
        this.notifications = notifications;
    }

    /**
     * Avalia a velocidade desta posição.
     *
     * @param inside áreas que contêm o ponto, já calculadas pela avaliação de
     *               geocercas — evita repetir a geometria
     * @return o alerta aberto ou atualizado, ou {@code null} se não há excesso
     */
    public TelemetryAlert check(
            Asset asset, GpsDevice device, GpsPosition p, List<Geofence> inside) {

        if (asset == null || p.getSpeedKph() == null) {
            return null;
        }
        Geofence zone = strictestZone(inside);
        BigDecimal limit = resolveLimit(asset, zone);
        if (limit == null || limit.signum() <= 0) {
            return null;
        }

        TelemetryAlert open = alerts
                .findFirstByAssetIdAndKindAndEndedAtIsNull(asset.getId(), TelemetryAlertKind.SPEEDING)
                .orElse(null);
        boolean over = p.getSpeedKph().compareTo(limit.add(TOLERANCE_KPH)) > 0;

        if (!over) {
            if (open != null) {
                open.setEndedAt(p.getRecordedAt());
            }
            return null;
        }
        if (open != null) {
            if (open.getPeakValue() == null
                    || p.getSpeedKph().compareTo(open.getPeakValue()) > 0) {
                open.setPeakValue(p.getSpeedKph());
                open.setLatitude(p.getLatitude());
                open.setLongitude(p.getLongitude());
            }
            return open;
        }
        TelemetryAlert alert = alerts.save(newAlert(asset, device, p, limit, zone));
        // Um aviso por episódio: quem gere a frota é avisado uma vez, não a
        // cada posição recebida enquanto o excesso dura.
        notifications.notifyManagers(NotificationService.Draft.of(
                        asset.getOrganization().getId(),
                        ao.autocare.domain.enums.Enums.AlertCategory.GPS,
                        ao.autocare.domain.enums.Enums.AlertSeverity.WARNING,
                        "Excesso de velocidade — " + asset.getTag(),
                        alert.getMessage(),
                        "telemetry_alert", alert.getId(),
                        "/frota/alertas/" + alert.getId())
                .forAsset(asset));
        return alert;
    }

    /** Fecha o episódio de excesso quando o ativo deixa de comunicar. */
    public void closeOpenEpisode(String assetId, java.time.Instant at) {
        alerts.findFirstByAssetIdAndKindAndEndedAtIsNull(assetId, TelemetryAlertKind.SPEEDING)
                .ifPresent(alert -> alert.setEndedAt(at));
    }

    /** Entre zonas sobrepostas manda a mais restritiva. */
    private Geofence strictestZone(List<Geofence> inside) {
        Geofence strictest = null;
        for (Geofence g : inside) {
            if (g.getSpeedLimitKph() == null || g.getSpeedLimitKph().signum() <= 0) {
                continue;
            }
            if (strictest == null
                    || g.getSpeedLimitKph().compareTo(strictest.getSpeedLimitKph()) < 0) {
                strictest = g;
            }
        }
        return strictest;
    }

    private BigDecimal resolveLimit(Asset asset, Geofence zone) {
        if (zone != null) {
            return zone.getSpeedLimitKph();
        }
        if (asset.getSpeedLimitKph() != null && asset.getSpeedLimitKph().signum() > 0) {
            return asset.getSpeedLimitKph();
        }
        return asset.getOrganization().getDefaultSpeedLimitKph();
    }

    private TelemetryAlert newAlert(
            Asset asset, GpsDevice device, GpsPosition p, BigDecimal limit, Geofence zone) {

        TelemetryAlert alert = new TelemetryAlert();
        alert.setOrganization(asset.getOrganization());
        alert.setAsset(asset);
        alert.setDevice(device);
        alert.setGeofence(zone);
        alert.setKind(TelemetryAlertKind.SPEEDING);
        alert.setStartedAt(p.getRecordedAt());
        alert.setLimitValue(limit);
        alert.setPeakValue(p.getSpeedKph());
        alert.setLatitude(p.getLatitude());
        alert.setLongitude(p.getLongitude());
        alert.setMessage(asset.getTag() + " a " + p.getSpeedKph() + " km/h"
                + (zone != null ? " em " + zone.getName() : "")
                + " (limite " + limit + " km/h)");
        return alert;
    }
}
