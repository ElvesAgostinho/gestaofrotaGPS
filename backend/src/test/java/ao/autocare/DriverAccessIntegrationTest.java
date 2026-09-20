package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O acesso do motorista, criado pelo gestor.
 *
 * <p>Ninguém se regista sozinho: o gestor carrega num botão, o sistema devolve
 * um identificador curto e uma palavra-passe — uma única vez —, e é com isso
 * que o motorista entra na aplicação do telemóvel. À entrada é obrigado a
 * trocar a palavra-passe, porque a que o gestor viu não pode continuar a ser a
 * dele; e o gestor pode bloquear o acesso a qualquer momento, o que também
 * termina a sessão aberta no telemóvel.
 */
class DriverAccessIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private int sequencia = 0;

    /** Cada motorista com telefone e carta próprios: a empresa não aceita repetidos. */
    private String novoMotorista(String nome) throws Exception {
        sequencia++;
        return send(post("/api/v1/drivers"), Map.of(
                "name", nome,
                "phone", String.format("+244 923 000 %03d", sequencia),
                "licenseNumber", "AO-12345" + sequencia), 201)
                .get("id").asText();
    }

    @Test
    void oGestorCriaOAcessoEOMotoristaEntraComOIdentificador() throws Exception {
        bearer = register("acesso1@teste.ao", "Transportes Acesso").bearer();
        String driverId = novoMotorista("Joaquim Manuel");

        // Antes de criado, não há acesso nenhum.
        JsonNode antes = send(get("/api/v1/drivers/" + driverId + "/access"), null, 200);
        assertThat(antes.get("exists").asBoolean()).isFalse();

        JsonNode cred = send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        String loginId = cred.get("loginId").asText();
        String senha = cred.get("password").asText();
        assertThat(loginId).matches("MOT-\\d{4}");
        assertThat(senha).hasSize(8);
        // O alfabeto evita o que se confunde escrito à mão.
        assertThat(senha).doesNotContain("O").doesNotContain("0").doesNotContain("I");

        // O motorista entra com o identificador — sem email nenhum.
        String semSessao = bearer;
        bearer = null;
        JsonNode login = send(post("/api/v1/auth/login"),
                Map.of("identifier", loginId, "password", senha), 200);
        assertThat(login.get("user").get("mustChangePassword").asBoolean()).isTrue();
        assertThat(login.get("user").get("loginId").asText()).isEqualTo(loginId);
        String bearerMotorista = "Bearer " + login.get("accessToken").asText();

        // Troca a palavra-passe: a obrigação desaparece.
        bearer = bearerMotorista;
        send(post("/api/v1/users/me/change-password"),
                Map.of("currentPassword", senha, "newPassword", "conduzir2026"), 200);
        JsonNode depois = send(post("/api/v1/auth/login"),
                Map.of("identifier", loginId, "password", "conduzir2026"), 200);
        assertThat(depois.get("user").get("mustChangePassword").asBoolean()).isFalse();

        // A palavra-passe antiga já não serve.
        send(post("/api/v1/auth/login"), Map.of("identifier", loginId, "password", senha), 401);

        bearer = semSessao;
        JsonNode estado = send(get("/api/v1/drivers/" + driverId + "/access"), null, 200);
        assertThat(estado.get("exists").asBoolean()).isTrue();
        assertThat(estado.get("active").asBoolean()).isTrue();
        assertThat(estado.get("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void oGestorRepoeAPalavraPasseEBloqueiaOAcesso() throws Exception {
        bearer = register("acesso2@teste.ao", "Transportes Bloqueio").bearer();
        String gestor = bearer;
        String driverId = novoMotorista("Maria Kiala");
        JsonNode cred = send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        String loginId = cred.get("loginId").asText();

        // Repor dá uma palavra-passe nova e volta a exigir a troca.
        JsonNode nova = send(post("/api/v1/drivers/" + driverId + "/access/password"), null, 200);
        assertThat(nova.get("password").asText()).isNotEqualTo(cred.get("password").asText());
        assertThat(nova.get("loginId").asText()).isEqualTo(loginId);

        bearer = null;
        send(post("/api/v1/auth/login"),
                Map.of("identifier", loginId, "password", cred.get("password").asText()), 401);
        JsonNode entrou = send(post("/api/v1/auth/login"),
                Map.of("identifier", loginId, "password", nova.get("password").asText()), 200);
        assertThat(entrou.get("user").get("mustChangePassword").asBoolean()).isTrue();

        // Bloquear: a conta continua a existir, mas deixa de entrar na empresa.
        bearer = gestor;
        JsonNode bloqueado = send(post("/api/v1/drivers/" + driverId + "/access/block"), null, 200);
        assertThat(bloqueado.get("active").asBoolean()).isFalse();

        JsonNode desbloqueado = send(post("/api/v1/drivers/" + driverId + "/access/unblock"), null, 200);
        assertThat(desbloqueado.get("active").asBoolean()).isTrue();
    }

    @Test
    void naoSeCriaDuasVezesOMesmoAcesso() throws Exception {
        bearer = register("acesso3@teste.ao", "Transportes Duplo").bearer();
        String driverId = novoMotorista("Pedro Nzita");
        send(post("/api/v1/drivers/" + driverId + "/access"), null, 201);
        send(post("/api/v1/drivers/" + driverId + "/access"), null, 409);
    }

    @Test
    void cadaMotoristaRecebeUmIdentificadorDiferente() throws Exception {
        bearer = register("acesso4@teste.ao", "Transportes Únicos").bearer();
        String a = send(post("/api/v1/drivers/" + novoMotorista("Um") + "/access"), null, 201)
                .get("loginId").asText();
        String b = send(post("/api/v1/drivers/" + novoMotorista("Dois") + "/access"), null, 201)
                .get("loginId").asText();
        assertThat(a).isNotEqualTo(b);
    }
}
