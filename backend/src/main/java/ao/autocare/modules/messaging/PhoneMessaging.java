package ao.autocare.modules.messaging;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * O canal para o telemóvel que a plataforma tiver: WhatsApp primeiro, SMS
 * como alternativa. Nenhum dos dois configurado = nada sai, e o aviso fica
 * marcado como «sem canal», não como «enviado».
 */
@Component
public class PhoneMessaging {

    private final List<MessageSender> canais;

    public PhoneMessaging(WhatsAppCloudSender whatsapp, HttpSmsSender sms) {
        this.canais = List.of(whatsapp, sms);
    }

    public Optional<MessageSender> canal() {
        return canais.stream().filter(MessageSender::isConfigured).findFirst();
    }

    public boolean isConfigured() {
        return canal().isPresent();
    }

    /** «WhatsApp», «SMS» ou {@code null} quando não há canal. */
    public String nome() {
        return canal().map(MessageSender::name).orElse(null);
    }

    public boolean send(String phone, String text) {
        return canal().map(c -> c.send(phone, text)).orElse(false);
    }
}
