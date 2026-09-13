package ao.autocare.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Recusa arrancar em produção com configuração de desenvolvimento.
 *
 * <p>O {@code application.yml} traz valores por omissão para o sistema correr na
 * máquina de quem o desenvolve sem configurar nada. Esses valores estão no
 * código-fonte: quem tiver acesso ao repositório consegue assinar tokens de
 * acesso válidos. Em produção isso não é um risco teórico, é uma porta aberta.
 *
 * <p>Por isso, com o perfil {@code prod} ativo, a aplicação <b>não arranca</b>
 * se os segredos ainda forem os de desenvolvimento, se forem curtos demais para
 * HMAC-SHA256, ou se a base de dados for a de ficheiro local. Falhar no arranque
 * é ruidoso e imediato; arrancar mal só se descobre quando já é tarde.
 */
@Component
public class ProductionSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    /**
     * Comprimento mínimo do segredo. HMAC-SHA256 usa uma chave de 256 bits; com
     * menos de 32 caracteres a chave é mais fraca do que o algoritmo.
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /** Trechos que denunciam um valor de exemplo que ficou por substituir. */
    private static final List<String> DEV_MARKERS =
            List.of("desenvolvimento", "substituir", "teste", "changeme", "example", "secret123");

    private final AutoCareProperties props;
    private final Environment environment;

    public ProductionSafetyCheck(AutoCareProperties props, Environment environment) {
        this.props = props;
        this.environment = environment;
    }

    @PostConstruct
    public void verify() {
        boolean production = List.of(environment.getActiveProfiles()).contains("prod");
        List<String> problems = collectProblems();

        if (problems.isEmpty()) {
            return;
        }
        if (production) {
            throw new IllegalStateException(
                    "Configuração insegura para produção:\n  - "
                            + String.join("\n  - ", problems)
                            + "\nDefina estas variáveis de ambiente antes de arrancar. "
                            + "Veja docs/DEPLOY.md.");
        }
        // Fora de produção avisa-se, mas deixa-se correr: é o que permite
        // arrancar o projeto sem configurar nada.
        for (String problem : problems) {
            log.warn("[CONFIGURAÇÃO] {}", problem);
        }
    }

    private List<String> collectProblems() {
        List<String> problems = new ArrayList<>();
        String access = props.security().jwt().accessSecret();
        String refresh = props.security().jwt().refreshSecret();

        checkSecret(problems, "JWT_ACCESS_SECRET", access);
        checkSecret(problems, "JWT_REFRESH_SECRET", refresh);

        if (access != null && access.equals(refresh)) {
            problems.add("JWT_ACCESS_SECRET e JWT_REFRESH_SECRET são iguais; "
                    + "um token de acesso passaria por token de renovação.");
        }

        String url = environment.getProperty("spring.datasource.url", "");
        if (url.startsWith("jdbc:h2:file") || url.startsWith("jdbc:h2:mem")) {
            problems.add("A base de dados é H2 local (" + url
                    + "); em produção use PostgreSQL através de DATABASE_URL.");
        }

        String origins = props.cors().allowedOrigins();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            problems.add("CORS_ORIGINS está aberto a qualquer origem; "
                    + "indique os endereços da aplicação web.");
        }

        // Registo fechado sem administrador da plataforma = um sistema em que
        // ninguém consegue entrar. Melhor recusar arrancar do que descobrir
        // isso no cliente.
        boolean registoAberto = props.registration() != null && props.registration().open();
        String adminEmail = props.admin() != null ? props.admin().email() : null;
        if (!registoAberto && (adminEmail == null || adminEmail.isBlank())) {
            problems.add("ADMIN_EMAIL não está definido. Com o registo livre fechado "
                    + "(REGISTRATION_OPEN=false) só o administrador da plataforma cria "
                    + "empresas — defina ADMIN_EMAIL e ADMIN_PASSWORD.");
        }
        return problems;
    }

    private void checkSecret(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + " não está definido.");
            return;
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            problems.add(name + " tem apenas " + value.length()
                    + " caracteres; o mínimo é " + MIN_SECRET_LENGTH + ".");
        }
        String lower = value.toLowerCase();
        for (String marker : DEV_MARKERS) {
            if (lower.contains(marker)) {
                problems.add(name + " ainda é um valor de exemplo do código-fonte. "
                        + "Gere um segredo novo (por exemplo, `openssl rand -base64 48`).");
                return;
            }
        }
    }
}
