package ao.autocare.modules.telemetry;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Empurra posições e alertas para os mapas abertos, por SSE.
 *
 * <p>Escolheu-se SSE em vez de WebSocket porque o fluxo é só num sentido
 * (servidor para o mapa) e o {@code EventSource} do browser volta a ligar-se
 * sozinho quando a rede cai — coisa que com WebSocket seria preciso escrever.
 *
 * <p>As ligações vivem em memória, agrupadas por empresa. Com mais do que uma
 * instância do servidor, cada uma só alimenta os mapas ligados a si; nessa
 * altura é preciso um canal partilhado (Redis, por exemplo) entre instâncias.
 */
@Component
public class TelemetryStream {

    private static final Logger log = LoggerFactory.getLogger(TelemetryStream.class);

    /** Fim da ligação ao fim deste tempo; o browser volta a ligar-se sozinho. */
    private static final Duration CONNECTION_TTL = Duration.ofMinutes(30);

    /** Travão para uma empresa não esgotar as ligações do servidor. */
    private static final int MAX_PER_ORG = 50;

    private final Map<String, Set<SseEmitter>> byOrg = new ConcurrentHashMap<>();

    /** Abre uma ligação nova para a empresa. */
    public SseEmitter open(String orgId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TTL.toMillis());
        Set<SseEmitter> set = byOrg.computeIfAbsent(orgId,
                key -> ConcurrentHashMap.newKeySet());

        if (set.size() >= MAX_PER_ORG) {
            emitter.completeWithError(new IllegalStateException(
                    "Demasiadas ligações abertas para esta empresa."));
            return emitter;
        }
        set.add(emitter);

        emitter.onCompletion(() -> remove(orgId, emitter));
        emitter.onTimeout(() -> remove(orgId, emitter));
        emitter.onError(e -> remove(orgId, emitter));

        try {
            // Primeiro evento imediato: confirma ao cliente que está ligado e
            // impede que proxies fechem uma ligação que ainda nada enviou.
            emitter.send(SseEmitter.event().name("ligado")
                    .data(Map.of("organizationId", orgId)));
        } catch (IOException e) {
            remove(orgId, emitter);
        }
        return emitter;
    }

    /**
     * Envia um evento <b>depois</b> da transação confirmar. Enviar durante a
     * transação mostraria no mapa uma posição que ainda podia ser desfeita.
     */
    public void publishAfterCommit(String orgId, String event, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(orgId, event, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish(orgId, event, payload);
                    }
                });
    }

    public void publish(String orgId, String event, Object payload) {
        Set<SseEmitter> set = byOrg.get(orgId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (Exception e) {
                // Cliente desligou-se sem avisar: limpa e segue.
                remove(orgId, emitter);
            }
        }
    }

    /**
     * Sinal de vida periódico. Sem tráfego, proxies e balanceadores fecham a
     * ligação ao fim de poucos minutos e o mapa fica parado sem dar erro.
     */
    public void heartbeat() {
        for (Map.Entry<String, Set<SseEmitter>> entry : byOrg.entrySet()) {
            publish(entry.getKey(), "batida", Map.of("at", java.time.Instant.now().toString()));
        }
    }

    public int connectionCount(String orgId) {
        Set<SseEmitter> set = byOrg.get(orgId);
        return set == null ? 0 : set.size();
    }

    private void remove(String orgId, SseEmitter emitter) {
        Set<SseEmitter> set = byOrg.get(orgId);
        if (set == null) {
            return;
        }
        set.remove(emitter);
        if (set.isEmpty()) {
            byOrg.remove(orgId, set);
        }
        log.debug("Ligação de tempo real fechada (empresa {})", orgId);
    }
}
