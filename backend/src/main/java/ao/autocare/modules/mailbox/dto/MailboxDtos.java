package ao.autocare.modules.mailbox.dto;

import ao.autocare.modules.mailbox.EntityDirectory.Entidade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** A caixa de correio da empresa. */
public final class MailboxDtos {

    private MailboxDtos() {}

    /** Uma pasta do servidor, com o que lá está por ler. */
    public record Pasta(
            String nome,
            String caminho,
            String papel,
            int total,
            int porLer) {

        /** INBOX, SENT, DRAFTS, TRASH, JUNK ou OTHER — o papel, não o nome. */
        public static final String ENTRADA = "INBOX";
        public static final String ENVIADOS = "SENT";
        public static final String RASCUNHOS = "DRAFTS";
        public static final String LIXO = "TRASH";
        public static final String SPAM = "JUNK";
    }

    /** Um endereço, já com a identidade de quem está por trás. */
    public record Pessoa(String nome, String email, Entidade entidade) {}

    /** Uma linha da lista. Leve de propósito: o corpo só vem quando se abre. */
    public record Resumo(
            long uid,
            String assunto,
            Pessoa de,
            List<Pessoa> para,
            Instant data,
            boolean lida,
            boolean respondida,
            boolean temAnexos,
            int anexos,
            long tamanho) {}

    /** Um anexo: o que é, quanto pesa e como se vai buscar. */
    public record Anexo(
            String id,
            String nome,
            String tipo,
            long tamanho,
            /** Imagens e PDF mostram-se no ecrã; o resto descarrega-se. */
            boolean previsualizavel) {}

    /** A mensagem aberta. */
    public record Mensagem(
            long uid,
            String pasta,
            String assunto,
            Pessoa de,
            List<Pessoa> para,
            List<Pessoa> cc,
            Instant data,
            boolean lida,
            /** O corpo em HTML, já limpo do que não pode correr. */
            String html,
            /** O corpo em texto, quando não há HTML. */
            String texto,
            List<Anexo> anexos) {}

    /** Uma página da lista. */
    public record Pagina(
            String pasta,
            int pagina,
            int tamanhoPagina,
            int total,
            List<Resumo> mensagens) {}

    /**
     * O que se envia.
     *
     * <p>Os ficheiros vão no próprio pedido, como num formulário. Carregá-los
     * primeiro para um sítio e só depois enviar deixaria ficheiros órfãos
     * sempre que alguém desistisse a meio — e alguém desiste sempre.
     */
    public record Envio(
            @NotBlank(message = "Indique pelo menos um destinatário.") String para,
            @Size(max = 500) String cc,
            @Size(max = 500) String bcc,
            @NotBlank(message = "Indique o assunto.") @Size(max = 300) String assunto,
            String corpo,
            /** A mensagem a que se responde, para a conversa ficar ligada. */
            Long emRespostaA,
            String pastaDaResposta) {}

    /** O estado da caixa, para o ecrã saber o que pode mostrar. */
    public record Estado(
            boolean configurada,
            boolean podeEnviar,
            String endereco,
            String servidor,
            String mensagem) {}
}
