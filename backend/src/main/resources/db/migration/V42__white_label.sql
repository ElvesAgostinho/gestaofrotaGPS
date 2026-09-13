-- Fatia 35: marca branca.
--
-- Uma empresa (ou um revendedor) pode entrar pelo seu proprio dominio
-- (frota.empresa.ao) e ver o seu nome, o seu logotipo e a sua cor no ecra
-- de entrada. O dominio e a chave: quem abre o sistema por ele ve a marca
-- dessa empresa em vez da marca do IMBONDEIRO OS.

ALTER TABLE organizations ADD COLUMN custom_domain VARCHAR(190);
ALTER TABLE organizations ADD COLUMN brand_name VARCHAR(80);
ALTER TABLE organizations ADD COLUMN brand_color VARCHAR(9);
CREATE UNIQUE INDEX uq_organizations_custom_domain ON organizations(custom_domain);
