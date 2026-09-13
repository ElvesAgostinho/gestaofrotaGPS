-- Fatia 40: tempo real por tecnico e regras de fecho da ordem.
--
-- O cronometro: cada tecnico carrega «iniciar» quando pega na ordem e «parar»
-- quando larga; ao parar nasce uma linha de mao de obra com as horas reais.
CREATE TABLE work_order_timers (
    id            VARCHAR(36)  PRIMARY KEY,
    work_order_id VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    user_id       VARCHAR(36)  NOT NULL REFERENCES users(id),
    started_at    TIMESTAMP    NOT NULL,
    ended_at      TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
CREATE INDEX idx_wo_timers_open ON work_order_timers (work_order_id, ended_at);

-- Regra da empresa: so se conclui com fotografia do «depois».
ALTER TABLE organizations ADD COLUMN close_requires_after_photo BOOLEAN NOT NULL DEFAULT FALSE;
