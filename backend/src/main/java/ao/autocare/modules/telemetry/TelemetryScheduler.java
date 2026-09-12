package ao.autocare.modules.telemetry;

import ao.autocare.modules.command.CommandService;
import ao.autocare.repo.OrganizationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefas periódicas da telemetria. Desligável com
 * {@code autocare.scheduler.enabled=false} (desligado nos testes).
 */
@Component
@ConditionalOnProperty(name = "autocare.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryScheduler.class);

    private final TelemetryStream stream;
    private final StreamTickets tickets;
    private final TelemetryService telemetry;
    private final CommsWatch commsWatch;
    private final CommandService commands;
    private final OrganizationRepository organizations;

    public TelemetryScheduler(
            TelemetryStream stream,
            StreamTickets tickets,
            TelemetryService telemetry,
            CommsWatch commsWatch,
            CommandService commands,
            OrganizationRepository organizations) {
        this.stream = stream;
        this.tickets = tickets;
        this.telemetry = telemetry;
        this.commsWatch = commsWatch;
        this.commands = commands;
        this.organizations = organizations;
    }

    /**
     * Sinal de vida nas ligações abertas. Sem tráfego, proxies e balanceadores
     * fecham a ligação ao fim de poucos minutos e o mapa fica parado sem dar
     * qualquer erro visível — o pior tipo de avaria.
     */
    @Scheduled(fixedDelayString = "${autocare.telemetry.heartbeat-ms:20000}", initialDelay = 20000)
    public void heartbeat() {
        stream.heartbeat();
    }

    /** Bilhetes de ligação que ninguém chegou a usar. */
    @Scheduled(fixedDelayString = "${autocare.telemetry.ticket-sweep-ms:300000}",
            initialDelay = 300000)
    public void sweepTickets() {
        tickets.sweep();
    }

    /**
     * Procura aparelhos que se calaram. É o primeiro sinal de avaria — ou de
     * alguém ter cortado a alimentação ao rastreador — por isso corre com
     * frequência e abre um alerta em vez de apenas mudar o estado no ecrã.
     */
    @Scheduled(fixedDelayString = "${autocare.telemetry.comms-scan-ms:300000}",
            initialDelay = 120000)
    public void scanCommunications() {
        int opened = 0;
        java.time.Instant now = java.time.Instant.now();
        for (String orgId : orgIds()) {
            try {
                opened += commsWatch.scan(orgId, now);
            } catch (Exception e) {
                log.warn("Falha ao verificar comunicação da empresa {}: {}", orgId, e.toString());
            }
        }
        if (opened > 0) {
            log.info("{} aparelho(s) deixaram de comunicar", opened);
        }
    }

    /**
     * Fecha viagens de ativos cujo aparelho deixou de comunicar. Sem isto, uma
     * viagem cujo aparelho se cala fica aberta para sempre e os totais de
     * quilómetros do mês nunca fecham.
     */
    @Scheduled(fixedDelayString = "${autocare.telemetry.close-trips-ms:600000}",
            initialDelay = 600000)
    public void closeStaleTrips() {
        int closed = 0;
        for (String orgId : orgIds()) {
            try {
                closed += telemetry.closeStaleTrips(orgId);
            } catch (Exception e) {
                log.warn("Falha ao fechar viagens da empresa {}: {}", orgId, e.toString());
            }
        }
        if (closed > 0) {
            log.debug("{} viagem(ns) fechada(s) por silêncio do aparelho", closed);
        }
    }

    /**
     * Tenta enviar os bloqueios que esperam que a viatura pare, e expira os que
     * passaram do prazo. Corre com frequencia porque a janela em que uma viatura
     * esta parada pode ser curta.
     */
    @Scheduled(fixedDelayString = "${autocare.telemetry.command-queue-ms:60000}",
            initialDelay = 60000)
    public void processCommandQueue() {
        try {
            int sent = commands.processQueue();
            if (sent > 0) {
                log.info("{} comando(s) enviados ao fornecedor", sent);
            }
        } catch (Exception e) {
            log.warn("Falha ao processar a fila de comandos: {}", e.toString());
        }
    }

    /**
     * Vai perguntar ao fornecedor se os comandos enviados já foram executados.
     *
     * <p>É isto que torna a confirmação real. Sem esta sondagem, "confirmado"
     * dependia de alguém carregar num botão — uma afirmação, não uma prova.
     */
    @Scheduled(fixedDelayString = "${autocare.telemetry.command-confirm-ms:120000}",
            initialDelay = 90000)
    public void confirmSentCommands() {
        try {
            int confirmed = commands.pollConfirmations();
            if (confirmed > 0) {
                log.info("{} comando(s) confirmados pelo aparelho", confirmed);
            }
        } catch (Exception e) {
            log.warn("Falha ao confirmar comandos: {}", e.toString());
        }
    }

    private List<String> orgIds() {
        return organizations.findAll().stream().map(org -> org.getId()).toList();
    }
}
