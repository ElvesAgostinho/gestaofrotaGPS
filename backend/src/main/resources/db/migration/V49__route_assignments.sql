-- Fatia 43: uma rota nunca fica sem viatura.
--
-- A rota continua a ser o percurso (reutilizavel: Luanda->Lobito e uma so,
-- nao uma por camiao), mas passa a ter atribuicoes: que viatura a faz, com
-- que motorista e em que dia. Ao criar uma rota exige-se a primeira.
CREATE TABLE route_assignments (
    id               VARCHAR(36) PRIMARY KEY,
    organization_id  VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    route_id         VARCHAR(36) NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    asset_id         VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    driver_id        VARCHAR(36) REFERENCES drivers(id) ON DELETE SET NULL,
    planned_for      DATE,
    notes            VARCHAR(500),
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL
);
CREATE INDEX ix_route_assignments_route ON route_assignments (route_id, planned_for);
CREATE INDEX ix_route_assignments_asset ON route_assignments (asset_id, planned_for);
CREATE INDEX ix_route_assignments_org ON route_assignments (organization_id, active);
