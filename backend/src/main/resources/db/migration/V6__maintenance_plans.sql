-- AutoCare — planos de manutenção preventiva (Fatia 2b/2c)
--
-- Um PLANO é um modelo reutilizável de tarefas agrupadas por sistema, cada uma
-- com gatilhos por horímetro/hodómetro e/ou calendário ("o que ocorrer primeiro").
-- Ao ATRIBUIR um plano a um ativo, cada tarefa ganha um "relógio" próprio
-- (asset_plan_tasks) com a data/medidor da última execução e o próximo vencimento.

CREATE TABLE maintenance_plans (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_type_id    VARCHAR(36) REFERENCES asset_types(id) ON DELETE SET NULL,
    name             VARCHAR(160) NOT NULL,
    description      TEXT,
    notes           TEXT,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL
);
CREATE INDEX ix_maintenance_plans_org ON maintenance_plans(organization_id);

CREATE TABLE plan_tasks (
    id                 VARCHAR(36) PRIMARY KEY,
    plan_id            VARCHAR(36) NOT NULL REFERENCES maintenance_plans(id) ON DELETE CASCADE,
    system_code        VARCHAR(30),
    system_name        VARCHAR(80),
    title              VARCHAR(200) NOT NULL,
    instructions      TEXT,
    estimated_minutes  INTEGER,
    tools             TEXT,
    sort_order         INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_plan_tasks_plan ON plan_tasks(plan_id);

CREATE TABLE plan_task_triggers (
    id               VARCHAR(36) PRIMARY KEY,
    task_id          VARCHAR(36) NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    trigger_type     VARCHAR(20) NOT NULL,
    meter_kind       VARCHAR(20),
    interval_value   NUMERIC(12,2) NOT NULL,
    tolerance_value  NUMERIC(12,2)
);
CREATE INDEX ix_plan_task_triggers_task ON plan_task_triggers(task_id);

CREATE TABLE plan_task_parts (
    id         VARCHAR(36) PRIMARY KEY,
    task_id    VARCHAR(36) NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    part_name  VARCHAR(200) NOT NULL,
    quantity   NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit       VARCHAR(20)
);
CREATE INDEX ix_plan_task_parts_task ON plan_task_parts(task_id);

-- ---------------------------------------------------------------------------
CREATE TABLE asset_plans (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    plan_id          VARCHAR(36) NOT NULL REFERENCES maintenance_plans(id) ON DELETE CASCADE,
    plan_name        VARCHAR(160) NOT NULL,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    assigned_at      TIMESTAMP   NOT NULL,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uq_asset_plan UNIQUE (asset_id, plan_id)
);
CREATE INDEX ix_asset_plans_asset ON asset_plans(asset_id);
CREATE INDEX ix_asset_plans_org ON asset_plans(organization_id);

CREATE TABLE asset_plan_tasks (
    id                    VARCHAR(36) PRIMARY KEY,
    asset_plan_id         VARCHAR(36) NOT NULL REFERENCES asset_plans(id) ON DELETE CASCADE,
    task_id               VARCHAR(36) REFERENCES plan_tasks(id) ON DELETE SET NULL,
    title                 VARCHAR(200) NOT NULL,
    system_name           VARCHAR(80),
    last_done_at          TIMESTAMP,
    last_done_meter       NUMERIC(14,2),
    next_due_at           TIMESTAMP,
    next_due_meter        NUMERIC(14,2),
    next_due_meter_kind   VARCHAR(20),
    remaining_meter       NUMERIC(14,2),
    remaining_days        INTEGER,
    status                VARCHAR(15) NOT NULL DEFAULT 'OK',
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_asset_plan_tasks_ap ON asset_plan_tasks(asset_plan_id);
CREATE INDEX ix_asset_plan_tasks_status ON asset_plan_tasks(status);

CREATE TABLE plan_task_completions (
    id                    VARCHAR(36) PRIMARY KEY,
    organization_id       VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_plan_task_id    VARCHAR(36) NOT NULL REFERENCES asset_plan_tasks(id) ON DELETE CASCADE,
    asset_id              VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    title                 VARCHAR(200) NOT NULL,
    completed_at          TIMESTAMP   NOT NULL,
    meter_value           NUMERIC(14,2),
    performed_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    performed_by_label    VARCHAR(120),
    notes                 VARCHAR(1000),
    created_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_plan_task_completions_apt ON plan_task_completions(asset_plan_task_id, completed_at);
CREATE INDEX ix_plan_task_completions_asset ON plan_task_completions(asset_id);
