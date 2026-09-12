-- Fatia 8: documentos do ativo.
--
-- Manuais, apólices de seguro, livretes, certificados de inspeção. O que
-- distingue isto das fotografias é a VALIDADE: um seguro caducado imobiliza a
-- viatura e uma inspeção fora de prazo é uma multa à espera de acontecer.

CREATE TABLE asset_documents (
    id              VARCHAR(36)  PRIMARY KEY,
    organization_id VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36)  NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    kind            VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    title           VARCHAR(200) NOT NULL,
    -- Número da apólice, do certificado, da licença.
    reference       VARCHAR(120),
    issuer          VARCHAR(160),
    issued_at       TIMESTAMP,
    -- Sem data de validade (um manual, uma fatura) o documento não caduca.
    expires_at      TIMESTAMP,
    file_id         VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    notes           VARCHAR(2000),
    created_by      VARCHAR(36)  REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_asset_documents_asset ON asset_documents(asset_id, kind);
-- Consulta central da fatia: o que está prestes a caducar em toda a frota.
CREATE INDEX ix_asset_documents_expiry ON asset_documents(organization_id, expires_at);
