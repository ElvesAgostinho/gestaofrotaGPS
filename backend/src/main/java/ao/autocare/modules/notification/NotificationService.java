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
    private final OrgEmailSender orgEmail;
    private final ao.autocare.modules.messaging.PhoneMessaging phone;

    private final PushService push;

    public NotificationService(
            PushService push,
            NotificationRepository notifications,
            NotificationPreferenceRepository preferences,
            MembershipRepository memberships,
            OrganizationRepository organizations,
            UserRepository users,
            EmailSender email,
            OrgEmailSender orgEmail,
            ao.autocare.modules.messaging.PhoneMessaging phone) {
        this.push = push;
        this.notifications = notifications;
        this.preferences = preferences;
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
        this.email = email;
        this.orgEmail = orgEmail;
        this.phone = phone;
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
            String link,
            /** Só dentro da aplicação: o conteúdo já seguiu por outro canal (ex.: o PDF por email). */
            boolean inAppOnly) {

        public static Draft of(
                String orgId, AlertCategory category, AlertSeverity severity,
                String title, String body, String sourceKind, String sourceId, String link) {
            return new Draft(orgId, null, category, severity, title, body,
                    sourceKind, sourceId, link, false);
        }

        public Draft forAsset(Asset asset) {
            return new Draft(orgId, asset, category, severity, title, body,
                    sourceKind, sourceId, link, inAppOnly);
        }

        public Draft soNaAplicacao() {
            return new Draft(orgId, asset, category, severity, title, body,
                    sourceKind, sourceId, link, true);
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

        // O aviso vai ao telemóvel mesmo com a aplicação fechada: é aqui que
        // deixa de ser um sino que ninguém vê e passa a ser um aviso a sério.
        if (pref == null || pref.isPush()) {
            try {
                push.enviar(n);
            } catch (RuntimeException e) {
                // Um aviso que não sai não pode estragar o que o originou.
                log.debug("Push não enviado: {}", e.getMessage());
            }
        }

        if ((pref == null || pref.isEmail()) && !draft.inAppOnly()) {
            deliverByEmail(user, n);
        }
        // Ao telemóvel só vai o que é grave: ninguém quer o WhatsApp a apitar por um aviso leve.
        if ((pref == null || pref.isSms()) && draft.severity() != AlertSeverity.INFO && !draft.inAppOnly()) {
            deliverByPhone(user, n);
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
        String corpo = n.getBody() != null ? n.getBody() : n.getTitle();
        String orgId = n.getOrganization() != null ? n.getOrganization().getId() : null;
        try {
            // Primeiro o email que a empresa configurou; só depois o da plataforma.
            Optional<Boolean> pelaEmpresa = orgEmail.send(orgId, user.getEmail(), n.getTitle(), corpo);
            if (pelaEmpresa.isPresent()) {
                n.setEmailState(pelaEmpresa.get() ? EmailState.SENT : EmailState.FAILED);
                n.setEmailAt(Instant.now());
                return;
            }
            if (!email.isConfigured()) {
                n.setEmailState(EmailState.DEMO_MODE);
                return;
            }
            boolean ok = email.send(user.getEmail(), n.getTitle(), corpo);
            n.setEmailState(ok ? EmailState.SENT : EmailState.FAILED);
            n.setEmailAt(Instant.now());
        } catch (RuntimeException e) {
            // Uma falha de email não pode desfazer o aviso dentro da aplicação.
            log.warn("Falha ao enviar email para {}: {}", user.getEmail(), e.toString());
            n.setEmailState(EmailState.FAILED);
            n.setEmailAt(Instant.now());
        }
    }

    private void deliverByPhone(User user, Notification n) {
        if (!phone.isConfigured()) {
            n.setPhoneState(ao.autocare.domain.enums.Enums.PhoneState.NO_CHANNEL);
            return;
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            n.setPhoneState(ao.autocare.domain.enums.Enums.PhoneState.NO_PHONE);
            return;
        }
        try {
            boolean ok = phone.send(user.getPhone(), textoParaTelemovel(n));
            n.setPhoneState(ok ? ao.autocare.domain.enums.Enums.PhoneState.SENT
                    : ao.autocare.domain.enums.Enums.PhoneState.FAILED);
        } catch (RuntimeException e) {
            log.warn("Falha ao enviar para o telemóvel de {}: {}", user.getEmail(), e.toString());
            n.setPhoneState(ao.autocare.domain.enums.Enums.PhoneState.FAILED);
        }
        n.setPhoneAt(Instant.now());
    }

    /** Curto, porque é para ler no telemóvel: empresa, título, corpo, ativo. */
    private static String textoParaTelemovel(Notification n) {
        StringBuilder sb = new StringBuilder();
        if (n.getOrganization() != null && n.getOrganization().getName() != null) {
            sb.append(n.getOrganization().getName()).append(" — ");
        }
        sb.append(n.getTitle());
        if (n.getAsset() != null && n.getAsset().getTag() != null) {
            sb.append(" [").append(n.getAsset().getTag()).append(']');
        }
        if (n.getBody() != null && !n.getBody().isBlank()) {
            sb.append('\n').append(n.getBody());
        }
        String t = sb.toString();
        return t.length() > 900 ? t.substring(0, 897) + "…" : t;
    }

    /** Os canais que existem de facto neste ambiente, para o ecrã não prometer o que não há. */
    @Transactional(readOnly = true)
    public ao.autocare.modules.notification.dto.NotificationDtos.ChannelsView channels(String userId) {
        User u = users.findById(userId).orElse(null);
        String meuTelemovel = u != null && u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone() : null;
        return new ao.autocare.modules.notification.dto.NotificationDtos.ChannelsView(
                email.isConfigured(), phone.isConfigured(), phone.nome(), meuTelemovel);
    }

    /** Como {@link #channels(String)}, mas a saber se a empresa tem email próprio. */
    @Transactional(readOnly = true)
    public ao.autocare.modules.notification.dto.NotificationDtos.ChannelsView channels(String userId, String orgId) {
        User u = users.findById(userId).orElse(null);
        String meuTelemovel = u != null && u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone() : null;
        return new ao.autocare.modules.notification.dto.NotificationDtos.ChannelsView(
                email.isConfigured() || orgEmail.isConfigured(orgId), phone.isConfigured(), phone.nome(), meuTelemovel);
    }

    /** Uma mensagem de teste para o próprio telemóvel: a única forma honesta de saber que chega. */
    @Transactional(readOnly = true)
    public ao.autocare.modules.notification.dto.NotificationDtos.TestMessageResult sendTestToPhone(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Utilizador não encontrado."));
        if (!phone.isConfigured()) {
            return new ao.autocare.modules.notification.dto.NotificationDtos.TestMessageResult(false,
                    "Esta plataforma ainda não tem WhatsApp nem SMS configurados.");
        }
        if (u.getPhone() == null || u.getPhone().isBlank()) {
            return new ao.autocare.modules.notification.dto.NotificationDtos.TestMessageResult(false,
                    "Não tem número de telemóvel no seu perfil.");
        }
        boolean ok = phone.send(u.getPhone(), "IMBONDEIRO OS — mensagem de teste. Se a recebeu, os avisos vão chegar aqui.");
        return new ao.autocare.modules.notification.dto.NotificationDtos.TestMessageResult(ok, ok
                ? "Mensagem aceite pelo " + phone.nome() + ". Confirme no telemóvel " + u.getPhone() + "."
                : "O " + phone.nome() + " recusou a mensagem para " + u.getPhone()
                        + ". Confirme o número (formato internacional, ex.: +244 923 000 000) e as credenciais da plataforma.");
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
                    pref == null || pref.isEmail(),
                    pref == null || pref.isSms()));
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
        if (req.phone() != null) pref.setSms(req.phone());
        preferences.save(pref);
        return new PreferenceView(category, label(category), pref.isPush(), pref.isEmail(), pref.isSms());
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
