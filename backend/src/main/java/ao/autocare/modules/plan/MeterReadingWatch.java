package ao.autocare.modules.plan;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.AssetStatus;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.GpsDeviceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vigia os contadores que ninguém lê.
 *
 * <p>Um plano preventivo por horas depende do contador. Numa viatura com GPS o
 * contador anda sozinho; num gerador ou numa máquina sem rastreador, só anda
 * quando alguém o escreve. Se ninguém escreve, o contador fica parado, a tarefa
 * das 250 h nunca vence — e o sistema fica calado enquanto a máquina trabalha
 * 400 horas sem revisão.
 *
 * <p><b>Era o maior buraco do controlo preventivo sem GPS.</b> Este vigia
 * fecha-o: em vez de esperar por uma leitura que não vem, avisa que ela está em
 * falta. O silêncio deixa de ser confundido com «está tudo em dia».
 *
 * <p>Não avisa de ativos abatidos nem em manutenção — uma máquina desmontada na
 * oficina não tem contador para ler — nem de ativos que recebem telemetria, que
 * se atualizam sozinhos.
 */
@Component
public class MeterReadingWatch {

    private static final Logger log = LoggerFactory.getLogger(MeterReadingWatch.class);

    private final AssetRepository assets;
    private final AssetMeterRepository meters;
    private final GpsDeviceRepository devices;
    private final NotificationService notifications;

    /** Dias sem leitura a partir dos quais se avisa. */
    private final int diasAviso;

    /** Dias a partir dos quais deixa de ser um lembrete e passa a ser um risco. */
    private final int diasGrave;

    public MeterReadingWatch(
            AssetRepository assets,
            AssetMeterRepository meters,
            GpsDeviceRepository devices,
            NotificationService notifications,
            @Value("${autocare.meter-watch.warn-days:14}") int diasAviso,
            @Value("${autocare.meter-watch.critical-days:45}") int diasGrave) {
        this.assets = assets;
        this.meters = meters;
        this.devices = devices;
        this.notifications = notifications;
        this.diasAviso = diasAviso;
        this.diasGrave = diasGrave;
    }

    /**
     * Uma vez por dia.
     *
     * <p>A leitura de um contador mede-se em dias, não em horas: verificar de
     * hora a hora só produzia o mesmo aviso vinte e quatro vezes.
     */
    @Scheduled(cron = "${autocare.scheduler.meter-watch-cron:0 30 6 * * *}")
    @Transactional
    public void notifyStaleMeters() {
        Instant agora = Instant.now();
        int avisados = 0;

        for (Asset a : assets.findAll()) {
            try {
                if (a.isArchived()
                        || a.getStatus() == AssetStatus.RETIRED
                        || a.getStatus() == AssetStatus.MAINTENANCE) {
                    continue;
                }
                // Com aparelho GPS instalado, o contador anda sozinho.
                if (devices.existsByAssetId(a.getId())) {
                    continue;
                }

                List<AssetMeter> seus = meters.findByAssetId(a.getId());
                AssetMeter principal = seus.stream().filter(AssetMeter::isPrimary)
                        .findFirst().orElse(seus.isEmpty() ? null : seus.get(0));
                if (principal == null) {
                    continue;
                }

                Instant ultima = principal.getLastReadingAt() != null
                        ? principal.getLastReadingAt()
                        : a.getCreatedAt();
                if (ultima == null) {
                    continue;
                }
                long dias = Duration.between(ultima, agora).toDays();
                if (dias < diasAviso) {
                    notifications.resolve("meter_reading_stale", a.getId());
                    continue;
                }

                boolean grave = dias >= diasGrave;
                String unidade = principal.getUnit() != null ? principal.getUnit()
                        : (principal.getKind().name().equals("HOURMETER") ? "h" : "km");

                notifications.notifyManagers(NotificationService.Draft.of(
                                a.getOrganization().getId(),
                                AlertCategory.MAINTENANCE,
                                grave ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                                "Contador por ler há " + dias + " dias — " + a.getTag(),
                                grave
                                        ? "Sem leitura há " + dias + " dias. O plano preventivo "
                                                + "desta máquina está parado no tempo: as tarefas "
                                                + "por horas não vencem enquanto o contador não "
                                                + "for lido, mesmo que ela esteja a trabalhar."
                                        : "Última leitura há " + dias + " dias ("
                                                + principal.getCurrentValue() + " " + unidade
                                                + "). Sem leituras, o plano por horas não avança.",
                                "meter_reading_stale", a.getId(),
                                "/ativos/" + a.getId())
                        .forAsset(a));
                avisados++;

            } catch (Exception e) {
                log.warn("Falha ao verificar o contador de {}: {}", a.getId(), e.toString());
            }
        }

        if (avisados > 0) {
            log.info("{} contador(es) por ler há mais de {} dias", avisados, diasAviso);
        }
    }
}
