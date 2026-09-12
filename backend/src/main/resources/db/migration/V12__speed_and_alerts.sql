-- Fatia 4c: limites de velocidade e alertas de telemetria.

-- Limite por ativo (um camião-cisterna não anda ao mesmo ritmo de uma carrinha)...
ALTER TABLE assets ADD COLUMN speed_limit_kph NUMERIC(6,2);
-- ...por zona (dentro da obra ou do parque anda-se devagar)...
ALTER TABLE geofences ADD COLUMN speed_limit_kph NUMERIC(6,2);
-- ...e um valor por omissão da empresa, para os ativos sem limite próprio.
ALTER TABLE organizations ADD COLUMN default_speed_limit_kph NUMERIC(6,2);

-- Alertas de telemetria. Um alerta é um EPISÓDIO, não uma posição: enquanto o
-- excesso dura, o mesmo registo é atualizado (guardando o pico) em vez de
-- nascer um alerta por cada posição recebida.
CREATE TABLE telemetry_alerts (
    id              VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    asset_id        VARCHAR(36) REFERENCES assets(id) ON DELETE CASCADE,
    device_id       VARCHAR(36) REFERENCES gps_devices(id) ON DELETE SET NULL,
    geofence_id     VARCHAR(36) REFERENCES geofences(id) ON DELETE SET NULL,
    kind            VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMP    NOT NULL,
    ended_at        TIMESTAMP,
    limit_value     NUMERIC(10,2),
    peak_value      NUMERIC(10,2),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    message         VARCHAR(400),
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
CREATE INDEX ix_telemetry_alerts_org_time ON telemetry_alerts(organization_id, started_at);
CREATE INDEX ix_telemetry_alerts_asset ON telemetry_alerts(asset_id, kind, ended_at);
CREATE INDEX ix_telemetry_alerts_open ON telemetry_alerts(organization_id, ended_at);
