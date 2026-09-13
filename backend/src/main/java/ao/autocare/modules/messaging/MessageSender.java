package ao.autocare.modules.messaging;

/**
 * Um canal para o telemóvel: WhatsApp ou SMS. Só existe um sentido — o
 * sistema avisa; ninguém responde por aqui.
 */
public interface MessageSender {

    /** Nome do canal, para o ecrã: «WhatsApp», «SMS». */
    String name();

    /** {@code false} enquanto a plataforma não tiver credenciais para este canal. */
    boolean isConfigured();

    /**
     * Envia. Devolve {@code true} só quando o fornecedor aceitou a mensagem —
     * não quando o utilizador a leu, que isso ninguém sabe.
     *
     * @param phone número em formato internacional (+244 9xx xxx xxx)
     */
    boolean send(String phone, String text);
}
