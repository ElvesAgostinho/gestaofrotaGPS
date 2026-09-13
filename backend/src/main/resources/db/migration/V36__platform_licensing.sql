-- Fatia 29: a plataforma e as licencas.
--
-- O sistema vende-se a empresas: quem cria as empresas e o administrador da
-- plataforma, e cada empresa tem uma licenca com validade. Uma empresa
-- suspensa ou com a licenca vencida continua com os dados intactos, mas os
-- utilizadores dela deixam de poder trabalhar ate a situacao ser resolvida.

ALTER TABLE organizations ADD COLUMN suspended_at TIMESTAMP;
ALTER TABLE organizations ADD COLUMN suspended_reason VARCHAR(300);
ALTER TABLE organizations ADD COLUMN license_until DATE;
ALTER TABLE organizations ADD COLUMN platform_notes VARCHAR(1000);
