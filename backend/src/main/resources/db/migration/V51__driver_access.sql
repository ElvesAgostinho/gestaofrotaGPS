-- Acesso de motorista criado pelo gestor.
--
-- O motorista não se regista: o gestor cria-lhe a conta e entrega-lhe um
-- identificador curto (MOT-0412) e uma palavra-passe. Por isso o utilizador
-- ganha duas coisas: o identificador com que entra, e a marca de que tem de
-- trocar a palavra-passe da primeira vez — a que o gestor viu não pode
-- continuar a ser a dele.
ALTER TABLE users ADD COLUMN login_id VARCHAR(20);
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_users_login_id ON users (login_id);
