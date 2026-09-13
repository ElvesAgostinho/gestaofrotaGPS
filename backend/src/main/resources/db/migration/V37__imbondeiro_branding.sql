-- Fatia 30: o nome do sistema e IMBONDEIRO OS.
--
-- A configuracao publica (nome, lema, contacto de apoio) ainda trazia a marca
-- antiga da fatia 2. So se mexe nos valores que continuam iguais aos de
-- origem: o que o administrador ja tiver mudado fica como esta.

UPDATE app_config SET config_value = 'IMBONDEIRO OS'
 WHERE config_key = 'app.name' AND config_value = 'AutoCare';
UPDATE app_config SET config_value = 'Gestão de frota, manutenção e rastreamento'
 WHERE config_key = 'app.tagline' AND config_value = 'A inteligência da sua viatura';
DELETE FROM app_config
 WHERE config_key = 'app.supportEmail' AND config_value = 'apoio@autocare.ao';
