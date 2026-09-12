package ao.autocare.modules.command.dto;

import ao.autocare.domain.DeviceCommand;
import ao.autocare.domain.enums.Enums.DeviceCommandKind;
import ao.autocare.domain.enums.Enums.CommandConfirmationSource;
import ao.autocare.domain.enums.Enums.DeviceCommandStatus;
import ao.autocare.domain.enums.Enums.LockReasonCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedidos e respostas do bloqueio remoto. */
public final class CommandDtos {

    private CommandDtos() {}

    public record RequestCommandRequest(
            @NotBlank(message = "Indique o motivo. Fica registado.")
            @Size(max = 500) String reason,
            /** Categoria do motivo: furto, incumprimento, manutenção... */
            LockReasonCategory reasonCategory,
            /** Confirmação explícita de que quem pede compreende o efeito. */
            Boolean acknowledged) {}

    /** Categoria de motivo disponível, para preencher a lista na interface. */
    public record ReasonCategoryView(LockReasonCategory code, String label) {}

    /**
     * Se é seguro cortar agora.
     *
     * @param stoppedReadings leituras seguidas com a viatura parada
     * @param reason          explicação para mostrar a quem pede — uma espera sem
     *                        motivo visível parece uma avaria do sistema
     */
    public record SafetyView(boolean safe, int stoppedReadings, String reason) {}

    public record CommandView(
            String id,
            String assetId,
            String assetTag,
            DeviceCommandKind kind,
            DeviceCommandStatus status,
            String statusLabel,
            String reason,
            String requestedByName,
            Instant requestedAt,
            String approvedByName,
            Instant approvedAt,
            Instant sentAt,
            Instant confirmedAt,
            String cancelledByName,
            Instant cancelledAt,
            String failureReason,
            Instant expiresAt,
            LockReasonCategory reasonCategory,
            String reasonCategoryLabel,
            /** De onde veio a confirmação; MANUAL = declarada, sem prova. */
            CommandConfirmationSource confirmationSource,
            String confirmationLabel,
            /** O fornecedor aceitou mas o aparelho estava offline. */
            boolean providerQueued,
            /**
             * Identificador do comando no fornecedor. É a chave que liga
             * este pedido ao registo do Traccar; sem ela, investigar um
             * bloqueio que não funcionou obriga a adivinhar qual foi.
             */
            String providerCommandId,
            Instant lastCheckedAt,
            String previousLockState,
            String resultingLockState,
            BigDecimal requestLatitude,
            BigDecimal requestLongitude,
            BigDecimal requestSpeedKph,
            BigDecimal sentLatitude,
            BigDecimal sentLongitude,
            BigDecimal sentSpeedKph,
            int attempts) {

        public static CommandView of(DeviceCommand c) {
            return new CommandView(
                    c.getId(), c.getAsset().getId(), c.getAsset().getTag(),
                    c.getKind(), c.getStatus(), label(c.getStatus()), c.getReason(),
                    name(c.getRequestedBy()), c.getRequestedAt(),
                    name(c.getApprovedBy()), c.getApprovedAt(),
                    c.getSentAt(), c.getConfirmedAt(),
                    name(c.getCancelledBy()), c.getCancelledAt(),
                    c.getFailureReason(), c.getExpiresAt(),
                    c.getReasonCategory(),
                    c.getReasonCategory() != null ? c.getReasonCategory().label() : null,
                    c.getConfirmationSource(), confirmationLabel(c.getConfirmationSource()),
                    c.isProviderQueued(), c.getProviderCommandId(), c.getLastCheckedAt(),
                    c.getPreviousLockState(), c.getResultingLockState(),
                    c.getRequestLatitude(), c.getRequestLongitude(), c.getRequestSpeedKph(),
                    c.getSentLatitude(), c.getSentLongitude(), c.getSentSpeedKph(),
                    c.getAttempts());
        }

        private static String name(ao.autocare.domain.User user) {
            try {
                return user != null ? user.getName() : null;
            } catch (RuntimeException e) {
                return null; // utilizador removido entretanto
            }
        }

        /**
         * Texto do estado. "Enviado" e "Confirmado" são deliberadamente
         * diferentes: sem confirmação do aparelho não se sabe se a viatura está
         * mesmo bloqueada, e o ecrã não pode sugerir que sabe.
         */
        public static String label(DeviceCommandStatus status) {
            return switch (status) {
                case PENDING_APPROVAL -> "À espera de aprovação";
                case QUEUED -> "Em fila, à espera de a viatura parar";
                case SENT -> "Enviado — por confirmar pelo aparelho";
                case CONFIRMED -> "Confirmado";
                case FAILED -> "Falhou";
                case EXPIRED -> "Caducou sem ser executado";
                case CANCELLED -> "Anulado";
                case UNCONFIRMED -> "Enviado — o aparelho nunca confirmou";
                case SUPERSEDED -> "Substituído por um comando posterior";
            };
        }

        /**
         * Texto da confirmação. A distinção é deliberada: "declarado por uma
         * pessoa" não é prova de que a viatura está imobilizada, e quem olha
         * para o ecrã tem de saber a diferença.
         */
        public static String confirmationLabel(CommandConfirmationSource source) {
            if (source == null) {
                return null;
            }
            return switch (source) {
                case DEVICE_ATTRIBUTE -> "Confirmado pelo aparelho";
                case TRACCAR_EVENT -> "O aparelho respondeu ao comando";
                case MANUAL -> "Declarado por uma pessoa — sem prova do aparelho";
            };
        }
    }

    /**
     * Estado de bloqueio de um ativo.
     *
     * @param providerConfigured {@code false} significa que o sistema NÃO
     *                           consegue bloquear nada — tem de ficar visível
     */
    public record LockStatusView(
            String assetId,
            String assetTag,
            boolean locked,
            Instant lockedSince,
            /** Como foi confirmado o bloqueio atual; MANUAL não é prova. */
            CommandConfirmationSource lockedConfirmationSource,
            String lockedConfirmationLabel,
            CommandView pending,
            boolean providerConfigured,
            String providerName,
            /** O aparelho instalado e o protocolo, quando conhecidos. */
            String deviceExternalId,
            String deviceProtocol,
            /** Se o protocolo deste aparelho aceita o comando de imobilização. */
            Boolean immobiliserSupported,
            String commandsSyncedLabel,
            SafetyView safety) {}

    /** Estado da ligação ao fornecedor de comandos, para o ecrã de configuração. */
    public record ProviderHealthView(
            boolean configured,
            boolean reachable,
            String name,
            String version,
            String failureReason) {}

    /** O que o fornecedor sabe de um aparelho, depois de sincronizar. */
    public record DeviceSyncView(
            String deviceId,
            String externalId,
            String protocol,
            String status,
            boolean online,
            java.util.List<String> supportedCommands,
            boolean immobiliserSupported,
            Instant syncedAt) {}
}
