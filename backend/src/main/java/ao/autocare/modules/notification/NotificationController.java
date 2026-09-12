package ao.autocare.modules.notification;

import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.modules.notification.dto.NotificationDtos.NotificationView;
import ao.autocare.modules.notification.dto.NotificationDtos.PreferenceView;
import ao.autocare.modules.notification.dto.NotificationDtos.UnreadCount;
import ao.autocare.modules.notification.dto.NotificationDtos.UpdatePreferenceRequest;
import ao.autocare.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificações da pessoa autenticada. Não há papel mínimo: cada um vê e gere
 * apenas os seus próprios avisos.
 */
@Tag(name = "Notificações")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Operation(summary = "As minhas notificações")
    @GetMapping
    public PagedResponse<NotificationView> list(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return notifications.list(p.id(), unread,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Quantas ainda não li")
    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal AuthPrincipal p) {
        return notifications.unreadCount(p.id());
    }

    @Operation(summary = "Marcar uma notificação como lida")
    @PostMapping("/{id}/read")
    public NotificationView markRead(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return notifications.markRead(p.id(), id);
    }

    @Operation(summary = "Marcar todas como lidas")
    @PostMapping("/read-all")
    public UnreadCount markAllRead(@AuthenticationPrincipal AuthPrincipal p) {
        return notifications.markAllRead(p.id());
    }

    @Operation(summary = "As minhas preferências por categoria")
    @GetMapping("/preferences")
    public List<PreferenceView> preferences(@AuthenticationPrincipal AuthPrincipal p) {
        return notifications.listPreferences(p.id());
    }

    @Operation(summary = "Ligar ou desligar uma categoria")
    @PatchMapping("/preferences/{category}")
    public PreferenceView updatePreference(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable AlertCategory category,
            @Valid @RequestBody UpdatePreferenceRequest req) {
        return notifications.updatePreference(p.id(), category, req);
    }
}
