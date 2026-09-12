-- Fatia 20: credenciais de integracao por empresa.
--
-- Ate aqui o Traccar e o servidor de email so se configuravam por variaveis de
-- ambiente. Isso serve um servidor proprio; nao serve um produto vendido a
-- varias empresas, cada uma com o seu Traccar e o seu email. E obrigava a
-- reiniciar a aplicacao para mudar uma palavra-passe.
--
-- As palavras-passe ficam cifradas na coluna. Guardar segredos em claro numa
-- base de dados de cliente e o tipo de decisao que so se descobre quando ja e
-- tarde.

CREATE TABLE integration_settings (
    id                     VARCHAR(36)  PRIMARY KEY,
    organization_id        VARCHAR(36)  NOT NULL UNIQUE
                                        REFERENCES organizations(id) ON DELETE CASCADE,

    -- ---- Traccar --------------------------------------------------------
    traccar_url            VARCHAR(300),
    traccar_user           VARCHAR(190),
    -- Cifradas (AES-GCM). Nunca saem da API: a resposta traz apenas se existem.
    traccar_password_enc   VARCHAR(1000),
    traccar_token_enc      VARCHAR(1000),
    traccar_checked_at     TIMESTAMP,
    traccar_ok             BOOLEAN,
    traccar_last_error     VARCHAR(500),

    -- ---- Servidor de email (SMTP) ---------------------------------------
    smtp_host              VARCHAR(190),
    smtp_port              INTEGER,
    smtp_username          VARCHAR(190),
    smtp_password_enc      VARCHAR(1000),
    smtp_from              VARCHAR(190),
    smtp_from_name         VARCHAR(120),
    -- NONE | STARTTLS | SSL
    smtp_security          VARCHAR(20)  DEFAULT 'STARTTLS',
    smtp_checked_at        TIMESTAMP,
    smtp_ok                BOOLEAN,
    smtp_last_error        VARCHAR(500),

    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL
);

CREATE INDEX idx_integration_org ON integration_settings(organization_id);
