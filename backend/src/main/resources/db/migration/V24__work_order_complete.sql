-- Fatia 16: a ficha de ordem de servico completa.
--
-- O que faltava nao eram campos decorativos. Era o que uma empresa grande tem
-- mesmo de responder: quanto custou ao todo (incluindo a oficina de fora),
-- quanto tempo a maquina esteve parada e o que isso vale, se foi feito dentro
-- do prazo, se estava em garantia, e porque e que a avaria aconteceu.

-- ===========================================================================
-- Prazo e cumprimento
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN due_at        TIMESTAMP;
-- Calculado no fecho e guardado: se fosse calculado na leitura, mudar a regra
-- de prazos amanha reescrevia a historia de cumprimento de ontem.
ALTER TABLE work_orders ADD COLUMN sla_met       BOOLEAN;

-- ===========================================================================
-- Previsto e realizado
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN estimated_hours NUMERIC(10,2);
ALTER TABLE work_orders ADD COLUMN estimated_cost  NUMERIC(16,2);

-- ===========================================================================
-- Custo total
-- ===========================================================================
-- Ate aqui so havia horas de mao de obra e custo de pecas. Sem o custo da mao
-- de obra e sem a oficina externa, o "custo de manutencao" de um ativo era uma
-- fraccao do que a empresa pagou -- e as decisoes de substituir ou reparar
-- eram tomadas com o numero errado.
ALTER TABLE work_orders ADD COLUMN total_labor_cost NUMERIC(16,2);
ALTER TABLE work_orders ADD COLUMN total_external_cost NUMERIC(16,2);
ALTER TABLE work_orders ADD COLUMN total_cost       NUMERIC(16,2);

-- ===========================================================================
-- Paragem
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN downtime_hours     NUMERIC(10,2);
-- Custo por hora de paragem deste ativo, para a paragem ter numero. Fica no
-- ativo porque um gerador parado custa o que a obra deixa de produzir, e isso
-- nao se adivinha.
ALTER TABLE assets ADD COLUMN downtime_cost_per_hour NUMERIC(14,2);
ALTER TABLE work_orders ADD COLUMN downtime_cost      NUMERIC(16,2);

-- ===========================================================================
-- Garantia, causa e desfecho
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN under_warranty      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE work_orders ADD COLUMN warranty_reference  VARCHAR(120);
ALTER TABLE work_orders ADD COLUMN warranty_recovered  NUMERIC(16,2);
ALTER TABLE work_orders ADD COLUMN root_cause          VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN corrective_action   VARCHAR(2000);
ALTER TABLE work_orders ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE work_orders ADD COLUMN verified_by_user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL;

-- ===========================================================================
-- Contexto
-- ===========================================================================
ALTER TABLE work_orders ADD COLUMN closing_meter_value NUMERIC(14,2);
ALTER TABLE work_orders ADD COLUMN branch_id           VARCHAR(36) REFERENCES locations(id) ON DELETE SET NULL;
ALTER TABLE work_orders ADD COLUMN driver_id           VARCHAR(36) REFERENCES drivers(id) ON DELETE SET NULL;
-- Ordem que deu origem a esta -- tipicamente uma inspecao que encontrou algo.
ALTER TABLE work_orders ADD COLUMN parent_work_order_id VARCHAR(36) REFERENCES work_orders(id) ON DELETE SET NULL;
ALTER TABLE work_orders ADD COLUMN system_code         VARCHAR(30);
ALTER TABLE work_orders ADD COLUMN requires_shutdown   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE work_orders ADD COLUMN safety_notes        VARCHAR(2000);

CREATE INDEX ix_work_orders_due ON work_orders(organization_id, due_at);
CREATE INDEX ix_work_orders_branch ON work_orders(branch_id);
CREATE INDEX ix_work_orders_system ON work_orders(asset_id, system_code, opened_at);

-- ===========================================================================
-- Servicos externos
-- ===========================================================================
-- Em Angola grande parte da manutencao pesada vai para fora. Sem estas linhas,
-- o custo da ordem ficava sistematicamente abaixo do real.
CREATE TABLE work_order_services (
    id               VARCHAR(36)  PRIMARY KEY,
    work_order_id    VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    supplier         VARCHAR(200) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    invoice_number   VARCHAR(60),
    cost             NUMERIC(16,2) NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AOA',
    performed_at     TIMESTAMP,
    -- Garantia dada pela oficina sobre o servico. Uma avaria que volte dentro
    -- deste prazo nao se paga duas vezes -- se alguem se lembrar de a invocar.
    warranty_months  INTEGER,
    warranty_until   TIMESTAMP,
    notes            VARCHAR(500),
    created_at       TIMESTAMP    NOT NULL
);
CREATE INDEX ix_work_order_services_wo ON work_order_services(work_order_id);

-- ===========================================================================
-- Anexos
-- ===========================================================================
CREATE TABLE work_order_attachments (
    id             VARCHAR(36)  PRIMARY KEY,
    work_order_id  VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    file_id        VARCHAR(36)  NOT NULL REFERENCES stored_files(id) ON DELETE CASCADE,
    caption        VARCHAR(300),
    kind           VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    uploaded_by    VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMP    NOT NULL
);
CREATE INDEX ix_work_order_attachments_wo ON work_order_attachments(work_order_id);
