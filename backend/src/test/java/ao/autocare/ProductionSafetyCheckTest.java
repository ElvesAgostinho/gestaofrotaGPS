package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ao.autocare.config.AutoCareProperties;
import ao.autocare.config.ProductionSafetyCheck;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * A verificação que impede a aplicação de arrancar em produção com a
 * configuração de desenvolvimento.
 */
class ProductionSafetyCheckTest {

    private static final String BOM_ACESSO =
            "K9x2mQ7vR4nT8pL1wZ6yB3cF5gH0jD7sA2eU4iO9kM3n";
    private static final String BOM_REFRESH =
            "P4tY7uI2oA9sD6fG3hJ0kL5zX8cV1bN4mQ7wE2rT9yU6";

    private ProductionSafetyCheck check(
            String access, String refresh, String url, String origins, String... profiles) {

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        env.setProperty("spring.datasource.url", url);

        AutoCareProperties props = new AutoCareProperties(
                new AutoCareProperties.App("AutoCare", "tagline", "https://app.autocare.ao"),
                new AutoCareProperties.Security(new AutoCareProperties.Security.Jwt(
                        access, refresh, Duration.ofMinutes(15), Duration.ofDays(30))),
                new AutoCareProperties.Cors(origins),
                new AutoCareProperties.Seed(false));
        return new ProductionSafetyCheck(props, env);
    }

    @Test
    void startsWhenEverythingIsProperlyConfigured() {
        ProductionSafetyCheck safe = check(BOM_ACESSO, BOM_REFRESH,
                "jdbc:postgresql://db:5432/autocare", "https://app.autocare.ao", "prod");

        assertThatCode(safe::verify).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartInProductionWithTheSecretFromTheSourceCode() {
        ProductionSafetyCheck unsafe = check(
                "desenvolvimento-segredo-de-acesso-substituir-em-producao-1234567890",
                BOM_REFRESH, "jdbc:postgresql://db:5432/autocare",
                "https://app.autocare.ao", "prod");

        assertThatThrownBy(unsafe::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ACCESS_SECRET")
                .hasMessageContaining("valor de exemplo")
                .hasMessageContaining("DEPLOY.md");
    }

    @Test
    void refusesShortSecrets() {
        ProductionSafetyCheck unsafe = check("curto", BOM_REFRESH,
                "jdbc:postgresql://db:5432/autocare", "https://app.autocare.ao", "prod");

        assertThatThrownBy(unsafe::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apenas 5 caracteres");
    }

    @Test
    void refusesTheSameSecretForAccessAndRefresh() {
        ProductionSafetyCheck unsafe = check(BOM_ACESSO, BOM_ACESSO,
                "jdbc:postgresql://db:5432/autocare", "https://app.autocare.ao", "prod");

        assertThatThrownBy(unsafe::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("passaria por token de renovação");
    }

    @Test
    void refusesTheLocalFileDatabaseInProduction() {
        ProductionSafetyCheck unsafe = check(BOM_ACESSO, BOM_REFRESH,
                "jdbc:h2:file:./data/autocare", "https://app.autocare.ao", "prod");

        assertThatThrownBy(unsafe::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void refusesCorsOpenToEveryone() {
        ProductionSafetyCheck unsafe = check(BOM_ACESSO, BOM_REFRESH,
                "jdbc:postgresql://db:5432/autocare", "*", "prod");

        assertThatThrownBy(unsafe::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    void reportsEveryProblemAtOnceInsteadOfOneAtATime() {
        ProductionSafetyCheck unsafe = check("curto", "teste-segredo",
                "jdbc:h2:file:./data/autocare", "*", "prod");

        // Quem está a instalar corrige tudo de uma vez, em vez de arrancar,
        // falhar, corrigir, arrancar, falhar...
        assertThatThrownBy(unsafe::verify)
                .hasMessageContaining("JWT_ACCESS_SECRET")
                .hasMessageContaining("JWT_REFRESH_SECRET")
                .hasMessageContaining("PostgreSQL")
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    void outsideProductionItOnlyWarnsSoTheProjectStillRunsOutOfTheBox() {
        ProductionSafetyCheck dev = check(
                "desenvolvimento-segredo-de-acesso-substituir-em-producao-1234567890",
                "desenvolvimento-segredo-de-refresh-substituir-em-producao-1234567890",
                "jdbc:h2:file:./data/autocare", "*");

        assertThatCode(dev::verify).doesNotThrowAnyException();
    }

    @Test
    void aMissingSecretIsAlsoAProblem() {
        ProductionSafetyCheck unsafe = check(null, BOM_REFRESH,
                "jdbc:postgresql://db:5432/autocare", "https://app.autocare.ao", "prod");

        assertThatThrownBy(unsafe::verify).hasMessageContaining("não está definido");
        assertThat(BOM_ACESSO.length()).isGreaterThanOrEqualTo(32);
    }
}
