-- AutoCare — coordenadas geográficas + peças e stock (Fatia 3a)

-- ---------------------------------------------------------------------------
-- Coordenadas (para o mapa). A telemetria automática chega na Fatia 4.
-- ---------------------------------------------------------------------------
ALTER TABLE locations ADD COLUMN latitude  NUMERIC(10,7);
ALTER TABLE locations ADD COLUMN longitude NUMERIC(10,7);

ALTER TABLE assets ADD COLUMN latitude       NUMERIC(10,7);
ALTER TABLE assets ADD COLUMN longitude      NUMERIC(10,7);
ALTER TABLE assets ADD COLUMN position_at    TIMESTAMP;
ALTER TABLE assets ADD COLUMN position_source VARCHAR(20);

-- ---------------------------------------------------------------------------
-- Peças (catálogo) e stock
-- ---------------------------------------------------------------------------
CREATE TABLE parts (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    part_number      VARCHAR(80),
    system_code      VARCHAR(30),
    category         VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    unit             VARCHAR(20) NOT NULL DEFAULT 'un',
    min_quantity     NUMERIC(12,2) NOT NULL DEFAULT 0,
    average_cost     NUMERIC(14,2),
    currency         VARCHAR(3) NOT NULL DEFAULT 'AOA',
    notes            TEXT,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uq_part_number UNIQUE (organization_id, part_number)
);
CREATE INDEX ix_parts_org ON parts(organization_id);

CREATE TABLE warehouses (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    location_id      VARCHAR(36) REFERENCES locations(id) ON DELETE SET NULL,
    name             VARCHAR(160) NOT NULL,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uq_warehouse_name UNIQUE (organization_id, name)
);
CREATE INDEX ix_warehouses_org ON warehouses(organization_id);

CREATE TABLE stock_items (
    id            VARCHAR(36) PRIMARY KEY,
    part_id       VARCHAR(36) NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
    warehouse_id  VARCHAR(36) NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    quantity      NUMERIC(14,2) NOT NULL DEFAULT 0,
    min_quantity  NUMERIC(14,2),
    updated_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uq_stock_item UNIQUE (part_id, warehouse_id)
);
CREATE INDEX ix_stock_items_part ON stock_items(part_id);
CREATE INDEX ix_stock_items_warehouse ON stock_items(warehouse_id);

CREATE TABLE stock_movements (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    part_id          VARCHAR(36) NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
    warehouse_id     VARCHAR(36) NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    movement_type    VARCHAR(20) NOT NULL,
    quantity         NUMERIC(14,2) NOT NULL,
    balance_after    NUMERIC(14,2),
    unit_cost        NUMERIC(14,2),
    reference        VARCHAR(200),
    work_order_id    VARCHAR(36),
    performed_by_user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    notes            VARCHAR(500),
    created_at       TIMESTAMP   NOT NULL
);
CREATE INDEX ix_stock_movements_part ON stock_movements(part_id, created_at);
CREATE INDEX ix_stock_movements_org ON stock_movements(organization_id, created_at);
