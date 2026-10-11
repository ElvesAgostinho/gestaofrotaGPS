-- Pedidos de cotação: pedir três preços deixa de ser uma promessa.
--
-- Numa obra, «pediram-se três preços» é uma frase que se diz e que ninguém
-- consegue provar. Os preços chegam por WhatsApp, por email e por telefone, e
-- a comparação faz-se de cabeça — quando se faz. O resultado é escolher pelo
-- que se lembra em vez de pelo que custa.
--
-- Quatro tabelas, que são as quatro perguntas:
--
--  * O PEDIDO: o que é preciso, para que equipamento, até quando.
--  * A QUEM se pediu, e quando respondeu (ou não).
--  * QUANTO cobrou cada um, linha a linha.
--  * E, no fim, a quem se ADJUDICOU — porque o pedido tem de terminar numa
--    encomenda, senão é papel.
CREATE TABLE rfqs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    number VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    -- OPEN, AWARDED, CANCELLED
    status VARCHAR(20) NOT NULL,
    -- Até quando se aceitam respostas. Sem data, o pedido fica aberto para
    -- sempre e a compra urgente espera por quem nunca responde.
    due_date DATE,
    notes VARCHAR(2000),
    -- A ordem de compra que nasceu deste pedido.
    awarded_order_id VARCHAR(36) REFERENCES purchase_orders (id),
    awarded_supplier_id VARCHAR(36) REFERENCES suppliers (id),
    awarded_reason VARCHAR(500),
    awarded_at TIMESTAMP,
    created_by VARCHAR(36) REFERENCES users (id),
    created_by_label VARCHAR(160),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_rfqs_numero ON rfqs (organization_id, number);
CREATE INDEX ix_rfqs_estado ON rfqs (organization_id, status);

CREATE TABLE rfq_lines (
    id VARCHAR(36) PRIMARY KEY,
    rfq_id VARCHAR(36) NOT NULL REFERENCES rfqs (id) ON DELETE CASCADE,
    part_id VARCHAR(36) REFERENCES parts (id),
    description VARCHAR(300) NOT NULL,
    specification VARCHAR(300),
    quantity NUMERIC(12, 2) NOT NULL,
    unit VARCHAR(20),
    asset_id VARCHAR(36) REFERENCES assets (id),
    asset_tag VARCHAR(40),
    sort_order INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_rfq_lines_pedido ON rfq_lines (rfq_id);

CREATE TABLE rfq_suppliers (
    id VARCHAR(36) PRIMARY KEY,
    rfq_id VARCHAR(36) NOT NULL REFERENCES rfqs (id) ON DELETE CASCADE,
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers (id),
    sent_at TIMESTAMP,
    sent_to VARCHAR(300),
    responded_at TIMESTAMP,
    -- Quem nao responde tambem e uma resposta, e fica registado.
    declined BOOLEAN DEFAULT FALSE NOT NULL,
    lead_time_days INT,
    valid_until DATE,
    payment_terms VARCHAR(200),
    notes VARCHAR(500),
    -- Somado das linhas respondidas; guardado para ordenar sem recalcular.
    total NUMERIC(14, 2),
    currency VARCHAR(3) DEFAULT 'AOA' NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_rfq_suppliers_par ON rfq_suppliers (rfq_id, supplier_id);

CREATE TABLE rfq_quotes (
    id VARCHAR(36) PRIMARY KEY,
    rfq_supplier_id VARCHAR(36) NOT NULL REFERENCES rfq_suppliers (id) ON DELETE CASCADE,
    rfq_line_id VARCHAR(36) NOT NULL REFERENCES rfq_lines (id) ON DELETE CASCADE,
    unit_price NUMERIC(14, 2),
    supplier_part_number VARCHAR(80),
    -- Um fornecedor pode nao ter uma das linhas: isso nao o desclassifica das
    -- outras, mas tem de se ver.
    unavailable BOOLEAN DEFAULT FALSE NOT NULL,
    note VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_rfq_quotes_par ON rfq_quotes (rfq_supplier_id, rfq_line_id);

-- A ordem de compra passa a saber de que pedido nasceu.
ALTER TABLE purchase_orders ADD COLUMN rfq_id VARCHAR(36) REFERENCES rfqs (id);
