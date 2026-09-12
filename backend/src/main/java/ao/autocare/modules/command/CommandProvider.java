package ao.autocare.modules.command;

import ao.autocare.domain.DeviceCommand;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída para quem consegue mesmo falar com o aparelho.
 *
 * <p>O AutoCare recebe posições por HTTP e não mantém ligação aberta a nenhum
 * rastreador — não tem por onde enviar um comando. Quem tem é o servidor do
 * fornecedor (Traccar), que mantém a sessão TCP de cada aparelho. Esta interface
 * é a fronteira: o ciclo de vida, as travas de segurança e a auditoria ficam do
 * lado do AutoCare; a entrega e a prova de execução ficam do lado de quem tem a
 * ligação.
 */
public interface CommandProvider {

    /**
     * Resultado da entrega ao fornecedor.
     *
     * @param queued o fornecedor aceitou mas o aparelho está offline: o comando
     *               fica em fila do lado dele e pode chegar horas depois. É uma
     *               situação diferente de "entregue", e o ecrã tem de a mostrar
     *               como tal — uma viatura pode receber o corte noutro sítio.
     */
    record Dispatch(
            boolean accepted, boolean queued, String providerCommandId, String failureReason) {

        public static Dispatch delivered(String providerCommandId) {
            return new Dispatch(true, false, providerCommandId, null);
        }

        public static Dispatch queuedForOfflineDevice(String providerCommandId) {
            return new Dispatch(true, true, providerCommandId, null);
        }

        public static Dispatch rejected(String reason) {
            return new Dispatch(false, false, null, reason);
        }
    }

    /** O que o fornecedor sabe sobre um aparelho. */
    record DeviceInfo(
            String providerDeviceId,
            String protocol,
            String status,
            boolean online,
            List<String> supportedCommands) {}

    /**
     * Prova de que o aparelho executou (ou não) o comando.
     *
     * @param locked  estado do imobilizador reportado pelo aparelho, quando ele
     *                o reporta; vazio quando não há informação
     * @param source  de onde veio a evidência
     */
    record Evidence(
            boolean conclusive,
            Optional<Boolean> locked,
            ao.autocare.domain.enums.Enums.CommandConfirmationSource source,
            String detail) {

        public static Evidence none() {
            return new Evidence(false, Optional.empty(), null, null);
        }
    }

    /** Estado da ligação ao fornecedor, para o ecrã de configuração. */
    record ProviderHealth(
            boolean configured,
            boolean reachable,
            String name,
            String version,
            String failureReason) {}

    /**
     * Entrega o comando. Devolver {@code accepted} significa que o fornecedor o
     * aceitou — <b>não</b> que o aparelho o executou.
     */
    Dispatch dispatch(DeviceCommand command);

    /** O que o fornecedor sabe do aparelho, incluindo os comandos que aceita. */
    Optional<DeviceInfo> describeDevice(String externalId);

    /**
     * Procura prova de execução de um comando já enviado. É isto que transforma
     * "enviado" em "confirmado" sem depender de alguém carregar num botão.
     */
    Evidence confirmationFor(DeviceCommand command);

    /** Testa a ligação sem enviar nada a nenhum aparelho. */
    ProviderHealth health();

    boolean isConfigured();

    String name();
}
