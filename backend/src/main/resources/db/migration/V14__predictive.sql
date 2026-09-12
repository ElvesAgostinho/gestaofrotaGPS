-- Fatia 7: manutenção preditiva.
--
-- A tabela do documento de referência (vibração mensal, termografia trimestral,
-- análise de óleo semestral) existia apenas como texto fixo no PDF. Aqui passa
-- a ser um programa por ativo, com agenda própria e registo de resultados.

CREATE TABLE predictive_programs (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id          VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    technique         VARCHAR(30)  NOT NULL,
    -- Periodicidade em meses: 1 = mensal, 3 = trimestral, 6 = semestral.
    frequency_months  INTEGER      NOT NULL,
    components        VARCHAR(300),
    goal              VARCHAR(300),
    responsible_label VARCHAR(120),
    last_done_at      TIMESTAMP,
    next_due_at       TIMESTAMP,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    notes             VARCHAR(2000),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    -- A mesma técnica no mesmo ativo é o mesmo programa.
    CONSTRAINT uq_predictive_program UNIQUE (asset_id, technique)
);
CREATE INDEX ix_predictive_programs_org ON predictive_programs(organization_id, next_due_at);
CREATE INDEX ix_predictive_programs_asset ON predictive_programs(asset_id);

CREATE TABLE predictive_readings (
    id                 VARCHAR(36) PRIMARY KEY,
    organization_id    VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    program_id         VARCHAR(36) NOT NULL REFERENCES predictive_programs(id) ON DELETE CASCADE,
    asset_id           VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    performed_at       TIMESTAMP   NOT NULL,
    -- NORMAL, ATTENTION, CRITICAL: é o que distingue medir de decidir.
    result             VARCHAR(20) NOT NULL,
    -- Valor medido tal como o laboratório ou o aparelho o dá ("4,5 mm/s RMS").
    measurement        VARCHAR(120),
    findings           TEXT,
    recommendation     TEXT,
    performed_by_label VARCHAR(120),
    performed_by       VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    -- Relatório do laboratório, termograma, espectro de vibração.
    file_id            VARCHAR(36) REFERENCES stored_files(id) ON DELETE SET NULL,
    -- Ordem de manutenção aberta a partir deste resultado, quando houve.
    work_order_id      VARCHAR(36) REFERENCES work_orders(id) ON DELETE SET NULL,
    meter_value        NUMERIC(14,2),
    created_at         TIMESTAMP   NOT NULL
);
CREATE INDEX ix_predictive_readings_program ON predictive_readings(program_id, performed_at);
CREATE INDEX ix_predictive_readings_asset ON predictive_readings(asset_id, performed_at);
