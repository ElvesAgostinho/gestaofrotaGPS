-- Fatia 12: combustível (abastecimentos).
--
-- IMPORTANTE, e deve ficar escrito: com abastecimentos lançados à mão o sistema
-- faz CONTABILIDADE de combustível, não CONTROLO. Calcula consumo real entre
-- abastecimentos e compara-o com a média da própria viatura, o que expõe
-- desvios ao fim de alguns depósitos. Não detecta um furto no momento em que
-- acontece — para isso é preciso sensor de nível com calibração ponto a ponto.
-- O modelo abaixo está preparado para receber esse sensor sem o refazer.

-- A tabela da V1 estava presa ao domínio "viatura" abandonado no pivot e nunca
-- teve dados nem entidade Java.
DROP TABLE IF EXISTS fuel_records;

-- Capacidade do depósito: permite recusar um abastecimento impossível e, mais
-- tarde, converter leitura de sensor em litros.
ALTER TABLE assets ADD COLUMN tank_capacity_liters NUMERIC(10,2);

CREATE TABLE fuel_records (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id          VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    filled_at         TIMESTAMP    NOT NULL,
    liters            NUMERIC(10,2) NOT NULL,
    price_per_liter   NUMERIC(14,2),
    total_cost        NUMERIC(14,2),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AOA',

    -- Leitura do medidor no momento do abastecimento. É o que permite calcular
    -- consumo: sem ela o registo serve só para somar custos.
    meter_value       NUMERIC(14,2),
    meter_kind        VARCHAR(20),

    -- Só depósitos cheios dão consumo fiável: entre dois enchimentos completos
    -- sabe-se exatamente quanto foi gasto. Um abastecimento parcial não fecha
    -- a conta.
    full_tank         BOOLEAN      NOT NULL DEFAULT TRUE,

    station           VARCHAR(160),
    driver_label      VARCHAR(120),
    payment_method    VARCHAR(40),
    receipt_file_id   VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    notes             VARCHAR(2000),

    -- MANUAL hoje; SENSOR quando houver leitura automática de nível.
    source            VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',

    -- Consumo calculado face ao abastecimento cheio anterior, guardado no
    -- momento do registo para o histórico não mudar quando se corrige o passado.
    consumption       NUMERIC(10,3),
    consumption_unit  VARCHAR(20),
    distance_or_hours NUMERIC(12,2),

    created_by        VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);
CREATE INDEX ix_fuel_records_asset ON fuel_records(asset_id, filled_at);
CREATE INDEX ix_fuel_records_org ON fuel_records(organization_id, filled_at);
