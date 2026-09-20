package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.UserTheme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends TimestampedEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 190)
    private String email;

    @Column(length = 40)
    private String phone;

    /**
      * O identificador curto com que se entra sem email — «MOT-0412».
      *
      * <p>Existe para quem não tem email: o gestor cria a conta, entrega o
      * identificador e a palavra-passe em papel, e o motorista entra com isso.
      */
    @Column(name = "login_id", length = 20)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** A palavra-passe foi posta por outra pessoa; tem de ser trocada à entrada. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false, length = 10)
    private String locale = "pt-AO";

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserTheme theme = UserTheme.SYSTEM;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "accepted_terms_at")
    private Instant acceptedTermsAt;

    @Column(name = "accepted_privacy_at")
    private Instant acceptedPrivacyAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
