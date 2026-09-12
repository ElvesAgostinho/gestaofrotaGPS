-- Fatia 4: GPS / telemetria.
-- Aparelhos, posições, geocercas e viagens. As colunas de posição atual do
-- ativo já vieram na V7; aqui guarda-se o histórico e o que o alimenta.

-- A V1 criou um esboço de GPS preso ao domínio "viatura" que foi abandonado no
-- pivot para CMMS: gps_providers/gps_devices/gps_positions/gps_trips/geofences
-- referenciam `vehicles`, nunca tiveram dados e nenhuma entidade Java as mapeia.
-- São substituídas aqui pelas tabelas de telemetria ligadas a `assets`.
-- (A V2 continua a inserir em gps_providers; corre antes desta, por isso não parte.)
DROP TABLE IF EXISTS gps_trips;
DROP TABLE IF EXISTS gps_positions;
DROP TABLE IF EXISTS geofences;
DROP TABLE IF EXISTS gps_devices;
DROP TABLE IF EXISTS gps_providers;

CREATE TABLE gps_devices (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36)  REFERENCES assets(id) ON DELETE SET NULL,
    -- Identificador do aparelho no fornecedor (IMEI, id Traccar, etc.).
    external_id     VARCHAR(120) NOT NULL,
    provider        VARCHAR(20)  NOT NULL DEFAULT 'GENERIC',
    name            VARCHAR(160),
    model           VARCHAR(120),
    sim_number      VARCHAR(40),
    -- SHA-256 da chave que o aparelho usa para publicar posições.
    ingest_key_hash VARCHAR(64),
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEVER_SEEN',
    last_seen_at    TIMESTAMP,
    battery_percent INTEGER,
    notes           VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_gps_device_external UNIQUE (organization_id, external_id)
);
CREATE INDEX ix_gps_devices_org ON gps_devices(organization_id);
CREATE INDEX ix_gps_devices_asset ON gps_devices(asset_id);

CREATE TABLE gps_positions (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    device_id       VARCHAR(36)  REFERENCES gps_devices(id) ON DELETE SET NULL,
    asset_id        VARCHAR(36)  REFERENCES assets(id) ON DELETE CASCADE,
    trip_id         VARCHAR(36),
    recorded_at     TIMESTAMP    NOT NULL,
    latitude        NUMERIC(10,7) NOT NULL,
    longitude       NUMERIC(10,7) NOT NULL,
    speed_kph       NUMERIC(6,2),
    heading         NUMERIC(5,1),
    altitude_m      NUMERIC(8,2),
    accuracy_m      NUMERIC(8,2),
    satellites      INTEGER,
    ignition        BOOLEAN,
    moving          BOOLEAN,
    -- Contadores do próprio aparelho, que alimentam os medidores do ativo.
    odometer_km     NUMERIC(12,2),
    engine_hours    NUMERIC(12,2),
    source          VARCHAR(20)  NOT NULL DEFAULT 'TELEMETRY',
    created_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_gps_positions_asset_time ON gps_positions(asset_id, recorded_at);
CREATE INDEX ix_gps_positions_device_time ON gps_positions(device_id, recorded_at);
CREATE INDEX ix_gps_positions_org_time ON gps_positions(organization_id, recorded_at);

CREATE TABLE geofences (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(160) NOT NULL,
    kind            VARCHAR(20)  NOT NULL DEFAULT 'CIRCLE',
    center_latitude  NUMERIC(10,7),
    center_longitude NUMERIC(10,7),
    radius_m        NUMERIC(10,2),
    -- Polígono como JSON [[lat,lon],...] em TEXT (portável entre H2 e PostgreSQL).
    polygon         TEXT,
    color           VARCHAR(20),
    alert_on_enter  BOOLEAN      NOT NULL DEFAULT TRUE,
    alert_on_exit   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    location_id     VARCHAR(36)  REFERENCES locations(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_geofences_org ON geofences(organization_id);

-- Sem linhas para uma geocerca, ela aplica-se a todos os ativos da empresa.
CREATE TABLE geofence_assets (
    id          VARCHAR(36) PRIMARY KEY,
    geofence_id VARCHAR(36) NOT NULL REFERENCES geofences(id) ON DELETE CASCADE,
    asset_id    VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_geofence_asset UNIQUE (geofence_id, asset_id)
);

CREATE TABLE geofence_events (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    geofence_id     VARCHAR(36) NOT NULL REFERENCES geofences(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    event_type      VARCHAR(20) NOT NULL,
    occurred_at     TIMESTAMP   NOT NULL,
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    acknowledged_at TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL
);
CREATE INDEX ix_geofence_events_org_time ON geofence_events(organization_id, occurred_at);
CREATE INDEX ix_geofence_events_asset ON geofence_events(asset_id);

CREATE TABLE trips (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    device_id       VARCHAR(36)  REFERENCES gps_devices(id) ON DELETE SET NULL,
    started_at      TIMESTAMP    NOT NULL,
    ended_at        TIMESTAMP,
    start_latitude  NUMERIC(10,7),
    start_longitude NUMERIC(10,7),
    end_latitude    NUMERIC(10,7),
    end_longitude   NUMERIC(10,7),
    distance_km     NUMERIC(10,3) NOT NULL DEFAULT 0,
    max_speed_kph   NUMERIC(6,2),
    duration_minutes INTEGER,
    position_count  INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_trips_asset_time ON trips(asset_id, started_at);
CREATE INDEX ix_trips_org_open ON trips(organization_id, ended_at);
