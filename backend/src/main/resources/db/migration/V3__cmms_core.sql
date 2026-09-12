-- AutoCare — núcleo CMMS (Fase 2)
--
-- Domínio de gestão de manutenção para empresas: locais, tipos de ativo,
-- sistemas, ativos (viaturas / máquinas / geradores), medidores (horímetro /
-- hodómetro), leituras e matriz de criticidade.
--
-- Mesmas regras de portabilidade das migrações anteriores:
--   PK VARCHAR(36) (UUID da aplicação) · sem enums nativos · JSON em TEXT
--   evitar palavras reservadas (value -> reading_value, year -> model_year)

-- ===========================================================================
-- Locais: hierarquia empresa -> obra / parque de máquinas -> ...
-- ===========================================================================
CREATE TABLE locations (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    parent_id       VARCHAR(36) REFERENCES locations(id) ON DELETE SET NULL,
    name            VARCHAR(160) NOT NULL,
    code            VARCHAR(40),
    kind            VARCHAR(20) NOT NULL DEFAULT 'SITE',
    notes           TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uq_location_name UNIQUE (organization_id, parent_id, name)
);
CREATE INDEX ix_locations_org ON locations(organization_id);
CREATE INDEX ix_locations_parent ON locations(parent_id);

-- ===========================================================================
-- Tipos de ativo (configuráveis por organização)
-- ===========================================================================
CREATE TABLE asset_types (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(80)  NOT NULL,
    category        VARCHAR(20)  NOT NULL DEFAULT 'MACHINE',
    primary_meter   VARCHAR(20)  NOT NULL DEFAULT 'HOURMETER',
    secondary_meter VARCHAR(20),
    icon            VARCHAR(40),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_asset_type_name UNIQUE (organization_id, name)
);
CREATE INDEX ix_asset_types_org ON asset_types(organization_id);

-- Sistemas do ativo (motor, hidráulico, transmissão, ...) por tipo de ativo
CREATE TABLE asset_systems (
    id             VARCHAR(36) PRIMARY KEY,
    asset_type_id  VARCHAR(36) NOT NULL REFERENCES asset_types(id) ON DELETE CASCADE,
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(80) NOT NULL,
    sort_order     INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_asset_system UNIQUE (asset_type_id, code)
);
CREATE INDEX ix_asset_systems_type ON asset_systems(asset_type_id);

-- ===========================================================================
-- Ativos
-- ===========================================================================
CREATE TABLE assets (
    id                   VARCHAR(36) PRIMARY KEY,
    organization_id      VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_type_id        VARCHAR(36) NOT NULL REFERENCES asset_types(id),
    location_id          VARCHAR(36) REFERENCES locations(id) ON DELETE SET NULL,
    responsible_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    tag                  VARCHAR(40)  NOT NULL,
    name                 VARCHAR(160) NOT NULL,
    manufacturer         VARCHAR(80),
    model                VARCHAR(120),
    serial_number        VARCHAR(120),
    model_year           INTEGER,
    plate                VARCHAR(20),
    responsible_label    VARCHAR(120),
    acquisition_date     TIMESTAMP,
    acquisition_value    NUMERIC(16,2),
    currency             VARCHAR(3)   NOT NULL DEFAULT 'AOA',
    photo_url            VARCHAR(500),
    objective            TEXT,
    notes                TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'OPERATIONAL',
    is_archived          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    CONSTRAINT uq_asset_tag UNIQUE (organization_id, tag)
);
CREATE INDEX ix_assets_org ON assets(organization_id);
CREATE INDEX ix_assets_type ON assets(asset_type_id);
CREATE INDEX ix_assets_location ON assets(location_id);

-- ===========================================================================
-- Medidores e leituras
-- ===========================================================================
CREATE TABLE asset_meters (
    id              VARCHAR(36) PRIMARY KEY,
    asset_id        VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    kind            VARCHAR(20) NOT NULL,
    unit            VARCHAR(10) NOT NULL,
    current_value   NUMERIC(14,2) NOT NULL DEFAULT 0,
    daily_average   NUMERIC(12,3),
    last_reading_at TIMESTAMP,
    is_primary      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uq_asset_meter UNIQUE (asset_id, kind)
);
CREATE INDEX ix_asset_meters_asset ON asset_meters(asset_id);

CREATE TABLE meter_readings (
    id                   VARCHAR(36) PRIMARY KEY,
    asset_meter_id       VARCHAR(36) NOT NULL REFERENCES asset_meters(id) ON DELETE CASCADE,
    reading_value        NUMERIC(14,2) NOT NULL,
    reading_at           TIMESTAMP   NOT NULL,
    source               VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    recorded_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    delta                NUMERIC(14,2),
    flagged              BOOLEAN     NOT NULL DEFAULT FALSE,
    flag_reason          VARCHAR(200),
    note                 VARCHAR(300),
    created_at           TIMESTAMP   NOT NULL
);
CREATE INDEX ix_meter_readings_meter ON meter_readings(asset_meter_id, reading_at);

-- ===========================================================================
-- Matriz de criticidade (um registo por ativo)
-- ===========================================================================
CREATE TABLE asset_criticality (
    id                   VARCHAR(36) PRIMARY KEY,
    asset_id             VARCHAR(36) NOT NULL UNIQUE REFERENCES assets(id) ON DELETE CASCADE,
    production_impact    INTEGER     NOT NULL DEFAULT 1,
    safety_impact        INTEGER     NOT NULL DEFAULT 1,
    financial_impact     INTEGER     NOT NULL DEFAULT 1,
    overall              VARCHAR(10) NOT NULL DEFAULT 'LOW',
    overall_manual       BOOLEAN     NOT NULL DEFAULT FALSE,
    assessed_at          TIMESTAMP,
    assessed_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    notes                VARCHAR(500),
    created_at           TIMESTAMP   NOT NULL,
    updated_at           TIMESTAMP   NOT NULL
);
