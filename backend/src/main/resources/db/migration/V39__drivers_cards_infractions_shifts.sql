-- Fatia 32: gestao de motoristas a serio.
--
-- A carta ja existia. Faltavam o cartao de motorista (certificado de aptidao
-- profissional) e o exame medico, que tambem caducam; as infracoes, para a
-- pontuacao ter memoria; e a escala de servico, para se saber quem esta com
-- que viatura em cada turno.

ALTER TABLE drivers ADD COLUMN card_number VARCHAR(60);
ALTER TABLE drivers ADD COLUMN card_expires_at DATE;
ALTER TABLE drivers ADD COLUMN medical_expires_at DATE;

CREATE TABLE driver_infractions (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    driver_id       VARCHAR(36)   NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36)   REFERENCES assets(id),
    occurred_at     TIMESTAMP     NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    description     VARCHAR(1000),
    points          INTEGER       NOT NULL DEFAULT 0,
    fine_amount     DECIMAL(14,2),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'AOA',
    paid            BOOLEAN       NOT NULL DEFAULT FALSE,
    reference       VARCHAR(80),
    recorded_by     VARCHAR(36)   REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL
);
CREATE INDEX idx_driver_infractions_driver ON driver_infractions(driver_id, occurred_at);
CREATE INDEX idx_driver_infractions_org ON driver_infractions(organization_id, occurred_at);

CREATE TABLE driver_shifts (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    driver_id       VARCHAR(36)   NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36)   REFERENCES assets(id),
    starts_at       TIMESTAMP     NOT NULL,
    ends_at         TIMESTAMP     NOT NULL,
    kind            VARCHAR(16)   NOT NULL DEFAULT 'DAY',
    notes           VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL
);
CREATE INDEX idx_driver_shifts_org_time ON driver_shifts(organization_id, starts_at);
CREATE INDEX idx_driver_shifts_driver ON driver_shifts(driver_id, starts_at);
