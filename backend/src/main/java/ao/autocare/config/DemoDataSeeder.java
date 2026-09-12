package ao.autocare.config;

import ao.autocare.domain.Membership;
import ao.autocare.domain.NotificationPreference;
import ao.autocare.domain.Organization;
import ao.autocare.domain.Subscription;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.domain.enums.Enums.OrganizationType;
import ao.autocare.domain.enums.Enums.PlanCode;
import ao.autocare.domain.enums.Enums.SubscriptionStatus;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.NotificationPreferenceRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PlanRepository;
import ao.autocare.repo.SubscriptionRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração (secção 70). Só corre com {@code autocare.seed.demo=true}.
 * Conta demo: demo@autocare.ao / demo1234 (é admin).
 * Cresce a cada fase — Fase 1 cria apenas a conta de demonstração.
 */
@Component
@ConditionalOnProperty(name = "autocare.seed.demo", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_EMAIL = "demo@autocare.ao";

    private final UserRepository users;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final NotificationPreferenceRepository notificationPrefs;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final CmmsDemoSeeder cmmsDemoSeeder;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            UserRepository users,
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            NotificationPreferenceRepository notificationPrefs,
            OrganizationRepository organizations,
            MembershipRepository memberships,
            CmmsDemoSeeder cmmsDemoSeeder,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.notificationPrefs = notificationPrefs;
        this.organizations = organizations;
        this.memberships = memberships;
        this.cmmsDemoSeeder = cmmsDemoSeeder;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.findByEmailIgnoreCase(DEMO_EMAIL).isPresent()) {
            return;
        }

        User demo = new User();
        demo.setName("João Manuel");
        demo.setEmail(DEMO_EMAIL);
        demo.setPhone("+244923000000");
        demo.setPasswordHash(passwordEncoder.encode("demo1234"));
        demo.setEmailVerifiedAt(Instant.now());
        demo.setAcceptedTermsAt(Instant.now());
        demo.setAcceptedPrivacyAt(Instant.now());
        demo.setAdmin(true);
        users.save(demo);

        Organization org = new Organization();
        org.setName("Construções Kwanza, Lda.");
        org.setType(OrganizationType.COMPANY);
        organizations.save(org);

        Membership membership = new Membership();
        membership.setOrganization(org);
        membership.setUser(demo);
        membership.setRole(MembershipRole.OWNER);
        memberships.save(membership);

        for (AlertCategory category : List.of(
                AlertCategory.MAINTENANCE, AlertCategory.DOCUMENT, AlertCategory.GPS,
                AlertCategory.EXPENSE, AlertCategory.INSURANCE)) {
            NotificationPreference pref = new NotificationPreference();
            pref.setUser(demo);
            pref.setCategory(category);
            List<Integer> leadDays = new ArrayList<>();
            for (int d : Enums.DEFAULT_EXPIRY_LEAD_DAYS) {
                leadDays.add(d);
            }
            pref.setLeadDays(leadDays);
            notificationPrefs.save(pref);
        }

        plans.findByCode(PlanCode.PERSONAL).ifPresent(plan -> {
            Subscription sub = new Subscription();
            sub.setUser(demo);
            sub.setPlan(plan);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setStartedAt(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
            subscriptions.save(sub);
        });

        cmmsDemoSeeder.seed(org, demo);

        log.info("Dados de demonstração carregados. Conta: {} / demo1234", DEMO_EMAIL);
    }
}
