package ao.autocare.modules.auth.dto;

import ao.autocare.domain.User;
import java.time.Instant;

/** Representação pública de um utilizador (nunca inclui a password_hash). */
public record UserView(
        String id,
        String name,
        String email,
        String phone,
        /** O identificador curto de entrada, quando a conta tem um. */
        String loginId,
        /** Entrou com uma palavra-passe posta pelo gestor: tem de a trocar. */
        boolean mustChangePassword,
        String avatarUrl,
        String locale,
        String currency,
        String theme,
        boolean admin,
        Instant emailVerifiedAt,
        Instant phoneVerifiedAt,
        Instant createdAt) {

    public static UserView from(User u) {
        return new UserView(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                u.getLoginId(),
                u.isMustChangePassword(),
                u.getAvatarUrl(),
                u.getLocale(),
                u.getCurrency(),
                u.getTheme().name(),
                u.isAdmin(),
                u.getEmailVerifiedAt(),
                u.getPhoneVerifiedAt(),
                u.getCreatedAt());
    }
}
