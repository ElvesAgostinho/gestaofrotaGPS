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
            ao.autocare.domain.enums.Enums.PhoneState phoneState,
            boolean read,
            Instant createdAt) {

        public static NotificationView of(Notification n) {
            return new NotificationView(
                    n.getId(), n.getCategory(),
                    ao.autocare.modules.notification.NotificationService.label(n.getCategory()),
                    n.getSeverity(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getAsset() != null ? n.getAsset().getId() : null,
                    n.getAsset() != null ? n.getAsset().getTag() : null,
                    n.getEmailState(), n.getPhoneState(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {}

    public record PreferenceView(
            AlertCategory category, String label, boolean inApp, boolean email, boolean phone) {}

    public record UpdatePreferenceRequest(Boolean inApp, Boolean email, Boolean phone) {}

    /**
     * Os canais que este ambiente tem de facto — para o ecrã dizer a verdade:
     * «email não configurado», «WhatsApp ligado», «sem número no seu perfil».
     */
    public record ChannelsView(
            boolean emailConfigured,
            boolean phoneConfigured,
            /** «WhatsApp» ou «SMS»; nulo sem canal. */
            String phoneChannel,
            /** O número do utilizador, tal como está no perfil (nulo se não tiver). */
            String myPhone) {}

    public record TestMessageResult(boolean sent, String message) {}
}
