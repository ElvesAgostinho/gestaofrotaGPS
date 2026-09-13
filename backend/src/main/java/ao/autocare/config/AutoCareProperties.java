package ao.autocare.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da aplicação a partir de {@code application.yml} / variáveis de ambiente.
 */
@ConfigurationProperties(prefix = "autocare")
public record AutoCareProperties(App app, Security security, Cors cors, Seed seed,
        Registration registration, Admin admin) {

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

    /**
     * @param open se qualquer pessoa pode criar uma empresa no ecrã de entrada.
     *             Em produção fica fechado: as empresas são criadas pelo
     *             administrador da plataforma, que é quem vende o sistema.
     */
    public record Registration(boolean open) {}

    /**
     * Administrador da plataforma criado no arranque a partir do ambiente
     * ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}). A palavra-passe só é
     * usada na criação; depois muda-se na aplicação.
     */
    public record Admin(String email, String password, String name) {}
}
