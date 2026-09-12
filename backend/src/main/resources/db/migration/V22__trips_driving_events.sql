-- Fatia 14: viagens com sitios nomeados, infracoes e pontuacao de conducao.

-- ===========================================================================
-- Viagens
-- ===========================================================================
-- O nome do sitio e resolvido contra o que a empresa REGISTOU: filiais,
-- locais e geocercas. Nao ha geocodificacao externa, e e deliberado: um
-- servico de mapas devolveria "Rua X, Luanda" para um ponto no meio do
-- estaleiro, e o gestor de frota nao reconheceria o sitio. Quando nada bate
-- certo, fica UNKNOWN e mostram-se as coordenadas -- o que e honesto.
ALTER TABLE trips ADD COLUMN driver_id        VARCHAR(36) REFERENCES drivers(id) ON DELETE SET NULL;
ALTER TABLE trips ADD COLUMN route_id         VARCHAR(36) REFERENCES routes(id) ON DELETE SET NULL;

ALTER TABLE trips ADD COLUMN start_place_name VARCHAR(200);
ALTER TABLE trips ADD COLUMN start_place_kind VARCHAR(20);
ALTER TABLE trips ADD COLUMN start_place_id   VARCHAR(36);
ALTER TABLE trips ADD COLUMN end_place_name   VARCHAR(200);
ALTER TABLE trips ADD COLUMN end_place_kind   VARCHAR(20);
ALTER TABLE trips ADD COLUMN end_place_id     VARCHAR(36);

-- Contadores de qualidade de conducao, somados quando a viagem fecha.
ALTER TABLE trips ADD COLUMN idle_minutes       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN night_minutes      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN harsh_brake_count  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN harsh_accel_count  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN harsh_corner_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN overspeed_count    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trips ADD COLUMN event_count        INTEGER NOT NULL DEFAULT 0;

ALTER TABLE trips ADD COLUMN purpose VARCHAR(200);
ALTER TABLE trips ADD COLUMN notes   VARCHAR(2000);

CREATE INDEX ix_trips_driver ON trips(driver_id, started_at);
CREATE INDEX ix_trips_route ON trips(route_id, started_at);

-- ===========================================================================
-- Infracoes de conducao
-- ===========================================================================
-- Uma linha por episodio, nunca uma por amostra: um travao brusco e um
-- acontecimento, nao trinta leituras seguidas.
--
-- source_alert_id liga um excesso de velocidade ao alerta de telemetria que ja
-- o detetou. A logica de limites (zona > ativo > empresa) fica onde estava e
-- testada; aqui so se conta uma vez para efeitos de pontuacao. Duplicar a
-- deteccao daria dois numeros diferentes para o mesmo excesso.
CREATE TABLE driving_events (
    id               VARCHAR(36)  PRIMARY KEY,
    organization_id  VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    driver_id        VARCHAR(36)  REFERENCES drivers(id) ON DELETE SET NULL,
    trip_id          VARCHAR(36)  REFERENCES trips(id) ON DELETE SET NULL,
    source_alert_id  VARCHAR(36)  REFERENCES telemetry_alerts(id) ON DELETE SET NULL,

    kind             VARCHAR(30)  NOT NULL,
    severity         VARCHAR(20)  NOT NULL,
    occurred_at      TIMESTAMP    NOT NULL,
    ended_at         TIMESTAMP,

    -- O valor medido e o limiar que foi ultrapassado, para a infracao poder ser
    -- contestada com numeros em vez de com a palavra do sistema.
    measured_value   NUMERIC(12,3),
    threshold_value  NUMERIC(12,3),
    unit             VARCHAR(20),
    speed_kph        NUMERIC(6,2),

    latitude         NUMERIC(10,7),
    longitude        NUMERIC(10,7),
    place_name       VARCHAR(200),

    penalty_points   NUMERIC(6,2) NOT NULL DEFAULT 0,
    description      VARCHAR(400),

    -- Uma infracao pode ser anulada por quem a analisa (sensor avariado,
    -- travagem de emergencia justificada). Fica registado quem e porque -- e
    -- deixa de contar para a pontuacao.
    dismissed_at     TIMESTAMP,
    dismissed_by     VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    dismiss_reason   VARCHAR(400),

    acknowledged_at  TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);
CREATE INDEX ix_driving_events_org ON driving_events(organization_id, occurred_at);
CREATE INDEX ix_driving_events_driver ON driving_events(driver_id, occurred_at);
CREATE INDEX ix_driving_events_asset ON driving_events(asset_id, occurred_at);
CREATE INDEX ix_driving_events_kind ON driving_events(organization_id, kind, occurred_at);
CREATE UNIQUE INDEX uq_driving_events_alert ON driving_events(source_alert_id);

-- ===========================================================================
-- Pontuacao de conducao
-- ===========================================================================
-- Guardada por periodo, e nao calculada na hora, por duas razoes: a conta
-- percorre todas as infracoes e toda a distancia do periodo (caro de repetir a
-- cada abertura de ecra), e um numero que muda sozinho entre duas consultas
-- nao serve para uma conversa com o motorista.
CREATE TABLE driver_scores (
    id               VARCHAR(36)  PRIMARY KEY,
    organization_id  VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    driver_id        VARCHAR(36)  NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    period_start     TIMESTAMP    NOT NULL,
    period_end       TIMESTAMP    NOT NULL,

    distance_km      NUMERIC(12,2) NOT NULL DEFAULT 0,
    driving_minutes  INTEGER      NOT NULL DEFAULT 0,
    trip_count       INTEGER      NOT NULL DEFAULT 0,

    overspeed_count    INTEGER NOT NULL DEFAULT 0,
    harsh_brake_count  INTEGER NOT NULL DEFAULT 0,
    harsh_accel_count  INTEGER NOT NULL DEFAULT 0,
    harsh_corner_count INTEGER NOT NULL DEFAULT 0,
    idling_count       INTEGER NOT NULL DEFAULT 0,
    night_count        INTEGER NOT NULL DEFAULT 0,
    total_penalty      NUMERIC(10,2) NOT NULL DEFAULT 0,

    -- Nulo quando nao houve distancia suficiente para a conta significar algo.
    -- Dar 100 a quem conduziu 3 km poria essa pessoa acima de quem fez 5000 km
    -- com duas infracoes -- e a tabela deixava de servir para alguma coisa.
    score            NUMERIC(5,2),
    band             VARCHAR(20),
    insufficient_data BOOLEAN     NOT NULL DEFAULT FALSE,

    computed_at      TIMESTAMP    NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uq_driver_score_period UNIQUE (driver_id, period_start, period_end)
);
CREATE INDEX ix_driver_scores_org ON driver_scores(organization_id, period_start);
