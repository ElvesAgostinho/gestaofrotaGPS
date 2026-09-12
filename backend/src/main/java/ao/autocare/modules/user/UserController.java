package ao.autocare.modules.user;

import ao.autocare.modules.auth.dto.UserView;
import ao.autocare.modules.user.dto.UserDtos.ChangePasswordRequest;
import ao.autocare.modules.user.dto.UserDtos.UpdateProfileRequest;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Utilizadores")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @Operation(summary = "Atualizar o meu perfil")
    @PatchMapping("/me")
    public UserView updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return users.updateProfile(principal.id(), req);
    }

    @Operation(summary = "Alterar a palavra-passe")
    @PostMapping("/me/change-password")
    public Map<String, String> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        users.changePassword(principal.id(), req);
        return Map.of("message", "Palavra-passe alterada. Inicie sessão novamente.");
    }

    @Operation(summary = "Exportar todos os meus dados")
    @GetMapping("/me/export")
    public Map<String, Object> exportData(@AuthenticationPrincipal AuthPrincipal principal) {
        return users.exportData(principal.id());
    }

    @Operation(summary = "Eliminar a minha conta e todos os dados")
    @DeleteMapping("/me")
    public Map<String, String> deleteAccount(@AuthenticationPrincipal AuthPrincipal principal) {
        users.deleteAccount(principal.id());
        return Map.of("message", "A sua conta foi eliminada.");
    }
}
