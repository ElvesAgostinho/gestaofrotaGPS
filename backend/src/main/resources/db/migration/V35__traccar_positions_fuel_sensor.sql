-- Fatia 27: as posicoes do Traccar entram no sistema, e o nivel de
-- combustivel do sensor vem com elas.
--
-- Ate aqui o sistema so MANDAVA comandos ao Traccar. As posicoes esperavam que
-- o aparelho publicasse JSON diretamente -- e um rastreador real (GT06,
-- Teltonika, Queclink) fala o protocolo do fabricante com o Traccar, nao JSON
-- connosco. Em producao o mapa, o odometro e o combustivel ficariam vazios.
--
-- Duas portas de entrada, ambas a partir do Traccar:
--   * sondagem: de 20 em 20 s pede-se ao Traccar a ultima posicao de cada
--     aparelho (funciona com qualquer Traccar, sem mexer na configuracao dele);
--   * encaminhamento (forward): o Traccar envia cada posicao ao nosso endpoint
--     assim que chega -- tempo real -- se o administrador o configurar.

-- ---- posicoes ----------------------------------------------------------
-- Id da posicao no fornecedor: a mesma posicao nao entra duas vezes quando a
-- sondagem e o encaminhamento estao os dois ligados.
ALTER TABLE gps_positions ADD COLUMN provider_position_id VARCHAR(60);
-- Nivel do deposito lido pelo sensor. Litros quando o aparelho os da; senao a
-- percentagem, convertida em litros pela capacidade do deposito do ativo.
ALTER TABLE gps_positions ADD COLUMN fuel_level_liters DECIMAL(10,2);
ALTER TABLE gps_positions ADD COLUMN fuel_level_percent DECIMAL(5,2);
-- Distancia total acumulada pelo aparelho, em km (o «totalDistance» do Traccar).
ALTER TABLE gps_positions ADD COLUMN total_distance_km DECIMAL(12,2);

CREATE INDEX idx_gps_positions_provider ON gps_positions(device_id, provider_position_id);

-- ---- o ultimo nivel conhecido, no ativo, para o mapa e a ficha ---------
ALTER TABLE assets ADD COLUMN fuel_level_liters DECIMAL(10,2);
ALTER TABLE assets ADD COLUMN fuel_level_at TIMESTAMP;

-- ---- estado da sondagem, por empresa --------------------------------------
ALTER TABLE integration_settings ADD COLUMN traccar_poll_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE integration_settings ADD COLUMN traccar_last_poll_at TIMESTAMP;
ALTER TABLE integration_settings ADD COLUMN traccar_last_position_at TIMESTAMP;
ALTER TABLE integration_settings ADD COLUMN traccar_poll_error VARCHAR(500);
-- Segredo do encaminhamento (so o SHA-256): o Traccar manda-o num cabecalho.
ALTER TABLE integration_settings ADD COLUMN traccar_forward_secret_hash VARCHAR(64);

-- ---- lancamentos vindos do sensor ---------------------------------------
-- fuel_records.source ja aceita SENSOR; fica registada a posicao que o detetou.
ALTER TABLE fuel_records ADD COLUMN sensor_position_id VARCHAR(36);
ALTER TABLE fuel_records ADD COLUMN sensor_level_before DECIMAL(10,2);
ALTER TABLE fuel_records ADD COLUMN sensor_level_after DECIMAL(10,2);

-- ---- a unidade do sensor de combustivel, por aparelho ---------------------
-- O «fuel» do Traccar e litros ou percentagem conforme o aparelho. Adivinhar
-- pelo valor nao serve: 100 e um valor valido nas duas. Diz-se no aparelho.
--   LITERS  - o valor e litros (omissao)
--   PERCENT - o valor e % do deposito; converte-se pela capacidade do ativo
ALTER TABLE gps_devices ADD COLUMN fuel_unit VARCHAR(10) NOT NULL DEFAULT 'LITERS';
