package ao.autocare.modules.telemetry;

import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.TelemetryAlert;
import ao.autocare.domain.enums.Enums.GpsDeviceStatus;
import ao.autocare.domain.enums.Enums.TelemetryAlertKind;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.TelemetryAlertRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deteta aparelhos que deixaram de comunicar.
 *
 * <p>Um rastreador calado é o primeiro sinal de avaria — ou de alguém lhe ter
 * cortado a alimentação. Por isso não basta o ecrã mostrar OFFLINE: abre-se um
 * alerta, que fecha sozinho quando o aparelho voltar a falar. Assim fica
 * registado quanto tempo esteve calado e quem o viu.
 */
@Component
public class CommsWatch {

    private final GpsDeviceRepository devices;
    private final TelemetryAlertRepository alerts;
    private final SpeedWatch speedWatch;
    private final NotificationService notifications;

    public CommsWatch(
            GpsDeviceRepository devices,
            TelemetryAlertRepository alerts,
            SpeedWatch speedWatch,
            NotificationService notifications) {
        this.devices = devices;
        this.alerts = alerts;
        this.speedWatch = speedWatch;
        this.notifications = notifications;
    }

    /**
     * Percorre os aparelhos de uma empresa e abre alertas para os que se calaram.
     *
     * @return quantos alertas foram abertos
     */
    @Transactional
    public int scan(String orgId, Instant now) {
        int opened = 0;
        for (GpsDevice device : devices.findByOrganizationIdOrderByCreatedAtDesc(orgId)) {
            if (device.getLastSeenAt() == null) {
                continue; // nunca comunicou: não é perda, é instalação por concluir
            }
            boolean silent = Duration.between(device.getLastSeenAt(), now)
                    .compareTo(GpsDevice.OFFLINE_AFTER) > 0;
            if (!silent) {
                continue;
            }
            device.setStatus(GpsDeviceStatus.OFFLINE);

            // Um episódio de excesso não pode ficar aberto num aparelho calado.
            if (device.getAsset() != null) {
                speedWatch.closeOpenEpisode(device.getAsset().getId(), device.getLastSeenAt());
            }
            if (alerts.findFirstByDeviceIdAndKindAndEndedAtIsNull(
                    device.getId(), TelemetryAlertKind.COMMS_LOST).isPresent()) {
                continue;
            }
            TelemetryAlert alert = alerts.save(newAlert(device));
            NotificationService.Draft draft = NotificationService.Draft.of(
                    orgId,
                    ao.autocare.domain.enums.Enums.AlertCategory.GPS,
                    ao.autocare.domain.enums.Enums.AlertSeverity.CRITICAL,
                    "Aparelho de GPS sem comunicar",
                    alert.getMessage(),
                    "telemetry_alert", alert.getId(),
                    "/frota/alertas/" + alert.getId());
            notifications.notifyManagers(
                    device.getAsset() != null ? draft.forAsset(device.getAsset()) : draft);
            opened++;
        }
        return opened;
    }

    /** O aparelho voltou a falar: fecha o alerta de silêncio, se houver. */
    @Transactional
    public void deviceReported(GpsDevice device, Instant at) {
        alerts.findFirstByDeviceIdAndKindAndEndedAtIsNull(
                        device.getId(), TelemetryAlertKind.COMMS_LOST)
                .ifPresent(alert -> alert.setEndedAt(at));
    }

    private TelemetryAlert newAlert(GpsDevice device) {
        TelemetryAlert alert = new TelemetryAlert();
        alert.setOrganization(device.getOrganization());
        alert.setAsset(device.getAsset());
        alert.setDevice(device);
        alert.setKind(TelemetryAlertKind.COMMS_LOST);
        // O episódio começa na última vez que o aparelho falou, não agora.
        alert.setStartedAt(device.getLastSeenAt());
        alert.setMessage("O aparelho " + device.getExternalId()
                + (device.getAsset() != null ? " (" + device.getAsset().getTag() + ")" : "")
                + " deixou de comunicar.");
        return alert;
    }
}
