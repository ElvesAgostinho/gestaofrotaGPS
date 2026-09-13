package ao.autocare.modules.command;

import ao.autocare.domain.DeviceCommand;
import ao.autocare.domain.enums.Enums.CommandConfirmationSource;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Integração com um servidor Traccar.
 *
 * <p>O Traccar mantém a ligação a cada aparelho e conhece o protocolo de cada
 * fabricante; é ele que sabe traduzir "bloquear motor" para os bytes certos do
 * rastreador em causa. O sistema limita-se a pedir-lho, com todas as travas de
 * segurança já verificadas do seu lado.
 *
 * <p>Endpoints usados, todos da API REST documentada do Traccar:
 * <ul>
 *   <li>{@code GET  /api/server} — teste de ligação e versão</li>
 *   <li>{@code GET  /api/devices?uniqueId=} — encontrar o aparelho e o protocolo</li>
 *   <li>{@code GET  /api/commands/types?deviceId=} — comandos que o aparelho aceita</li>
 *   <li>{@code POST /api/commands/send} — enviar (200 entregue, 202 em fila)</li>
 *   <li>{@code GET  /api/positions?deviceId=} — atributo {@code blocked} reportado</li>
 *   <li>{@code GET  /api/reports/events?type=commandResult} — resposta do aparelho</li>
 * </ul>
 *
 * <p><b>Nada disto foi verificado contra um Traccar real</b> — não há nenhum
 * configurado nesta instalação. O endpoint de diagnóstico
 * {@code GET /api/v1/telemetry/traccar/status} existe precisamente para isso:
 * confirmar a ligação antes de confiar no bloqueio.
 */
@Component
@Primary
// Sobre o conteúdo, não a presença: TRACCAR_URL tem um valor por omissão vazio,
// e @ConditionalOnProperty daria esta classe por ativa sem servidor nenhum.
@ConditionalOnExpression("'${autocare.traccar.url:}' != ''")
public class TraccarCommandProvider implements CommandProvider {

    private static final Logger log = LoggerFactory.getLogger(TraccarCommandProvider.class);

    /**
     * Curto de propósito. Se o Traccar não responder depressa é melhor falhar e
     * deixar o comando na fila do que prender o pedido do utilizador — a fila
     * volta a tentar.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Nomes dos comandos no Traccar. */
    private static final String ENGINE_STOP = "engineStop";
    private static final String ENGINE_RESUME = "engineResume";

    /** Quanto para trás se procuram respostas do aparelho. */
    private static final Duration EVENT_WINDOW = Duration.ofHours(24);

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String authorization;

    public TraccarCommandProvider(
            ObjectMapper json,
            @Value("${autocare.traccar.url}") String baseUrl,
            @Value("${autocare.traccar.user:}") String user,
            @Value("${autocare.traccar.password:}") String password,
            @Value("${autocare.traccar.token:}") String token) {

        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        if (token != null && !token.isBlank()) {
            this.authorization = "Bearer " + token;
        } else if (user != null && !user.isBlank()) {
            this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                    (user + ":" + password).getBytes(StandardCharsets.UTF_8));
        } else {
            this.authorization = null;
        }
    }

    // ==== Envio =========================================================
    @Override
    public Dispatch dispatch(DeviceCommand command) {
        String externalId = command.getDevice().getExternalId();
        try {
            Optional<DeviceInfo> info = describeDevice(externalId);
            if (info.isEmpty()) {
                return Dispatch.rejected("O aparelho " + externalId + " não existe no Traccar.");
            }
            String type = traccarType(command.getKind());
            List<String> supported = info.get().supportedCommands();

            // O comando pode simplesmente não existir neste protocolo. Enviá-lo
            // às cegas daria um erro obscuro do Traccar, ou pior: silêncio.
            if (!supported.isEmpty() && !supported.contains(type)) {
                return Dispatch.rejected(
                        "O aparelho " + externalId + " (protocolo " + info.get().protocol()
                                + ") não suporta \"" + type + "\". Comandos disponíveis: "
                                + String.join(", ", supported) + ".");
            }

            String body = json.writeValueAsString(Map.of(
                    "deviceId", Long.parseLong(info.get().providerDeviceId()),
                    "type", type));

            HttpResponse<String> response = send(
                    request("/api/commands/send")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build());

            if (response.statusCode() / 100 != 2) {
                return Dispatch.rejected("O Traccar recusou o comando (HTTP "
                        + response.statusCode() + "): " + truncate(response.body()));
            }
            String providerId = readId(response.body());

            // 202 = o Traccar guardou o comando porque o aparelho está offline.
            // Não é a mesma coisa que entregue, e a diferença importa: pode ser
            // executado horas depois, com a viatura noutro sítio.
            return response.statusCode() == 202
                    ? Dispatch.queuedForOfflineDevice(providerId)
                    : Dispatch.delivered(providerId);

        } catch (Exception e) {
            log.warn("Falha ao enviar comando para o Traccar ({}): {}", externalId, e.toString());
            return Dispatch.rejected("Não foi possível contactar o Traccar: " + e.getMessage());
        }
    }

    // ==== Aparelho ======================================================
    @Override
    public Optional<DeviceInfo> describeDevice(String externalId) {
        try {
            HttpResponse<String> response = send(
                    request("/api/devices?uniqueId=" + encode(externalId)).GET().build());
            if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode devices = json.readTree(response.body());
            if (!devices.isArray() || devices.isEmpty()) {
                return Optional.empty();
            }
            JsonNode device = devices.get(0);
            String providerId = device.get("id").asText();
            String status = device.path("status").asText("unknown");

            return Optional.of(new DeviceInfo(
                    providerId,
                    device.path("protocol").asText(null),
                    status,
                    "online".equalsIgnoreCase(status),
                    supportedCommands(providerId)));

        } catch (Exception e) {
            log.warn("Falha ao consultar o aparelho {} no Traccar: {}", externalId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Tipos de comando que este aparelho aceita, segundo o Traccar. É a única
     * forma de saber se "engineStop" existe neste protocolo sem o experimentar
     * numa viatura real.
     */
    private List<String> supportedCommands(String providerDeviceId) {
        try {
            HttpResponse<String> response = send(
                    request("/api/commands/types?deviceId=" + encode(providerDeviceId))
                            .GET().build());
            if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
                return List.of();
            }
            List<String> types = new ArrayList<>();
            for (JsonNode node : json.readTree(response.body())) {
                String type = node.path("type").asText(null);
                if (type != null) {
                    types.add(type);
                }
            }
            return types;
        } catch (Exception e) {
            log.debug("Não foi possível listar os comandos do aparelho {}: {}",
                    providerDeviceId, e.toString());
            return List.of();
        }
    }

    // ==== Confirmação ===================================================
    /**
     * Procura prova de execução. Duas fontes, por ordem de qualidade:
     *
     * <ol>
     *   <li>o <b>atributo {@code blocked}</b> na última posição — é o próprio
     *       aparelho a dizer em que estado está, e é a melhor prova que existe;</li>
     *   <li>um <b>evento {@code commandResult}</b> — o aparelho respondeu ao
     *       comando, mas a resposta diz "recebi", não necessariamente "cortei".</li>
     * </ol>
     *
     * <p>Nem todos os protocolos reportam qualquer uma delas. Quando não há
     * prova, o comando fica em "enviado, por confirmar" — que é a verdade.
     */
    @Override
    public Evidence confirmationFor(DeviceCommand command) {
        String externalId = command.getDevice().getExternalId();
        try {
            // O id do aparelho no fornecedor já está guardado desde a
            // sincronização. Voltar a procurá-lo em cada sondagem custava dois
            // pedidos HTTP a mais por comando e por ciclo — incluindo a lista de
            // comandos suportados, que aqui não serve para nada.
            String providerId = command.getDevice().getProviderDeviceId();
            if (providerId == null || providerId.isBlank()) {
                Optional<DeviceInfo> info = describeDevice(externalId);
                if (info.isEmpty()) {
                    return Evidence.none();
                }
                providerId = info.get().providerDeviceId();
            }

            Optional<Boolean> blocked = reportedLockState(providerId);
            if (blocked.isPresent()) {
                boolean expected = command.getKind() == DeviceCommandKind.ENGINE_STOP;
                if (blocked.get() == expected) {
                    return new Evidence(true, blocked,
                            CommandConfirmationSource.DEVICE_ATTRIBUTE,
                            "O aparelho reporta o imobilizador "
                                    + (blocked.get() ? "ativo" : "inativo") + ".");
                }
                // O aparelho reporta o contrário do pedido: não é confirmação.
                return new Evidence(false, blocked, null,
                        "O aparelho ainda reporta o estado anterior.");
            }

            Optional<String> result = commandResult(providerId, command.getSentAt());
            if (result.isPresent()) {
                return new Evidence(true, Optional.empty(),
                        CommandConfirmationSource.TRACCAR_EVENT,
                        "O aparelho respondeu ao comando: " + result.get());
            }
            return Evidence.none();

        } catch (Exception e) {
            log.debug("Falha ao procurar confirmação de {}: {}", command.getId(), e.toString());
            return Evidence.none();
        }
    }

    /** Atributo {@code blocked} da última posição, quando o protocolo o reporta. */
    private Optional<Boolean> reportedLockState(String providerDeviceId) throws Exception {
        HttpResponse<String> response = send(
                request("/api/positions?deviceId=" + encode(providerDeviceId)).GET().build());
        if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
            return Optional.empty();
        }
        JsonNode positions = json.readTree(response.body());
        if (!positions.isArray() || positions.isEmpty()) {
            return Optional.empty();
        }
        JsonNode attributes = positions.get(0).path("attributes");
        if (attributes.hasNonNull("blocked")) {
            return Optional.of(attributes.get("blocked").asBoolean());
        }
        return Optional.empty();
    }

    /** Evento {@code commandResult} posterior ao envio, se o aparelho respondeu. */
    private Optional<String> commandResult(String providerDeviceId, Instant sentAt)
            throws Exception {

        Instant from = sentAt != null ? sentAt : Instant.now().minus(EVENT_WINDOW);
        String path = "/api/reports/events"
                + "?deviceId=" + encode(providerDeviceId)
                + "&type=commandResult"
                + "&from=" + encode(DateTimeFormatter.ISO_INSTANT.format(from))
                + "&to=" + encode(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        HttpResponse<String> response = send(
                request(path).header("Accept", "application/json").GET().build());
        if (response.statusCode() / 100 != 2 || response.body().isBlank()) {
            return Optional.empty();
        }
        JsonNode events = json.readTree(response.body());
        if (!events.isArray() || events.isEmpty()) {
            return Optional.empty();
        }
        JsonNode last = events.get(events.size() - 1);
        return Optional.of(last.path("attributes").path("result").asText("recebido"));
    }

    // ==== Diagnóstico ===================================================
    @Override
    public ProviderHealth health() {
        if (authorization == null) {
            return new ProviderHealth(true, false, name(), null,
                    "Faltam as credenciais: defina TRACCAR_TOKEN ou "
                            + "TRACCAR_USER e TRACCAR_PASSWORD.");
        }
        try {
            HttpResponse<String> response = send(request("/api/server").GET().build());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return new ProviderHealth(true, false, name(), null,
                        "O Traccar recusou as credenciais (HTTP " + response.statusCode() + ").");
            }
            if (response.statusCode() / 100 != 2) {
                return new ProviderHealth(true, false, name(), null,
                        "O Traccar respondeu HTTP " + response.statusCode() + ".");
            }
            String version = json.readTree(response.body()).path("version").asText(null);
            return new ProviderHealth(true, true, name(), version, null);

        } catch (Exception e) {
            return new ProviderHealth(true, false, name(), null,
                    "Não foi possível contactar o Traccar: " + e.getMessage());
        }
    }

    private String traccarType(DeviceCommandKind kind) {
        return kind == DeviceCommandKind.ENGINE_STOP ? ENGINE_STOP : ENGINE_RESUME;
    }

    private String readId(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode node = json.readTree(body);
        return node.hasNonNull("id") ? node.get("id").asText() : null;
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String name() {
        return "Traccar (" + baseUrl + ")";
    }
}
