package ao.autocare.modules.telemetry;

import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestResult;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.PositionInput;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.GpsPositionRepository;
import ao.autocare.repo.IntegrationSettingsRepository;
import ao.autocare.security.SecretBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * As posições do Traccar entram no sistema — por sondagem e por encaminhamento.
 *
 * <p>Até aqui o sistema só <b>mandava</b> comandos ao Traccar. As posições
 * esperavam que o aparelho publicasse JSON diretamente no nosso endpoint — e um
 * rastreador real (GT06, Teltonika, Queclink) fala o protocolo do fabricante
 * com o Traccar, não JSON connosco. Em produção, o mapa, o odómetro e o
 * combustível ficavam vazios.
 *
 * <p>Duas portas, ambas a partir do Traccar:
 * <ul>
 *   <li><b>Sondagem</b>: de 20 em 20 s pede-se a última posição de cada
 *       aparelho ({@code GET /api/positions}). Funciona com qualquer Traccar,
 *       sem mexer na configuração dele. É a rede de segurança.</li>
 *   <li><b>Encaminhamento</b>: o Traccar envia cada posição ao nosso endpoint
 *       assim que chega ({@code forward.url}), com um segredo no cabeçalho.
 *       É o tempo real; exige uma linha no {@code traccar.xml}.</li>
 * </ul>
 * A mesma posição não entra duas vezes: leva o id que o Traccar lhe deu.
 *
 * <p>Unidades do Traccar, que enganam: a velocidade vem em <b>nós</b>, o
 * odómetro e a distância total em <b>metros</b>, as horas de motor em
 * <b>milissegundos</b>. Tudo se converte aqui, uma vez, e não em cada ecrã.
 */
@Component
public class TraccarPositions {

    private static final Logger log = LoggerFactory.getLogger(TraccarPositions.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(10);
    private static final BigDecimal NOS_PARA_KMH = new BigDecimal("1.852");
    private static final BigDecimal MIL = new BigDecimal("1000");

    private final IntegrationSettingsRepository settings;
    private final GpsDeviceRepository devices;
    private final GpsPositionRepository positions;
    private final TelemetryService telemetry;
    private final SecretBox cofre;
    private final ObjectMapper json;
    private final org.springframework.transaction.support.TransactionTemplate transacao;

    public TraccarPositions(IntegrationSettingsRepository settings, GpsDeviceRepository devices,
            GpsPositionRepository positions, TelemetryService telemetry, SecretBox cofre,
            ObjectMapper json, org.springframework.transaction.PlatformTransactionManager txManager) {
        this.transacao = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.settings = settings;
        this.devices = devices;
        this.positions = positions;
        this.telemetry = telemetry;
        this.cofre = cofre;
        this.json = json;
    }

    // ==== Sondagem =========================================================

    /**
     * Visita cada empresa com Traccar apontado. Erros de uma não param as outras.
     *
     * <p>A transação é explícita: {@code sondar} chamado daqui é uma chamada
     * interna, e o {@code @Transactional} dele não se aplica a chamadas
     * internas. Sem isto a ronda corria, mas o que gravava — última ronda,
     * erro, posições — perdia-se em silêncio. Os testes passavam porque chamam
     * {@code sondar} de fora, pelo proxy. Só a primeira instalação real o
     * mostrou.
     */
    @Scheduled(fixedDelayString = "${autocare.traccar.poll-ms:20000}",
            initialDelayString = "${autocare.traccar.poll-initial-ms:15000}")
    public void sondarTodas() {
        for (IntegrationSettings s : settings.findByTraccarUrlIsNotNull()) {
            if (!s.hasTraccar() || !s.isTraccarPollEnabled()) {
                continue;
            }
            try {
                String id = s.getId();
                transacao.execute(status -> sondar(id));
            } catch (RuntimeException e) {
                log.warn("Sondagem do Traccar falhou para {}: {}", s.getId(), e.toString());
            }
        }
    }

    /** Uma ronda de sondagem a uma empresa. Devolve quantas posições entraram. */
    @Transactional
    public int sondar(String settingsId) {
        IntegrationSettings s = settings.findById(settingsId).orElseThrow();
        String orgId = s.getOrganization().getId();
        int aceites = 0;
        try {
            // Os aparelhos primeiro: o Traccar identifica-os por um id numérico,
            // e o que nós conhecemos é o uniqueId (o IMEI).
            Map<Long, String> uniqueIdPorDeviceId = new HashMap<>();
            for (JsonNode d : pedir(s, "/api/devices")) {
                uniqueIdPorDeviceId.put(d.path("id").asLong(), d.path("uniqueId").asText());
            }
            for (JsonNode p : pedir(s, "/api/positions")) {
                String uniqueId = uniqueIdPorDeviceId.get(p.path("deviceId").asLong());
                if (uniqueId == null) {
                    continue;
                }
                if (aceitar(orgId, uniqueId, p)) {
                    aceites++;
                }
            }
            s.setTraccarPollError(null);
        } catch (Exception e) {
            // A mensagem técnica fica no log; a empresa vê uma frase. Um Traccar
            // em baixo avisa-se uma vez, não de 20 em 20 segundos até encher o log.
            if (s.getTraccarPollError() == null) {
                log.warn("Sondagem do Traccar para a empresa {}: {}", orgId, e.toString());
            } else {
                log.debug("Sondagem do Traccar para a empresa {} continua a falhar: {}", orgId, e.toString());
            }
            s.setTraccarPollError(explicar(e));
        }
        s.setTraccarLastPollAt(Instant.now());
        if (aceites > 0) {
            s.setTraccarLastPositionAt(Instant.now());
        }
        return aceites;
    }

    // ==== Encaminhamento ===================================================

    /**
     * Uma posição que o Traccar nos mandou ({@code forward.url}).
     *
     * @return o que se fez com ela, ou vazio se o segredo não bate com nenhuma
     *     empresa — e aí a resposta é 401, não 200 com «ignorada»
     */
    @Transactional
    public Optional<IngestResult> receber(String segredo, JsonNode corpo) {
        if (segredo == null || segredo.isBlank()) {
            return Optional.empty();
        }
        Optional<IntegrationSettings> dona = settings.findByTraccarForwardSecretHash(sha256(segredo));
        if (dona.isEmpty()) {
            return Optional.empty();
        }
        IntegrationSettings s = dona.get();
        JsonNode posicao = corpo.path("position");
        String uniqueId = corpo.path("device").path("uniqueId").asText(null);
        if (posicao.isMissingNode() || uniqueId == null) {
            return Optional.of(new IngestResult(false,
                    "O corpo não traz «position» e «device.uniqueId».", null, null, null, 0));
        }
        boolean ok = aceitar(s.getOrganization().getId(), uniqueId, posicao);
        if (ok) {
            s.setTraccarLastPositionAt(Instant.now());
        }
        return Optional.of(new IngestResult(ok, ok ? null : "Posição ignorada.", null, null, null, 0));
    }

    /** Gera um segredo novo para o encaminhamento; devolve-o em claro uma única vez. */
    @Transactional
    public String novoSegredoDeEncaminhamento(IntegrationSettings s) {
        byte[] bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        String segredo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        s.setTraccarForwardSecretHash(sha256(segredo));
        return segredo;
    }

    // ==== O que uma posição do Traccar tem ==================================

    /** Converte e entrega ao mesmo caminho que qualquer outra posição. */
    private boolean aceitar(String orgId, String uniqueId, JsonNode p) {
        Optional<GpsDevice> aparelho = devices.findByOrganizationIdAndExternalId(orgId, uniqueId);
        if (aparelho.isEmpty()) {
            // Um aparelho que existe no Traccar mas não cá é decisão da empresa:
            // regista-se cá quando se quiser vê-lo. Não se cria sozinho.
            return false;
        }
        GpsDevice device = aparelho.get();
        String providerId = p.hasNonNull("id") ? p.get("id").asText() : null;
        if (providerId != null && positions.existsByDeviceIdAndProviderPositionId(device.getId(), providerId)) {
            return false;
        }

        JsonNode a = p.path("attributes");
        PositionInput in = new PositionInput(
                decimal(p, "latitude"), decimal(p, "longitude"),
                instante(p.path("fixTime").asText(null)),
                p.hasNonNull("speed") ? BigDecimal.valueOf(p.get("speed").asDouble())
                        .multiply(NOS_PARA_KMH).setScale(1, RoundingMode.HALF_UP) : null,
                decimal(p, "course"),
                decimal(p, "altitude"),
                decimal(p, "accuracy"),
                a.hasNonNull("sat") ? a.get("sat").asInt() : null,
                a.hasNonNull("ignition") ? a.get("ignition").asBoolean() : null,
                a.hasNonNull("batteryLevel") ? a.get("batteryLevel").asInt() : null,
                metrosParaKm(a, "odometer"),
                a.hasNonNull("hours") ? BigDecimal.valueOf(a.get("hours").asDouble())
                        .divide(new BigDecimal("3600000"), 2, RoundingMode.HALF_UP) : null,
                nivelEmLitros(a, device),
                a.hasNonNull("fuel") && emPercentagem(device)
                        ? BigDecimal.valueOf(a.get("fuel").asDouble()).setScale(2, RoundingMode.HALF_UP)
                        : null,
                metrosParaKm(a, "totalDistance"),
                providerId);

        IngestResult r = telemetry.record(device, in);
        return r.accepted();
    }

    /**
     * O «fuel» do Traccar em litros, pela unidade definida no aparelho.
     *
     * <p>Sem adivinhar: 100 é um valor válido em litros e em percentagem. Em
     * PERCENT sem capacidade de depósito no ativo não há como converter — fica
     * só a percentagem, e o sensor de litros não conta.
     */
    private BigDecimal nivelEmLitros(JsonNode a, GpsDevice device) {
        if (!a.hasNonNull("fuel")) {
            return null;
        }
        BigDecimal v = BigDecimal.valueOf(a.get("fuel").asDouble());
        if (emPercentagem(device)) {
            BigDecimal deposito = device.getAsset() != null ? device.getAsset().getTankCapacityLiters() : null;
            if (deposito == null) {
                return null;
            }
            return deposito.multiply(v).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean emPercentagem(GpsDevice device) {
        return "PERCENT".equalsIgnoreCase(device.getFuelUnit());
    }

    private static BigDecimal metrosParaKm(JsonNode a, String campo) {
        if (!a.hasNonNull(campo)) {
            return null;
        }
        return BigDecimal.valueOf(a.get(campo).asDouble()).divide(MIL, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? BigDecimal.valueOf(n.get(campo).asDouble()) : null;
    }

    private static Instant instante(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==== HTTP ==============================================================

    private JsonNode pedir(IntegrationSettings s, String caminho) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder()
                .uri(URI.create(s.getTraccarUrl() + caminho))
                .timeout(TEMPO_LIMITE)
                .header("Accept", "application/json")
                .GET();
        String token = cofre.decrypt(s.getTraccarTokenEnc());
        String senha = cofre.decrypt(s.getTraccarPasswordEnc());
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        } else if (s.getTraccarUser() != null && senha != null) {
            String par = s.getTraccarUser() + ":" + senha;
            pedido.header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(par.getBytes(StandardCharsets.UTF_8)));
        } else {
            throw new IllegalStateException("Sem credenciais do Traccar.");
        }
        HttpResponse<String> r = HttpClient.newBuilder()
                .connectTimeout(TEMPO_LIMITE)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(pedido.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 401) {
            throw new IllegalStateException("O Traccar recusou as credenciais.");
        }
        if (r.statusCode() != 200) {
            throw new IllegalStateException("O Traccar respondeu " + r.statusCode() + ".");
        }
        return json.readTree(r.body());
    }

    private static String explicar(Exception e) {
        String m = String.valueOf(e.getMessage());
        if (e instanceof java.net.ConnectException || m.contains("Connection refused")) {
            return "Não foi possível contactar o Traccar no endereço indicado.";
        }
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "O Traccar não respondeu a tempo.";
        }
        if (m.contains("credenciais") || m.contains("respondeu")) {
            return m;
        }
        return "A sondagem falhou. Verifique o endereço e as credenciais.";
    }

    static String sha256(String v) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
