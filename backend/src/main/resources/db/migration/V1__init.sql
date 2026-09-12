-- AutoCare — esquema inicial (Fase 1)
--
-- SQL escrito de forma portável entre H2 (MODE=PostgreSQL) e PostgreSQL:
--   * chaves primárias VARCHAR(36) geradas pela aplicação (UUID)
--   * sem tipos nativos de enum (VARCHAR + validação na aplicação)
--   * JSON guardado como TEXT (conversores na aplicação)
--   * tipos: varchar / text / integer / boolean / numeric / double precision / timestamp

-- ===========================================================================
-- Configuração da aplicação
-- ===========================================================================
CREATE TABLE app_config (
    id          VARCHAR(36)  PRIMARY KEY,
    config_key  VARCHAR(80)  NOT NULL UNIQUE,
    config_value VARCHAR(1000) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- ===========================================================================
-- Organizações e membros (multi-tenant)
-- ===========================================================================
CREATE TABLE organizations (
    id         VARCHAR(36) PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    type       VARCHAR(20)  NOT NULL DEFAULT 'PERSONAL',
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

-- ===========================================================================
-- Utilizadores e autenticação
-- ===========================================================================
CREATE TABLE users (
    id                  VARCHAR(36) PRIMARY KEY,
    name                VARCHAR(160) NOT NULL,
    email               VARCHAR(190) UNIQUE,
    phone               VARCHAR(40)  UNIQUE,
    password_hash       VARCHAR(100) NOT NULL,
    avatar_url          VARCHAR(500),
    locale              VARCHAR(10)  NOT NULL DEFAULT 'pt-AO',
    currency            VARCHAR(3)   NOT NULL DEFAULT 'AOA',
    theme               VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    email_verified_at   TIMESTAMP,
    phone_verified_at   TIMESTAMP,
    accepted_terms_at   TIMESTAMP,
    accepted_privacy_at TIMESTAMP,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    is_admin            BOOLEAN     NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL
);

CREATE TABLE memberships (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'OWNER',
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uq_membership UNIQUE (organization_id, user_id)
);
CREATE INDEX ix_memberships_user ON memberships(user_id);

CREATE TABLE refresh_tokens (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_agent VARCHAR(400),
    ip         VARCHAR(64),
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE password_reset_tokens (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL
);
CREATE INDEX ix_password_reset_user ON password_reset_tokens(user_id);

CREATE TABLE verification_codes (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel     VARCHAR(10) NOT NULL,
    target      VARCHAR(190) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    consumed_at TIMESTAMP,
    attempts    INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_verification_codes_user ON verification_codes(user_id);

-- ===========================================================================
-- Catálogo: marcas e modelos
-- ===========================================================================
CREATE TABLE vehicle_brands (
    id   VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE vehicle_models (
    id       VARCHAR(36) PRIMARY KEY,
    brand_id VARCHAR(36) NOT NULL REFERENCES vehicle_brands(id) ON DELETE CASCADE,
    name     VARCHAR(120) NOT NULL,
    CONSTRAINT uq_vehicle_model UNIQUE (brand_id, name)
);

-- ===========================================================================
-- Planos, assinaturas e pagamentos (SaaS)
-- ===========================================================================
CREATE TABLE plans (
    id            VARCHAR(36) PRIMARY KEY,
    code          VARCHAR(20) NOT NULL UNIQUE,
    name          VARCHAR(80) NOT NULL,
    max_vehicles  INTEGER     NOT NULL,
    has_gps       BOOLEAN     NOT NULL DEFAULT FALSE,
    price_monthly NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency      VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    features_json TEXT,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL
);

CREATE TABLE subscriptions (
    id                 VARCHAR(36) PRIMARY KEY,
    user_id            VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id            VARCHAR(36) NOT NULL REFERENCES plans(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at         TIMESTAMP   NOT NULL,
    current_period_end TIMESTAMP,
    canceled_at        TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL
);
CREATE INDEX ix_subscriptions_user ON subscriptions(user_id);

CREATE TABLE payments (
    id              VARCHAR(36) PRIMARY KEY,
    subscription_id VARCHAR(36) REFERENCES subscriptions(id) ON DELETE SET NULL,
    user_id         VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        VARCHAR(30) NOT NULL,
    provider_ref    VARCHAR(120),
    amount          NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at         TIMESTAMP,
    metadata_json   TEXT,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);
CREATE INDEX ix_payments_user ON payments(user_id);

-- ===========================================================================
-- Viaturas
-- ===========================================================================
CREATE TABLE vehicles (
    id                 VARCHAR(36) PRIMARY KEY,
    owner_id           VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id    VARCHAR(36) REFERENCES organizations(id) ON DELETE SET NULL,
    nickname           VARCHAR(120),
    brand              VARCHAR(80)  NOT NULL,
    model              VARCHAR(120) NOT NULL,
    variant            VARCHAR(120),
    model_year         INTEGER,
    color              VARCHAR(40),
    plate              VARCHAR(20)  NOT NULL,
    vin                VARCHAR(40),
    fuel_type          VARCHAR(15),
    displacement_cc    INTEGER,
    transmission       VARCHAR(15),
    current_mileage_km INTEGER     NOT NULL DEFAULT 0,
    acquisition_date   TIMESTAMP,
    acquisition_value  NUMERIC(14,2),
    photo_url          VARCHAR(500),
    plate_photo_url    VARCHAR(500),
    notes              TEXT,
    is_archived        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    CONSTRAINT uq_vehicle_owner_plate UNIQUE (owner_id, plate)
);
CREATE INDEX ix_vehicles_owner ON vehicles(owner_id);
CREATE INDEX ix_vehicles_org ON vehicles(organization_id);

CREATE TABLE vehicle_mileage (
    id          VARCHAR(36) PRIMARY KEY,
    vehicle_id  VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    mileage_km  INTEGER     NOT NULL,
    recorded_at TIMESTAMP   NOT NULL,
    source      VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    note        VARCHAR(400)
);
CREATE INDEX ix_vehicle_mileage_vehicle ON vehicle_mileage(vehicle_id, recorded_at);

CREATE TABLE vehicle_documents (
    id          VARCHAR(36) PRIMARY KEY,
    vehicle_id  VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    number      VARCHAR(80),
    issuer      VARCHAR(160),
    issue_date  TIMESTAMP,
    expiry_date TIMESTAMP,
    file_url    VARCHAR(500),
    notes       TEXT,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_vehicle_documents_vehicle ON vehicle_documents(vehicle_id);
CREATE INDEX ix_vehicle_documents_expiry ON vehicle_documents(expiry_date);

CREATE TABLE vehicle_alerts (
    id                 VARCHAR(36) PRIMARY KEY,
    vehicle_id         VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    category           VARCHAR(20) NOT NULL,
    severity           VARCHAR(10) NOT NULL DEFAULT 'INFO',
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    recommended_action VARCHAR(400),
    due_date           TIMESTAMP,
    due_mileage_km     INTEGER,
    resolved_at        TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL
);
CREATE INDEX ix_vehicle_alerts_vehicle ON vehicle_alerts(vehicle_id, resolved_at);

CREATE TABLE service_schedules (
    id              VARCHAR(36) PRIMARY KEY,
    vehicle_id      VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    item_key        VARCHAR(60) NOT NULL,
    label           VARCHAR(120) NOT NULL,
    interval_km     INTEGER,
    interval_months INTEGER,
    last_service_km INTEGER,
    last_service_at TIMESTAMP,
    next_due_km     INTEGER,
    next_due_at     TIMESTAMP,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL
);
CREATE INDEX ix_service_schedules_vehicle ON service_schedules(vehicle_id);

-- ===========================================================================
-- Oficinas e condutores
-- ===========================================================================
CREATE TABLE workshops (
    id            VARCHAR(36) PRIMARY KEY,
    owner_id      VARCHAR(36) REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(160) NOT NULL,
    phone         VARCHAR(40),
    whatsapp      VARCHAR(40),
    address       VARCHAR(400),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    services_json TEXT,
    rating        DOUBLE PRECISION,
    notes         TEXT,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL
);
CREATE INDEX ix_workshops_owner ON workshops(owner_id);

CREATE TABLE drivers (
    id                  VARCHAR(36) PRIMARY KEY,
    owner_id            VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(160) NOT NULL,
    phone               VARCHAR(40),
    license_number      VARCHAR(60),
    license_category    VARCHAR(20),
    license_valid_until TIMESTAMP,
    photo_url           VARCHAR(500),
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL,
    updated_at          TIMESTAMP   NOT NULL
);
CREATE INDEX ix_drivers_owner ON drivers(owner_id);

CREATE TABLE vehicle_drivers (
    id            VARCHAR(36) PRIMARY KEY,
    vehicle_id    VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    driver_id     VARCHAR(36) NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    role          VARCHAR(20) NOT NULL DEFAULT 'DRIVER',
    assigned_at   TIMESTAMP   NOT NULL,
    unassigned_at TIMESTAMP,
    CONSTRAINT uq_vehicle_driver UNIQUE (vehicle_id, driver_id)
);

-- ===========================================================================
-- Manutenção
-- ===========================================================================
CREATE TABLE maintenances (
    id             VARCHAR(36) PRIMARY KEY,
    vehicle_id     VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    kind           VARCHAR(15) NOT NULL DEFAULT 'PREVENTIVE',
    category       VARCHAR(20),
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    performed_at   TIMESTAMP   NOT NULL,
    mileage_km     INTEGER,
    workshop_id    VARCHAR(36) REFERENCES workshops(id) ON DELETE SET NULL,
    mechanic       VARCHAR(160),
    labor_cost     NUMERIC(14,2),
    parts_cost     NUMERIC(14,2),
    total_cost     NUMERIC(14,2),
    currency       VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    warranty_until TIMESTAMP,
    warranty_km    INTEGER,
    file_urls_json TEXT,
    notes          TEXT,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL
);
CREATE INDEX ix_maintenances_vehicle ON maintenances(vehicle_id, performed_at);

CREATE TABLE maintenance_items (
    id             VARCHAR(36) PRIMARY KEY,
    maintenance_id VARCHAR(36) NOT NULL REFERENCES maintenances(id) ON DELETE CASCADE,
    item_key       VARCHAR(60),
    description    VARCHAR(300) NOT NULL,
    quantity       DOUBLE PRECISION NOT NULL DEFAULT 1,
    unit_cost      NUMERIC(14,2),
    total_cost     NUMERIC(14,2),
    part_number    VARCHAR(80)
);
CREATE INDEX ix_maintenance_items_maintenance ON maintenance_items(maintenance_id);

-- ===========================================================================
-- Despesas e combustível
-- ===========================================================================
CREATE TABLE expenses (
    id             VARCHAR(36) PRIMARY KEY,
    vehicle_id     VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    category       VARCHAR(20) NOT NULL,
    description    VARCHAR(300),
    amount         NUMERIC(14,2) NOT NULL,
    currency       VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    spent_at       TIMESTAMP   NOT NULL,
    mileage_km     INTEGER,
    payment_method VARCHAR(40),
    file_url       VARCHAR(500),
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL
);
CREATE INDEX ix_expenses_vehicle ON expenses(vehicle_id, spent_at);
CREATE INDEX ix_expenses_category ON expenses(category);

CREATE TABLE fuel_records (
    id              VARCHAR(36) PRIMARY KEY,
    vehicle_id      VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    filled_at       TIMESTAMP   NOT NULL,
    station         VARCHAR(160),
    liters          DOUBLE PRECISION NOT NULL,
    price_per_liter NUMERIC(14,2),
    total_cost      NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    mileage_km      INTEGER     NOT NULL,
    fuel_type       VARCHAR(15),
    is_full_tank    BOOLEAN     NOT NULL DEFAULT TRUE,
    payment_method  VARCHAR(40),
    note            VARCHAR(400),
    created_at      TIMESTAMP   NOT NULL
);
CREATE INDEX ix_fuel_records_vehicle ON fuel_records(vehicle_id, filled_at);

-- ===========================================================================
-- Pneus, bateria, seguro, inspeção, multas
-- ===========================================================================
CREATE TABLE tires (
    id                 VARCHAR(36) PRIMARY KEY,
    vehicle_id         VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    brand              VARCHAR(80),
    model              VARCHAR(120),
    size               VARCHAR(40),
    position           VARCHAR(15),
    purchase_date      TIMESTAMP,
    install_mileage_km INTEGER,
    remove_mileage_km  INTEGER,
    price              NUMERIC(14,2),
    currency           VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    expected_life_km   INTEGER,
    replaced_at        TIMESTAMP,
    notes              TEXT,
    created_at         TIMESTAMP   NOT NULL
);
CREATE INDEX ix_tires_vehicle ON tires(vehicle_id);

CREATE TABLE batteries (
    id                    VARCHAR(36) PRIMARY KEY,
    vehicle_id            VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    brand                 VARCHAR(80),
    model                 VARCHAR(120),
    install_date          TIMESTAMP,
    install_mileage_km    INTEGER,
    voltage               DOUBLE PRECISION,
    capacity_ah           DOUBLE PRECISION,
    warranty_months       INTEGER,
    expected_life_months  INTEGER,
    replaced_at           TIMESTAMP,
    notes                 TEXT,
    created_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_batteries_vehicle ON batteries(vehicle_id);

CREATE TABLE insurances (
    id            VARCHAR(36) PRIMARY KEY,
    vehicle_id    VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    insurer       VARCHAR(160) NOT NULL,
    policy_number VARCHAR(80),
    coverage      VARCHAR(20),
    start_date    TIMESTAMP,
    end_date      TIMESTAMP,
    premium       NUMERIC(14,2),
    currency      VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    file_url      VARCHAR(500),
    notes         TEXT,
    created_at    TIMESTAMP   NOT NULL
);
CREATE INDEX ix_insurances_vehicle ON insurances(vehicle_id);
CREATE INDEX ix_insurances_end ON insurances(end_date);

CREATE TABLE inspections (
    id           VARCHAR(36) PRIMARY KEY,
    vehicle_id   VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    center       VARCHAR(160),
    performed_at TIMESTAMP,
    result       VARCHAR(20),
    valid_until  TIMESTAMP,
    mileage_km   INTEGER,
    cost         NUMERIC(14,2),
    currency     VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    file_url     VARCHAR(500),
    notes        TEXT,
    created_at   TIMESTAMP   NOT NULL
);
CREATE INDEX ix_inspections_vehicle ON inspections(vehicle_id);
CREATE INDEX ix_inspections_valid ON inspections(valid_until);

CREATE TABLE fines (
    id          VARCHAR(36) PRIMARY KEY,
    vehicle_id  VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    occurred_at TIMESTAMP   NOT NULL,
    location    VARCHAR(300),
    reason      VARCHAR(300),
    amount      NUMERIC(14,2) NOT NULL,
    currency    VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    status      VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    file_url    VARCHAR(500),
    notes       TEXT,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_fines_vehicle ON fines(vehicle_id);

-- ===========================================================================
-- GPS — arquitetura multi-provedor
-- ===========================================================================
CREATE TABLE gps_providers (
    id         VARCHAR(36) PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    adapter    VARCHAR(20) NOT NULL,
    base_url   VARCHAR(400),
    config_enc TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

CREATE TABLE gps_devices (
    id           VARCHAR(36) PRIMARY KEY,
    vehicle_id   VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    provider_id  VARCHAR(36) REFERENCES gps_providers(id) ON DELETE SET NULL,
    external_id  VARCHAR(120),
    label        VARCHAR(120),
    imei         VARCHAR(40),
    phone_number VARCHAR(40),
    status       VARCHAR(15) NOT NULL DEFAULT 'UNCONFIGURED',
    last_seen_at TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL,
    updated_at   TIMESTAMP   NOT NULL
);
CREATE INDEX ix_gps_devices_vehicle ON gps_devices(vehicle_id);

CREATE TABLE gps_positions (
    id            VARCHAR(36) PRIMARY KEY,
    device_id     VARCHAR(36) NOT NULL REFERENCES gps_devices(id) ON DELETE CASCADE,
    vehicle_id    VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    speed_kph     DOUBLE PRECISION,
    heading_deg   DOUBLE PRECISION,
    ignition      BOOLEAN,
    odometer_km   DOUBLE PRECISION,
    battery_volt  DOUBLE PRECISION,
    device_status VARCHAR(40),
    recorded_at   TIMESTAMP   NOT NULL,
    received_at   TIMESTAMP   NOT NULL
);
CREATE INDEX ix_gps_positions_device ON gps_positions(device_id, recorded_at);
CREATE INDEX ix_gps_positions_vehicle ON gps_positions(vehicle_id, recorded_at);

CREATE TABLE gps_trips (
    id            VARCHAR(36) PRIMARY KEY,
    device_id     VARCHAR(36) NOT NULL REFERENCES gps_devices(id) ON DELETE CASCADE,
    vehicle_id    VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    started_at    TIMESTAMP   NOT NULL,
    ended_at      TIMESTAMP,
    distance_km   DOUBLE PRECISION,
    duration_sec  INTEGER,
    avg_speed_kph DOUBLE PRECISION,
    max_speed_kph DOUBLE PRECISION,
    start_lat     DOUBLE PRECISION,
    start_lng     DOUBLE PRECISION,
    end_lat       DOUBLE PRECISION,
    end_lng       DOUBLE PRECISION,
    stops_count   INTEGER,
    is_estimated  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP   NOT NULL
);
CREATE INDEX ix_gps_trips_vehicle ON gps_trips(vehicle_id, started_at);

CREATE TABLE geofences (
    id              VARCHAR(36) PRIMARY KEY,
    vehicle_id      VARCHAR(36) NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    type            VARCHAR(15) NOT NULL DEFAULT 'CIRCLE',
    center_lat      DOUBLE PRECISION,
    center_lng      DOUBLE PRECISION,
    radius_m        DOUBLE PRECISION,
    polygon_json    TEXT,
    notify_on_enter BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_on_exit  BOOLEAN     NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL
);
CREATE INDEX ix_geofences_vehicle ON geofences(vehicle_id);

-- ===========================================================================
-- Notificações
-- ===========================================================================
CREATE TABLE notifications (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id   VARCHAR(36) REFERENCES vehicles(id) ON DELETE SET NULL,
    category     VARCHAR(20) NOT NULL,
    severity     VARCHAR(10) NOT NULL DEFAULT 'INFO',
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    data_json    TEXT,
    channels_json TEXT,
    read_at      TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL
);
CREATE INDEX ix_notifications_user ON notifications(user_id, read_at);

CREATE TABLE notification_preferences (
    id        VARCHAR(36) PRIMARY KEY,
    user_id   VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category  VARCHAR(20) NOT NULL,
    push      BOOLEAN     NOT NULL DEFAULT TRUE,
    email     BOOLEAN     NOT NULL DEFAULT TRUE,
    sms       BOOLEAN     NOT NULL DEFAULT FALSE,
    lead_days_json TEXT,
    CONSTRAINT uq_notification_pref UNIQUE (user_id, category)
);

-- ===========================================================================
-- Auditoria
-- ===========================================================================
CREATE TABLE audit_logs (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60),
    entity_id   VARCHAR(36),
    summary     VARCHAR(400),
    metadata_json TEXT,
    ip          VARCHAR(64),
    user_agent  VARCHAR(400),
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_audit_logs_user ON audit_logs(user_id, created_at);
CREATE INDEX ix_audit_logs_entity ON audit_logs(entity_type, entity_id);
