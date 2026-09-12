-- Fatia 4b: estado de presença de cada ativo em cada geocerca.
--
-- Sem isto, decidir se uma posição é "entrada" ou "saída" obrigaria a procurar
-- o último evento de cada par (cerca, ativo) — N consultas por cada posição
-- recebida. Com esta tabela é uma só consulta por posição (todas as presenças
-- do ativo), e só se escreve quando o estado muda.

CREATE TABLE geofence_presence (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    geofence_id     VARCHAR(36) NOT NULL REFERENCES geofences(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    inside          BOOLEAN     NOT NULL,
    -- Desde quando está neste estado: dá "está na obra há 3 h" sem varrer eventos.
    since           TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    CONSTRAINT uq_geofence_presence UNIQUE (geofence_id, asset_id)
);
CREATE INDEX ix_geofence_presence_asset ON geofence_presence(asset_id);
CREATE INDEX ix_geofence_presence_org ON geofence_presence(organization_id, inside);
