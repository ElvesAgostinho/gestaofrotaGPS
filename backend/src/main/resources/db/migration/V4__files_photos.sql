-- AutoCare — ficheiros e fotografias de ativos (Fatia 1)
--
-- Os bytes ficam num "storage provider" (disco local em dev, object storage em
-- produção). A BD guarda apenas os metadados e a chave de armazenamento.

CREATE TABLE stored_files (
    id                   VARCHAR(36) PRIMARY KEY,
    organization_id      VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    storage_key          VARCHAR(300) NOT NULL,
    original_name        VARCHAR(300),
    content_type         VARCHAR(120) NOT NULL,
    size_bytes           BIGINT      NOT NULL,
    width                INTEGER,
    height               INTEGER,
    uploaded_by_user_id  VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    created_at           TIMESTAMP   NOT NULL
);
CREATE INDEX ix_stored_files_org ON stored_files(organization_id);

CREATE TABLE asset_photos (
    id          VARCHAR(36) PRIMARY KEY,
    asset_id    VARCHAR(36) NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    file_id     VARCHAR(36) NOT NULL REFERENCES stored_files(id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    caption     VARCHAR(300),
    is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_asset_photos_asset ON asset_photos(asset_id);
