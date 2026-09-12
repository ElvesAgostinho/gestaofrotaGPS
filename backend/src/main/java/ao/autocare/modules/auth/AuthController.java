package ao.autocare.modules.auth;

import ao.autocare.modules.auth.dto.AuthDtos.AuthResponse;
import ao.autocare.modules.auth.dto.AuthDtos.ForgotPasswordRequest;
import ao.autocare.modules.auth.dto.AuthDtos.ForgotPasswordResponse;
import ao.autocare.modules.auth.dto.AuthDtos.LoginRequest;
import ao.autocare.modules.auth.dto.AuthDtos.RefreshRequest;
import ao.autocare.modules.auth.dto.AuthDtos.RegisterRequest;
import ao.autocare.modules.auth.dto.AuthDtos.ResetPasswordRequest;
import ao.autocare.modules.auth.dto.AuthDtos.TokenPair;
import ao.autocare.modules.auth.dto.UserView;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Criar conta")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, http);
    }

    @Operation(summary = "Iniciar sessão (email ou telefone)")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http);
    }

    @Operation(summary = "Renovar o token de acesso")
    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), http);
    }

    @Operation(summary = "Terminar sessão")
    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return Map.of("message", "Sessão terminada.");
    }

    @Operation(summary = "Pedir reposição da palavra-passe")
    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return auth.forgotPassword(req.identifier());
    }

    @Operation(summary = "Definir nova palavra-passe")
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.password());
        return Map.of("message", "Palavra-passe atualizada. Já pode iniciar sessão.");
    }

    @Operation(summary = "Dados da conta autenticada")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthPrincipal principal) {
        return auth.getProfile(principal.id());
    }
}
