package ao.autocare.modules.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação usada enquanto não há servidor de email configurado.
 *
 * <p>Regista o que seria enviado e diz claramente que não enviou. Fingir o
 * envio seria pior do que não ter email: a empresa contaria com avisos que
 * nunca chegaram.
 *
 * <p>Para passar a enviar a sério, acrescenta-se o starter de email e publica-se
 * outra implementação de {@link EmailSender} marcada como {@code @Primary}.
 */
@Component
public class DemoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DemoEmailSender.class);

    @Override
    public boolean send(String to, String subject, String body) {
        log.warn("[MODO DEMONSTRAÇÃO] Sem servidor de email configurado. "
                + "Não foi enviado para {}: {}", to, subject);
        return false;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
