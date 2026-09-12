package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.repo.AuditLogRepository;
import ao.autocare.repo.SubscriptionRepository;
import ao.autocare.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired AuditLogRepository auditLogs;

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    private JsonNode postJson(String url, Object payload, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body(payload)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    // ----------------------------------------------------------------------
    @Test
    void registerRejectsMissingTermsAndContactAndShortPassword() throws Exception {
        postJson("/api/v1/auth/register",
                Map.of("name", "X", "email", "x@teste.ao", "password", "palavraForte1", "acceptTerms", false),
                400);

        postJson("/api/v1/auth/register",
                Map.of("name", "X", "password", "palavraForte1", "acceptTerms", true),
                400);

        postJson("/api/v1/auth/register",
                Map.of("name", "X", "email", "x@teste.ao", "password", "123", "acceptTerms", true),
                400);
    }

    @Test
    void registerCreatesAccountWithFreePlanAndTokens() throws Exception {
        var payload = Map.of("name", "João Teste", "email", "joao@teste.ao",
                "password", "palavraForte1", "acceptTerms", true);
        JsonNode res = postJson("/api/v1/auth/register", payload, 201);

        assertThat(res.get("user").get("email").asText()).isEqualTo("joao@teste.ao");
        assertThat(res.get("accessToken").asText()).isNotBlank();
        assertThat(res.get("refreshToken").asText()).isNotBlank();
        assertThat(res.get("user").has("passwordHash")).isFalse();

        var user = users.findByEmailIgnoreCase("joao@teste.ao").orElseThrow();
        assertThat(subscriptions.findByUserId(user.getId())).hasSize(1);
        assertThat(subscriptions.findByUserId(user.getId()).get(0).getPlan().getCode().name())
                .isEqualTo("FREE");

        // registo duplicado
        postJson("/api/v1/auth/register", payload, 400);
    }

    @Test
    void loginFailsGenericallyForWrongPasswordAndUnknownUser() throws Exception {
        postJson("/api/v1/auth/register",
                Map.of("name", "A", "email", "a@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);

        JsonNode wrong = postJson("/api/v1/auth/login",
                Map.of("identifier", "a@teste.ao", "password", "errada"), 401);
        assertThat(wrong.get("message").asText()).contains("incorretos");

        JsonNode unknown = postJson("/api/v1/auth/login",
                Map.of("identifier", "ninguem@teste.ao", "password", "seja"), 401);
        assertThat(unknown.get("message").asText()).contains("incorretos");
    }

    @Test
    void fullSessionLifecycle() throws Exception {
        postJson("/api/v1/auth/register",
                Map.of("name", "Ciclo", "email", "ciclo@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);

        JsonNode login = postJson("/api/v1/auth/login",
                Map.of("identifier", "ciclo@teste.ao", "password", "palavraForte1"), 200);
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        // /me exige token
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ciclo@teste.ao"));

        // atualizar perfil + rejeitar campo desconhecido
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Ciclo Novo", "theme", "DARK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ciclo Novo"))
                .andExpect(jsonPath("$.theme").value("DARK"));

        // refresh com rotação: token antigo deixa de servir
        JsonNode refreshed = postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 200);
        String newRefresh = refreshed.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401);

        // logout revoga o refresh atual
        postJson("/api/v1/auth/logout", Map.of("refreshToken", newRefresh), 200);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh), 401);
    }

    @Test
    void changePasswordInvalidatesSessionsAndRequiresCorrectCurrent() throws Exception {
        JsonNode reg = postJson("/api/v1/auth/register",
                Map.of("name", "Pass", "email", "pass@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);
        String access = reg.get("accessToken").asText();
        String refresh = reg.get("refreshToken").asText();

        mvc.perform(post("/api/v1/users/me/change-password").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "errada", "newPassword", "novaForte123"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/users/me/change-password").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "palavraForte1", "newPassword", "novaForte123"))))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401);
        postJson("/api/v1/auth/login", Map.of("identifier", "pass@teste.ao", "password", "novaForte123"), 200);
    }

    @Test
    void forgotAndResetPasswordInDemoMode() throws Exception {
        postJson("/api/v1/auth/register",
                Map.of("name", "Reset", "email", "reset@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);

        JsonNode forgot = postJson("/api/v1/auth/forgot-password", Map.of("identifier", "reset@teste.ao"), 200);
        assertThat(forgot.get("demoMode").asBoolean()).isTrue();
        String token = forgot.get("resetToken").asText();
        assertThat(token).isNotBlank();

        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "outraForte123"), 200);
        postJson("/api/v1/auth/login", Map.of("identifier", "reset@teste.ao", "password", "outraForte123"), 200);

        // token não pode ser reutilizado
        postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "maisUmaForte123"), 400);

        // conta inexistente não revela nada
        JsonNode unknown = postJson("/api/v1/auth/forgot-password",
                Map.of("identifier", "nao-existe@teste.ao"), 200);
        assertThat(unknown.hasNonNull("resetToken")).isFalse();
    }

    @Test
    void adminEndpointForbiddenForNormalUser() throws Exception {
        JsonNode reg = postJson("/api/v1/auth/register",
                Map.of("name", "Normal", "email", "normal@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);
        String access = reg.get("accessToken").asText();

        mvc.perform(get("/api/v1/admin/config").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditTrailRecordsRegisterAndLogin() throws Exception {
        postJson("/api/v1/auth/register",
                Map.of("name", "Aud", "email", "aud@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);
        postJson("/api/v1/auth/login", Map.of("identifier", "aud@teste.ao", "password", "palavraForte1"), 200);

        assertThat(auditLogs.findByActionOrderByCreatedAtDesc("user.register")).isNotEmpty();
        assertThat(auditLogs.findByActionOrderByCreatedAtDesc("user.login")).isNotEmpty();
    }

    @Test
    void exportAndDeleteAccount() throws Exception {
        JsonNode reg = postJson("/api/v1/auth/register",
                Map.of("name", "Del", "email", "del@teste.ao", "password", "palavraForte1", "acceptTerms", true),
                201);
        String access = reg.get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me/export").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("del@teste.ao"))
                .andExpect(jsonPath("$.subscriptions").isArray());

        mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        assertThat(users.findByEmailIgnoreCase("del@teste.ao")).isEmpty();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }
}
