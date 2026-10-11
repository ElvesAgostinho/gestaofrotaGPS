-- A caixa de correio da empresa dentro do sistema.
--
-- Enviar já se fazia: os avisos saem por SMTP. Faltava RECEBER — e é o que
-- transforma o sistema no sítio onde o trabalho acontece em vez de um sítio por
-- onde o trabalho passa. A resposta do fornecedor à ordem de compra, a factura
-- que vem em anexo, a confirmação do prazo: tudo isso vive hoje numa caixa de
-- correio que ninguém liga ao equipamento nem à encomenda.
--
-- Ler é IMAP, e IMAP precisa dos seus próprios dados: o servidor de receção
-- raramente é o de envio (imap.x vs smtp.x) e o porto nunca é o mesmo. As
-- credenciais costumam ser as mesmas, por isso há um interruptor para as
-- reaproveitar em vez de obrigar a escrevê-las duas vezes.
ALTER TABLE integration_settings ADD COLUMN imap_host VARCHAR(190);
ALTER TABLE integration_settings ADD COLUMN imap_port INT;
ALTER TABLE integration_settings ADD COLUMN imap_security VARCHAR(20);
ALTER TABLE integration_settings ADD COLUMN imap_username VARCHAR(190);
ALTER TABLE integration_settings ADD COLUMN imap_password_enc VARCHAR(1000);
-- Quando ligado, usa o utilizador e a palavra-passe do SMTP.
ALTER TABLE integration_settings ADD COLUMN imap_use_smtp_login BOOLEAN DEFAULT TRUE NOT NULL;
-- O nome da pasta de enviados varia com o servidor: «Sent», «INBOX.Sent»,
-- «Enviados». Guarda-se o que o servidor disser, descoberto no teste.
ALTER TABLE integration_settings ADD COLUMN imap_sent_folder VARCHAR(190);
ALTER TABLE integration_settings ADD COLUMN imap_checked_at TIMESTAMP;
ALTER TABLE integration_settings ADD COLUMN imap_ok BOOLEAN;
ALTER TABLE integration_settings ADD COLUMN imap_last_error VARCHAR(500);
