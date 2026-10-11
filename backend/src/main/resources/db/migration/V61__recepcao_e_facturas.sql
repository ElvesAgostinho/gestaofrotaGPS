-- Recepção e facturas: a encomenda fecha o ciclo.
--
-- Uma ordem de compra que nunca é recebida é uma promessa: o stock continua a
-- dizer que não há, o painel continua a dizer que vem a caminho, e a peça está
-- na prateleira há três semanas. Receber é o acto que torna tudo verdadeiro —
-- entra no armazém, actualiza o custo médio, e o que faltava deixa de faltar.
--
-- Recebe-se em PARTES, porque é assim que chega: o fornecedor manda metade e
-- promete o resto. Uma recepção que só aceitasse tudo de uma vez obrigava a
-- mentir ou a não registar nada.
CREATE TABLE purchase_receipts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    number VARCHAR(40) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    warehouse_id VARCHAR(36) REFERENCES warehouses (id),
    -- A guia de remessa que veio com a mercadoria.
    delivery_note VARCHAR(80),
    received_by VARCHAR(36) REFERENCES users (id),
    received_by_label VARCHAR(160),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_purchase_receipts_ordem ON purchase_receipts (order_id);

CREATE TABLE purchase_receipt_lines (
    id VARCHAR(36) PRIMARY KEY,
    receipt_id VARCHAR(36) NOT NULL REFERENCES purchase_receipts (id) ON DELETE CASCADE,
    order_line_id VARCHAR(36) NOT NULL REFERENCES purchase_order_lines (id),
    quantity NUMERIC(12, 2) NOT NULL,
    -- O que chegou mal: nao entra no stock e fica a aguardar devolucao.
    rejected_quantity NUMERIC(12, 2) DEFAULT 0 NOT NULL,
    rejection_reason VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_purchase_receipt_lines_recepcao ON purchase_receipt_lines (receipt_id);

-- As facturas do fornecedor.
--
-- Ficam ligadas a ordem quando ha ordem, e ao fornecedor sempre. E o que
-- permite responder a duas perguntas que hoje vivem numa pasta de papel: «ja
-- pagamos esta?» e «quanto e que esta empresa nos facturou este ano?».
CREATE TABLE supplier_invoices (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    order_id VARCHAR(36) REFERENCES purchase_orders (id),
    number VARCHAR(80) NOT NULL,
    issued_at DATE,
    due_at DATE,
    amount NUMERIC(14, 2),
    tax_amount NUMERIC(14, 2),
    withholding_amount NUMERIC(14, 2),
    currency VARCHAR(3) DEFAULT 'AOA' NOT NULL,
    -- PENDING, PAID, DISPUTED, CANCELLED
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    paid_at DATE,
    -- O ficheiro digitalizado.
    file_id VARCHAR(36),
    file_name VARCHAR(300),
    notes VARCHAR(1000),
    created_by VARCHAR(36) REFERENCES users (id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_supplier_invoices_fornecedor ON supplier_invoices (supplier_id);
CREATE INDEX ix_supplier_invoices_ordem ON supplier_invoices (order_id);
CREATE INDEX ix_supplier_invoices_estado ON supplier_invoices (organization_id, status);
CREATE UNIQUE INDEX ux_supplier_invoices_numero ON supplier_invoices (supplier_id, number);
