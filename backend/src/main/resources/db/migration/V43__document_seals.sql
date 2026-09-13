-- Fatia 36: selo de autenticidade dos documentos.
--
-- Cada PDF emitido (ordem, guia, historico) leva um codigo unico no rodape.
-- Quem receber o papel -- um fornecedor, um auditor, a policia -- pode
-- verificar no endereco publico que o documento foi emitido por esta
-- empresa, quando, e por quem; e, carregando o ficheiro, que nao foi
-- alterado (o resumo SHA-256 e guardado no momento da emissao).

CREATE TABLE document_seals (
    id              VARCHAR(36)   PRIMARY KEY,
    organization_id VARCHAR(36)   NOT NULL REFERENCES organizations(id),
    code            VARCHAR(20)   NOT NULL,
    kind            VARCHAR(30)   NOT NULL,
    reference_id    VARCHAR(36),
    reference       VARCHAR(80),
    issued_by       VARCHAR(36)   REFERENCES users(id),
    issued_at       TIMESTAMP     NOT NULL,
    sha256          VARCHAR(64)   NOT NULL,
    size_bytes      BIGINT        NOT NULL
);
CREATE UNIQUE INDEX uq_document_seals_code ON document_seals(code);
CREATE INDEX idx_document_seals_org ON document_seals(organization_id, issued_at);
