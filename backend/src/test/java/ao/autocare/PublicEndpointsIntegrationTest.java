package ao.autocare;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class PublicEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void healthIsOk() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database").value("ok"));
    }

    @Test
    void publicConfigHasProductName() throws Exception {
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AutoCare"))
                .andExpect(jsonPath("$.currency").value("AOA"));
    }

    @Test
    void plansAreListed() throws Exception {
        mvc.perform(get("/api/v1/catalog/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].code").value("FREE"));
    }

    @Test
    void brandsIncludeModels() throws Exception {
        mvc.perform(get("/api/v1/catalog/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Toyota')].models[?(@.name == 'Hilux')]")
                        .isNotEmpty());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401WithFriendlyBody() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
