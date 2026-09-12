-- Fatia 13: filiais, motoristas e rotas.
--
-- FILIAIS nao ganham tabela nova. Uma filial E um local -- com hierarquia,
-- coordenadas e ativos ja ligados por locations.kind. Criar uma segunda
-- hierarquia paralela obrigaria a decidir, em cada consulta de custos, qual
-- delas manda. Ganha colunas proprias e o tipo BRANCH.

-- As tabelas drivers/vehicle_drivers da V1 pertenciam ao dominio "viatura"
-- abandonado no pivot: estao presas a vehicles e a users.owner_id, nunca
-- tiveram entidade Java nem dados. Mesmo caso do gps_devices na V10 e do
-- fuel_records na V17. Largam-se aqui, sem tocar em nenhuma migracao ja
-- aplicada -- editar uma migracao antiga partiria a validacao do Flyway em
-- qualquer instalacao existente.
DROP TABLE IF EXISTS vehicle_drivers;
DROP TABLE IF EXISTS drivers;

ALTER TABLE locations ADD COLUMN cost_center       VARCHAR(40);
ALTER TABLE locations ADD COLUMN manager_user_id   VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE locations ADD COLUMN address           VARCHAR(300);
ALTER TABLE locations ADD COLUMN city              VARCHAR(120);
ALTER TABLE locations ADD COLUMN province          VARCHAR(120);
ALTER TABLE locations ADD COLUMN phone             VARCHAR(40);
-- Raio em metros para reconhecer que uma viatura esta "na filial" sem obrigar
-- a desenhar uma geocerca a mao para cada uma.
ALTER TABLE locations ADD COLUMN radius_meters     INTEGER;

CREATE INDEX ix_locations_kind ON locations(organization_id, kind);

-- ===========================================================================
-- Motoristas
-- ===========================================================================
-- Um motorista NAO e obrigatoriamente um utilizador do sistema. Na maior parte
-- das frotas angolanas quem conduz nao tem conta nem precisa: e uma pessoa com
-- nome, numero de funcionario e carta de conducao. O user_id existe para os
-- casos em que ha conta, e fica nulo nos outros -- exigir conta a cada
-- motorista tornaria o modulo inutilizavel na empresa real.
CREATE TABLE drivers (
    id                    VARCHAR(36)  PRIMARY KEY,
    organization_id       VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id               VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    branch_id             VARCHAR(36)  REFERENCES locations(id) ON DELETE SET NULL,

    employee_number       VARCHAR(40),
    name                  VARCHAR(160) NOT NULL,
    phone                 VARCHAR(40),
    email                 VARCHAR(190),
    national_id           VARCHAR(60),
    birth_date            DATE,
    hired_at              DATE,

    -- Carta de conducao. A validade alimenta os mesmos avisos de caducidade dos
    -- documentos dos ativos: um motorista com carta caducada a conduzir e
    -- responsabilidade da empresa, nao dele.
    license_number        VARCHAR(60),
    license_categories    VARCHAR(60),
    license_issued_at     DATE,
    license_expires_at    DATE,
    license_country       VARCHAR(60),

    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    photo_file_id         VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    notes                 VARCHAR(2000),

    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uq_driver_employee UNIQUE (organization_id, employee_number)
);
CREATE INDEX ix_drivers_org ON drivers(organization_id, status);
CREATE INDEX ix_drivers_branch ON drivers(branch_id);
CREATE INDEX ix_drivers_license_expiry ON drivers(organization_id, license_expires_at);

-- ===========================================================================
-- Quem conduziu o que, e quando
-- ===========================================================================
-- Sem isto nao ha forma honesta de imputar uma viagem, uma infracao ou um
-- abastecimento a alguem. Um campo "motorista" no ativo responderia "quem
-- conduz hoje" e mentiria sobre o passado assim que houvesse uma troca.
CREATE TABLE driver_assignments (
    id               VARCHAR(36)  PRIMARY KEY,
    organization_id  VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    driver_id        VARCHAR(36)  NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    started_at       TIMESTAMP    NOT NULL,
    ended_at         TIMESTAMP,
    is_primary       BOOLEAN      NOT NULL DEFAULT TRUE,
    notes            VARCHAR(500),
    created_by       VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);
CREATE INDEX ix_driver_assign_asset ON driver_assignments(asset_id, started_at);
CREATE INDEX ix_driver_assign_driver ON driver_assignments(driver_id, started_at);
CREATE INDEX ix_driver_assign_open ON driver_assignments(organization_id, ended_at);

-- ===========================================================================
-- Rotas previstas
-- ===========================================================================
-- Serve para comparar o previsto com o realizado: distancia a mais, tempo a
-- mais, combustivel a mais. Sem um previsto credivel, "consumo alto" nao tem
-- termo de comparacao nenhum.
CREATE TABLE routes (
    id                       VARCHAR(36)  PRIMARY KEY,
    organization_id          VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code                     VARCHAR(40),
    name                     VARCHAR(200) NOT NULL,
    origin_location_id       VARCHAR(36)  REFERENCES locations(id) ON DELETE SET NULL,
    destination_location_id  VARCHAR(36)  REFERENCES locations(id) ON DELETE SET NULL,
    origin_label             VARCHAR(200),
    destination_label        VARCHAR(200),

    expected_distance_km     NUMERIC(10,2),
    expected_duration_minutes INTEGER,
    -- Litros previstos. Nulo enquanto nao houver base de consumo do ativo --
    -- preencher com um palpite seria dar ar de rigor a um numero inventado.
    expected_fuel_liters     NUMERIC(10,2),
    -- Tolerancia antes de considerar desvio (percentagem).
    tolerance_percent        NUMERIC(5,2) NOT NULL DEFAULT 15,

    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                    VARCHAR(2000),
    created_at               TIMESTAMP    NOT NULL,
    updated_at               TIMESTAMP    NOT NULL,
    CONSTRAINT uq_route_code UNIQUE (organization_id, code)
);
CREATE INDEX ix_routes_org ON routes(organization_id, is_active);

CREATE TABLE route_waypoints (
    id           VARCHAR(36)  PRIMARY KEY,
    route_id     VARCHAR(36)  NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    location_id  VARCHAR(36)  REFERENCES locations(id) ON DELETE SET NULL,
    label        VARCHAR(200) NOT NULL,
    latitude     NUMERIC(10,7),
    longitude    NUMERIC(10,7),
    sort_order   INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_route_waypoints_route ON route_waypoints(route_id, sort_order);
