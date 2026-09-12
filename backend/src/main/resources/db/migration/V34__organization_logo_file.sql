-- Fatia 26b: o logotipo passa a ser um registo de ficheiro, como as fotografias.
--
-- O URL assinado conhece o id do registo em stored_files, nao a chave no disco.
-- Guardar so a chave dava um URL que respondia 404.

ALTER TABLE organizations ADD COLUMN logo_file_id VARCHAR(36);
