-- Fatia 13: comandos ao aparelho (bloqueio remoto do motor).
--
-- SEGURANÇA. Um corte de motor mal executado mata pessoas. O modelo abaixo
-- existe para tornar impossível, por construção, o caso perigoso:
--
--   1. O comando NÃO é enviado quando é pedido. Fica em fila e só sai quando a
--      viatura estiver comprovadamente parada — confirmado por várias leituras
--      consecutivas, nunca por uma amostra isolada.
--   2. O motivo é obrigatório e fica gravado.
--   3. Exige aprovação num segundo passo deliberado, por outra pessoa quando a
--      empresa tem mais do que um dono.
--   4. Expira. Um corte pedido de manhã não pode disparar à noite, quando a
--      viatura já está noutro sítio e noutra situação.
--   5. Guarda a posição e a velocidade no momento do pedido E no momento do
--      envio, porque são coisas diferentes e ambas interessam a uma auditoria.
--
-- O AutoCare não fala diretamente com o aparelho: delega no servidor do
-- fornecedor (Traccar), que é quem mantém a ligação TCP aberta.

CREATE TABLE device_commands (
    id                  VARCHAR(36)  PRIMARY KEY,
    organization_id     VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id            VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    device_id           VARCHAR(36)  NOT NULL REFERENCES gps_devices(id) ON DELETE CASCADE,

    kind                VARCHAR(30)  NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    -- Obrigatório. Quem corta um motor tem de dizer porquê.
    reason              VARCHAR(500) NOT NULL,

    requested_by        VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    requested_at        TIMESTAMP    NOT NULL,
    approved_by         VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    approved_at         TIMESTAMP,
    sent_at             TIMESTAMP,
    confirmed_at        TIMESTAMP,
    cancelled_by        VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    cancelled_at        TIMESTAMP,
    failure_reason      VARCHAR(500),

    -- Passado este prazo o comando caduca sem ser enviado.
    expires_at          TIMESTAMP    NOT NULL,

    request_latitude    NUMERIC(10,7),
    request_longitude   NUMERIC(10,7),
    request_speed_kph   NUMERIC(6,2),
    sent_latitude       NUMERIC(10,7),
    sent_longitude      NUMERIC(10,7),
    sent_speed_kph      NUMERIC(6,2),

    -- Identificador do comando no fornecedor, para cruzar registos.
    provider_command_id VARCHAR(120),
    attempts            INTEGER      NOT NULL DEFAULT 0,

    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL
);
CREATE INDEX ix_device_commands_org ON device_commands(organization_id, requested_at);
CREATE INDEX ix_device_commands_asset ON device_commands(asset_id, status);
-- Consulta do agendador: o que está à espera de condições de segurança.
CREATE INDEX ix_device_commands_pending ON device_commands(status, expires_at);
