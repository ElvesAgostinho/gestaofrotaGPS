package ao.autocare.modules.integration;

import ao.autocare.domain.IntegrationSettings;
import ao.autocare.security.SecretBox;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Constrói o remetente SMTP a partir do que a empresa configurou. Usado pelo
 * botão «Testar» e — o que importa — pelo envio real dos avisos.
 */
public final class SmtpFactory {

    private SmtpFactory() {}

    public static JavaMailSenderImpl build(IntegrationSettings s, SecretBox cofre) {
        JavaMailSenderImpl remetente = new JavaMailSenderImpl();
        remetente.setHost(s.getSmtpHost());
        remetente.setPort(s.getSmtpPort() != null ? s.getSmtpPort() : 587);
        remetente.setUsername(s.getSmtpUsername());
        remetente.setPassword(cofre.decrypt(s.getSmtpPasswordEnc()));
        remetente.setDefaultEncoding("UTF-8");

        Properties p = remetente.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", String.valueOf(s.getSmtpUsername() != null));
        p.put("mail.smtp.connectiontimeout", "12000");
        p.put("mail.smtp.timeout", "12000");
        p.put("mail.smtp.writetimeout", "12000");

        String seguranca = s.getSmtpSecurity() != null ? s.getSmtpSecurity() : "STARTTLS";
        if ("SSL".equals(seguranca)) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(seguranca)) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        return remetente;
    }
}
