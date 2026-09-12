-- Fatia 19: o que se faz mesmo na oficina.
--
-- A ordem ja tinha o lado administrativo completo (custos, prazos, aprovacoes,
-- orcamentos, garantia, imobilizacao). O que faltava era o lado tecnico: o que
-- o mecanico mede, o que despeja, os codigos que le no computador de bordo, e
-- quem assina no fim.
--
-- Nao duplica nada do que ja existe:
--   * analise de oleo -> modulo preditivo (PredictiveReading, OIL_ANALYSIS)
--   * inspecao passa/nao-passa -> checklists (V5), agora com medicao numerica
--   * pecas e stock -> V7/V8
--   * proxima revisao por intervalo -> planos preventivos (V6)

-- ===========================================================================
-- Medicoes
-- ===========================================================================
-- A tabela que faltava. Um checklist so diz "OK" ou "nao OK"; nao diz que a
-- pastilha tem 4 mm. Sem o numero nao ha forma de saber se ela estava a 9 mm
-- ha tres meses, e portanto nao ha forma de prever quando chega ao limite.
--
-- E deliberadamente generica: serve pastilhas, pneus, tensao de bateria,
-- pressao de oleo, tensao por fase de um gerador e prova de carga. Uma tabela
-- por tipo de medicao daria dez tabelas e nenhuma consulta que as cruzasse.
CREATE TABLE work_order_measurements (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    work_order_id     VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    -- Agrupa no ecra: "Travagem", "Pneus", "Motor", "Gerador".
    group_name        VARCHAR(60)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    -- Onde foi medido: "Dianteiro esquerdo", "Fase R", "Cilindro 3".
    position          VARCHAR(60),
    value_num         DECIMAL(18,4),
    -- Para o que nao e numero: "limpo", "com folga", "verde".
    value_text        VARCHAR(200),
    unit              VARCHAR(20),
    -- Limites de servico. Fora deles o sistema marca sozinho.
    min_value         DECIMAL(18,4),
    max_value         DECIMAL(18,4),
    -- OK | ATTENTION | REPLACE | FAIL
    verdict           VARCHAR(20)  NOT NULL DEFAULT 'OK',
    note              VARCHAR(500),
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    recorded_by       VARCHAR(36)  REFERENCES users(id),
    recorded_by_label VARCHAR(120),
    recorded_at       TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_wo_measure_order ON work_order_measurements(work_order_id, sort_order);
CREATE INDEX idx_wo_measure_org ON work_order_measurements(organization_id, name);

-- ===========================================================================
-- Fluidos e filtros
-- ===========================================================================
-- Uma peca diz "filtro de oleo". Nao diz que se meteram 38 litros de 15W-40
-- CH-4. Numa frota pesada o consumo de lubrificante e uma rubrica de custo por
-- si so, e a especificacao errada estraga o motor.
CREATE TABLE work_order_fluids (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    work_order_id     VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    -- ENGINE_OIL | TRANSMISSION | HYDRAULIC | COOLANT | BRAKE_FLUID
    -- | DIFFERENTIAL | GREASE | FUEL | ADBLUE | OTHER
    kind              VARCHAR(24)  NOT NULL,
    -- Especificacao tecnica: "15W-40 CH-4", "SAE 80W-90 GL-5", "ELC".
    spec              VARCHAR(80),
    brand             VARCHAR(80),
    -- ADDED | REPLACED | TOPPED_UP | DRAINED | SAMPLED
    action            VARCHAR(20)  NOT NULL DEFAULT 'REPLACED',
    quantity          DECIMAL(12,3),
    unit              VARCHAR(12)  NOT NULL DEFAULT 'L',
    -- Filtro trocado ao mesmo tempo. O numero de peca e o que permite repetir
    -- a compra sem enganos.
    filter_changed    BOOLEAN      NOT NULL DEFAULT FALSE,
    filter_part_number VARCHAR(60),
    batch             VARCHAR(60),
    unit_cost         DECIMAL(18,2),
    total_cost        DECIMAL(18,2),
    note              VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_wo_fluid_order ON work_order_fluids(work_order_id);

-- ===========================================================================
-- Codigos de avaria lidos no equipamento
-- ===========================================================================
-- Um camiao moderno e um gerador dizem o que tem: J1939 (SPN/FMI) num pesado,
-- OBD-II num ligeiro, o painel do gerador nos restantes. Guardar o codigo e o
-- que permite, um ano depois, ver que o mesmo SPN voltou tres vezes.
CREATE TABLE work_order_fault_codes (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    work_order_id     VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    -- J1939 | OBD2 | PANEL | MANUFACTURER | OTHER
    source            VARCHAR(20)  NOT NULL DEFAULT 'J1939',
    code              VARCHAR(40)  NOT NULL,
    description       VARCHAR(300),
    occurrences       INTEGER,
    -- ACTIVE | STORED | CLEARED
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    first_seen_at     TIMESTAMP,
    cleared_at        TIMESTAMP,
    note              VARCHAR(500),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_wo_fault_order ON work_order_fault_codes(work_order_id);
CREATE INDEX idx_wo_fault_code ON work_order_fault_codes(organization_id, code);

-- ===========================================================================
-- Assinaturas e aceitacao
-- ===========================================================================
-- O PDF ja tinha tres linhas para assinar em papel. Isto guarda quem assinou
-- de facto: sem a aceitacao do operador, "a viatura ja vinha assim" e
-- "estragaram-na na oficina" continuam indemonstraveis.
CREATE TABLE work_order_signatures (
    id                VARCHAR(36)  PRIMARY KEY,
    organization_id   VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    work_order_id     VARCHAR(36)  NOT NULL REFERENCES work_orders(id) ON DELETE CASCADE,
    -- TECHNICIAN | SUPERVISOR | OPERATOR | QUALITY | CLIENT
    role              VARCHAR(20)  NOT NULL,
    person_name       VARCHAR(150) NOT NULL,
    person_document   VARCHAR(60),
    user_id           VARCHAR(36)  REFERENCES users(id),
    accepted          BOOLEAN      NOT NULL DEFAULT TRUE,
    note              VARCHAR(500),
    signed_at         TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_wo_sign_order ON work_order_signatures(work_order_id);

-- ===========================================================================
-- Campos novos na propria ordem
-- ===========================================================================

-- --- Bloqueio e etiquetagem (LOTO) ---------------------------------------
-- requires_shutdown e safety_notes ja existiam, mas em texto livre. Numa
-- manutencao com risco, quem bloqueou e quem desbloqueou tem de ficar
-- registado com nome e hora, ou o procedimento nao vale nada numa auditoria.
ALTER TABLE work_orders ADD COLUMN loto_applied BOOLEAN DEFAULT FALSE;
ALTER TABLE work_orders ADD COLUMN loto_tag_number VARCHAR(40);
ALTER TABLE work_orders ADD COLUMN loto_applied_by_label VARCHAR(120);
ALTER TABLE work_orders ADD COLUMN loto_applied_at TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN loto_removed_by_label VARCHAR(120);
ALTER TABLE work_orders ADD COLUMN loto_removed_at TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN work_permit_number VARCHAR(40);
-- LOW | MEDIUM | HIGH | CRITICAL
ALTER TABLE work_orders ADD COLUMN risk_level VARCHAR(20);
ALTER TABLE work_orders ADD COLUMN ppe_required VARCHAR(400);

-- --- Segundo medidor ------------------------------------------------------
-- Um gerador conta-se em horas e um camiao em quilometros, mas um camiao com
-- tomada de forca tem os dois, e a revisao depende de ambos.
ALTER TABLE work_orders ADD COLUMN hour_meter_value DECIMAL(18,2);
ALTER TABLE work_orders ADD COLUMN closing_hour_meter_value DECIMAL(18,2);

-- --- Ensaio final ---------------------------------------------------------
-- Entregar sem provar que ficou bom e devolver o problema ao condutor.
ALTER TABLE work_orders ADD COLUMN test_performed BOOLEAN DEFAULT FALSE;
-- ROAD | LOAD | BENCH | NONE
ALTER TABLE work_orders ADD COLUMN test_kind VARCHAR(20);
ALTER TABLE work_orders ADD COLUMN test_distance_km DECIMAL(12,2);
ALTER TABLE work_orders ADD COLUMN test_duration_minutes INTEGER;
-- PASSED | FAILED | PARTIAL
ALTER TABLE work_orders ADD COLUMN test_result VARCHAR(20);
ALTER TABLE work_orders ADD COLUMN test_notes VARCHAR(2000);

-- --- Proxima intervencao --------------------------------------------------
-- O plano preventivo trata do ciclo; isto guarda o que a oficina recomendou
-- nesta intervencao concreta ("voltar aos 5.000 km por causa do diferencial").
ALTER TABLE work_orders ADD COLUMN next_service_meter DECIMAL(18,2);
ALTER TABLE work_orders ADD COLUMN next_service_hour_meter DECIMAL(18,2);
ALTER TABLE work_orders ADD COLUMN next_service_at TIMESTAMP;
ALTER TABLE work_orders ADD COLUMN next_service_note VARCHAR(1000);

-- --- Codificacao de avaria (norma ISO 14224) ------------------------------
-- system_code ja dizia onde. Isto diz o que falhou e porque, em codigo, que e
-- o que permite somar avarias iguais em toda a frota.
ALTER TABLE work_orders ADD COLUMN component_code VARCHAR(40);
ALTER TABLE work_orders ADD COLUMN failure_mode VARCHAR(40);
ALTER TABLE work_orders ADD COLUMN failure_cause VARCHAR(40);
