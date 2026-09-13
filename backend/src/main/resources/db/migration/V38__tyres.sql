-- Fatia 31: pneus.
--
-- Numa frota de camioes o pneu e o segundo custo depois do gasoleo. Cada pneu
-- e uma peca com vida propria: entra numa posicao a um certo contador, roda,
-- mede-se (pressao, sulco) e sai a outro contador -- e so ai se sabe quanto
-- custou por quilometro. Sem isto ninguem sabe que marca dura mais nem qual
-- o pneu que vai rebentar na estrada.

CREATE TABLE tyres (
    id                  VARCHAR(36)   PRIMARY KEY,
    organization_id     VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    asset_id            VARCHAR(36)   REFERENCES assets(id),
    -- Posicao na viatura: FE, FD (frente esq./dir.), TE1, TD1, TE2 ... ; nula em stock.
    position            VARCHAR(12),
    brand               VARCHAR(80),
    model               VARCHAR(80),
    size                VARCHAR(40),
    serial_number       VARCHAR(80),
    status              VARCHAR(16)   NOT NULL DEFAULT 'INSTALLED',
    cost                DECIMAL(14,2),
    currency            VARCHAR(3)    NOT NULL DEFAULT 'AOA',
    installed_at        TIMESTAMP,
    installed_meter     DECIMAL(14,2),
    removed_at          TIMESTAMP,
    removed_meter       DECIMAL(14,2),
    removal_reason      VARCHAR(30),
    -- Ultima medicao: pressao (bar) e sulco (mm). Os limites vem do tipo/modelo.
    target_pressure     DECIMAL(5,2),
    last_pressure       DECIMAL(5,2),
    last_tread_mm       DECIMAL(5,2),
    min_tread_mm        DECIMAL(5,2)  NOT NULL DEFAULT 3.0,
    last_measured_at    TIMESTAMP,
    notes               VARCHAR(1000),
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL
);
CREATE INDEX idx_tyres_org ON tyres(organization_id);
CREATE INDEX idx_tyres_asset ON tyres(asset_id);

CREATE TABLE tyre_readings (
    id              VARCHAR(36)   PRIMARY KEY,
    tyre_id         VARCHAR(36)   NOT NULL REFERENCES tyres(id) ON DELETE CASCADE,
    measured_at     TIMESTAMP     NOT NULL,
    meter_value     DECIMAL(14,2),
    pressure        DECIMAL(5,2),
    tread_mm        DECIMAL(5,2),
    note            VARCHAR(300),
    recorded_by     VARCHAR(36)   REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL
);
CREATE INDEX idx_tyre_readings_tyre ON tyre_readings(tyre_id, measured_at);
