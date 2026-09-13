package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O ecrã «Plataforma»: o dono do sistema cria as empresas clientes, dá-lhes
 * prazo e suspende-as. Um Dono de empresa não entra aqui.
 */
class PlatformIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;
    private String donoEmpresa;

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
        JsonNode res = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", email, "password", password), 200);
        return "Bearer " + res.get("accessToken").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        Session a = register("plataforma@teste.ao", "Plataforma");
        jdbc.update("update users set is_admin = true where id = ?", a.userId());
        admin = a.bearer();
        donoEmpresa = register("dono@cliente.ao", "Cliente Lda").bearer();
    }

    @Test
    void donoDeEmpresaNaoEntraNaPlataforma() throws Exception {
        send(donoEmpresa, get("/api/v1/admin/platform/organizations"), null, 403);
        send(donoEmpresa, get("/api/v1/admin/platform/summary"), null, 403);
        send(null, get("/api/v1/admin/platform/organizations"), null, 401);
    }

    @Test
    void adminVeTodasAsEmpresasComDonoEContagens() throws Exception {
        JsonNode lista = send(admin, get("/api/v1/admin/platform/organizations"), null, 200)
                .get("items");
        assertThat(lista).hasSize(2);
        JsonNode cliente = null;
        for (JsonNode o : lista) {
            if ("Cliente Lda".equals(o.get("name").asText())) {
                cliente = o;
            }
        }
        assertThat(cliente).isNotNull();
        assertThat(cliente.get("ownerEmail").asText()).isEqualTo("dono@cliente.ao");
        assertThat(cliente.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(cliente.get("memberCount").asLong()).isEqualTo(1);
        assertThat(cliente.get("assetCount").asLong()).isEqualTo(0);

        JsonNode resumo = send(admin, get("/api/v1/admin/platform/summary"), null, 200);
        assertThat(resumo.get("organizations").asLong()).isEqualTo(2);
        assertThat(resumo.get("active").asLong()).isEqualTo(2);
    }

    @Test
    void criarEmpresaGeraDonoComPalavraPasseTemporariaQueFunciona() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Transportes do Sul");
        body.put("taxId", "5417000000");
        body.put("city", "Lubango");
        body.put("ownerName", "Maria Chipenda");
        body.put("ownerEmail", "Maria@TransSul.ao");
        body.put("licenseUntil", LocalDate.now().plusYears(1).toString());
        body.put("platformNotes", "Contrato anual, 40 viaturas");

        JsonNode criada = send(admin, post("/api/v1/admin/platform/organizations"), body, 201);
        assertThat(criada.get("ownerExisted").asBoolean()).isFalse();
        String temporaria = criada.get("temporaryPassword").asText();
        assertThat(temporaria).hasSize(12);
        assertThat(criada.get("organization").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(criada.get("organization").get("ownerEmail").asText())
                .isEqualTo("maria@transsul.ao");

        // A Dona entra com a palavra-passe temporária e vê a sua empresa, não outra.
        String dona = login("maria@transsul.ao", temporaria);
        JsonNode org = send(dona, get("/api/v1/organization"), null, 200);
        assertThat(org.get("name").asText()).isEqualTo("Transportes do Sul");
        assertThat(org.get("myRole").asText()).isEqualTo("OWNER");
        assertThat(org.has("blockedReason")).isFalse();
        assertThat(org.get("licenseUntil").asText()).isEqualTo(LocalDate.now().plusYears(1).toString());

        // As notas da plataforma nunca saem para a empresa.
        assertThat(org.has("platformNotes")).isFalse();
    }

    @Test
    void criarEmpresaComPalavraPasseEscolhidaNaoDevolveTemporaria() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Obras Norte");
        body.put("ownerName", "Pedro");
        body.put("ownerEmail", "pedro@obrasnorte.ao");
        body.put("ownerPassword", "EscolhidaPeloAdmin9");
        JsonNode criada = send(admin, post("/api/v1/admin/platform/organizations"), body, 201);
        assertThat(criada.has("temporaryPassword")).isFalse();
        login("pedro@obrasnorte.ao", "EscolhidaPeloAdmin9");
    }

    @Test
    void emailJaComContaFicaDonoSemMexerNaPalavraPasse() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Segunda Empresa");
        body.put("ownerName", "Ignorado");
        body.put("ownerEmail", "dono@cliente.ao");
        JsonNode criada = send(admin, post("/api/v1/admin/platform/organizations"), body, 201);
        assertThat(criada.get("ownerExisted").asBoolean()).isTrue();
        assertThat(criada.has("temporaryPassword")).isFalse();
        // A palavra-passe antiga continua a servir.
        login("dono@cliente.ao", "palavraForte1");
    }

    @Test
    void suspenderTravaOsUtilizadoresMasDeixaVerOMotivo() throws Exception {
        String id = idDe("Cliente Lda");

        JsonNode s = send(admin, post("/api/v1/admin/platform/organizations/" + id + "/suspend"),
                Map.of("reason", "Fatura de Agosto em atraso"), 200);
        assertThat(s.get("status").asText()).isEqualTo("SUSPENDED");

        // O Dono continua a conseguir entrar e a ler o aviso...
        JsonNode org = send(donoEmpresa, get("/api/v1/organization"), null, 200);
        assertThat(org.get("blockedReason").asText())
                .contains("suspensa").contains("Fatura de Agosto em atraso");
        send(donoEmpresa, get("/api/v1/auth/me"), null, 200);

        // ...mas não trabalha: nem ler nem escrever.
        JsonNode erro = send(donoEmpresa, get("/api/v1/assets"), null, 403);
        assertThat(erro.get("message").asText()).contains("suspensa");
        send(donoEmpresa, post("/api/v1/assets"), Map.of("name", "X", "tag", "X-1"), 403);

        // Reativar devolve tudo.
        send(admin, post("/api/v1/admin/platform/organizations/" + id + "/activate"), null, 200);
        send(donoEmpresa, get("/api/v1/assets"), null, 200);
        assertThat(send(donoEmpresa, get("/api/v1/organization"), null, 200)
                .has("blockedReason")).isFalse();
    }

    @Test
    void licencaVencidaTravaEAvencerAvisa() throws Exception {
        String id = idDe("Cliente Lda");

        // Vence daqui a 10 dias: ainda trabalha, mas a plataforma vê «a vencer».
        JsonNode r = send(admin, put("/api/v1/admin/platform/organizations/" + id),
                Map.of("licenseUntil", LocalDate.now().plusDays(10).toString()), 200);
        assertThat(r.get("status").asText()).isEqualTo("EXPIRING");
        send(donoEmpresa, get("/api/v1/assets"), null, 200);

        // Venceu ontem: travado.
        r = send(admin, put("/api/v1/admin/platform/organizations/" + id),
                Map.of("licenseUntil", LocalDate.now().minusDays(1).toString()), 200);
        assertThat(r.get("status").asText()).isEqualTo("EXPIRED");
        JsonNode erro = send(donoEmpresa, get("/api/v1/assets"), null, 403);
        assertThat(erro.get("message").asText()).contains("licença");

        // Sem prazo: volta a trabalhar.
        r = send(admin, put("/api/v1/admin/platform/organizations/" + id),
                Map.of("clearLicense", true), 200);
        assertThat(r.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(r.has("licenseUntil")).isFalse();
        send(donoEmpresa, get("/api/v1/assets"), null, 200);
    }

    @Test
    void reporPalavraPasseDoDono() throws Exception {
        String id = idDe("Cliente Lda");
        JsonNode r = send(admin, post("/api/v1/admin/platform/organizations/" + id + "/owner-password"),
                null, 200);
        assertThat(r.get("ownerEmail").asText()).isEqualTo("dono@cliente.ao");
        String nova = r.get("temporaryPassword").asText();
        login("dono@cliente.ao", nova);
        send(null, post("/api/v1/auth/login"),
                Map.of("identifier", "dono@cliente.ao", "password", "palavraForte1"), 401);
    }

    @Test
    void oAdminNaoETravadoPelaSuspensaoDaSuaPropriaEmpresa() throws Exception {
        // O admin de teste tem uma empresa («Plataforma»); suspendê-la não o pode fechar fora.
        String id = idDe("Plataforma");
        send(admin, post("/api/v1/admin/platform/organizations/" + id + "/suspend"), null, 200);
        send(admin, get("/api/v1/admin/platform/organizations"), null, 200);
        send(admin, post("/api/v1/admin/platform/organizations/" + id + "/activate"), null, 200);
    }

    @Test
    void marcaBrancaPeloDominioDaEmpresa() throws Exception {
        String id = idDe("Cliente Lda");
        JsonNode r = send(admin, put("/api/v1/admin/platform/organizations/" + id),
                Map.of("customDomain", "https://frota.cliente.ao/", "brandName", "Cliente Frota", "brandColor", "#1E88E5"), 200);
        assertThat(r.get("customDomain").asText()).isEqualTo("frota.cliente.ao");

        // Pelo domínio geral: sem marca. Pelo domínio da empresa: a marca dela.
        assertThat(send(null, get("/api/v1/config"), null, 200).has("brand")).isFalse();
        JsonNode cfg = json.readTree(mvc.perform(get("/api/v1/config").header("Host", "frota.cliente.ao"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(cfg.get("brand").get("name").asText()).isEqualTo("Cliente Frota");
        assertThat(cfg.get("brand").get("color").asText()).isEqualTo("#1E88E5");
        // atrás de um proxy, o domínio vem em X-Forwarded-Host
        cfg = json.readTree(mvc.perform(get("/api/v1/config").header("Host", "api-interna")
                        .header("X-Forwarded-Host", "FROTA.cliente.ao:443"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(cfg.get("brand").get("name").asText()).isEqualTo("Cliente Frota");

        // Domínio inválido e domínio repetido
        send(admin, put("/api/v1/admin/platform/organizations/" + id), Map.of("customDomain", "sem ponto"), 400);
        String outra = idDe("Plataforma");
        send(admin, put("/api/v1/admin/platform/organizations/" + outra), Map.of("customDomain", "frota.cliente.ao"), 409);
        send(admin, put("/api/v1/admin/platform/organizations/" + id), Map.of("brandColor", "azul"), 400);

        // Retirar o domínio
        send(admin, put("/api/v1/admin/platform/organizations/" + id), Map.of("customDomain", ""), 200);
        assertThat(json.readTree(mvc.perform(get("/api/v1/config").header("Host", "frota.cliente.ao"))
                .andReturn().getResponse().getContentAsByteArray()).has("brand")).isFalse();
    }

    @Test
    void raizDaApiRedirecionaParaAAplicacao() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound());
        mvc.perform(get("/index.html")).andExpect(status().isFound());
    }

    @Test
    void configPublicaDizSeORegistoEstaAberto() throws Exception {
        JsonNode cfg = send(null, get("/api/v1/config"), null, 200);
        assertThat(cfg.get("registrationOpen").asBoolean()).isTrue();
    }

    private String idDe(String nome) throws Exception {
        for (JsonNode o : send(admin, get("/api/v1/admin/platform/organizations"), null, 200)
                .get("items")) {
            if (nome.equals(o.get("name").asText())) {
                return o.get("id").asText();
            }
        }
        throw new AssertionError("empresa não encontrada: " + nome);
    }
}
