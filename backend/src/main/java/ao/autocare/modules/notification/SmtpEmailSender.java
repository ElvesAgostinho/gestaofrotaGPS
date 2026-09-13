package ao.autocare.modules.notification;

import ao.autocare.config.AutoCareProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Envio real por SMTP, com o corpo em HTML.
 *
 * <p>Só entra em cena quando {@code MAIL_HOST} está definido. Sem isso continua
 * a valer o {@link DemoEmailSender}, e nada é marcado como enviado — é a mesma
 * regra de sempre: o sistema não finge integrações que não tem.
 *
 * <p>O HTML é gerado a partir de um modelo Thymeleaf e acompanhado da versão em
 * texto simples. Alguns clientes de email não mostram HTML, e um email vazio é
 * pior do que um email feio.
 */
@Component
@Primary
// Atenção: @ConditionalOnProperty considera "presente" uma propriedade definida
// com valor vazio, e MAIL_HOST tem um valor por omissão vazio no application.yml.
// Com essa anotação, quem não configurasse email teria esta classe ativa e todos
// os avisos marcados como FALHADOS em vez de MODO DEMONSTRAÇÃO — exatamente o
// engano que se quer evitar. Daí a condição ser sobre o conteúdo, não a presença.
@ConditionalOnExpression("'${spring.mail.host:}' != ''")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AutoCareProperties props;
    private final String from;
    private final String fromName;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AutoCareProperties props,
            @Value("${autocare.mail.from:${spring.mail.username:nao-responder@autocare.ao}}")
            String from,
            @Value("${autocare.mail.from-name:AutoCare}") String fromName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.props = props;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public boolean send(String to, String subject, String body) {
        return send(to, subject, body, null, null, null);
    }

    @Override
    public boolean send(String to, String subject, String body, String attachmentName, byte[] attachment,
            String attachmentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");

            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            // Texto simples e HTML: o cliente de email escolhe o que consegue ler.
            helper.setText(body, render(subject, body));
            if (attachment != null && attachmentName != null) {
                helper.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment),
                        attachmentType != null ? attachmentType : "application/octet-stream");
            }

            mailSender.send(message);
            return true;
        } catch (Exception e) {
            // Quem chama regista FAILED; aqui só se explica porquê.
            log.warn("Falha ao enviar email para {}: {}", to, e.toString());
            return false;
        }
    }

    @Override
    public boolean isConfigured() {
        return true;
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
