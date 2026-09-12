-- Fatia 21: guia de transporte.
--
-- O documento que acompanha a carga. Em Angola e o que a autoridade pede na
-- estrada e o que o cliente assina na entrega -- e, para a frota, e a peca que
-- faltava para ligar uma viagem a uma carga, a um cliente e a um motorista.
--
-- Reaproveita o que ja existe: viaturas, motoristas, filiais e locais (V21),
-- viagens (V22). O que entra aqui e so o documento e as suas linhas.

CREATE TABLE transport_notes (
    id                  VARCHAR(36)  PRIMARY KEY,
    organization_id     VARCHAR(36)  NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,

    -- Numero sequencial por ano, como uma factura: GT-2026-000001.
    number              VARCHAR(30)  NOT NULL,
    note_year           INTEGER      NOT NULL,

    -- DRAFT | ISSUED | IN_TRANSIT | DELIVERED | CANCELLED
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    -- ---- Quem transporta -------------------------------------------------
    asset_id            VARCHAR(36)  REFERENCES assets(id),
    trailer_plate       VARCHAR(30),
    driver_id           VARCHAR(36)  REFERENCES drivers(id),
    -- Nome livre para quem ainda nao esta registado como motorista.
    driver_label        VARCHAR(150),
    driver_license      VARCHAR(60),

    -- ---- De onde e para onde --------------------------------------------
    origin_branch_id    VARCHAR(36)  REFERENCES locations(id),
    origin_label        VARCHAR(300) NOT NULL,
    origin_address      VARCHAR(400),
    destination_label   VARCHAR(300) NOT NULL,
    destination_address VARCHAR(400),
    route_id            VARCHAR(36)  REFERENCES routes(id),

    -- ---- Cliente ---------------------------------------------------------
    customer_name       VARCHAR(200),
    -- Numero de Identificacao Fiscal do cliente.
    customer_tax_id     VARCHAR(40),
    customer_contact    VARCHAR(120),

    -- ---- Tempos e medidores ---------------------------------------------
    issued_at           TIMESTAMP,
    departed_at         TIMESTAMP,
    delivered_at        TIMESTAMP,
    departure_meter     DECIMAL(18,2),
    arrival_meter       DECIMAL(18,2),
    -- Calculada na entrega, a partir dos medidores.
    distance_km         DECIMAL(12,2),

    -- ---- Carga -----------------------------------------------------------
    total_weight_kg     DECIMAL(14,3),
    total_volume_m3     DECIMAL(14,3),
    total_packages      INTEGER,
    cargo_description   VARCHAR(1000),
    -- Mercadoria perigosa: classe ONU, se aplicavel.
    hazard_class        VARCHAR(40),

    -- ---- Entrega ---------------------------------------------------------
    received_by_name    VARCHAR(150),
    received_by_document VARCHAR(60),
    -- Falso quando o cliente recebe com reservas; a nota diz quais.
    delivery_accepted   BOOLEAN,
    delivery_notes      VARCHAR(1000),

    cancellation_reason VARCHAR(500),
    notes               VARCHAR(2000),

    created_by          VARCHAR(36)  REFERENCES users(id),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,

    CONSTRAINT uk_transport_note_number UNIQUE (organization_id, number)
);

CREATE INDEX idx_transport_note_org ON transport_notes(organization_id, issued_at DESC);
CREATE INDEX idx_transport_note_asset ON transport_notes(asset_id);
CREATE INDEX idx_transport_note_status ON transport_notes(organization_id, status);

-- Linhas da carga. Uma guia com um so campo de texto para a mercadoria nao
-- serve para conferir nada na entrega.
CREATE TABLE transport_note_items (
    id                VARCHAR(36)  PRIMARY KEY,
    transport_note_id VARCHAR(36)  NOT NULL
                                   REFERENCES transport_notes(id) ON DELETE CASCADE,
    description       VARCHAR(300) NOT NULL,
    reference         VARCHAR(60),
    quantity          DECIMAL(14,3),
    unit              VARCHAR(20)  DEFAULT 'un',
    weight_kg         DECIMAL(14,3),
    volume_m3         DECIMAL(14,3),
    packages          INTEGER,
    notes             VARCHAR(500),
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_transport_item_note ON transport_note_items(transport_note_id, sort_order);
