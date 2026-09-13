-- Fatia 37: avisos pelo telemovel (WhatsApp / SMS).
--
-- O email quase nao se le em Angola; o WhatsApp le-se no minuto. Cada aviso
-- guarda se foi (ou nao) entregue ao telemovel do utilizador, e a
-- preferencia «sms» passa a valer «telemovel»: WhatsApp se a plataforma o
-- tiver, SMS se tiver uma gateway, nada se nao tiver nenhum.

ALTER TABLE notifications ADD COLUMN phone_state VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED';
ALTER TABLE notifications ADD COLUMN phone_at TIMESTAMP;
-- O que se quer e ser avisado no telemovel do que e grave: por omissao ligado.
UPDATE notification_preferences SET sms = TRUE;
