-- Registo de auditoria por empresa.
--
-- A tabela audit_logs nasceu antes do multi-tenant e nunca soube a que empresa
-- pertencia cada linha. Enquanto assim foi, o registo era impossivel de expor:
-- mostra-lo a alguem significava mostrar a atividade de todas as empresas.
--
-- A coluna fica NULA para o que nao se conseguir atribuir com certeza, e a
-- consulta filtra SEMPRE por empresa. Uma linha sem empresa nao aparece a
-- ninguem -- e a falha segura: perde-se informacao, nunca se vaza.

ALTER TABLE audit_logs ADD COLUMN organization_id VARCHAR(36);

CREATE INDEX ix_audit_logs_org ON audit_logs(organization_id, created_at);
CREATE INDEX ix_audit_logs_org_action ON audit_logs(organization_id, action);

-- Preenchimento retroativo apenas onde NAO ha duvida: utilizadores que
-- pertencem a exatamente uma empresa. Quem pertence a mais do que uma fica com
-- a coluna nula, porque atribuir a linha a uma delas seria adivinhar -- e
-- adivinhar aqui e exatamente o que se quer evitar.
UPDATE audit_logs
   SET organization_id = (
       SELECT MIN(m.organization_id)
         FROM memberships m
        WHERE m.user_id = audit_logs.user_id)
 WHERE organization_id IS NULL
   AND user_id IS NOT NULL
   AND (SELECT COUNT(DISTINCT m.organization_id)
          FROM memberships m
         WHERE m.user_id = audit_logs.user_id) = 1;
