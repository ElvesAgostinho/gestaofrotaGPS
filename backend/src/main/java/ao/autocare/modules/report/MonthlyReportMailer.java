package ao.autocare.modules.report;

import ao.autocare.domain.Membership;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.messaging.PhoneMessaging;
import ao.autocare.modules.notification.EmailSender;
import ao.autocare.modules.notification.NotificationService;
import ao.autocare.modules.notification.OrgEmailSender;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.OrganizationRepository;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * No dia 1 de cada mês, o relatório do mês anterior segue sozinho: PDF por
 * email (pelo servidor da empresa, ou o da plataforma) e um resumo de cinco
 * linhas no telemóvel de quem gere. Um aviso dentro da aplicação aponta para
 * o PDF, para quem não lê nem um nem outro.
 */
@Component
public class MonthlyReportMailer {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportMailer.class);
    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final MonthlyReportService reports;
    private final OrgEmailSender orgEmail;
    private final EmailSender email;
    private final PhoneMessaging phone;
    private final NotificationService notifications;

    public MonthlyReportMailer(OrganizationRepository organizations, MembershipRepository memberships,
            MonthlyReportService reports, OrgEmailSender orgEmail, EmailSender email, PhoneMessaging phone,
            NotificationService notifications) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.reports = reports;
        this.orgEmail = orgEmail;
        this.email = email;
        this.phone = phone;
        this.notifications = notifications;
    }

    /** Envia (ou reenvia, com {@code forcar}) o relatório de um mês a uma empresa. Devolve quantas pessoas receberam. */
    @Transactional
    public int enviar(String orgId, YearMonth mes, boolean forcar) {
        Organization o = organizations.findById(orgId).orElseThrow();
        if (!o.isMonthlyReportEnabled() && !forcar) {
            return 0;
        }
        if (!forcar && mes.toString().equals(o.getMonthlyReportSentFor())) {
            return 0;
        }
        if (o.blockedReason(java.time.LocalDate.now(FUSO)) != null && !forcar) {
            return 0; // empresa suspensa ou com licença vencida: nada sai
        }
        MonthlyReportService.Doc doc = reports.build(orgId, mes, true);
        byte[] pdf = reports.pdf(orgId, mes, true);
        String assunto = o.getName() + " — relatório mensal da frota, " + doc.monthLabel();
        String resumo = reports.resumoCurto(o, doc);
        String nomeFicheiro = "relatorio-mensal-" + mes + ".pdf";

        int recebidos = 0;
        for (Membership m : memberships.findTeam(orgId)) {
            if (m.isSuspended() || !m.getRole().covers(MembershipRole.MANAGER)) {
                continue;
            }
            User u = m.getUser();
            boolean chegou = false;
            if (u.getEmail() != null && !u.getEmail().isBlank()) {
                Optional<Boolean> pelaEmpresa = orgEmail.send(orgId, u.getEmail(), assunto, resumo, nomeFicheiro, pdf, "application/pdf");
                chegou = pelaEmpresa.orElseGet(() -> email.isConfigured()
                        && email.send(u.getEmail(), assunto, resumo, nomeFicheiro, pdf, "application/pdf"));
            }
            if (phone.isConfigured() && u.getPhone() != null && !u.getPhone().isBlank()) {
                chegou |= phone.send(u.getPhone(), resumo);
            }
            // Dentro da aplicação fica sempre, com o link para o PDF.
            notifications.notifyUser(u, NotificationService.Draft.of(orgId,
                    ao.autocare.domain.enums.Enums.AlertCategory.SYSTEM,
                    ao.autocare.domain.enums.Enums.AlertSeverity.INFO,
                    "Relatório mensal — " + doc.monthLabel(),
                    resumo, "monthly_report", mes.toString(), "/relatorios?mes=" + mes).soNaAplicacao());
            if (chegou) {
                recebidos++;
            }
        }
        o.setMonthlyReportSentFor(mes.toString());
        log.info("Relatório mensal {} da empresa {} enviado a {} pessoa(s)", mes, o.getName(), recebidos);
        return recebidos;
    }
}
