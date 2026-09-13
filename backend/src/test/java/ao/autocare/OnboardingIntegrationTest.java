package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** O assistente de primeira utilizacao aparece uma vez a quem cria a empresa, e nunca mais. */
class OnboardingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void aEmpresaNovaComecaPorFazerOAssistenteEDepoisDeConcluidoNaoVoltaAAparecer() throws Exception {
        String bearer = register("novo@teste.ao", "Empresa Nova").bearer();
        JsonNode org = json.readTree(mvc.perform(get("/api/v1/organization").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(org.get("onboardingDone").asBoolean()).isFalse();

        JsonNode depois = json.readTree(mvc.perform(post("/api/v1/organization/onboarding/done").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(depois.get("onboardingDone").asBoolean()).isTrue();

        // Um tecnico nao pode dar o assistente por concluido pela empresa toda.
        mvc.perform(post("/api/v1/organization/onboarding/done").header("Authorization", bearer))
                .andExpect(status().isOk()); // o dono repete sem erro
    }
}
