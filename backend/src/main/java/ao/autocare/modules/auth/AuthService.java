package ao.autocare.modules.auth;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Membership;
import ao.autocare.domain.NotificationPreference;
import ao.autocare.domain.Organization;
import ao.autocare.domain.PasswordResetToken;
import ao.autocare.domain.Plan;
import ao.autocare.domain.RefreshToken;
import ao.autocare.domain.Subscription;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.domain.enums.Enums.OrganizationType;
import ao.autocare.domain.enums.Enums.PlanCode;
import ao.autocare.domain.enums.Enums.SubscriptionStatus;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.auth.dto.AuthDtos.AuthResponse;
import ao.autocare.modules.auth.dto.AuthDtos.ForgotPasswordResponse;
import ao.autocare.modules.auth.dto.AuthDtos.LoginRequest;
import ao.autocare.modules.auth.dto.AuthDtos.RegisterRequest;
import ao.autocare.modules.auth.dto.AuthDtos.TokenPair;
import ao.autocare.modules.auth.dto.UserView;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.NotificationPreferenceRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.PasswordResetTokenRepository;
import ao.autocare.repo.PlanRepository;
import ao.autocare.repo.RefreshTokenRepository;
import ao.autocare.repo.SubscriptionRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Hash BCrypt válido usado para manter o tempo de resposta constante quando a conta não existe. */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final List<AlertCategory> DEFAULT_PREF_CATEGORIES = List.of(
            AlertCategory.MAINTENANCE, AlertCategory.DOCUMENT, AlertCategory.GPS,
            AlertCategory.EXPENSE, AlertCategory.INSURANCE, AlertCategory.INSPECTION,
            AlertCategory.TIRE, AlertCategory.SYSTEM);

    private final UserRepository users;
    private final ao.autocare.security.RateLimiter rateLimiter;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final NotificationPreferenceRepository notificationPrefs;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordResetTokenRepository resetTokens,
            NotificationPreferenceRepository notificationPrefs,
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            OrganizationRepository organizations,
            MembershipRepository memberships,
            PasswordEncoder passwordEncoder,
            JwtService jwt,
            AuditService audit,
            ao.autocare.security.RateLimiter rateLimiter) {
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.notificationPrefs = notificationPrefs;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.organizations = organizations;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    // -----------------------------------------------------------------------
    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest http) {
        if (!req.acceptTerms()) {
            throw ApiException.badRequest(
                    "Precisa de aceitar os termos e a política de privacidade para continuar.");
        }

        String email = normalizeEmail(req.email());
        String phone = normalizePhone(req.phone());

        if (email != null && users.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("Já existe uma conta com estes dados.");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw ApiException.badRequest("Já existe uma conta com estes dados.");
        }

        Instant now = Instant.now();
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setAcceptedTermsAt(now);
        user.setAcceptedPrivacyAt(now);
        users.save(user);

        Organization org = createOrganization(user, req.organizationName());
        createDefaultPreferences(user);
        assignFreePlan(user);

        audit.record(org.getId(), user.getId(), "user.register", "User", user.getId(),
                "Nova conta: " + user.getName() + " · empresa: " + org.getName(), http);

        TokenPair tokens = issueTokens(user, http);
        return toAuthResponse(user, tokens);
    }

    // -----------------------------------------------------------------------
    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest http) {
        String identifier = req.identifier().trim();
        Optional<User> maybeUser = users.findByEmailOrPhone(identifier);

        ApiException invalid = ApiException.unauthorized("Email/telefone ou palavra-passe incorretos.");

        if (maybeUser.isEmpty()) {
            passwordEncoder.matches(req.password(), DUMMY_HASH); // tempo constante
            throw invalid;
        }
        User user = maybeUser.get();
        if (!user.isActive()) {
            throw ApiException.unauthorized("Esta conta está desativada.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw invalid;
        }

        user.setLastLoginAt(Instant.now());
        // O login resultou: devolver a tentativa ao contador. Contar sucessos
        // não trava ataque nenhum e trava quem trabalha — ver RateLimiter.forget().
        rateLimiter.forget("login:" + clientIp(http));

        audit.recordForUser(user.getId(), "user.login", "User", user.getId(), null, http);

        TokenPair tokens = issueTokens(user, http);
        return toAuthResponse(user, tokens);
    }

    // -----------------------------------------------------------------------
    @Transactional
    public TokenPair refresh(String refreshToken, HttpServletRequest http) {
        String userId = jwt.parseRefreshSubject(refreshToken);
        ApiException expired = ApiException.unauthorized("A sessão expirou. Inicie sessão novamente.");
        if (userId == null) {
            throw expired;
        }

        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash).orElseThrow(() -> expired);
        if (!stored.isUsable()) {
            throw expired;
        }

        stored.setRevokedAt(Instant.now()); // rotação
        User user = users.findById(userId).orElseThrow(() -> expired);
        return issueTokens(user, http);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(sha256(refreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    // -----------------------------------------------------------------------
    @Transactional
    public ForgotPasswordResponse forgotPassword(String identifier) {
        String message = "Se existir uma conta associada, enviaremos instruções "
                + "para repor a palavra-passe.";
        Optional<User> user = users.findByEmailOrPhone(identifier.trim());
        if (user.isEmpty()) {
            return new ForgotPasswordResponse(message, true, null);
        }

        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken record = new PasswordResetToken();
        record.setUser(user.get());
        record.setTokenHash(sha256(token));
        record.setExpiresAt(Instant.now().plusSeconds(3600));
        resetTokens.save(record);

        log.warn("[MODO DEMONSTRAÇÃO] Sem canal de envio configurado. "
                + "Token de reposição para {}: {}", identifier, token);
        return new ForgotPasswordResponse(message, true, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken record = resetTokens.findByTokenHash(sha256(token))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> ApiException.badRequest(
                        "Este link de reposição já não é válido. Peça um novo."));

        User user = record.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        record.setUsedAt(Instant.now());
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());

        audit.recordForUser(user.getId(), "user.password_reset", "User", user.getId(), null);
    }

    // -----------------------------------------------------------------------
    @Transactional(readOnly = true)
    public UserView getProfile(String userId) {
        return UserView.from(users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada.")));
    }

    // -----------------------------------------------------------------------
    /**
     * Cria a conta de alguém que aceitou um convite. Ao contrário do registo
     * normal, <b>não</b> cria uma empresa nova — quem chama associa depois o
     * utilizador à empresa que o convidou.
     */
    @Transactional
    public User createInvitedUser(String name, String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        if (normalized != null && users.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.badRequest("Já existe uma conta com estes dados.");
        }
        Instant now = Instant.now();
        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setAcceptedTermsAt(now);
        user.setAcceptedPrivacyAt(now);
        user.setEmailVerifiedAt(now); // o convite chegou ao próprio email
        users.save(user);

        createDefaultPreferences(user);
        assignFreePlan(user);
        return user;
    }

    /** Emite uma sessão para um utilizador já existente. */
    @Transactional
    public AuthResponse issueSession(User user, HttpServletRequest http) {
        return toAuthResponse(user, issueTokens(user, http));
    }

    // -----------------------------------------------------------------------
    private TokenPair issueTokens(User user, HttpServletRequest http) {
        String access = jwt.generateAccessToken(user.getId());
        String refresh = jwt.generateRefreshToken(user.getId());

        RefreshToken record = new RefreshToken();
        record.setUser(user);
        record.setTokenHash(sha256(refresh));
        record.setExpiresAt(Instant.now().plus(jwt.refreshTtl()));
        if (http != null) {
            record.setIp(http.getRemoteAddr());
            record.setUserAgent(truncate(http.getHeader("User-Agent"), 400));
        }
        refreshTokens.save(record);

        return new TokenPair(access, refresh, "Bearer", jwt.accessTtl().toSeconds());
    }

    private Organization createOrganization(User user, String requestedName) {
        String name = requestedName != null && !requestedName.isBlank()
                ? requestedName.trim()
                : user.getName().trim();

        Organization org = new Organization();
        org.setName(name);
        org.setType(OrganizationType.COMPANY);
        organizations.save(org);

        Membership membership = new Membership();
        membership.setOrganization(org);
        membership.setUser(user);
        membership.setRole(MembershipRole.OWNER);
        memberships.save(membership);

        return org;
    }

    private void createDefaultPreferences(User user) {
        for (AlertCategory category : DEFAULT_PREF_CATEGORIES) {
            NotificationPreference pref = new NotificationPreference();
            pref.setUser(user);
            pref.setCategory(category);
            pref.setLeadDays(defaultLeadDays());
            notificationPrefs.save(pref);
        }
    }

    private void assignFreePlan(User user) {
        Optional<Plan> plan = plans.findByCode(PlanCode.FREE);
        if (plan.isEmpty()) {
            log.warn("Plano FREE não encontrado — verifique as migrações de referência.");
            return;
        }
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan.get());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartedAt(Instant.now());
        subscriptions.save(sub);
    }

    private AuthResponse toAuthResponse(User user, TokenPair tokens) {
        return new AuthResponse(
                UserView.from(user),
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.tokenType(),
                tokens.expiresInSeconds());
    }

    private static java.util.List<Integer> defaultLeadDays() {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int d : Enums.DEFAULT_EXPIRY_LEAD_DAYS) {
            list.add(d);
        }
        return list;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase();
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return phone.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Endereço do cliente, com a mesma regra do limitador.
     *
     * <p>Tem de ser a mesma, senão o sucesso devolvia a tentativa a uma chave
     * diferente daquela onde foi contada — e o contador nunca descia.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request != null ? request.getHeader("X-Forwarded-For") : null;
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String remote = request != null ? request.getRemoteAddr() : null;
        return remote != null ? remote : "desconhecido";
    }
}
