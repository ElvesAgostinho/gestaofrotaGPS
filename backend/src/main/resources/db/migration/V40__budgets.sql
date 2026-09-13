-- Fatia 33: orcamento anual.
--
-- A primeira pergunta do diretor financeiro: quanto e que esta viatura (ou
-- esta filial) ja gastou este ano contra o que estava previsto? Um orcamento
-- e um valor anual por ambito (frota inteira, filial ou viatura) e por
-- categoria (manutencao, combustivel ou tudo). O real vem das ordens
-- concluidas e dos abastecimentos; o aviso sai aos 80 % e aos 100 %.

CREATE TABLE budgets (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    fiscal_year     INTEGER       NOT NULL,
    scope           VARCHAR(12)   NOT NULL,          -- ORG | LOCATION | ASSET
    asset_id        VARCHAR(36)   REFERENCES assets(id),
    location_id     VARCHAR(36)   REFERENCES locations(id),
    category        VARCHAR(16)   NOT NULL,          -- MAINTENANCE | FUEL | TOTAL
    amount          DECIMAL(16,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'AOA',
    notes           VARCHAR(500),
    -- Ultimo marco avisado (80 ou 100), para nao repetir o aviso todos os dias.
    warned_at_pct   INTEGER,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL
);
CREATE INDEX idx_budgets_org_year ON budgets(organization_id, fiscal_year);
