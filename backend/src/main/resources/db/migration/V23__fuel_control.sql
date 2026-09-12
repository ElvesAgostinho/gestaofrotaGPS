-- Fatia 15: controlo de consumo a serio.
--
-- A V17 deixou escrito que com lancamentos a mao o sistema faz CONTABILIDADE de
-- combustivel, nao CONTROLO. Isto muda parte disso: cruzando o que foi
-- declarado com o que o GPS viu, aparecem coisas que a contabilidade sozinha
-- nunca mostra -- um cartao usado a 40 km da viatura, litros a mais do que cabe
-- no deposito, quilometros declarados que nunca foram percorridos.
--
-- O que continua a nao ser possivel sem sensor de nivel: apanhar o furto NO
-- MOMENTO. Aqui apanha-se depois, com numeros que aguentam uma conversa.

-- ===========================================================================
-- Abastecimentos: mais o que faz falta para cruzar
-- ===========================================================================
ALTER TABLE fuel_records ADD COLUMN driver_id       VARCHAR(36) REFERENCES drivers(id) ON DELETE SET NULL;
ALTER TABLE fuel_records ADD COLUMN branch_id       VARCHAR(36) REFERENCES locations(id) ON DELETE SET NULL;

-- Onde o abastecimento diz ter acontecido.
ALTER TABLE fuel_records ADD COLUMN latitude        NUMERIC(10,7);
ALTER TABLE fuel_records ADD COLUMN longitude       NUMERIC(10,7);

-- Resultado do cruzamento com a posicao real da viatura naquele momento.
-- NULO significa "nao foi possivel verificar" (sem GPS, sem posicao na altura),
-- que e diferente de "verificado e esta bem". A distincao importa: um ecra que
-- mostre tudo a verde quando na verdade nao verificou nada e pior do que nao
-- ter verificacao nenhuma.
ALTER TABLE fuel_records ADD COLUMN gps_verified    BOOLEAN;
ALTER TABLE fuel_records ADD COLUMN gps_distance_m  NUMERIC(12,2);
ALTER TABLE fuel_records ADD COLUMN gps_checked_at  TIMESTAMP;

-- Distancia percorrida segundo o GPS entre este abastecimento e o anterior,
-- para comparar com o que o medidor diz.
ALTER TABLE fuel_records ADD COLUMN gps_distance_km NUMERIC(12,3);

ALTER TABLE fuel_records ADD COLUMN card_number     VARCHAR(40);
ALTER TABLE fuel_records ADD COLUMN invoice_number  VARCHAR(60);
ALTER TABLE fuel_records ADD COLUMN fuel_type       VARCHAR(20);

CREATE INDEX ix_fuel_records_driver ON fuel_records(driver_id, filled_at);
CREATE INDEX ix_fuel_records_branch ON fuel_records(branch_id, filled_at);
CREATE INDEX ix_fuel_records_card ON fuel_records(organization_id, card_number);

-- ===========================================================================
-- Base de consumo de cada ativo
-- ===========================================================================
-- Um camiao de obra gasta o dobro de um ligeiro e isso nao e anomalia nenhuma.
-- So faz sentido comparar cada ativo CONSIGO PROPRIO. A base e recalculada a
-- medida que ha historico, e enquanto nao houver amostras que cheguem o sistema
-- diz que ainda nao sabe -- em vez de acusar com base em dois depositos.
CREATE TABLE asset_consumption_baselines (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id          VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,

    unit              VARCHAR(20)  NOT NULL,
    baseline          NUMERIC(10,3) NOT NULL,
    -- Desvio padrao da amostra: distingue um ativo regular de um que sempre
    -- oscilou muito. Num ativo instavel, 25% acima da media pode ser normal.
    std_deviation     NUMERIC(10,3),
    sample_count      INTEGER      NOT NULL,
    best              NUMERIC(10,3),
    worst             NUMERIC(10,3),

    first_sample_at   TIMESTAMP,
    last_sample_at    TIMESTAMP,
    computed_at       TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_consumption_baseline UNIQUE (asset_id)
);
CREATE INDEX ix_consumption_baseline_org ON asset_consumption_baselines(organization_id);

-- ===========================================================================
-- Anomalias de combustivel
-- ===========================================================================
-- Cada linha tem de responder a tres perguntas: o que esta mal, quanto e que
-- isso vale em litros e em dinheiro, e o que foi feito. Sem a segunda, isto
-- seria mais um ecra de avisos que ninguem abre. E o dinheiro em risco que poe
-- uma direccao a olhar.
CREATE TABLE fuel_anomalies (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id          VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    fuel_record_id    VARCHAR(36)  REFERENCES fuel_records(id) ON DELETE CASCADE,
    driver_id         VARCHAR(36)  REFERENCES drivers(id) ON DELETE SET NULL,

    kind              VARCHAR(40)  NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    detected_at       TIMESTAMP    NOT NULL,
    occurred_at       TIMESTAMP    NOT NULL,

    -- O que se esperava, o que se observou, e a diferenca em litros e em
    -- dinheiro. Uma anomalia sem estes numeros nao aguenta uma conversa.
    expected_value    NUMERIC(14,3),
    observed_value    NUMERIC(14,3),
    unit              VARCHAR(20),
    liters_at_risk    NUMERIC(12,2),
    cost_at_risk      NUMERIC(16,2),
    currency          VARCHAR(3)   NOT NULL DEFAULT 'AOA',

    title             VARCHAR(200) NOT NULL,
    detail            VARCHAR(1000),

    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolution        VARCHAR(1000),
    resolved_at       TIMESTAMP,
    resolved_by       VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,

    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);
CREATE INDEX ix_fuel_anomalies_org ON fuel_anomalies(organization_id, status, occurred_at);
CREATE INDEX ix_fuel_anomalies_asset ON fuel_anomalies(asset_id, occurred_at);
CREATE INDEX ix_fuel_anomalies_driver ON fuel_anomalies(driver_id, occurred_at);
-- A mesma regra nao dispara duas vezes para o mesmo abastecimento.
CREATE UNIQUE INDEX uq_fuel_anomaly_record_kind ON fuel_anomalies(fuel_record_id, kind);
