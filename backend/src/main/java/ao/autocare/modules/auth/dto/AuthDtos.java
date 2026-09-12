package ao.autocare.modules.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs de entrada e saída da autenticação. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Indique o seu nome.")
            @Size(max = 160)
            String name,

            @Email(message = "Indique um email válido.")
            @Size(max = 190)
            String email,

            @Size(max = 40)
            String phone,

            @NotBlank(message = "Indique uma palavra-passe.")
            @Size(min = 8, message = "A palavra-passe deve ter pelo menos 8 caracteres.")
            String password,

            boolean acceptTerms,

            /** Nome da empresa. Opcional — se vazio, usa o nome do utilizador. */
            @Size(max = 160)
            String organizationName) {

        @AssertTrue(message = "Indique o email ou o telefone.")
        public boolean isContactProvided() {
            return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Indique o email ou o telefone.")
            String identifier,

            @NotBlank(message = "Indique a palavra-passe.")
            String password) {}

    public record RefreshRequest(
            @NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(
            @NotBlank String identifier) {}

    public record ResetPasswordRequest(
            @NotBlank String token,

            @NotBlank
            @Size(min = 8, message = "A palavra-passe deve ter pelo menos 8 caracteres.")
            String password) {}

    public record TokenPair(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds) {}

    public record AuthResponse(
            UserView user,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds) {}

    public record ForgotPasswordResponse(
            String message,
            boolean demoMode,
            String resetToken) {}
}
