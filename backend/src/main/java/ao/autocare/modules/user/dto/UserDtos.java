package ao.autocare.modules.user.dto;

import ao.autocare.domain.enums.Enums.UserTheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {}

    public record UpdateProfileRequest(
            @Size(min = 2, max = 160) String name,
            @Size(max = 10) String locale,
            @Size(max = 3) String currency,
            UserTheme theme,
            @Size(max = 500) String avatarUrl) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 8, message = "A nova palavra-passe deve ter pelo menos 8 caracteres.")
            String newPassword) {}
}
