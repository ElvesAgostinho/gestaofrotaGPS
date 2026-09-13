-- Fatia 41: relatorio mensal automatico ao gestor.
ALTER TABLE organizations ADD COLUMN monthly_report_enabled BOOLEAN NOT NULL DEFAULT TRUE;
-- Ultimo mes enviado (AAAA-MM): um reinicio no dia 1 nao repete o envio.
ALTER TABLE organizations ADD COLUMN monthly_report_sent_for VARCHAR(7);
