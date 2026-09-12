package ao.autocare.modules.team;

import ao.autocare.modules.auth.dto.AuthDtos.AuthResponse;
import ao.autocare.modules.team.dto.TeamDtos.AcceptInvitationRequest;
import ao.autocare.modules.team.dto.TeamDtos.InvitationPreview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos do convite: quem é convidado ainda não tem conta, por isso
 * chega aqui sem sessão iniciada. O token do convite é a credencial.
 */
@Tag(name = "Convites")
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final TeamService team;

    public InvitationController(TeamService team) {
        this.team = team;
    }

    @Operation(summary = "Ver a que empresa e com que papel o convite dá acesso")
    @GetMapping("/{token}")
    public InvitationPreview preview(@PathVariable String token) {
        return team.preview(token);
    }

    @Operation(summary = "Aceitar o convite criando uma conta nova")
    @PostMapping("/{token}/accept")
    public AuthResponse accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest req,
            HttpServletRequest http) {
        return team.acceptAsNewUser(token, req, http);
    }
}
