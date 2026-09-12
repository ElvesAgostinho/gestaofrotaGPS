package ao.autocare.modules.user;

import ao.autocare.common.ApiException;
import ao.autocare.domain.User;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.auth.dto.UserView;
import ao.autocare.modules.user.dto.UserDtos.ChangePasswordRequest;
import ao.autocare.modules.user.dto.UserDtos.UpdateProfileRequest;
import ao.autocare.repo.RefreshTokenRepository;
import ao.autocare.repo.SubscriptionRepository;
import ao.autocare.repo.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            SubscriptionRepository subscriptions,
            PasswordEncoder passwordEncoder,
            AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public UserView updateProfile(String userId, UpdateProfileRequest req) {
        User user = load(userId);
        if (req.name() != null) user.setName(req.name().trim());
        if (req.locale() != null) user.setLocale(req.locale());
        if (req.currency() != null) user.setCurrency(req.currency());
        if (req.theme() != null) user.setTheme(req.theme());
        if (req.avatarUrl() != null) user.setAvatarUrl(req.avatarUrl());
        audit.recordForUser(userId, "user.update_profile", "User", userId, null);
        return UserView.from(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest req) {
        User user = load(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("A palavra-passe atual não está correta.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(userId, java.time.Instant.now());
        audit.recordForUser(userId, "user.change_password", "User", userId, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportData(String userId) {
        User user = load(userId);
        return Map.of(
                "user", UserView.from(user),
                "subscriptions", subscriptions.findByUserId(userId).stream()
                        .map(s -> Map.of(
                                "plan", s.getPlan().getCode().name(),
                                "status", s.getStatus().name(),
                                "startedAt", s.getStartedAt()))
                        .toList(),
                "vehicles", List.of(),
                "note", "A exportação completa de viaturas e histórico fica disponível "
                        + "quando esses módulos forem ativados.");
    }

    @Transactional
    public void deleteAccount(String userId) {
        User user = load(userId);
        audit.recordForUser(userId, "user.delete_account", "User", userId,
                "Conta eliminada a pedido do utilizador");
        users.delete(user);
    }

    private User load(String userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada."));
    }
}
