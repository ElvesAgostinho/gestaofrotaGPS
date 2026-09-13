package ao.autocare.modules.notification;

/**
 * Porta de saída para email.
 *
 * <p>Está deliberadamente separada do resto: enquanto não houver um serviço de
 * email configurado, a implementação em uso é a de demonstração, que regista o
 * que <em>seria</em> enviado e devolve {@code false}. O sistema nunca marca
 * como enviado o que não saiu daqui.
 */
public interface EmailSender {

    /**
     * @return {@code true} se a mensagem foi mesmo entregue a um servidor de email
     */
    boolean send(String to, String subject, String body);

    /** Com um ficheiro anexo (ex.: o relatório mensal em PDF). Por omissão não suportado. */
    default boolean send(String to, String subject, String body, String attachmentName, byte[] attachment,
            String attachmentType) {
        return false;
    }

    /** {@code false} enquanto não houver serviço de email configurado. */
    boolean isConfigured();
}
