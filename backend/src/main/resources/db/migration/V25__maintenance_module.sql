-- Fatia 17: modulo de manutencao completo.
--
-- Reaproveita tudo o que ja existe: pecas e stock (V7/V8), checklists (V5),
-- planos preventivos (V6), custos e imobilizacao (V24), auditoria (V20),
-- centro de custo (V21). O que entra aqui e so o que faltava mesmo.

-- ===========================================================================
-- Fornecedores e oficinas
-- ===========================================================================
-- A tabela workshops da V1 pertencia ao dominio "viatura" abandonado no pivot:
-- presa a users.owner_id, sem entidade Java e sem dados. Mesmo caso do
-- drivers na V21. Larga-se e cria-se a serio, com NIF e multiempresa.
-- maintenances e maintenance_items sao do mesmo lote: presas a vehicles, com
-- workshop_id a apontar para a tabela acima. Largam-se pela ordem das
-- dependencias, senao a base recusa-se a largar a primeira.
DROP TABLE IF EXISTS maintenance_items;
DROP TABLE IF EXISTS maintenances;
DROP TABLE IF EXISTS workshops;

CREATE TABLE suppliers (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    -- Numero de Identificacao Fiscal. Sem ele nao ha factura que se reconcilie
    -- com a contabilidade da empresa.
    tax_id            VARCHAR(40),
    kind              VARCHAR(20)  NOT NULL DEFAULT 'WORKSHOP',
    phone             VARCHAR(40),
    email             VARCHAR(190),
    address           VARCHAR(400),
    city              VARCHAR(120),
    contact_person    VARCHAR(160),
    payment_terms     VARCHAR(200),
    default_warranty_months INTEGER,
    notes             VARCHAR(2000),
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uq_supplier_tax UNIQUE (organization_id, tax_id)
);
CREATE INDEX ix_suppliers_org ON suppliers(organization_id, is_active);

-- Os servicos externos passam a apontar para o fornecedor. O nome em texto
-- fica: ordens antigas nao podem perder a oficina so porque o modelo melhorou.
ALTER TABLE work_order_services ADD COLUMN supplier_id VARCHAR(36) REFERENCES suppliers(id) ON DELETE SET NULL;

-- ===========================================================================
-- Diagnostico
-- ===========================================================================
-- Quatro campos, nao um so de texto livre. "Vibra ao travar" (o que o condutor
-- sente), "discos empenados" (o que o tecnico ve), "desgaste irregular por
-- travagem prolongada em descida" (porque aconteceu) e "substituir discos e
-- pastilhas" (o que fazer) sao coisas diferentes -- e so separadas permitem
-- perceber, meses depois, que o problema volta sempre pela mesma razao.
ALTER TABLE work_orders ADD COLUMN symptom            VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN diagnosis          VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN probable_cause     VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN recommended_action VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN diagnosed_by       VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE work_orders ADD COLUMN diagnosed_by_label VARCHAR(160);
ALTER TABLE work_orders ADD COLUMN diagnosed_at       TIMESTAMP;

-- ===========================================================================
-- Fornecedor e aprovacao na ordem
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN supplier_id      VARCHAR(36) REFERENCES suppliers(id) ON DELETE SET NULL;
-- Interna (equipa da casa) ou externa (oficina de fora).
ALTER TABLE work_orders ADD COLUMN execution        VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- approved_by_user_id existia desde a V8, mas nunca houve data: sabia-se quem
-- aprovou e nao quando. Numa aprovacao de despesa, o quando e metade do registo.
ALTER TABLE work_orders ADD COLUMN approved_at      TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN approved_amount  NUMERIC(16,2);
ALTER TABLE work_orders ADD COLUMN approval_note    VARCHAR(1000);
ALTER TABLE work_orders ADD COLUMN rejected_by      VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE work_orders ADD COLUMN rejected_at      TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN rejection_reason VARCHAR(1000);
ALTER TABLE work_orders ADD COLUMN closed_at        TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN closed_by        VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;

-- Limite acima do qual uma manutencao externa precisa de aprovacao. Fica na
-- empresa porque o que e caro para uma frota de cinco viaturas e trivial para
-- uma de duzentas.
ALTER TABLE organizations ADD COLUMN maintenance_approval_limit NUMERIC(16,2);

-- ===========================================================================
-- Orcamentos
-- ===========================================================================
-- Varios por ordem, de fornecedores diferentes: e assim que uma empresa
-- compara propostas antes de decidir. Um orcamento unico embutido na ordem
-- obrigava a apagar o anterior para registar o seguinte.
CREATE TABLE work_order_quotes (
    id               VARCHAR(36)  PRIMARY KEY,
    work_order_id    VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    supplier_id      VARCHAR(36)  REFERENCES suppliers(id) ON DELETE SET NULL,
    supplier_label   VARCHAR(200),
    quote_number     VARCHAR(60),
    quoted_at        TIMESTAMP,
    valid_until      TIMESTAMP,

    parts_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,
    labor_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,
    other_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,
    discount_amount  NUMERIC(16,2) NOT NULL DEFAULT 0,
    tax_amount       NUMERIC(16,2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(16,2) NOT NULL DEFAULT 0,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AOA',

    -- Escolhido: e este que passa a orcamento da ordem. Só um por ordem.
    is_selected      BOOLEAN      NOT NULL DEFAULT FALSE,
    file_id          VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    notes            VARCHAR(2000),
    created_by       VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);
CREATE INDEX ix_work_order_quotes_wo ON work_order_quotes(work_order_id);

-- ===========================================================================
-- Historico de estados
-- ===========================================================================
-- Uma linha por transicao. O registo de auditoria geral guarda "a ordem mudou";
-- isto guarda a sequencia, que e o que permite ver que uma ordem esteve tres
-- semanas em "aguardando pecas" -- e nenhuma media de tempo de reparacao
-- explica isso sozinha.
CREATE TABLE work_order_status_history (
    id               VARCHAR(36)  PRIMARY KEY,
    work_order_id    VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    from_status      VARCHAR(30),
    to_status        VARCHAR(30)  NOT NULL,
    changed_by       VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    changed_by_label VARCHAR(160),
    note             VARCHAR(1000),
    -- Quanto tempo esteve no estado anterior. Calculado na transicao para o
    -- historico nao mudar quando alguem corrige uma data mais tarde.
    minutes_in_previous INTEGER,
    changed_at       TIMESTAMP    NOT NULL
);
CREATE INDEX ix_wo_status_history ON work_order_status_history(work_order_id, changed_at);

-- ===========================================================================
-- Checklist na ordem
-- ===========================================================================
-- Reaproveita o modulo de checklists da V5 em vez de criar um segundo. A
-- execucao ja guarda um instantaneo imutavel dos itens; so lhe faltava saber a
-- que ordem pertencia.
ALTER TABLE checklist_executions ADD COLUMN work_order_id VARCHAR(36) REFERENCES work_orders(id) ON DELETE SET NULL;
CREATE INDEX ix_checklist_exec_wo ON checklist_executions(work_order_id);

-- ===========================================================================
-- Numeracao com ano
-- ===========================================================================
-- OM-2026-000001. O contador passa a ser por ano: um contador unico daria
-- OM-2027-000998 no primeiro dia de 2027, e o numero deixaria de dizer nada
-- sobre o volume do ano.
-- A coluna de estado nasceu VARCHAR(15) na V8, quando os estados eram curtos.
-- "AWAITING_APPROVAL" tem 17 caracteres e nao cabia: a ordem recusava a
-- transicao com um erro de integridade que nao dizia nada a ninguem.
ALTER TABLE work_orders ALTER COLUMN status SET DATA TYPE VARCHAR(30);
ALTER TABLE work_orders ALTER COLUMN order_type SET DATA TYPE VARCHAR(20);

ALTER TABLE work_orders ADD COLUMN order_year INTEGER;
CREATE INDEX ix_work_orders_year ON work_orders(organization_id, order_year);
