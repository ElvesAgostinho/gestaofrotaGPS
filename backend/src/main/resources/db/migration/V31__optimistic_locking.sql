-- Fatia 24: versao por registo, para gravacoes concorrentes.
--
-- Dois gestores abrem a mesma ficha; o segundo a gravar apagava em silencio o
-- que o primeiro tinha escrito. A coluna e comparada pelo Hibernate ao gravar
-- (@Version): se mudou entretanto, a gravacao e recusada com um 409 explicado.
--
-- DEFAULT 0 e nao NULL: um registo antigo tem versao zero, nao «sem versao».

ALTER TABLE assets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE asset_types ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE work_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE locations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE drivers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE routes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE parts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE warehouses ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transport_notes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE maintenance_plans ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
