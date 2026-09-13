package ao.autocare.modules.platform;

import ao.autocare.config.AutoCareProperties;
import ao.autocare.domain.User;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que o dono do sistema tem conta de administrador da plataforma.
 *
 * <p>{@code ADMIN_EMAIL} diz quem é. Se a conta não existir e houver
 * {@code ADMIN_PASSWORD}, é criada (sem empresa: o administrador não é
 * cliente de si próprio). Se já existir, passa a administradora — a
 * palavra-passe do ambiente não a substitui, para que uma variável antiga
 * nunca reponha uma palavra-passe que o utilizador já mudou.
 */
@Component
public class PlatformAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final AutoCareProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminBootstrap(AutoCareProperties props, UserRepository users,
            PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AutoCareProperties.Admin admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()) {
            return;
        }
        String email = admin.email().trim().toLowerCase();
        User existente = users.findByEmailIgnoreCase(email).orElse(null);
        if (existente != null) {
            if (!existente.isAdmin()) {
                existente.setAdmin(true);
                existente.setActive(true);
                users.save(existente);
                log.info("[PLATAFORMA] {} passou a administrador da plataforma.", email);
            }
            return;
        }
        if (admin.password() == null || admin.password().length() < 8) {
            log.warn("[PLATAFORMA] ADMIN_EMAIL={} não tem conta e ADMIN_PASSWORD tem menos de "
                    + "8 caracteres: a conta de administrador NÃO foi criada.", email);
            return;
        }
        Instant agora = Instant.now();
        User u = new User();
        u.setName(admin.name() != null && !admin.name().isBlank()
                ? admin.name().trim() : "Administrador da plataforma");
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(admin.password()));
        u.setAdmin(true);
        u.setActive(true);
        u.setAcceptedTermsAt(agora);
        u.setAcceptedPrivacyAt(agora);
        u.setEmailVerifiedAt(agora);
        users.save(u);
        log.info("[PLATAFORMA] Conta de administrador da plataforma criada: {}", email);
    }
}
