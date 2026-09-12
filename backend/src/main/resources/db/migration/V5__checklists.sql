-- AutoCare — checklists de inspeção (Fatia 2a)
--
-- Modelos de checklist reutilizáveis (ex.: "Inspeção diária antes do arranque")
-- e o registo de cada execução contra um ativo. A execução guarda uma cópia
-- imutável dos itens (o histórico não muda se o modelo for editado).

CREATE TABLE checklist_templates (
    id                 VARCHAR(36) PRIMARY KEY,
    organization_id    VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_type_id      VARCHAR(36) REFERENCES asset_types(id) ON DELETE SET NULL,
    name               VARCHAR(160) NOT NULL,
    description        TEXT,
    estimated_minutes  INTEGER,
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL
);
CREATE INDEX ix_checklist_templates_org ON checklist_templates(organization_id);

CREATE TABLE checklist_items (
    id            VARCHAR(36) PRIMARY KEY,
    template_id   VARCHAR(36) NOT NULL REFERENCES checklist_templates(id) ON DELETE CASCADE,
    item_text     VARCHAR(300) NOT NULL,
    verification  VARCHAR(15) NOT NULL DEFAULT 'VERIFY',
    is_critical   BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order    INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_checklist_items_template ON checklist_items(template_id);

CREATE TABLE checklist_executions (
    id                    VARCHAR(36) PRIMARY KEY,
    organization_id       VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id              VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    template_id           VARCHAR(36) REFERENCES checklist_templates(id) ON DELETE SET NULL,
    template_name         VARCHAR(160) NOT NULL,
    performed_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    performed_by_label    VARCHAR(120),
    performed_at          TIMESTAMP   NOT NULL,
    meter_value           NUMERIC(14,2),
    outcome               VARCHAR(15) NOT NULL DEFAULT 'OK',
    notes                 VARCHAR(1000),
    created_at            TIMESTAMP   NOT NULL
);
CREATE INDEX ix_checklist_exec_asset ON checklist_executions(asset_id, performed_at);

CREATE TABLE checklist_execution_items (
    id            VARCHAR(36) PRIMARY KEY,
    execution_id  VARCHAR(36) NOT NULL REFERENCES checklist_executions(id) ON DELETE CASCADE,
    item_text     VARCHAR(300) NOT NULL,
    verification  VARCHAR(15) NOT NULL,
    is_critical   BOOLEAN     NOT NULL DEFAULT FALSE,
    result        VARCHAR(10) NOT NULL DEFAULT 'OK',
    note          VARCHAR(300),
    sort_order    INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_checklist_exec_items_exec ON checklist_execution_items(execution_id);
