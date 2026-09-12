package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.domain.AppConfigEntry;
import ao.autocare.domain.Membership;
import ao.autocare.domain.NotificationPreference;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.repo.AppConfigRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.NotificationPreferenceRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirma que o esquema Flyway e os mapeamentos JPA estão coerentes
 * (não usamos ddl-auto=validate; este teste faz esse papel para as entidades da Fase 1).
 */
class PersistenceSmokeTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired NotificationPreferenceRepository prefs;
    @Autowired AppConfigRepository appConfig;
    @Autowired JdbcTemplate jdbc;

    private User newUser(String email) {
        User u = new User();
        u.setName("Teste");
        u.setEmail(email);
        u.setPasswordHash("hash");
        return users.save(u);
    }

    @Test
    @Transactional
    void organizationMembershipAndPreferencesPersist() {
        User u = newUser("smoke1@teste.ao");

        Organization org = new Organization();
        org.setName("Empresa Teste");
        organizations.save(org);

        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(u);
        m.setRole(MembershipRole.MANAGER);
        memberships.save(m);

        NotificationPreference pref = new NotificationPreference();
        pref.setUser(u);
        pref.setCategory(AlertCategory.DOCUMENT);
        pref.setLeadDays(List.of(60, 30, 7));
        prefs.save(pref);

        assertThat(memberships.findByUserId(u.getId())).hasSize(1);
        NotificationPreference reloaded = prefs.findByUserId(u.getId()).get(0);
        assertThat(reloaded.getLeadDays()).containsExactly(60, 30, 7);
    }

    @Test
    void verificationCodesTableMatchesSchema() {
        User u = newUser("smoke2@teste.ao");
        jdbc.update("INSERT INTO verification_codes "
                + "(id, user_id, channel, target, code_hash, expires_at, attempts, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?)",
                "vc-1", u.getId(), "EMAIL", "smoke2@teste.ao", "abc",
                java.sql.Timestamp.from(Instant.now().plusSeconds(600)), 0,
                java.sql.Timestamp.from(Instant.now()));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM verification_codes WHERE user_id = ?", Integer.class, u.getId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void referenceDataLoadedByFlyway() {
        assertThat(appConfig.findByKey("app.name")).isPresent();
        Long plans = jdbc.queryForObject("SELECT count(*) FROM plans", Long.class);
        assertThat(plans).isGreaterThanOrEqualTo(4L);
        Long models = jdbc.queryForObject("SELECT count(*) FROM vehicle_models", Long.class);
        assertThat(models).isGreaterThan(30L);
    }

    @Test
    void appConfigUpsert() {
        AppConfigEntry e = new AppConfigEntry();
        e.setKey("test.key");
        e.setValue("v1");
        appConfig.save(e);
        assertThat(appConfig.findByKey("test.key")).get()
                .extracting(AppConfigEntry::getValue).isEqualTo("v1");
    }
}
