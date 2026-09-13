package ao.autocare.modules.integration;

import ao.autocare.common.ApiException;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.domain.Organization;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.IntegrationSettingsRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.security.SecretBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * O Traccar da plataforma, ao serviço das empresas clientes.
 *
 * <p>Quem vende o sistema tem um Traccar; cada empresa cliente precisa de uma
 * conta lá dentro (para que só veja os seus aparelhos) e de um token guardado
 * nas suas Configurações. Fazer isso à mão por cada cliente — criar o
 * utilizador, gerar o token, copiá-lo para o sítio certo — era o passo em que
 * tudo se enganava. Esta classe faz-o pela API do Traccar, com as credenciais
 * de administrador do ambiente ({@code TRACCAR_URL}, {@code TRACCAR_USER},
 * {@code TRACCAR_PASSWORD}).
 *
 * <p>Também regista os aparelhos: quando a empresa cadastra um rastreador aqui
 * (IMEI), ele passa a existir no Traccar na conta dela, sem ninguém ter de o
 * cadastrar duas vezes.
 *
 * <p>Provado contra um Traccar 6.6 real: POST /api/users (admin), POST
 * /api/session/token (como o utilizador novo), POST /api/devices (com o token).
 */
@Component
public class TraccarAccounts {

    private static final Logger log = LoggerFactory.getLogger(TraccarAccounts.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(12);
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final IntegrationSettingsRepository settings;
    private final OrganizationRepository organizations;
    private final SecretBox cofre;
    private final AuditService audit;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TEMPO_LIMITE).build();

    private final String url;
    private final String adminUser;
    private final String adminPassword;

    public TraccarAccounts(
            IntegrationSettingsRepository settings,
            OrganizationRepository organizations,
            SecretBox cofre,
            AuditService audit,
            ObjectMapper json,
            @Value("${autocare.traccar.url:}") String url,
            @Value("${autocare.traccar.user:}") String adminUser,
            @Value("${autocare.traccar.password:}") String adminPassword) {
        this.settings = settings;
        this.organizations = organizations;
        this.cofre = cofre;
        this.audit = audit;
        this.json = json;
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.adminUser = adminUser == null ? "" : adminUser.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    /** Há um Traccar da plataforma com credenciais de administrador? */
    public boolean disponivel() {
        return !url.isBlank() && !adminUser.isBlank() && !adminPassword.isBlank();
    }

    public String url() {
        return url;
    }

    /** O que foi criado, para mostrar uma vez ao administrador. */
    public record Conta(String traccarUrl, String traccarUser, boolean jaExistia) {}

    /**
     * Cria (ou reaproveita) a conta da empresa no Traccar da plataforma e
     * guarda o endereço e o token nas Configurações dela. A palavra-passe da
     * conta é aleatória e não fica guardada: tudo fala por token.
     */
    @Transactional
    public Conta provision(String orgId, String adminId) {
        if (!disponivel()) {
            throw ApiException.conflict("A plataforma não tem um servidor Traccar configurado "
                    + "(TRACCAR_URL, TRACCAR_USER e TRACCAR_PASSWORD no ambiente da API).");
        }
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
        IntegrationSettings s = settings.findByOrganizationId(orgId).orElseGet(() -> {
            IntegrationSettings novo = new IntegrationSettings();
            novo.setOrganization(org);
            return settings.save(novo);
        });

        String email = "empresa-" + orgId.substring(0, 8) + "@imbondeiro.local";
        String password = gerar(20);
        boolean existia = false;

        try {
            // 1. O utilizador. Se já existir (provisionado antes), fica; muda-se-lhe
            //    a palavra-passe para se poder gerar um token novo.
            JsonNode existente = procurarUtilizador(email);
            long userId;
            if (existente != null) {
                existia = true;
                userId = existente.get("id").asLong();
                var corpo = ((com.fasterxml.jackson.databind.node.ObjectNode) existente)
                        .put("password", password);
                HttpResponse<String> r = enviar(admin(HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/users/" + userId))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))));
                exigir(r, "atualizar o utilizador da empresa");
            } else {
                Map<String, Object> corpo = new java.util.LinkedHashMap<>();
                corpo.put("name", org.getName());
                corpo.put("email", email);
                corpo.put("password", password);
                corpo.put("readonly", false);
                corpo.put("administrator", false);
                corpo.put("deviceLimit", -1);
                corpo.put("userLimit", 0);
                HttpResponse<String> r = enviar(admin(HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/users"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))));
                exigir(r, "criar o utilizador da empresa");
                userId = json.readTree(r.body()).get("id").asLong();
            }

            // 2. Um token dessa conta, com dez anos: é ele que fica guardado.
            String validade = LocalDate.now().plusYears(10) + "T00:00:00.000Z";
            HttpResponse<String> t = enviar(basic(email, password, HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/session/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "expiration=" + URLEncoder.encode(validade, StandardCharsets.UTF_8)))));
            exigir(t, "gerar o token da empresa");
            String token = t.body().trim();
            if (token.isBlank()) {
                throw new IllegalStateException("o Traccar devolveu um token vazio");
            }

            // 3. Guardar nas Configurações da empresa, já testado.
            s.setTraccarUrl(url);
            s.setTraccarUser(email);
            s.setTraccarPasswordEnc(null);
            s.setTraccarTokenEnc(cofre.encrypt(token));
            s.setTraccarOk(true);
            s.setTraccarCheckedAt(Instant.now());
            s.setTraccarLastError(null);
            s.setTraccarPollEnabled(true);
            settings.save(s);

            audit.record(orgId, adminId, "platform.traccar.provision", "Organization", orgId,
                    "Conta no Traccar da plataforma: " + email + " (id " + userId + ")"
                            + (existia ? " · já existia, token renovado" : ""));
            log.info("[PLATAFORMA] Traccar provisionado para a empresa {} ({})", org.getName(), email);
            return new Conta(url, email, existia);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha a provisionar o Traccar da empresa {}: {}", orgId, e.toString());
            throw ApiException.conflict("Não foi possível criar a conta no Traccar da plataforma: "
                    + e.getMessage());
        }
    }

    /**
     * Regista o aparelho no Traccar da empresa (na conta dela), se ela tiver
     * Traccar configurado. Devolve uma frase para o ecrã; nunca lança — o
     * aparelho fica sempre registado aqui, e a frase diz o que falta.
     */
    public String registarAparelho(String orgId, String externalId, String nome) {
        IntegrationSettings s = settings.findByOrganizationId(orgId).orElse(null);
        if (s == null || !s.hasTraccar()) {
            return "Sem servidor Traccar configurado: registe o aparelho também no Traccar, "
                    + "com este mesmo IMEI.";
        }
        String token = cofre.decrypt(s.getTraccarTokenEnc());
        String senha = cofre.decrypt(s.getTraccarPasswordEnc());
        try {
            HttpRequest.Builder pedido = HttpRequest.newBuilder()
                    .uri(URI.create(s.getTraccarUrl() + "/api/devices"))
                    .timeout(TEMPO_LIMITE)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                            "name", nome != null && !nome.isBlank() ? nome : externalId,
                            "uniqueId", externalId))));
            if (token != null) {
                pedido.header("Authorization", "Bearer " + token);
            } else if (s.getTraccarUser() != null && senha != null) {
                pedido.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (s.getTraccarUser() + ":" + senha).getBytes(StandardCharsets.UTF_8)));
            } else {
                return "As credenciais do Traccar estão incompletas: registe o aparelho no Traccar à mão.";
            }
            HttpResponse<String> r = enviar(pedido);
            if (r.statusCode() / 100 == 2) {
                return "Registado também no Traccar (" + s.getTraccarUrl() + ").";
            }
            // 400 com «unique»/«duplicate»: já lá estava, com este IMEI. É o resultado desejado.
            String corpo = r.body() == null ? "" : r.body().toLowerCase();
            if (r.statusCode() == 400 && (corpo.contains("unique") || corpo.contains("duplicate"))) {
                return "Já existia no Traccar com este IMEI. Se não foi a sua empresa que o registou, "
                        + "contacte o fornecedor: dois clientes não podem ter o mesmo aparelho.";
            }
            if (r.statusCode() == 400 || r.statusCode() == 401 || r.statusCode() == 403) {
                return "O Traccar recusou as credenciais guardadas em Configurações → Servidor Traccar: "
                        + "corrija o token (ou peça ao fornecedor para renovar o acesso) e registe o "
                        + "aparelho lá com este IMEI.";
            }
            return "O Traccar respondeu HTTP " + r.statusCode() + ": registe o aparelho lá à mão "
                    + "com este IMEI.";
        } catch (Exception e) {
            log.warn("Falha a registar o aparelho {} no Traccar da empresa {}: {}",
                    externalId, orgId, e.toString());
            return "Não foi possível contactar o Traccar agora: registe o aparelho lá à mão "
                    + "com este IMEI.";
        }
    }

    /** Apaga o aparelho no Traccar da empresa, se lá estiver. Silencioso: é limpeza. */
    public void apagarAparelho(String orgId, String externalId) {
        IntegrationSettings s = settings.findByOrganizationId(orgId).orElse(null);
        if (s == null || !s.hasTraccar() || s.getTraccarTokenEnc() == null) {
            return;
        }
        String token = cofre.decrypt(s.getTraccarTokenEnc());
        try {
            HttpResponse<String> lista = enviar(HttpRequest.newBuilder()
                    .uri(URI.create(s.getTraccarUrl() + "/api/devices?uniqueId="
                            + URLEncoder.encode(externalId, StandardCharsets.UTF_8)))
                    .header("Authorization", "Bearer " + token).GET());
            if (lista.statusCode() != 200) {
                return;
            }
            JsonNode arr = json.readTree(lista.body());
            if (arr.isArray() && !arr.isEmpty()) {
                enviar(HttpRequest.newBuilder()
                        .uri(URI.create(s.getTraccarUrl() + "/api/devices/" + arr.get(0).get("id").asLong()))
                        .header("Authorization", "Bearer " + token).DELETE());
            }
        } catch (Exception e) {
            log.debug("Não foi possível apagar {} no Traccar: {}", externalId, e.toString());
        }
    }

    // -----------------------------------------------------------------------

    private JsonNode procurarUtilizador(String email) throws Exception {
        HttpResponse<String> r = enviar(admin(HttpRequest.newBuilder()
                .uri(URI.create(url + "/api/users")).GET()));
        exigir(r, "listar os utilizadores");
        for (JsonNode u : json.readTree(r.body())) {
            if (email.equalsIgnoreCase(u.path("email").asText(""))) {
                return u;
            }
        }
        return null;
    }

    private HttpRequest.Builder admin(HttpRequest.Builder b) {
        return basic(adminUser, adminPassword, b);
    }

    private static HttpRequest.Builder basic(String user, String password, HttpRequest.Builder b) {
        return b.timeout(TEMPO_LIMITE).header("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> enviar(HttpRequest.Builder b) throws Exception {
        return http.send(b.timeout(TEMPO_LIMITE).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void exigir(HttpResponse<String> r, String acao) {
        if (r.statusCode() / 100 != 2) {
            String corpo = r.body() == null ? "" : r.body();
            throw new IllegalStateException("não foi possível " + acao + " (HTTP " + r.statusCode()
                    + (corpo.isBlank() ? ")" : "): " + corpo.substring(0, Math.min(160, corpo.length()))));
        }
    }

    private String gerar(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Para o ecrã da plataforma: o Traccar existe e, se sim, onde. */
    public Optional<String> urlPublica() {
        return disponivel() ? Optional.of(url) : Optional.empty();
    }
}
