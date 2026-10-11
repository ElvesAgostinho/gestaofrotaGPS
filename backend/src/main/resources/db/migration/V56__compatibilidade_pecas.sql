-- Compatibilidade de peças: que peça serve que equipamento, e o que a substitui.
--
-- Até aqui o armazém sabia que tinha «Filtro de óleo». Não sabia para quê. Um
-- Hino 500 e um Hilux levam os dois um «filtro de óleo», e não é o mesmo — mas
-- o sistema deixava comprar o errado e montá-lo sem dizer nada. É a falha que
-- custa um motor, e não um filtro.
--
-- Duas tabelas, à maneira de quem vive disto:
--
-- 1. APLICAÇÕES — a peça serve esta família, esta marca, este modelo, deste ano
--    àquele, com este motor. Cada campo vazio é «qualquer»: uma linha só com
--    family='TRUCK_HEAVY' serve todos os camiões; uma com marca, modelo, motor
--    e anos serve exactamente uma viatura. A peça serve o equipamento se
--    QUALQUER das suas aplicações encaixar.
--
-- 2. REFERÊNCIAS CRUZADAS — o número do fabricante, o do original e os dos
--    alternativos, com a relação entre eles. Em Angola isto não é luxo: o
--    original não chega, chega o equivalente, e quem está no balcão precisa de
--    saber que são o mesmo. Também serve para encontrar o artigo escrevendo o
--    número que vem escrito na caixa, seja ele de quem for.
CREATE TABLE part_applications (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    part_id VARCHAR(36) NOT NULL REFERENCES parts (id) ON DELETE CASCADE,
    -- Família do catálogo (TRUCK_HEAVY, PICKUP, GENERATOR…). Vazio = qualquer.
    family VARCHAR(30),
    make VARCHAR(80),
    model VARCHAR(120),
    -- O motor é o que decide de verdade num filtro ou numa correia.
    engine VARCHAR(120),
    year_from INT,
    year_to INT,
    note VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_part_applications_part ON part_applications (part_id);
CREATE INDEX ix_part_applications_org ON part_applications (organization_id);

CREATE TABLE part_cross_refs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations (id),
    part_id VARCHAR(36) NOT NULL REFERENCES parts (id) ON DELETE CASCADE,
    number VARCHAR(80) NOT NULL,
    brand VARCHAR(80),
    -- OEM, EQUIVALENT, SUPERSEDES, SUPERSEDED_BY
    kind VARCHAR(20) NOT NULL,
    note VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_part_cross_refs_part ON part_cross_refs (part_id);
CREATE INDEX ix_part_cross_refs_number ON part_cross_refs (organization_id, number);

-- O motor do equipamento.
--
-- Faltava, e é o dado que decide a peça: dois Hilux do mesmo ano levam filtros
-- diferentes se um é 2.4 D-4D e o outro 2.8 GD-6. Sem isto, a compatibilidade
-- só se pode confirmar por marca e modelo, o que é adivinhar com jeito.
ALTER TABLE assets ADD COLUMN engine VARCHAR(120);
