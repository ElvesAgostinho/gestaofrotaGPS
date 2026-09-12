package ao.autocare.modules.notification;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Notification;
import ao.autocare.domain.NotificationPreference;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.AlertCategory;
import ao.autocare.domain.enums.Enums.AlertSeverity;
import ao.autocare.domain.enums.Enums.EmailState;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.notification.dto.NotificationDtos.NotificationView;
import ao.autocare.modules.notification.dto.NotificationDtos.PreferenceView;
import ao.autocare.modules.notification.dto.NotificationDtos.UnreadCount;
import ao.autocare.modules.notification.dto.NotificationDtos.UpdatePreferenceRequest;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.NotificationPreferenceRepository;
import ao.autocare.repo.NotificationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria e entrega avisos.
 *
 * <p>Um aviso nasce sempre dentro da aplicação. O email é um extra: se não
 * houver servidor configurado, o aviso continua a existir e fica marcado como
 * {@code DEMO_MODE} — nunca como enviado.
 *
 * <p>Repetição é o maior risco de um sistema destes: o agendador passa de hora
 * a hora pelas mesmas tarefas vencidas. Por isso cada aviso traz a sua origem
 * ({@code sourceKind} + {@code sourceId}) e o segundo pedido para a mesma
 * origem e a mesma pessoa não cria nada.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final EmailSender email;

    public NotificationService(
            NotificationRepository notifications,
            NotificationPreferenceRepository preferences,
            MembershipRepository memberships,
            OrganizationRepository organizations,
            UserRepository users,
            EmailSender email) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
        this.email = email;
    }

    /** Tudo o que é preciso para criar um aviso. */
    public record Draft(
            String orgId,
            Asset asset,
            AlertCategory category,
            AlertSeverity severity,
            String title,
            String body,
            String sourceKind,
            String sourceId,
            String link) {

        public static Draft of(
                String orgId, AlertCategory category, AlertSeverity severity,
                String title, String body, String sourceKind, String sourceId, String link) {
            return new Draft(orgId, null, category, severity, title, body,
                    sourceKind, sourceId, link);
        }

        public Draft forAsset(Asset asset) {
            return new Draft(orgId, asset, category, severity, title, body,
                    sourceKind, sourceId, link);
        }
    }

    // ==== Criação =======================================================
    /**
     * Avisa uma pessoa. Devolve vazio se a pessoa já foi avisada desta origem
     * ou se desligou esta categoria.
     */
    @Transactional
    public Optional<Notification> notifyUser(User user, Draft draft) {
        if (user == null) {
            return Optional.empty();
        }
        if (draft.sourceKind() != null && draft.sourceId() != null
                && notifications.findByUserIdAndSourceKindAndSourceId(
                        user.getId(), draft.sourceKind(), draft.sourceId()).isPresent()) {
            return Optional.empty();
        }
        NotificationPreference pref = preferences
                .findByUserIdAndCategory(user.getId(), draft.category())
                .orElse(null);
        if (pref != null && !pref.isPush()) {
            return Optional.empty();
        }

        Notification n = new Notification();
        n.setUser(user);
        if (draft.orgId() != null) {
            n.setOrganization(organizations.getReferenceById(draft.orgId()));
        }
        n.setAsset(draft.asset());
        n.setCategory(draft.category());
        n.setSeverity(draft.severity());
        n.setTitle(draft.title());
        n.setBody(draft.body());
        n.setSourceKind(draft.sourceKind());
        n.setSourceId(draft.sourceId());
        n.setLink(draft.link());
        notifications.save(n);

        if (pref == null || pref.isEmail()) {
            deliverByEmail(user, n);
        }
        return Optional.of(n);
    }

    /**
     * Avisa quem manda na empresa (donos e gestores). É para eles que vão os
     * alertas de frota: excesso de velocidade, aparelho calado, stock no mínimo.
     */
    @Transactional
    public int notifyManagers(Draft draft) {
        List<Membership> team = memberships.findTeam(draft.orgId());
        int sent = 0;
        for (Membership m : team) {
            if (m.isSuspended() || !m.getRole().covers(MembershipRole.MANAGER)) {
                continue;
            }
            if (notifyUser(m.getUser(), draft).isPresent()) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * O problema que deu origem a estes avisos deixou de existir: apaga-os, para
     * que a próxima vez que aconteça volte a avisar.
     */
    @Transactional
    public int resolve(String sourceKind, String sourceId) {
        if (sourceKind == null || sourceId == null) {
            return 0;
        }
        return notifications.deleteBySource(sourceKind, sourceId);
    }

    private void deliverByEmail(User user, Notification n) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        if (!email.isConfigured()) {
            n.setEmailState(EmailState.DEMO_MODE);
            return;
        }
        try {
            boolean ok = email.send(user.getEmail(), n.getTitle(),
                    n.getBody() != null ? n.getBody() : n.getTitle());
            n.setEmailState(ok ? EmailState.SENT : EmailState.FAILED);
            n.setEmailAt(Instant.now());
        } catch (RuntimeException e) {
            // Uma falha de email não pode desfazer o aviso dentro da aplicação.
            log.warn("Falha ao enviar email para {}: {}", user.getEmail(), e.toString());
            n.setEmailState(EmailState.FAILED);
            n.setEmailAt(Instant.now());
        }
    }

    // ==== Consulta ======================================================
    @Transactional(readOnly = true)
    public PagedResponse<NotificationView> list(String userId, boolean onlyUnread, Pageable page) {
        return PagedResponse.of(
                (onlyUnread
                        ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, page)
                        : notifications.findByUserIdOrderByCreatedAtDesc(userId, page))
                        .map(NotificationView::of));
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(String userId) {
        return new UnreadCount(notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationView markRead(String userId, String id) {
        Notification n = notifications.findById(id)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notificação não encontrada."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return NotificationView.of(n);
    }

    @Transactional
    public UnreadCount markAllRead(String userId) {
        notifications.markAllRead(userId, Instant.now());
        return new UnreadCount(0);
    }

    // ==== Preferências ==================================================
    @Transactional(readOnly = true)
    public List<PreferenceView> listPreferences(String userId) {
        List<NotificationPreference> stored = preferences.findByUserId(userId);
        List<PreferenceView> out = new ArrayList<>();
        for (AlertCategory category : AlertCategory.values()) {
            NotificationPreference pref = stored.stream()
                    .filter(item -> item.getCategory() == category)
                    .findFirst().orElse(null);
            // Sem preferência gravada vale o padrão: dentro da aplicação e por email.
            out.add(new PreferenceView(category, label(category),
                    pref == null || pref.isPush(),
                    pref == null || pref.isEmail()));
        }
        return out;
    }

    @Transactional
    public PreferenceView updatePreference(
            String userId, AlertCategory category, UpdatePreferenceRequest req) {

        NotificationPreference pref = preferences.findByUserIdAndCategory(userId, category)
                .orElseGet(() -> {
                    NotificationPreference fresh = new NotificationPreference();
                    fresh.setUser(users.getReferenceById(userId));
                    fresh.setCategory(category);
                    return fresh;
                });
        if (req.inApp() != null) pref.setPush(req.inApp());
        if (req.email() != null) pref.setEmail(req.email());
        preferences.save(pref);
        return new PreferenceView(category, label(category), pref.isPush(), pref.isEmail());
    }

    /** Nome da categoria em português, para a interface. */
    public static String label(AlertCategory category) {
        return switch (category) {
            case MAINTENANCE -> "Manutenção";
            case WORK_ORDER -> "Ordens de manutenção";
            case STOCK -> "Peças e stock";
            case GPS -> "GPS e frota";
            case DOCUMENT -> "Documentos";
            case EXPENSE -> "Custos";
            case INSURANCE -> "Seguros";
            case INSPECTION -> "Inspeções";
            case TIRE -> "Pneus";
            case BATTERY -> "Baterias";
            case SYSTEM -> "Sistema";
        };
    }
}
