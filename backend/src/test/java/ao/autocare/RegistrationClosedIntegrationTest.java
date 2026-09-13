package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.platform.PlatformAdminBootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Produção: o registo livre está fechado e o dono do sistema entra com a
 * conta criada a partir do ambiente (ADMIN_EMAIL / ADMIN_PASSWORD).
 */
@TestPropertySource(properties = {
        "autocare.registration.open=false",
        "autocare.admin.email=Dono@Sistema.ao",
        "autocare.admin.password=SegredoDoDono1",
        "autocare.admin.name=Elves"
})
class RegistrationClosedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlatformAdminBootstrap bootstrap;

    private JsonNode send(String bearer, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
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

    @Test
    void ninguemCriaEmpresaPeloEcraDeEntrada() throws Exception {
        JsonNode erro = send(null, post("/api/v1/auth/register"), Map.of(
                "name", "Curioso", "email", "curioso@qualquer.ao",
                "password", "palavraForte1", "acceptTerms", true,
                "organizationName", "Empresa Pirata"), 403);
        assertThat(erro.get("message").asText()).contains("fechado");

        JsonNode cfg = send(null, get("/api/v1/config"), null, 200);
        assertThat(cfg.get("registrationOpen").asBoolean()).isFalse();
    }

    @Test
    void oAdminDoAmbienteEntraECriaEmpresas() throws Exception {
        // O @BeforeEach limpou a tabela users; o arranque real corre uma vez só.
        bootstrap.run();

        JsonNode sessao = send(null, post("/api/v1/auth/login"),
                Map.of("identifier", "dono@sistema.ao", "password", "SegredoDoDono1"), 200);
        assertThat(sessao.get("user").get("admin").asBoolean()).isTrue();
        assertThat(sessao.get("user").get("name").asText()).isEqualTo("Elves");
        String admin = "Bearer " + sessao.get("accessToken").asText();

        // Sem empresa própria: a plataforma responde na mesma.
        send(admin, get("/api/v1/organization"), null, 403);
        JsonNode resumo = send(admin, get("/api/v1/admin/platform/summary"), null, 200);
        assertThat(resumo.get("organizations").asLong()).isZero();

        JsonNode criada = send(admin, post("/api/v1/admin/platform/organizations"), Map.of(
                "name", "Primeiro Cliente", "ownerName", "Ana", "ownerEmail", "ana@cliente.ao"), 201);
        String dona = "Bearer " + send(null, post("/api/v1/auth/login"), Map.of(
                "identifier", "ana@cliente.ao",
                "password", criada.get("temporaryPassword").asText()), 200)
                .get("accessToken").asText();
        assertThat(send(dona, get("/api/v1/organization"), null, 200).get("name").asText())
                .isEqualTo("Primeiro Cliente");

        // Correr o arranque outra vez não mexe na conta nem duplica nada.
        bootstrap.run();
        send(null, post("/api/v1/auth/login"),
                Map.of("identifier", "dono@sistema.ao", "password", "SegredoDoDono1"), 200);
    }
}
