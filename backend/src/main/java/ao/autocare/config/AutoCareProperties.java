package ao.autocare.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da aplicação a partir de {@code application.yml} / variáveis de ambiente.
 */
@ConfigurationProperties(prefix = "autocare")
public record AutoCareProperties(App app, Security security, Cors cors, Seed seed) {

    /**
     * @param webUrl endereço da aplicação web, usado para montar links enviados
     *               ao utilizador (convites, reposição de palavra-passe).
     */
    public record App(String name, String tagline, String webUrl) {}

    public record Security(Jwt jwt) {
        public record Jwt(
                String accessSecret,
                String refreshSecret,
                Duration accessTtl,
                Duration refreshTtl) {}
    }

    public record Cors(String allowedOrigins) {}

    public record Seed(boolean demo) {}
}
