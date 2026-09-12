package ao.autocare.modules.telemetry;

import ao.autocare.common.ApiException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Bilhetes de acesso ao fluxo em tempo real.
 *
 * <p>O {@code EventSource} do browser não permite enviar cabeçalhos, por isso a
 * ligação ao fluxo não pode levar o {@code Authorization: Bearer}. Pôr o token
 * de acesso no URL resolveria, mas o URL aparece nos registos do servidor, do
 * proxy e no histórico — e esse token vale 15 minutos em toda a API.
 *
 * <p>Em vez disso, o cliente pede um bilhete com a sua sessão normal e usa-o no
 * URL do fluxo: vale {@value #TTL_SECONDS} segundos, serve <b>uma única vez</b>
 * e não dá acesso a mais nada.
 */
@Component
public class StreamTickets {

    public static final int TTL_SECONDS = 60;

    /** Acima disto deixa de se aceitar bilhetes novos — trava emissão descontrolada. */
    private static final int MAX_PENDING = 10_000;

    private final Map<String, Ticket> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public record Ticket(String orgId, String userId, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String issue(String orgId, String userId) {
        sweep();
        if (pending.size() >= MAX_PENDING) {
            throw ApiException.conflict("Demasiados pedidos de ligação em curso. Tente daqui a pouco.");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);
        pending.put(ticket, new Ticket(orgId, userId,
                Instant.now().plus(Duration.ofSeconds(TTL_SECONDS))));
        return ticket;
    }

    /** Valida e consome o bilhete. Um bilhete só serve uma vez. */
    public Ticket consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw ApiException.unauthorized("Falta o bilhete de ligação.");
        }
        Ticket found = pending.remove(ticket);
        if (found == null || found.expired()) {
            throw ApiException.unauthorized("Bilhete de ligação inválido ou expirado.");
        }
        return found;
    }

    /** Limpa bilhetes que ninguém chegou a usar. */
    public void sweep() {
        pending.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    int pendingCount() {
        return pending.size();
    }
}
