-- Fatia 22: quem elaborou e quem aprovou o plano.
--
-- O documento de manutencao preventiva de um fabricante termina sempre com
-- "Elaborado por / Aprovado por / Data". Nao e formalidade: um plano que
-- ninguem aprovou e uma sugestao, e numa auditoria de seguranca a pergunta e
-- sempre a mesma -- quem decidiu que esta maquina se revê às 250 horas e nao
-- às 500?
--
-- Fica no plano e nao na ordem: a ordem ja tem o seu proprio percurso de
-- aprovacao (V25). Isto aprova o plano, uma vez, e vale para todas as ordens
-- que ele gerar.

ALTER TABLE maintenance_plans ADD COLUMN prepared_by_label VARCHAR(150);
ALTER TABLE maintenance_plans ADD COLUMN prepared_at TIMESTAMP;
ALTER TABLE maintenance_plans ADD COLUMN approved_by_label VARCHAR(150);
ALTER TABLE maintenance_plans ADD COLUMN approved_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE maintenance_plans ADD COLUMN approved_at TIMESTAMP;
-- Referencia do documento de origem: manual do fabricante, norma, versao.
ALTER TABLE maintenance_plans ADD COLUMN source_reference VARCHAR(300);
-- Objetivo do plano, separado da descricao: e o que a auditoria le primeiro.
ALTER TABLE maintenance_plans ADD COLUMN objective VARCHAR(2000);
