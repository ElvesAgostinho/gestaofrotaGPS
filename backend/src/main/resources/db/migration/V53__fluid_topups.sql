-- Atestos de fluidos: água/líquido de arrefecimento, óleo, hidráulico, travões.
--
-- Um autocarro não pega fogo de repente: perde água durante semanas e alguém
-- vai atestando sem dizer nada a ninguém. Registar cada atesto transforma esse
-- hábito num sinal: «esta viatura levou 9 litros em 30 dias — há fuga».
--
-- O líquido dos travões é o caso extremo: num circuito fechado ele não
-- desaparece. Se houve atesto, há fuga — e avisa-se logo.
CREATE TABLE fluid_topups (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    asset_id VARCHAR(36) NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    liters NUMERIC(8, 2) NOT NULL,
    meter_value NUMERIC(12, 2),
    note VARCHAR(500),
    recorded_by VARCHAR(36) REFERENCES users (id),
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_fluid_topups_asset ON fluid_topups (asset_id, recorded_at DESC);
CREATE INDEX ix_fluid_topups_org ON fluid_topups (organization_id, recorded_at DESC);
