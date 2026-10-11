-- Fornecedores a sério, e o preço de cada peça em cada um deles.
--
-- O que lá estava servia para dizer «a oficina X arranjou isto». Para COMPRAR
-- falta tudo o que decide a quem se compra:
--
--  * o IBAN e o regime fiscal, sem os quais a ordem de compra não serve ao
--    financeiro e a retenção na fonte dos serviços (6,5 % em Angola) não se
--    calcula;
--  * o prazo de entrega REAL deste fornecedor — não o do artigo —, porque é
--    com ele que se decide quando encomendar;
--  * se é importador: um filtro CIF por despachar em Luanda não é o mesmo
--    artigo, em dias, que um filtro em prateleira no Rangel;
--  * o que ele fornece, para a lista de compras saber a quem pedir cada linha.
--
-- E duas tabelas novas que faltavam por completo:
--
--  * CONTACTOS — na prática a ordem vai para o comercial com cópia ao
--    financeiro, e o armazém é outro número. Um `contact_person` não chega.
--  * PREÇOS — é o elo que não existia. Sem ele a lista de compras sabe que
--    faltam quatro filtros mas não sabe quanto custam nem a quem; e a ordem de
--    compra não tem preço nenhum para levar.
ALTER TABLE suppliers ADD COLUMN vat_regime VARCHAR(30);
ALTER TABLE suppliers ADD COLUMN withholding_tax NUMERIC(5, 2);
ALTER TABLE suppliers ADD COLUMN iban VARCHAR(60);
ALTER TABLE suppliers ADD COLUMN bank_name VARCHAR(120);
ALTER TABLE suppliers ADD COLUMN currency VARCHAR(3);
ALTER TABLE suppliers ADD COLUMN avg_lead_time_days INT;
ALTER TABLE suppliers ADD COLUMN importer BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE suppliers ADD COLUMN incoterm VARCHAR(20);
ALTER TABLE suppliers ADD COLUMN customs_days INT;
ALTER TABLE suppliers ADD COLUMN min_order_value NUMERIC(14, 2);
-- Marcas que representa e famílias que fornece, em texto separado por vírgulas:
-- uma lista destas muda ao telefone, e uma tabela de ligação só acrescentava
-- cliques a quem está a escriturar um fornecedor novo.
ALTER TABLE suppliers ADD COLUMN brands VARCHAR(500);
ALTER TABLE suppliers ADD COLUMN families VARCHAR(300);

CREATE TABLE supplier_contacts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    -- SALES, FINANCE, WAREHOUSE, TECHNICAL, OTHER
    role VARCHAR(20) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(190),
    note VARCHAR(300),
    -- O que recebe a ordem de compra por omissão.
    primary_contact BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_supplier_contacts_supplier ON supplier_contacts (supplier_id);

-- Alvará, certidão de não dívida, declaração de início de actividade. Comprar
-- a quem tem a certidão caducada é um problema que só aparece na contabilidade,
-- meses depois — e usa-se a mesma máquina de avisos de validade dos ativos.
CREATE TABLE supplier_documents (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    number VARCHAR(80),
    issued_at DATE,
    expires_at DATE,
    file_id VARCHAR(36),
    note VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_supplier_documents_supplier ON supplier_documents (supplier_id);
CREATE INDEX ix_supplier_documents_expiry ON supplier_documents (organization_id, expires_at);

CREATE TABLE supplier_prices (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    part_id VARCHAR(36) NOT NULL REFERENCES parts (id) ON DELETE CASCADE,
    -- A referência DELE, que raramente é a nossa: vai escrita na ordem de
    -- compra para o armazém dele encontrar a peça.
    supplier_part_number VARCHAR(80),
    price NUMERIC(14, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'AOA' NOT NULL,
    -- Quantidade mínima por encomenda: pedir 2 quando o mínimo é 10 é um
    -- pedido que volta.
    min_order_quantity NUMERIC(12, 2),
    lead_time_days INT,
    -- Uma cotação tem validade. Passada a data, o preço é uma referência
    -- histórica e o painel diz isso em vez de fingir que ainda vale.
    quoted_at DATE,
    valid_until DATE,
    note VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_supplier_prices_part ON supplier_prices (part_id);
CREATE INDEX ix_supplier_prices_supplier ON supplier_prices (supplier_id);
CREATE UNIQUE INDEX ux_supplier_prices_par ON supplier_prices (supplier_id, part_id);
