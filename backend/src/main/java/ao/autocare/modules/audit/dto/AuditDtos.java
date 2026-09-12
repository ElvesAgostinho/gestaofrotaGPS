package ao.autocare.modules.audit.dto;

import java.time.Instant;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {}

    /**
     * Uma linha do registo, como é mostrada.
     *
     * <p>O {@code organizationId} <b>não</b> aparece de propósito: quem lê já
     * está dentro da sua empresa, e um campo desses num ecrã só serviria para
     * revelar que existem outras.
     */
    public record AuditEntryView(
            String id,
            Instant at,
            String userId,
            String userName,
            String action,
            String actionLabel,
            String entityType,
            String entityId,
            String summary,
            String ip) {}

    /** Uma ação e o seu nome em português, para o filtro do ecrã. */
    public record AuditActionView(String code, String label) {}

    /**
     * Nomes em português das ações registadas.
     *
     * <p>Um código como {@code work_order.from_due} não diz nada a quem tem de
     * auditar a empresa. A lista é explícita — e não construída a partir do
     * código — porque cada ação merece uma frase que se perceba sem conhecer o
     * modelo de dados.
     */
    public static final class Actions {

        private Actions() {}

        private static final Map<String, String> LABELS = Map.ofEntries(
                Map.entry("asset.create", "Ativo criado"),
                Map.entry("asset.update", "Ativo alterado"),
                Map.entry("asset.delete", "Ativo eliminado"),
                Map.entry("asset.archive", "Ativo arquivado"),
                Map.entry("asset.unarchive", "Ativo desarquivado"),
                Map.entry("asset.criticality", "Criticidade do ativo alterada"),
                Map.entry("asset.position", "Posição do ativo registada à mão"),
                Map.entry("asset.photo.upload", "Fotografia adicionada"),
                Map.entry("asset.photo.update", "Fotografia alterada"),
                Map.entry("asset.photo.delete", "Fotografia eliminada"),
                Map.entry("asset.document.create", "Documento adicionado"),
                Map.entry("asset.document.update", "Documento alterado"),
                Map.entry("asset.document.delete", "Documento eliminado"),
                Map.entry("asset_type.create", "Tipo de ativo criado"),
                Map.entry("asset_type.update", "Tipo de ativo alterado"),
                Map.entry("asset_type.delete", "Tipo de ativo eliminado"),
                Map.entry("asset_plan.assign", "Plano atribuído ao ativo"),
                Map.entry("asset_plan.unassign", "Plano retirado do ativo"),
                Map.entry("asset_plan.task_done", "Tarefa de plano concluída"),
                Map.entry("checklist_template.create", "Modelo de inspeção criado"),
                Map.entry("checklist_template.update", "Modelo de inspeção alterado"),
                Map.entry("checklist_template.delete", "Modelo de inspeção eliminado"),
                Map.entry("checklist.execute", "Inspeção realizada"),
                Map.entry("command.request", "BLOQUEIO: pedido"),
                Map.entry("command.approve", "BLOQUEIO: aprovado"),
                Map.entry("command.cancel", "BLOQUEIO: anulado"),
                Map.entry("command.supersede", "BLOQUEIO: substituído por um desbloqueio"),
                Map.entry("command.confirm_manual", "BLOQUEIO: confirmado por declaração"),
                Map.entry("gps_device.create", "Aparelho de GPS registado"),
                Map.entry("gps_device.update", "Aparelho de GPS alterado"),
                Map.entry("gps_device.delete", "Aparelho de GPS removido"),
                Map.entry("gps_device.rotate_key", "Chave do aparelho substituída"),
                Map.entry("gps_device.sync", "Aparelho sincronizado com o fornecedor"),
                Map.entry("geofence.create", "Geocerca criada"),
                Map.entry("geofence.update", "Geocerca alterada"),
                Map.entry("geofence.delete", "Geocerca eliminada"),
                Map.entry("geofence.event_ack", "Evento de geocerca visto"),
                Map.entry("telemetry.alert_ack", "Alerta de telemetria visto"),
                Map.entry("fuel.record", "Abastecimento registado"),
                Map.entry("fuel.delete", "Abastecimento eliminado"),
                Map.entry("meter.reading", "Leitura de medidor registada"),
                Map.entry("location.create", "Localização criada"),
                Map.entry("location.update", "Localização alterada"),
                Map.entry("location.delete", "Localização eliminada"),
                Map.entry("part.create", "Peça criada"),
                Map.entry("part.update", "Peça alterada"),
                Map.entry("part.delete", "Peça eliminada"),
                Map.entry("warehouse.create", "Armazém criado"),
                Map.entry("warehouse.update", "Armazém alterado"),
                Map.entry("stock.movement", "Movimento de stock"),
                Map.entry("stock.transfer", "Transferência de stock"),
                Map.entry("plan.create", "Plano de manutenção criado"),
                Map.entry("plan.update", "Plano de manutenção alterado"),
                Map.entry("plan.delete", "Plano de manutenção eliminado"),
                Map.entry("predictive.create", "Programa preditivo criado"),
                Map.entry("predictive.update", "Programa preditivo alterado"),
                Map.entry("predictive.delete", "Programa preditivo eliminado"),
                Map.entry("predictive.reading", "Medição preditiva registada"),
                Map.entry("predictive.standard_set", "Programas preditivos padrão aplicados"),
                Map.entry("work_order.create", "Ordem de manutenção criada"),
                Map.entry("work_order.from_due", "Ordem gerada de tarefas vencidas"),
                Map.entry("work_order.auto_generated", "Ordem gerada automaticamente"),
                Map.entry("work_order.update", "Ordem alterada"),
                Map.entry("work_order.start", "Ordem iniciada"),
                Map.entry("work_order.complete", "Ordem concluída"),
                Map.entry("work_order.verify", "Ordem verificada"),
                Map.entry("work_order.cancel", "Ordem anulada"),
                Map.entry("work_order.labor", "Mão de obra registada"),
                Map.entry("work_order.part", "Peça consumida"),
                Map.entry("failure.record", "Falha registada"),
                Map.entry("import.assets", "Ativos importados de ficheiro"),
                Map.entry("import.parts", "Peças importadas de ficheiro"),
                Map.entry("team.invite", "Convite enviado"),
                Map.entry("team.invite_resend", "Convite reenviado"),
                Map.entry("team.invite_revoke", "Convite anulado"),
                Map.entry("team.invite_accept", "Convite aceite"),
                Map.entry("team.member_update", "Membro da equipa alterado"),
                Map.entry("team.member_remove", "Membro da equipa removido"),
                Map.entry("organization.update", "Dados da empresa alterados"),
                Map.entry("email.test", "Email de teste enviado"),
                Map.entry("user.register", "Conta criada"),
                Map.entry("user.login", "Entrada na conta"),
                Map.entry("user.password_reset", "Palavra-passe reposta"),
                Map.entry("user.change_password", "Palavra-passe alterada"),
                Map.entry("user.update_profile", "Perfil alterado"),
                Map.entry("user.delete_account", "Conta eliminada"));

        /**
         * Nome em português, ou o próprio código quando a ação é nova.
         *
         * <p>Devolver o código cru é melhor do que esconder a linha: um registo
         * de auditoria que omite o que não sabe nomear deixa de servir para
         * auditar.
         */
        public static String label(String action) {
            if (action == null) {
                return null;
            }
            return LABELS.getOrDefault(action, action);
        }
    }
}
