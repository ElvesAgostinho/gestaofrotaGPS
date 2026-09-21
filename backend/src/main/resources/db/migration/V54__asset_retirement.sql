-- Abate de ativos: quando uma viatura, máquina ou gerador sai da frota.
--
-- Até aqui «abater» era arquivar: o ativo desaparecia das listas e mais nada.
-- Faltava o que uma empresa precisa de saber depois — quando saiu, porquê, com
-- que contador, por quanto foi vendido, e quanto custou ao longo da vida. Sem
-- isso não se responde à única pergunta que interessa na altura de comprar
-- outra: valeu a pena?
ALTER TABLE assets ADD COLUMN retired_at TIMESTAMP;
ALTER TABLE assets ADD COLUMN retired_reason VARCHAR(30);
ALTER TABLE assets ADD COLUMN retired_notes VARCHAR(1000);
ALTER TABLE assets ADD COLUMN retired_meter NUMERIC(12, 2);
ALTER TABLE assets ADD COLUMN residual_value NUMERIC(14, 2);
ALTER TABLE assets ADD COLUMN retired_by VARCHAR(36);

CREATE INDEX ix_assets_retired ON assets (organization_id, retired_at);
