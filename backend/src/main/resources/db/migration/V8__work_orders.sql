-- AutoCare — Ordens de Manutenção (Fatia 3b)
--
-- Uma OM executa trabalho num ativo: preventiva (das tarefas de plano vencidas),
-- corretiva (avaria) ou inspeção. Regista mão de obra, peças consumidas e tempos
-- de paragem, que alimentam os indicadores (MTBF, MTTR, disponibilidade).

CREATE TABLE org_counters (
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    counter_key      VARCHAR(40) NOT NULL,
    counter_value    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_org_counter PRIMARY KEY (organization_id, counter_key)
);

CREATE TABLE work_orders (
    id                    VARCHAR(36) PRIMARY KEY,
    organization_id       VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    number                VARCHAR(20) NOT NULL,
    asset_id              VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    order_type            VARCHAR(15) NOT NULL,
    status                VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    priority              VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    title                 VARCHAR(200) NOT NULL,
    description          TEXT,
    resolution          TEXT,
    requested_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    assigned_to_user_id   VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    assigned_to_label     VARCHAR(120),
    approved_by_user_id   VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    meter_value           NUMERIC(14,2),
    scheduled_for         TIMESTAMP,
    opened_at             TIMESTAMP   NOT NULL,
    started_at            TIMESTAMP,
    completed_at          TIMESTAMP,
    verified_at           TIMESTAMP,
    downtime_start        TIMESTAMP,
    downtime_end          TIMESTAMP,
    total_labor_hours     NUMERIC(10,2),
    total_parts_cost      NUMERIC(14,2),
    currency              VARCHAR(3)  NOT NULL DEFAULT 'AOA',
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL,
    CONSTRAINT uq_work_order_number UNIQUE (organization_id, number)
);
CREATE INDEX ix_work_orders_org ON work_orders(organization_id, status);
CREATE INDEX ix_work_orders_asset ON work_orders(asset_id, opened_at);

CREATE TABLE work_order_tasks (
    id                   VARCHAR(36) PRIMARY KEY,
    work_order_id        VARCHAR(36) NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    asset_plan_task_id   VARCHAR(36) REFERENCES asset_plan_tasks(id) ON DELETE SET NULL,
    title                VARCHAR(200) NOT NULL,
    system_name          VARCHAR(80),
    instructions        TEXT,
    is_done              BOOLEAN     NOT NULL DEFAULT FALSE,
    done_at              TIMESTAMP,
    notes                VARCHAR(500),
    sort_order           INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_work_order_tasks_wo ON work_order_tasks(work_order_id);

CREATE TABLE work_order_labor (
    id                    VARCHAR(36) PRIMARY KEY,
    work_order_id         VARCHAR(36) NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    technician_user_id    VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    technician_label      VARCHAR(120),
    hours                 NUMERIC(10,2) NOT NULL,
    hourly_rate           NUMERIC(12,2),
    worked_on             TIMESTAMP,
    notes                 VARCHAR(300),
    created_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_work_order_labor_wo ON work_order_labor(work_order_id);

CREATE TABLE work_order_parts (
    id             VARCHAR(36) PRIMARY KEY,
    work_order_id  VARCHAR(36) NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    part_id        VARCHAR(36) REFERENCES parts(id) ON DELETE SET NULL,
    part_name      VARCHAR(200) NOT NULL,
    warehouse_id   VARCHAR(36) REFERENCES warehouses(id) ON DELETE SET NULL,
    quantity       NUMERIC(12,2) NOT NULL,
    unit_cost      NUMERIC(14,2),
    is_consumed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP   NOT NULL
);
CREATE INDEX ix_work_order_parts_wo ON work_order_parts(work_order_id);

CREATE TABLE failures (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    work_order_id    VARCHAR(36) REFERENCES work_orders(id) ON DELETE SET NULL,
    system_code      VARCHAR(30),
    description      VARCHAR(500) NOT NULL,
    cause            VARCHAR(500),
    detected_at      TIMESTAMP   NOT NULL,
    meter_value      NUMERIC(14,2),
    caused_downtime  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL
);
CREATE INDEX ix_failures_asset ON failures(asset_id, detected_at);

CREATE TABLE repairs (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    work_order_id    VARCHAR(36) REFERENCES work_orders(id) ON DELETE SET NULL,
    failure_id       VARCHAR(36) REFERENCES failures(id) ON DELETE SET NULL,
    started_at       TIMESTAMP   NOT NULL,
    finished_at      TIMESTAMP   NOT NULL,
    repair_hours     NUMERIC(10,2) NOT NULL,
    created_at       TIMESTAMP   NOT NULL
);
CREATE INDEX ix_repairs_asset ON repairs(asset_id, finished_at);
