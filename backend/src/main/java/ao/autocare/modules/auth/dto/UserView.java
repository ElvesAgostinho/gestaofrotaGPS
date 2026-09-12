package ao.autocare.modules.auth.dto;

import ao.autocare.domain.User;
import java.time.Instant;

/** Representação pública de um utilizador (nunca inclui a password_hash). */
public record UserView(
        String id,
        String name,
        String email,
        String phone,
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
