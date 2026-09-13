-- Fatia 34: extrato dos cartoes de combustivel.
--
-- A gestora de cartoes (Sonangol, Pumangol, banco) manda todos os meses o
-- extrato: cada linha e um abastecimento que foi mesmo pago. Cruzado com o
-- que os motoristas registaram, e onde a fraude aparece: o cartao pagou e
-- ninguem registou (foi para outro deposito?), ou registou-se um
-- abastecimento que o cartao nunca pagou.

CREATE TABLE fuel_card_transactions (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    asset_id        VARCHAR(36)   REFERENCES assets(id),
    card_number     VARCHAR(40),
    transacted_at   TIMESTAMP     NOT NULL,
    liters          DECIMAL(10,2),
    amount          DECIMAL(14,2),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'AOA',
    station         VARCHAR(160),
    reference       VARCHAR(80),
    -- MATCHED (bate com um registo), UNMATCHED (pago sem registo), IGNORED (tratado)
    status          VARCHAR(12)   NOT NULL DEFAULT 'UNMATCHED',
    fuel_record_id  VARCHAR(36)   REFERENCES fuel_records(id) ON DELETE SET NULL,
    imported_at     TIMESTAMP     NOT NULL,
    import_batch    VARCHAR(36)   NOT NULL
);
CREATE INDEX idx_fuel_card_tx_org ON fuel_card_transactions(organization_id, transacted_at);
CREATE INDEX idx_fuel_card_tx_asset ON fuel_card_transactions(asset_id, transacted_at);
-- A mesma linha do extrato nao entra duas vezes.
CREATE UNIQUE INDEX uq_fuel_card_tx ON fuel_card_transactions(organization_id, card_number, transacted_at, liters, amount);
