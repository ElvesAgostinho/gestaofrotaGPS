-- Ordens de compra: a encomenda deixa de ser um telefonema.
--
-- Hoje a compra acontece assim: alguém olha para a lista do que falta, liga ao
-- fornecedor, e o que foi pedido fica na memória de quem ligou. Não há registo
-- do que se encomendou, nem do preço combinado, nem de quem autorizou — e
-- quando a peça não chega, não há a quem mostrar nada.
--
-- Três tabelas e uma de configuração:
--
--  * A ORDEM, com número próprio e sequencial, e uma cópia dos dados do
--    fornecedor à data. A cópia é deliberada: se amanhã o IBAN dele mudar, a
--    ordem que se enviou tem de continuar a dizer o que dizia quando saiu.
--  * As LINHAS, cada uma com a peça, a referência DELE, o preço combinado e o
--    equipamento a que se destina.
--  * O HISTÓRICO: criada por, aprovada por, enviada a, confirmada em. Sem
--    isto, «quem autorizou isto?» não tem resposta.
--  * E os LIMITES de aprovação, que são o que impede a aprovação de se tornar
--    uma chatice numa empresa de três pessoas e de desaparecer numa de
--    trezentas.
CREATE TABLE purchase_settings (
    organization_id VARCHAR(36) PRIMARY KEY REFERENCES organizations (id),
    -- Abaixo deste valor, quem cria aprova. Senão ninguém usa o sistema.
    auto_approve_below NUMERIC(14, 2),
    -- Acima deste valor, só o dono aprova.
    owner_approval_above NUMERIC(14, 2),
    currency VARCHAR(3) DEFAULT 'AOA' NOT NULL,
    -- Onde a mercadoria é entregue, impresso na ordem.
    delivery_address VARCHAR(400),
    delivery_contact VARCHAR(200),
    -- Texto fixo no rodapé da ordem (condições gerais de compra).
    terms VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE purchase_orders (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    number VARCHAR(40) NOT NULL,
    supplier_id VARCHAR(36) REFERENCES suppliers (id),
    -- A fotografia do fornecedor no momento em que a ordem saiu.
    supplier_name VARCHAR(200) NOT NULL,
    supplier_tax_id VARCHAR(40),
    supplier_email VARCHAR(190),
    supplier_iban VARCHAR(60),
    supplier_bank VARCHAR(120),
    payment_terms VARCHAR(200),
    -- DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, SENT, CONFIRMED,
    -- PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED
    status VARCHAR(24) NOT NULL,
    currency VARCHAR(3) DEFAULT 'AOA' NOT NULL,
    subtotal NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    discount_amount NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    tax_amount NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    -- Retenção na fonte: o que se retém e entrega ao Estado, e que por isso
    -- NÃO vai na transferência para o fornecedor.
    withholding_amount NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    total NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    -- O que se transfere de facto: total menos retenção.
    payable NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    expected_at DATE,
    delivery_address VARCHAR(400),
    notes VARCHAR(2000),
    created_by VARCHAR(36) REFERENCES users (id),
    created_by_label VARCHAR(160),
    approved_by VARCHAR(36) REFERENCES users (id),
    approved_by_label VARCHAR(160),
    approved_at TIMESTAMP,
    rejected_reason VARCHAR(500),
    sent_at TIMESTAMP,
    sent_to VARCHAR(300),
    confirmed_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_purchase_orders_numero ON purchase_orders (organization_id, number);
CREATE INDEX ix_purchase_orders_estado ON purchase_orders (organization_id, status);
CREATE INDEX ix_purchase_orders_fornecedor ON purchase_orders (supplier_id);

CREATE TABLE purchase_order_lines (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    part_id VARCHAR(36) REFERENCES parts (id),
    -- Em texto, porque se compra coisas que não estão no armazém.
    description VARCHAR(300) NOT NULL,
    specification VARCHAR(300),
    -- A referência dele: é com ela que o armazém do fornecedor encontra a peça.
    supplier_part_number VARCHAR(80),
    quantity NUMERIC(12, 2) NOT NULL,
    unit VARCHAR(20),
    unit_price NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    line_total NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    -- Para que equipamento é: é o que liga a compra ao custo da viatura.
    asset_id VARCHAR(36) REFERENCES assets (id),
    asset_tag VARCHAR(40),
    received_quantity NUMERIC(12, 2) DEFAULT 0 NOT NULL,
    sort_order INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_purchase_order_lines_ordem ON purchase_order_lines (order_id);
CREATE INDEX ix_purchase_order_lines_peca ON purchase_order_lines (part_id);

CREATE TABLE purchase_order_events (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    note VARCHAR(500),
    by_user VARCHAR(36) REFERENCES users (id),
    by_label VARCHAR(160),
    at TIMESTAMP NOT NULL
);

CREATE INDEX ix_purchase_order_events_ordem ON purchase_order_events (order_id, at);
