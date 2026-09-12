package ao.autocare.modules.notification.dto;

import ao.autocare.domain.Notification;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.EmailState;
import java.time.Instant;

/** Pedidos e respostas das notificações. */
public final class NotificationDtos {

    private NotificationDtos() {}

    public record NotificationView(
            String id,
            AlertCategory category,
            String categoryLabel,
            AlertSeverity severity,
            String title,
            String body,
            String link,
            String assetId,
            String assetTag,
            EmailState emailState,
            boolean read,
            Instant createdAt) {

        public static NotificationView of(Notification n) {
            return new NotificationView(
                    n.getId(), n.getCategory(),
                    ao.autocare.modules.notification.NotificationService.label(n.getCategory()),
                    n.getSeverity(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getAsset() != null ? n.getAsset().getId() : null,
                    n.getAsset() != null ? n.getAsset().getTag() : null,
                    n.getEmailState(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {}

    public record PreferenceView(
            AlertCategory category, String label, boolean inApp, boolean email) {}

    public record UpdatePreferenceRequest(Boolean inApp, Boolean email) {}
}
