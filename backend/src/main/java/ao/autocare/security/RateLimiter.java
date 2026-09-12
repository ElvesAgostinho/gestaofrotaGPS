package ao.autocare.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limitador de tentativas por janela deslizante, em memória.
 *
 * <p>Protege sobretudo o início de sessão: sem isto, quem tenha uma lista de
 * emails pode tentar palavras-passe indefinidamente. Guarda apenas os instantes
 * das tentativas recentes por chave, o que é barato e não precisa de nada
 * externo.
 *
 * <p>Sendo em memória, o limite é <b>por instância</b>. Com várias instâncias
 * atrás de um balanceador, o limite efetivo multiplica-se pelo número delas;
 * nessa altura o sítio certo para isto passa a ser o proxy de entrada ou um
 * contador partilhado (Redis).
 */
@Component
public class RateLimiter {

    /** Acima disto a memória é limpa das chaves que já não interessam. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * Regista uma tentativa e diz se está dentro do limite.
     *
     * @param key    o que se está a limitar (por exemplo {@code "login:41.222.1.9"})
     * @param limit  tentativas permitidas na janela
     * @param window duração da janela
     * @return {@code true} se a tentativa é permitida
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanup(cutoff);
        }

        Deque<Instant> recent = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }
            if (recent.size() >= limit) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    /** Quantos segundos faltam até a chave voltar a ter margem. */
    public long retryAfterSeconds(String key, Duration window) {
        Deque<Instant> recent = attempts.get(key);
        if (recent == null) {
            return 0;
        }
        synchronized (recent) {
            Instant oldest = recent.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long seconds = Duration.between(Instant.now(), oldest.plus(window)).toSeconds();
            return Math.max(1, seconds);
        }
    }

    /** Limpa o contador de uma chave — usado quando o login tem sucesso. */
    public void reset(String key) {
        attempts.remove(key);
    }

    private void cleanup(Instant cutoff) {
        attempts.entrySet().removeIf(entry -> {
            Deque<Instant> value = entry.getValue();
            synchronized (value) {
                while (!value.isEmpty() && value.peekFirst().isBefore(cutoff)) {
                    value.pollFirst();
                }
                return value.isEmpty();
            }
        });
    }

    /**
     * Esquece a última tentativa registada para esta chave.
     *
     * <p>Chamado quando o login resulta. Um sucesso não é uma tentativa de
     * adivinhação: contá-lo não trava ataque nenhum — quem acerta à primeira
     * nunca chega ao contador — e castiga quem trabalha. Uma equipa de oficina
     * que entra e sai do sistema ao longo do dia batia no limite sem nunca ter
     * falhado uma vez.
     *
     * <p>O que isto trava é a adivinhação por força bruta, e essa é feita de
     * falhas. São essas que ficam a contar.
     */
    public void forget(String key) {
        Deque<Instant> recent = attempts.get(key);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            recent.pollLast();
        }
    }
}
