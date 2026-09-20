package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Driver;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.RefreshTokenRepository;
import ao.autocare.repo.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O acesso do motorista à aplicação, criado pelo gestor.
 *
 * <p>Um motorista não se regista sozinho nem precisa de email: o gestor cria-lhe
 * a conta, o sistema gera um identificador curto — «MOT-0412» — e uma
 * palavra-passe, e isso entrega-se em papel ou por WhatsApp. É a única forma de
 * entrar que funciona numa frota real, onde metade das pessoas não tem email e
 * ninguém quer depender de um convite que não chega.
 *
 * <p>Duas decisões deliberadas. A palavra-passe <b>mostra-se uma única vez</b>,
 * no momento em que é criada: fica guardada cifrada e nem o gestor a pode ver
 * outra vez — se se perder, repõe-se. E o motorista é <b>obrigado a trocá-la</b>
 * no primeiro acesso, porque uma palavra-passe que outra pessoa viu não é dele.
 */
@Service
public class DriverAccessService {

    /** Sem caracteres que se confundem em papel: O/0, I/1, S/5. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRTUVWXYZ2346789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** O que se devolve ao gestor quando o acesso é criado ou reposto. */
    public record Credenciais(
            String driverId,
            String userId,
            String name,
            /** O identificador de entrada: «MOT-0412». */
            String loginId,
            /** A palavra-passe em claro — mostrada uma vez e nunca mais. */
            String password,
            String message) {}

    /** O estado do acesso, para a ficha do motorista. */
    public record Acesso(
            boolean exists,
            String userId,
            String loginId,
            boolean active,
            boolean mustChangePassword,
            Instant lastLoginAt) {}

    private final DriverRepository drivers;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public DriverAccessService(DriverRepository drivers, UserRepository users,
            MembershipRepository memberships, OrganizationRepository organizations,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            AuditService audit) {
        this.drivers = drivers;
        this.users = users;
        this.memberships = memberships;
        this.organizations = organizations;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    // ==== Consultar =========================================================

    @Transactional(readOnly = true)
    public Acesso estado(String orgId, String driverId) {
        Driver d = exigir(orgId, driverId);
        User u = d.getUser();
        if (u == null) {
            return new Acesso(false, null, null, false, false, null);
        }
        boolean activo = u.isActive() && memberships.findByUserIdAndOrganizationId(u.getId(), orgId)
                .map(m -> m.getSuspendedAt() == null).orElse(false);
        return new Acesso(true, u.getId(), u.getLoginId(), activo,
                u.isMustChangePassword(), u.getLastLoginAt());
    }

    // ==== Criar =============================================================

    /**
     * Cria o acesso deste motorista e devolve as credenciais, uma só vez.
     */
    @Transactional
    public Credenciais criar(String orgId, String actorId, String driverId) {
        Driver d = exigir(orgId, driverId);
        if (d.getUser() != null) {
            throw ApiException.conflict(
                    "Este motorista já tem acesso. Para lhe dar uma palavra-passe nova, "
                            + "use «Repor palavra-passe».");
        }
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));

        String loginId = gerarIdentificador();
        String senha = gerarPalavraPasse();

        User u = new User();
        u.setName(d.getName());
        u.setPhone(vazioParaNulo(d.getPhone()));
        u.setEmail(vazioParaNulo(d.getEmail()));
        u.setLoginId(loginId);
        u.setPasswordHash(passwordEncoder.encode(senha));
        u.setMustChangePassword(true);
        Instant agora = Instant.now();
        u.setAcceptedTermsAt(agora);
        u.setAcceptedPrivacyAt(agora);
        users.save(u);

        // O papel decide o que ele vê: DRIVER não entra no sistema de gestão,
        // cai na aplicação do telemóvel e só com as suas viaturas.
        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(u);
        m.setRole(MembershipRole.DRIVER);
        m.setJobTitle("Motorista");
        m.setInvitedBy(actorId);
        memberships.save(m);

        d.setUser(u);

        audit.record(orgId, actorId, "driver.access_created", "Driver", d.getId(),
                d.getName() + " · " + loginId);

        return new Credenciais(d.getId(), u.getId(), d.getName(), loginId, senha,
                "Entregue estes dados ao motorista. A palavra-passe não volta a ser mostrada "
                        + "e terá de ser trocada por ele no primeiro acesso.");
    }

    // ==== Repor palavra-passe ==============================================

    @Transactional
    public Credenciais reporPalavraPasse(String orgId, String actorId, String driverId) {
        Driver d = exigir(orgId, driverId);
        User u = exigirAcesso(d);

        String senha = gerarPalavraPasse();
        u.setPasswordHash(passwordEncoder.encode(senha));
        u.setMustChangePassword(true);
        // Quem estivesse com sessão aberta no telemóvel perdido sai agora.
        refreshTokens.revokeAllForUser(u.getId(), Instant.now());

        audit.record(orgId, actorId, "driver.access_password_reset", "Driver", d.getId(), d.getName());

        return new Credenciais(d.getId(), u.getId(), d.getName(), u.getLoginId(), senha,
                "Palavra-passe nova. As sessões abertas no telemóvel foram terminadas.");
    }

    // ==== Bloquear e desbloquear ===========================================

    @Transactional
    public Acesso bloquear(String orgId, String actorId, String driverId) {
        Driver d = exigir(orgId, driverId);
        User u = exigirAcesso(d);
        memberships.findByUserIdAndOrganizationId(u.getId(), orgId)
                .ifPresent(m -> m.setSuspendedAt(Instant.now()));
        refreshTokens.revokeAllForUser(u.getId(), Instant.now());
        audit.record(orgId, actorId, "driver.access_blocked", "Driver", d.getId(), d.getName());
        return estado(orgId, driverId);
    }

    @Transactional
    public Acesso desbloquear(String orgId, String actorId, String driverId) {
        Driver d = exigir(orgId, driverId);
        User u = exigirAcesso(d);
        memberships.findByUserIdAndOrganizationId(u.getId(), orgId)
                .ifPresent(m -> m.setSuspendedAt(null));
        audit.record(orgId, actorId, "driver.access_unblocked", "Driver", d.getId(), d.getName());
        return estado(orgId, driverId);
    }

    // ==== Auxiliares ========================================================

    private Driver exigir(String orgId, String driverId) {
        return drivers.findByIdAndOrganizationId(driverId, orgId)
                .orElseThrow(() -> ApiException.notFound("Motorista não encontrado."));
    }

    private static User exigirAcesso(Driver d) {
        if (d.getUser() == null) {
            throw ApiException.badRequest("Este motorista ainda não tem acesso à aplicação.");
        }
        return d.getUser();
    }

    /** «MOT-0412», único em toda a plataforma. */
    private String gerarIdentificador() {
        for (int tentativa = 0; tentativa < 50; tentativa++) {
            String candidato = "MOT-" + String.format("%04d", ALEATORIO.nextInt(10_000));
            if (!users.existsByLoginIdIgnoreCase(candidato)) {
                return candidato;
            }
        }
        throw ApiException.conflict("Não foi possível gerar um identificador livre. Tente outra vez.");
    }

    /**
     * Oito caracteres sem os que se confundem escritos à mão.
     *
     * <p>Um «O» e um zero num papel amachucado dentro da cabina custam uma
     * chamada ao escritório; o alfabeto é escolhido para isso não acontecer.
     */
    private static String gerarPalavraPasse() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
