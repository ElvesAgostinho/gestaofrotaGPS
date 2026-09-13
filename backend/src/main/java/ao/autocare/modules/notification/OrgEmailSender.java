package ao.autocare.modules.notification;

import ao.autocare.config.AutoCareProperties;
import ao.autocare.domain.IntegrationSettings;
import ao.autocare.modules.integration.SmtpFactory;
import ao.autocare.repo.IntegrationSettingsRepository;
import ao.autocare.security.SecretBox;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * O email da própria empresa (Configurações → Integrações → Email). Quando
 * uma empresa configurou o seu servidor, os avisos saem por ele, com o seu
 * remetente — não pelo da plataforma. Sem configuração própria, cai-se no
 * {@link EmailSender} da plataforma.
 */
@Component
public class OrgEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OrgEmailSender.class);

    private final IntegrationSettingsRepository settings;
    private final SecretBox cofre;
    private final TemplateEngine templateEngine;
    private final AutoCareProperties props;

    public OrgEmailSender(IntegrationSettingsRepository settings, SecretBox cofre,
            TemplateEngine templateEngine, AutoCareProperties props) {
        this.settings = settings;
        this.cofre = cofre;
        this.templateEngine = templateEngine;
        this.props = props;
    }

    public boolean isConfigured(String orgId) {
        return orgId != null && settings.findByOrganizationId(orgId).map(IntegrationSettings::hasSmtp).orElse(false);
    }

    /**
     * Envia pelo servidor da empresa. Vazio quando a empresa não tem email
     * próprio (então é a plataforma que decide); {@code true}/{@code false}
     * quando tem e o envio foi aceite/recusado.
     */
    public Optional<Boolean> send(String orgId, String to, String subject, String body) {
        if (orgId == null) {
            return Optional.empty();
        }
        IntegrationSettings s = settings.findByOrganizationId(orgId).orElse(null);
        if (s == null || !s.hasSmtp()) {
            return Optional.empty();
        }
        try {
            JavaMailSenderImpl remetente = SmtpFactory.build(s, cofre);
            MimeMessage msg = remetente.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            h.setFrom(s.getSmtpFrom(), s.getSmtpFromName() != null ? s.getSmtpFromName() : props.app().name());
            h.setTo(to);
            h.setSubject(subject);
            h.setText(body, render(subject, body));
            remetente.send(msg);
            return Optional.of(true);
        } catch (Exception e) {
            log.warn("Falha ao enviar email pela empresa {} para {}: {}", orgId, to, e.toString());
            return Optional.of(false);
        }
    }

    private String render(String subject, String body) {
        Context context = new Context();
        context.setVariable("title", subject);
        context.setVariable("body", body);
        context.setVariable("appName", props.app().name());
        context.setVariable("webUrl", props.app().webUrl());
        context.setVariable("year", java.time.Year.now().getValue());
        return templateEngine.process("email/notification", context);
    }
}
