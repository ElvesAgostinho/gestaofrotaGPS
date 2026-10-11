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

        /*
         * O nome com que nos apresentamos no EHLO.
         *
         * Sem isto, o JavaMail usa o nome da maquina -- que dentro de um
         * contentor e algo como "57b5d892cc74". Um cliente de email a
         * apresentar-se com um identificador de contentor parece o que nao e,
         * e ha servidores e camadas anti-abuso que o penalizam. Usa-se o
         * dominio do proprio remetente, que e o que qualquer cliente faz.
         */
        String dominio = dominioDe(s.getSmtpFrom());
        if (dominio != null) {
            p.put("mail.smtp.localhost", dominio);
        }
        p.put("mail.smtp.auth", String.valueOf(s.getSmtpUsername() != null));
        /*
         * A ordem dos metodos de autenticacao.
         *
         * Por omissao o JavaMail tenta LOGIN primeiro; o Nodemailer e o
         * PHPMailer -- com que a maior parte dos fornecedores e testada --
         * usam PLAIN. Ha servidores que anunciam os dois e so aceitam PLAIN na
         * pratica, e nesses o mesmo utilizador e a mesma palavra-passe
         * funcionavam noutras aplicacoes e eram recusados aqui, sem nada que o
         * explicasse. Pedir PLAIN primeiro poe-nos a falar como toda a gente;
         * LOGIN fica a seguir, para os servidores que so tem esse.
         */
        p.put("mail.smtp.auth.mechanisms", "PLAIN LOGIN");

        /*
         * Deixa o registo de depuracao mostrar o passo da autenticacao.
         *
         * So tem efeito quando a depuracao esta ligada, e isso so acontece no
         * botao de testar -- onde o dialogo e capturado para memoria, limpo
         * das credenciais e devolvido ao ecra. Nunca vai para ficheiro nenhum.
         */
        p.put("mail.debug.auth", "true");

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

    /** O dominio de um endereco de email, ou nulo se nao houver. */
    private static String dominioDe(String endereco) {
        if (endereco == null || !endereco.contains("@")) {
            return null;
        }
        String d = endereco.substring(endereco.indexOf('@') + 1).trim();
        return d.isEmpty() ? null : d;
    }
}
