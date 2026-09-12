package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Permissões por módulo, por cima do papel.
 *
 * <p>O caso que os quatro papéis não resolvem: o contabilista vê custos mas
 * não abre ordens; o chefe de oficina fecha ordens mas não vê o que custam.
 */
class PermissionIntegrationTest extends AbstractIntegrationTest {

    private String dono;
    private String assetId;

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

    /** Convida, aceita e devolve o bearer e o id da adesão. */
    private String[] membro(String email, String role, String nome) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("role", role);
        String token = send(dono, post("/api/v1/team/invitations"), body, 201).get("token").asText();
        JsonNode res = send(null, post("/api/v1/invitations/" + token + "/accept"),
                Map.of("name", nome, "password", "palavraForte1"), 200);
        String bearer = "Bearer " + res.get("accessToken").asText();
        String membershipId = null;
        for (JsonNode m : send(dono, get("/api/v1/team/members"), null, 200)) {
            if (email.equals(m.get("email").asText())) {
                membershipId = m.get("id").asText();
            }
        }
        return new String[] {bearer, membershipId};
    }

    @BeforeEach
    void setUp() throws Exception {
        dono = register("perm-dono@teste.ao").bearer();
        String typeId = send(dono, post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        assetId = send(dono, post("/api/v1/assets"), Map.of(
                "tag", "CAM-1", "name", "Camião", "assetTypeId", typeId,
                "acquisitionValue", 50000000), 201).get("id").asText();
    }

    @Test
    void oCatalogoDizOQueCadaPermissaoEEQuemATrazPorOmissao() throws Exception {
        JsonNode cat = send(dono, get("/api/v1/team/permissions"), null, 200);
        assertThat(cat.size()).isGreaterThan(10);
        JsonNode custos = null;
        for (JsonNode p : cat) {
            if ("COSTS_VIEW".equals(p.get("code").asText())) {
                custos = p;
            }
        }
        assertThat(custos).isNotNull();
        assertThat(custos.get("label").asText()).contains("financeiros");
        assertThat(custos.get("module").asText()).isEqualTo("Custos");
        assertThat(custos.get("defaultRoles").toString()).contains("MANAGER").contains("OWNER");
    }

    @Test
    void asMinhasPermissoesVemComAEmpresa() throws Exception {
        JsonNode org = send(dono, get("/api/v1/organization"), null, 200);
        assertThat(org.get("myPermissions").toString()).contains("COSTS_VIEW").contains("TEAM_MANAGE");

        String[] leitor = membro("leitor@teste.ao", "VIEWER", "Rui");
        JsonNode orgLeitor = send(leitor[0], get("/api/v1/organization"), null, 200);
        assertThat(orgLeitor.get("myPermissions")).isEmpty();
    }

    @Test
    void oContabilistaVeCustosSemSerGestor() throws Exception {
        String[] contab = membro("contab@teste.ao", "VIEWER", "Ana Contabilista");

        // Como leitor, a ficha vem sem dinheiro.
        JsonNode antes = send(contab[0], get("/api/v1/assets/" + assetId), null, 200);
        assertThat(antes.has("acquisitionValue")).isFalse();

        // O dono dá-lhe a permissão de custos, sem lhe mudar o papel.
        Map<String, Object> req = new HashMap<>();
        req.put("granted", List.of("COSTS_VIEW"));
        JsonNode m = send(dono, patch("/api/v1/team/members/" + contab[1]), req, 200);
        assertThat(m.get("role").asText()).isEqualTo("VIEWER");
        assertThat(m.get("permissions").toString()).contains("COSTS_VIEW");
        assertThat(m.get("granted").toString()).contains("COSTS_VIEW");

        // A sessão antiga foi cortada de propósito: a permissão nova vale já.
        // Entra de novo.
        JsonNode sessao = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", "contab@teste.ao", "password", "palavraForte1"), 200);
        String contabDeNovo = "Bearer " + sessao.get("accessToken").asText();

        JsonNode depois = send(contabDeNovo, get("/api/v1/assets/" + assetId), null, 200);
        assertThat(depois.get("acquisitionValue").asLong()).isEqualTo(50000000L);

        // Continua sem poder criar ativos: só recebeu custos.
        send(contabDeNovo, post("/api/v1/assets"),
                Map.of("tag", "X", "name", "X", "assetTypeId", "nada"), 403);
    }

    @Test
    void aoGestorTiraSeAImobilizacaoEAMensagemDizQual() throws Exception {
        String[] gestor = membro("gestor@teste.ao", "MANAGER", "Zé Gestor");

        // O gestor, por omissão, não imobiliza (é do dono) — mas vê custos.
        assertThat(send(gestor[0], get("/api/v1/assets/" + assetId), null, 200)
                .has("acquisitionValue")).isTrue();

        // Tira-se-lhe os custos.
        Map<String, Object> req = new HashMap<>();
        req.put("denied", List.of("COSTS_VIEW"));
        send(dono, patch("/api/v1/team/members/" + gestor[1]), req, 200);

        JsonNode sessao = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", "gestor@teste.ao", "password", "palavraForte1"), 200);
        String gestorDeNovo = "Bearer " + sessao.get("accessToken").asText();

        // Continua gestor: cria ativos.
        send(gestorDeNovo, post("/api/v1/assets"), Map.of(
                "tag", "CAM-2", "name", "Outro", "assetTypeId",
                send(dono, get("/api/v1/asset-types"), null, 200).get(0).get("id").asText()), 201);
        // Mas a ficha vem sem dinheiro.
        assertThat(send(gestorDeNovo, get("/api/v1/assets/" + assetId), null, 200)
                .has("acquisitionValue")).isFalse();

        // E os relatórios, que são de custos, dizem-lhe qual a permissão que falta.
        // (REPORTS_VIEW continua: só se tirou COSTS_VIEW.) Testa-se a mensagem
        // com a imobilização, que o gestor nunca teve.
        JsonNode erro = send(gestorDeNovo, post("/api/v1/assets/" + assetId + "/lock"),
                Map.of("reason", "teste"), 403);
        assertThat(erro.get("message").asText()).contains("Imobilizar");
    }

    @Test
    void ninguemMexeNasProprias() throws Exception {
        String minha = null;
        for (JsonNode m : send(dono, get("/api/v1/team/members"), null, 200)) {
            if ("perm-dono@teste.ao".equals(m.get("email").asText())) {
                minha = m.get("id").asText();
            }
        }
        Map<String, Object> req = new HashMap<>();
        req.put("denied", List.of("TEAM_MANAGE"));
        // A forma clássica de um administrador se trancar fora da administração.
        send(dono, patch("/api/v1/team/members/" + minha), req, 409);
    }

    @Test
    void codigoDesconhecidoEUmErroDeQuemChamou() throws Exception {
        String[] leitor = membro("leitor2@teste.ao", "VIEWER", "Rui");
        Map<String, Object> req = new HashMap<>();
        req.put("granted", List.of("VOAR"));
        JsonNode erro = send(dono, patch("/api/v1/team/members/" + leitor[1]), req, 400);
        assertThat(erro.get("message").asText()).contains("VOAR");
    }

    @Test
    void oMotoristaLancaCombustivelEMaisNada() throws Exception {
        String[] motorista = membro("motorista@teste.ao", "DRIVER", "Manuel");
        Map<String, Object> abastecimento = new HashMap<>();
        abastecimento.put("liters", 80);
        abastecimento.put("odometerValue", 1000);
        abastecimento.put("filledAt", java.time.Instant.now().toString());
        int cod = mvc.perform(post("/api/v1/assets/" + assetId + "/fuel")
                        .header("Authorization", motorista[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(abastecimento)))
                .andReturn().getResponse().getStatus();
        // Passa a permissão (não é 403); o corpo pode falhar por validação, mas
        // isso é outra conversa.
        assertThat(cod).isNotEqualTo(403);
        // E não abre ordens.
        send(motorista[0], post("/api/v1/work-orders"),
                Map.of("assetId", assetId, "type", "CORRECTIVE", "title", "x"), 403);
    }
}
