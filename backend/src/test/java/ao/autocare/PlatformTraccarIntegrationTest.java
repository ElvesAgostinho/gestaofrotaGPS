package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O Traccar e o motor de rotas DA PLATAFORMA ao serviço das empresas clientes.
 *
 * <p>Um Traccar a sério é levantado aqui (a API documentada: utilizadores,
 * tokens, aparelhos por conta), com as regras que importam: cada conta só vê
 * os seus aparelhos e um IMEI só pode existir uma vez. É contra isso que se
 * prova que criar uma empresa lhe dá conta, token e aparelhos sem ninguém
 * copiar nada à mão — e que uma empresa nunca vê os aparelhos de outra.
 */
@TestPropertySource(properties = {
        "autocare.traccar.url=http://127.0.0.1:47392",
        "autocare.traccar.user=admin@plataforma.ao",
        "autocare.traccar.password=segredo-admin",
        "autocare.routing.url=http://127.0.0.1:47392"
})
class PlatformTraccarIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "admin@plataforma.ao:segredo-admin".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private JdbcTemplate jdbc;

    private HttpServer traccar;
    /** Utilizadores do Traccar: email → {id, password}. */
    private final Map<String, Map<String, Object>> utilizadores = new HashMap<>();
    /** Aparelhos: uniqueId → {id, email do dono, name}. */
    private final Map<String, Map<String, Object>> aparelhos = new HashMap<>();
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private int proximoId = 10;

    private String admin;

    private JsonNode send(String bearer, MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private String login(String email, String password) throws Exception {
        return "Bearer " + send(null, post("/api/v1/auth/login"),
                Map.of("identifier", email, "password", password), 200).get("accessToken").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        Session a = register("plataforma@teste.ao", "Plataforma");
        jdbc.update("update users set is_admin = true where id = ?", a.userId());
        admin = a.bearer();

        traccar = HttpServer.create(new InetSocketAddress("127.0.0.1", 47392), 0);
        traccar.createContext("/", this::atender);
        traccar.start();
    }

    @AfterEach
    void tearDown() {
        traccar.stop(0);
    }

    // ---- o Traccar de brincar, com as regras do verdadeiro ------------------

    private synchronized void atender(HttpExchange t) throws java.io.IOException {
        String path = t.getRequestURI().getPath();
        String query = t.getRequestURI().getQuery();
        String metodo = t.getRequestMethod();
        String auth = t.getRequestHeaders().getFirst("Authorization");
        String corpo = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        pedidos.add(metodo + " " + path + (query != null ? "?" + query : ""));
        try {
            if (path.startsWith("/route/v1/driving/")) {
                responder(t, 200, "{\"code\":\"Ok\",\"routes\":[{\"distance\":497300.0,\"duration\":21600.0}]}");
                return;
            }
            if (path.equals("/api/server")) {
                responder(t, 200, "{\"version\":\"6.6\"}");
                return;
            }
            if (path.equals("/api/users") && metodo.equals("GET")) {
                exigirAdmin(auth);
                List<Map<String, Object>> lista = new ArrayList<>();
                utilizadores.forEach((email, u) -> lista.add(Map.of("id", u.get("id"), "email", email, "name", "x")));
                responder(t, 200, json.writeValueAsString(lista));
                return;
            }
            if (path.equals("/api/users") && metodo.equals("POST")) {
                exigirAdmin(auth);
                JsonNode u = json.readTree(corpo);
                String email = u.get("email").asText();
                if (utilizadores.containsKey(email)) {
                    responder(t, 400, "Duplicate entry");
                    return;
                }
                Map<String, Object> novo = new HashMap<>();
                novo.put("id", proximoId++);
                novo.put("password", u.get("password").asText());
                utilizadores.put(email, novo);
                responder(t, 200, "{\"id\":" + novo.get("id") + ",\"email\":\"" + email + "\"}");
                return;
            }
            if (path.startsWith("/api/users/") && metodo.equals("PUT")) {
                exigirAdmin(auth);
                JsonNode u = json.readTree(corpo);
                Map<String, Object> existente = utilizadores.get(u.get("email").asText());
                existente.put("password", u.get("password").asText());
                responder(t, 200, corpo);
                return;
            }
            if (path.equals("/api/session/token") && metodo.equals("POST")) {
                String email = emailDoBasic(auth);
                if (email == null) {
                    responder(t, 401, "");
                    return;
                }
                responder(t, 200, "TOKEN-" + email);
                return;
            }
            if (path.startsWith("/api/devices")) {
                String dono = donoDoToken(auth);
                if (dono == null) {
                    responder(t, 401, "");
                    return;
                }
                if (metodo.equals("GET")) {
                    String filtro = query != null && query.startsWith("uniqueId=")
                            ? java.net.URLDecoder.decode(query.substring(9), StandardCharsets.UTF_8) : null;
                    List<Map<String, Object>> meus = new ArrayList<>();
                    aparelhos.forEach((uid, d) -> {
                        if (dono.equals(d.get("email")) && (filtro == null || filtro.equals(uid))) {
                            meus.add(Map.of("id", d.get("id"), "uniqueId", uid, "name", d.get("name"),
                                    "status", "offline", "protocol", "gt06"));
                        }
                    });
                    responder(t, 200, json.writeValueAsString(meus));
                    return;
                }
                if (metodo.equals("POST")) {
                    JsonNode d = json.readTree(corpo);
                    String uid = d.get("uniqueId").asText();
                    if (aparelhos.containsKey(uid)) {
                        // Como o Traccar real: o IMEI é único no servidor inteiro.
                        responder(t, 400, "Duplicate entry '" + uid + "' for key 'tc_devices.uniqueid'");
                        return;
                    }
                    Map<String, Object> novo = new HashMap<>();
                    novo.put("id", proximoId++);
                    novo.put("email", dono);
                    novo.put("name", d.path("name").asText(uid));
                    aparelhos.put(uid, novo);
                    responder(t, 200, "{\"id\":" + novo.get("id") + ",\"uniqueId\":\"" + uid + "\"}");
                    return;
                }
                if (metodo.equals("DELETE")) {
                    long id = Long.parseLong(path.substring("/api/devices/".length()));
                    aparelhos.entrySet().removeIf(e -> ((Integer) e.getValue().get("id")) == id
                            && dono.equals(e.getValue().get("email")));
                    responder(t, 204, "");
                    return;
                }
            }
            responder(t, 404, "");
        } catch (SecurityException e) {
            responder(t, 401, "");
        } catch (Exception e) {
            responder(t, 500, e.toString());
        }
    }

    private void exigirAdmin(String auth) {
        if (!ADMIN_BASIC.equals(auth)) {
            throw new SecurityException("não é o administrador");
        }
    }

    private String emailDoBasic(String auth) {
        if (auth == null || !auth.startsWith("Basic ")) {
            return null;
        }
        String par = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
        String email = par.substring(0, par.indexOf(':'));
        String senha = par.substring(par.indexOf(':') + 1);
        Map<String, Object> u = utilizadores.get(email);
        return u != null && senha.equals(u.get("password")) ? email : null;
    }

    private String donoDoToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer TOKEN-")) {
            return null;
        }
        String email = auth.substring("Bearer TOKEN-".length());
        return utilizadores.containsKey(email) ? email : null;
    }

    private static void responder(HttpExchange t, int codigo, String corpo) throws java.io.IOException {
        byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", codigo == 200 && corpo.startsWith("{") || corpo.startsWith("[")
                ? "application/json" : "text/plain");
        t.sendResponseHeaders(codigo, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            try (OutputStream out = t.getResponseBody()) {
                out.write(b);
            }
        }
        t.close();
    }

    // ---- helpers ----------------------------------------------------------

    private JsonNode criarEmpresa(String nome, String emailDono) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", nome);
        body.put("ownerName", "Dono " + nome);
        body.put("ownerEmail", emailDono);
        body.put("ownerPassword", "palavraForte1");
        return send(admin, post("/api/v1/admin/platform/organizations"), body, 201);
    }

    private String ativo(String bearer, String tag) throws Exception {
        String tipo = send(bearer, post("/api/v1/asset-types"), Map.of("name", "Camião " + tag), 201)
                .get("id").asText();
        return send(bearer, post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Camião " + tag, "assetTypeId", tipo), 201).get("id").asText();
    }

    // ---- testes -----------------------------------------------------------

    @Test
    void criarEmpresaDaLheContaNoTraccarEOsAparelhosNascemLa() throws Exception {
        JsonNode criada = criarEmpresa("Transportes Kwanza", "dona@kwanza.ao");
        assertThat(criada.get("traccar").get("traccarUser").asText()).startsWith("empresa-")
                .endsWith("@imbondeiro.local");
        assertThat(criada.get("traccar").get("existed").asBoolean()).isFalse();
        assertThat(criada.has("traccarError")).isFalse();
        assertThat(criada.get("organization").get("traccarConfigured").asBoolean()).isTrue();
        assertThat(utilizadores).hasSize(1);

        // A empresa vê o Traccar já configurado e testado — sem tocar em nada.
        String dona = login("dona@kwanza.ao", "palavraForte1");
        JsonNode cfg = send(dona, get("/api/v1/integrations"), null, 200);
        assertThat(cfg.get("traccarConfigured").asBoolean()).isTrue();
        assertThat(cfg.get("traccarOk").asBoolean()).isTrue();
        assertThat(cfg.get("traccarTokenSet").asBoolean()).isTrue();
        assertThat(cfg.get("traccarUrl").asText()).isEqualTo("http://127.0.0.1:47392");
        assertThat(cfg.get("traccarPollEnabled").asBoolean()).isTrue();
        assertThat(cfg.get("traccarPlatformAvailable").asBoolean()).isTrue();
        // e o teste real da ligação passa com o token dela
        JsonNode teste = send(dona, post("/api/v1/integrations/traccar/test"), null, 200);
        assertThat(teste.get("ok").asBoolean()).isTrue();

        // O servidor de comandos (bloqueio) é o mesmo — o da empresa, não um do ambiente.
        JsonNode saude = send(dona, get("/api/v1/telemetry/traccar/status"), null, 200);
        assertThat(saude.get("configured").asBoolean()).isTrue();
        assertThat(saude.get("reachable").asBoolean()).isTrue();
        assertThat(saude.get("version").asText()).isEqualTo("6.6");

        // Registar o rastreador aqui cria-o no Traccar, na conta dela.
        String camiao = ativo(dona, "KW-01");
        JsonNode reg = send(dona, post("/api/v1/gps-devices"),
                Map.of("externalId", "358000000000001", "assetId", camiao), 201);
        assertThat(reg.get("traccarNote").asText()).contains("Registado também no Traccar");
        assertThat(aparelhos).containsKey("358000000000001");
        assertThat(aparelhos.get("358000000000001").get("email").toString()).startsWith("empresa-");
        assertThat(aparelhos.get("358000000000001").get("name")).isEqualTo("KW-01");

        // Apagar aqui apaga lá.
        String deviceId = reg.get("device").get("id").asText();
        send(dona, delete("/api/v1/gps-devices/" + deviceId), null, 200);
        assertThat(aparelhos).doesNotContainKey("358000000000001");
    }

    @Test
    void duasEmpresasNuncaVeemOsAparelhosUmaDaOutra() throws Exception {
        criarEmpresa("Empresa A", "a@a.ao");
        criarEmpresa("Empresa B", "b@b.ao");
        String a = login("a@a.ao", "palavraForte1");
        String b = login("b@b.ao", "palavraForte1");
        assertThat(utilizadores).hasSize(2);

        String camA = ativo(a, "A-01");
        send(a, post("/api/v1/gps-devices"), Map.of("externalId", "111111111111111", "assetId", camA), 201);

        // B tenta o mesmo IMEI: o Traccar recusa (é único), e a nota diz-lhe o que fazer.
        String camB = ativo(b, "B-01");
        JsonNode regB = send(b, post("/api/v1/gps-devices"),
                Map.of("externalId", "111111111111111", "assetId", camB), 201);
        assertThat(regB.get("traccarNote").asText()).contains("Já existia").contains("fornecedor");
        // A sincronização do aparelho no Traccar só encontra o de A na conta de A.
        // (a conta de B não tem esse aparelho: o Traccar responde lista vazia)
        String idB = regB.get("device").get("id").asText();
        JsonNode erro = send(b, post("/api/v1/gps-devices/" + idB + "/sync"), null, 404);
        assertThat(erro.get("message").asText()).contains("não existe");
        JsonNode aparelhosA = send(a, get("/api/v1/gps-devices"), null, 200);
        String idA = aparelhosA.isArray() ? aparelhosA.get(0).get("id").asText()
                : aparelhosA.get("content").get(0).get("id").asText();
        JsonNode sincA = send(a, post("/api/v1/gps-devices/" + idA + "/sync"), null, 200);
        assertThat(sincA.get("protocol").asText()).isEqualTo("gt06");
    }

    @Test
    void empresaSemTraccarNaoBloqueiaNemFingeQueEnviou() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Sem GPS Lda");
        body.put("ownerName", "Dono");
        body.put("ownerEmail", "sem@gps.ao");
        body.put("ownerPassword", "palavraForte1");
        body.put("provisionTraccar", false);
        JsonNode criada = send(admin, post("/api/v1/admin/platform/organizations"), body, 201);
        assertThat(criada.has("traccar")).isFalse();
        assertThat(criada.get("organization").get("traccarConfigured").asBoolean()).isFalse();

        String dono = login("sem@gps.ao", "palavraForte1");
        JsonNode saude = send(dono, get("/api/v1/telemetry/traccar/status"), null, 200);
        assertThat(saude.get("configured").asBoolean()).isFalse();
        assertThat(saude.get("failureReason").asText()).contains("Configurações");

        // Registar o aparelho funciona na mesma; a nota diz que falta o Traccar.
        String cam = ativo(dono, "SG-01");
        JsonNode reg = send(dono, post("/api/v1/gps-devices"),
                Map.of("externalId", "222222222222222", "assetId", cam), 201);
        assertThat(reg.get("traccarNote").asText()).contains("Sem servidor Traccar");
        assertThat(aparelhos).doesNotContainKey("222222222222222");

        // E a plataforma pode dar-lhe a conta depois, com um botão.
        String id = criada.get("organization").get("id").asText();
        JsonNode conta = send(admin, post("/api/v1/admin/platform/organizations/" + id + "/traccar"), null, 200);
        assertThat(conta.get("traccarUser").asText()).startsWith("empresa-");
        assertThat(send(dono, get("/api/v1/telemetry/traccar/status"), null, 200)
                .get("configured").asBoolean()).isTrue();

        // Repetir renova o token sem duplicar o utilizador.
        JsonNode outra = send(admin, post("/api/v1/admin/platform/organizations/" + id + "/traccar"), null, 200);
        assertThat(outra.get("existed").asBoolean()).isTrue();
        assertThat(utilizadores).hasSize(1);
    }

    @Test
    void oMotorDeRotasDaPlataformaServeTodasAsEmpresasSemConfigurarNada() throws Exception {
        criarEmpresa("Rotas Lda", "rotas@r.ao");
        String dona = login("rotas@r.ao", "palavraForte1");

        JsonNode cfg = send(dona, get("/api/v1/integrations"), null, 200);
        assertThat(cfg.get("routingConfigured").asBoolean()).isFalse();
        assertThat(cfg.get("routingPlatformUrl").asText()).isEqualTo("http://127.0.0.1:47392");

        // Testar sem endereço próprio testa o da plataforma.
        assertThat(send(dona, post("/api/v1/integrations/routing/test"), null, 200)
                .get("ok").asBoolean()).isTrue();

        // E calcular uma rota vai pelas estradas, não em linha reta.
        Map<String, Object> l1 = new HashMap<>();
        l1.put("name", "Luanda"); l1.put("kind", "BRANCH"); l1.put("latitude", "-8.8383"); l1.put("longitude", "13.2344");
        Map<String, Object> l2 = new HashMap<>();
        l2.put("name", "Lobito"); l2.put("kind", "BRANCH"); l2.put("latitude", "-12.3644"); l2.put("longitude", "13.5456");
        String a = send(dona, post("/api/v1/locations"), l1, 201).get("id").asText();
        String b = send(dona, post("/api/v1/locations"), l2, 201).get("id").asText();
        JsonNode r = send(dona, post("/api/v1/routes/calculate"),
                Map.of("points", List.of(Map.of("locationId", a), Map.of("locationId", b))), 200);
        assertThat(r.get("source").asText()).isEqualTo("ENGINE");
        assertThat(r.get("distanceKm").asDouble()).isEqualTo(497.3);

        JsonNode resumo = send(admin, get("/api/v1/admin/platform/summary"), null, 200);
        assertThat(resumo.get("platformTraccarUrl").asText()).isEqualTo("http://127.0.0.1:47392");
        assertThat(resumo.get("platformRoutingUrl").asText()).isEqualTo("http://127.0.0.1:47392");
    }

    @Test
    void tokenErradoNaoPassaNoTeste() throws Exception {
        criarEmpresa("Errada Lda", "errada@e.ao");
        String dona = login("errada@e.ao", "palavraForte1");
        // A empresa troca o token por um inventado: o teste tem de reprovar.
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("url", "http://127.0.0.1:47392");
        cfg.put("token", "inventado");
        send(dona, put("/api/v1/integrations/traccar"), cfg, 200);
        JsonNode teste = send(dona, post("/api/v1/integrations/traccar/test"), null, 200);
        assertThat(teste.get("ok").asBoolean()).isFalse();
        assertThat(teste.get("message").asText()).contains("recusou");
        JsonNode saude = send(dona, get("/api/v1/telemetry/traccar/status"), null, 200);
        assertThat(saude.get("reachable").asBoolean()).isFalse();
    }
}
