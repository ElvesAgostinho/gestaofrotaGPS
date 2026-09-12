-- Fatia 26: o timbre da empresa.
--
-- Os impressos levavam a marca do software no cabecalho. Um documento que o
-- cliente entrega a um fornecedor, a um motorista ou a um auditor leva o nome
-- e o NIF da empresa dele, com o logotipo dele.

ALTER TABLE organizations ADD COLUMN tax_id VARCHAR(40);
ALTER TABLE organizations ADD COLUMN address VARCHAR(300);
ALTER TABLE organizations ADD COLUMN city VARCHAR(120);
ALTER TABLE organizations ADD COLUMN phone VARCHAR(40);
ALTER TABLE organizations ADD COLUMN email VARCHAR(190);
-- O logotipo fica no armazenamento de ficheiros; aqui so a chave.
ALTER TABLE organizations ADD COLUMN logo_key VARCHAR(300);
ALTER TABLE organizations ADD COLUMN logo_content_type VARCHAR(80);
