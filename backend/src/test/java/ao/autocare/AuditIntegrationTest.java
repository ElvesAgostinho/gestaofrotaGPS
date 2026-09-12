package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Registo de auditoria: o que se vê e, sobretudo, o que nunca se pode ver.
 *
 * <p>O registo nasceu sem saber a que empresa pertencia cada linha. Expô-lo
 * nesse estado teria mostrado a atividade de todas as empresas a qualquer
 * pessoa com o papel certo — num sistema vendido a várias empresas, é a pior
 * fuga possível. Os testes de isolamento aqui são a razão de ser deste ficheiro.
 */
class AuditIntegrationTest extends AbstractIntegrationTest {

    private JsonNode send(String bearer, MockHttpServletRequestBuilder req,
                          Object body, int expect) throws Exception {
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

    /** Cria um ativo com uma etiqueta única, para ser reconhecível no registo. */
    private void createAsset(String bearer, String tag) throws Exception {
        String typeId = send(bearer, post("/api/v1/asset-types"),
                Map.of("name", "Tipo " + tag), 201).get("id").asText();
        send(bearer, post("/api/v1/assets"),
                Map.of("tag", tag, "name", "Ativo " + tag, "assetTypeId", typeId), 201);
    }

    // ---- isolamento entre empresas ---------------------------------------
    @Test
    void oneCompanyNeverSeesAnotherCompanysActivity() throws Exception {
        String empresaA = register("dono.a@teste.ao", "Construcoes A").bearer();
        String empresaB = register("dono.b@teste.ao", "Transportes B").bearer();

        createAsset(empresaA, "AAA-001");
        createAsset(empresaB, "BBB-999");

        JsonNode registoA = send(empresaA, get("/api/v1/audit?size=100"), null, 200);
        String textoA = registoA.toString();

        // O que é da empresa A aparece.
        assertThat(textoA).contains("AAA-001");
        // O que é da empresa B NUNCA aparece. Esta é a asserção que importa.
        assertThat(textoA).doesNotContain("BBB-999");
        assertThat(textoA).doesNotContain("Transportes B");
        assertThat(textoA).doesNotContain("dono.b@teste.ao");

        // E o inverso também.
        String textoB = send(empresaB, get("/api/v1/audit?size=100"), null, 200).toString();
        assertThat(textoB).contains("BBB-999");
        assertThat(textoB).doesNotContain("AAA-001");
        assertThat(textoB).doesNotContain("Construcoes A");
    }

    @Test
    void theSearchFilterCannotBeUsedToReachAnotherCompany() throws Exception {
        String empresaA = register("busca.a@teste.ao", "Empresa A").bearer();
        String empresaB = register("busca.b@teste.ao", "Empresa B").bearer();

        createAsset(empresaB, "SEGREDO-123");

        // Procurar de propósito por algo que só existe na outra empresa.
        JsonNode resultado = send(empresaA,
                get("/api/v1/audit?search=SEGREDO-123&size=100"), null, 200);
        assertThat(resultado.get("content")).isEmpty();
        assertThat(resultado.toString()).doesNotContain("SEGREDO-123");
    }

    @Test
    void theActionListIsAlsoScopedToTheCompany() throws Exception {
        String empresaA = register("acoes.a@teste.ao", "Empresa A").bearer();
        String empresaB = register("acoes.b@teste.ao", "Empresa B").bearer();

        createAsset(empresaB, "CAM-B");
        // A empresa B criou ativos; a A só tem o seu próprio registo de conta.
        JsonNode acoesA = send(empresaA, get("/api/v1/audit/actions"), null, 200);
        assertThat(acoesA.toString()).doesNotContain("asset.create");

        JsonNode acoesB = send(empresaB, get("/api/v1/audit/actions"), null, 200);
        assertThat(acoesB.toString()).contains("asset.create");
    }

    @Test
    void theSearchTreatsWildcardsAsLiteralText() throws Exception {
        String bearer = register("curinga@teste.ao", "Empresa W").bearer();
        createAsset(bearer, "CAM-100");
        createAsset(bearer, "CAM-200");

        // "_" e "%" sao universais do LIKE. Sem os escapar, procurar "CAM_1"
        // apanhava tambem "CAM-100" -- e quem procura uma matricula exata fica
        // sem perceber porque e que lhe aparecem outras.
        assertThat(send(bearer, get("/api/v1/audit?search=CAM_1&size=100"), null, 200)
                .get("content")).isEmpty();
        assertThat(send(bearer, get("/api/v1/audit?search=%25&size=100"), null, 200)
                .get("content")).isEmpty();

        // O texto a serio continua a encontrar.
        assertThat(send(bearer, get("/api/v1/audit?search=CAM-100&size=100"), null, 200)
                .get("content")).isNotEmpty();
    }

    // ---- quem pode ler ----------------------------------------------------
    @Test
    void onlyAnOwnerCanReadTheAuditTrail() throws Exception {
        String dono = register("dono.c@teste.ao", "Empresa C").bearer();

        // Convidar um gestor e aceitar o convite com uma conta nova.
        JsonNode convite = send(dono, post("/api/v1/team/invitations"),
                Map.of("email", "gestor.c@teste.ao", "name", "Gestor",
                        "role", "MANAGER"), 201);
        String token = convite.get("token").asText();

        JsonNode conta = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", "Gestor C", "password", "palavraForte1"), 200);
        String gestor = "Bearer " + conta.get("accessToken").asText();

        // O registo diz o que toda a gente fez, incluindo entradas na conta.
        // Isso não pertence a quem trabalha na empresa, mas a quem responde por ela.
        send(gestor, get("/api/v1/audit"), null, 403);
        send(gestor, get("/api/v1/audit/actions"), null, 403);
        send(null, get("/api/v1/audit"), null, 401);

        send(dono, get("/api/v1/audit"), null, 200);
    }

    // ---- conteúdo ---------------------------------------------------------
    @Test
    void eachEntrySaysWhoDidWhatInPlainPortuguese() throws Exception {
        String bearer = register("conteudo@teste.ao", "Empresa D").bearer();
        createAsset(bearer, "RE-777");

        JsonNode registo = send(bearer, get("/api/v1/audit?action=asset.create"), null, 200);
        assertThat(registo.get("content")).hasSize(1);

        JsonNode linha = registo.get("content").get(0);
        assertThat(linha.get("action").asText()).isEqualTo("asset.create");
        assertThat(linha.get("actionLabel").asText()).isEqualTo("Ativo criado");
        assertThat(linha.get("userName").asText()).isEqualTo("Utilizador Teste");
        assertThat(linha.get("summary").asText()).contains("RE-777");
        assertThat(linha.hasNonNull("at")).isTrue();

        // O identificador da empresa nunca sai no JSON: quem lê já está dentro
        // da sua, e o campo só serviria para revelar que existem outras.
        assertThat(linha.hasNonNull("organizationId")).isFalse();
    }

    @Test
    void accountActionsAreAttributedToTheUsersCompany() throws Exception {
        String bearer = register("conta@teste.ao", "Empresa E").bearer();

        // Entrar gera um registo, e tem de cair na empresa certa.
        Map<String, Object> login = new HashMap<>();
        login.put("identifier", "conta@teste.ao");
        login.put("password", "palavraForte1");
        send(null, post("/api/v1/auth/login"), login, 200);

        JsonNode entradas = send(bearer, get("/api/v1/audit?action=user.login"), null, 200);
        assertThat(entradas.get("content")).isNotEmpty();
        assertThat(entradas.get("content").get(0).get("actionLabel").asText())
                .isEqualTo("Entrada na conta");
    }

    @Test
    void theLockTrailIsInTheAuditAsWell() throws Exception {
        String bearer = register("bloqueio@teste.ao", "Empresa F").bearer();
        String typeId = send(bearer, post("/api/v1/asset-types"),
                Map.of("name", "Camiao"), 201).get("id").asText();
        String assetId = send(bearer, post("/api/v1/assets"),
                Map.of("tag", "CAM-900", "name", "Camiao", "assetTypeId", typeId), 201)
                .get("id").asText();
        send(bearer, post("/api/v1/gps-devices"),
                Map.of("externalId", "IMEI-AUD", "assetId", assetId), 201);

        Map<String, Object> pedido = new HashMap<>();
        pedido.put("reason", "Furto participado na esquadra");
        pedido.put("reasonCategory", "THEFT");
        pedido.put("acknowledged", true);
        send(bearer, post("/api/v1/assets/" + assetId + "/lock"), pedido, 201);

        JsonNode registo = send(bearer, get("/api/v1/audit?action=command.request"), null, 200);
        assertThat(registo.get("content")).hasSize(1);
        assertThat(registo.get("content").get(0).get("actionLabel").asText())
                .isEqualTo("BLOQUEIO: pedido");
        assertThat(registo.get("content").get(0).get("summary").asText())
                .contains("CAM-900")
                .contains("Furto");
    }
}
