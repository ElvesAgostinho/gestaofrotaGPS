-- Fatia 39: assistente de primeira utilizacao.
-- Guarda-se quando a empresa o terminou (ou saltou), para nao voltar a aparecer.
ALTER TABLE organizations ADD COLUMN onboarding_done_at TIMESTAMP;
-- Empresas que ja tem ativos ja passaram por isto sem assistente: nao as chatear.
UPDATE organizations SET onboarding_done_at = CURRENT_TIMESTAMP
 WHERE id IN (SELECT DISTINCT organization_id FROM assets);
