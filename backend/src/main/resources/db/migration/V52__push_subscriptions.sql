-- Notificações push: o telemóvel do motorista e do gestor avisam mesmo com a
-- aplicação fechada.
--
-- Cada aparelho guarda aqui a sua «morada» (endpoint) e as duas chaves que o
-- browser gerou. As chaves VAPID da instalação ficam na tabela de baixo: são
-- geradas à primeira utilização, para isto funcionar sem ninguém ter de
-- configurar nada.
CREATE TABLE push_subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id VARCHAR(36) REFERENCES organizations (id) ON DELETE CASCADE,
    endpoint VARCHAR(600) NOT NULL,
    p256dh VARCHAR(200) NOT NULL,
    auth VARCHAR(100) NOT NULL,
    user_agent VARCHAR(300),
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_push_subscriptions_endpoint ON push_subscriptions (endpoint);
CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);

CREATE TABLE push_keys (
    id VARCHAR(36) PRIMARY KEY,
    public_key VARCHAR(200) NOT NULL,
    private_key VARCHAR(200) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
