-- Fatia 5: gestão de equipa.
-- Convites para uma empresa e campos extra do membro (cargo, suspensão).

CREATE TABLE invitations (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email           VARCHAR(190) NOT NULL,
    invited_name    VARCHAR(160),
    job_title       VARCHAR(120),
    role            VARCHAR(20)  NOT NULL,
    -- Guardamos apenas o SHA-256 do token; o token em claro só existe no convite enviado.
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    invited_by      VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    expires_at      TIMESTAMP    NOT NULL,
    accepted_at     TIMESTAMP,
    accepted_by     VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_invitations_org ON invitations(organization_id);
CREATE INDEX ix_invitations_email ON invitations(email);

-- Cargo do membro dentro da empresa (ex.: "Chefe de oficina") e suspensão sem
-- apagar o histórico de ordens que lhe estão atribuídas.
ALTER TABLE memberships ADD COLUMN job_title      VARCHAR(120);
ALTER TABLE memberships ADD COLUMN suspended_at   TIMESTAMP;
ALTER TABLE memberships ADD COLUMN invited_by     VARCHAR(36);
