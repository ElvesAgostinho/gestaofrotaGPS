-- Fatia 6: notificações.
--
-- A tabela `notifications` veio da V1 presa ao domínio "viatura". Aqui passa a
-- pertencer a uma empresa e a um ativo, e ganha a chave de origem que impede
-- que o agendador crie a mesma notificação de hora a hora.

ALTER TABLE notifications ADD COLUMN organization_id VARCHAR(36)
    REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE notifications ADD COLUMN asset_id VARCHAR(36)
    REFERENCES assets(id) ON DELETE SET NULL;

-- Origem da notificação (ex.: 'plan_task' + id da tarefa). Duas notificações
-- com a mesma origem para o mesmo utilizador são a mesma coisa dita duas vezes.
ALTER TABLE notifications ADD COLUMN source_kind VARCHAR(40);
ALTER TABLE notifications ADD COLUMN source_id   VARCHAR(36);

-- Caminho que a aplicação web abre ao clicar na notificação.
ALTER TABLE notifications ADD COLUMN link VARCHAR(300);

-- Estado do envio por email: NOT_REQUESTED, DEMO_MODE (sem SMTP configurado),
-- SENT, FAILED. Nunca se marca como enviado o que não saiu daqui.
ALTER TABLE notifications ADD COLUMN email_state VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED';
ALTER TABLE notifications ADD COLUMN email_at    TIMESTAMP;

-- O domínio "viatura" foi abandonado no pivot para CMMS.
ALTER TABLE notifications DROP COLUMN vehicle_id;

-- Vários NULL são permitidos em H2 e PostgreSQL, por isso as notificações
-- avulsas (sem origem) não colidem entre si.
CREATE UNIQUE INDEX uq_notification_source ON notifications(user_id, source_kind, source_id);
CREATE INDEX ix_notifications_org ON notifications(organization_id, created_at);
