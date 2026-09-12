-- Fatia 23: o sistema calcula a rota em vez de a pedir escrita.
--
-- Ate aqui a distancia e a duracao previstas de uma rota eram dois numeros
-- digitados por alguem. Ninguem os conferia. "Luanda -> Lobito, 40 km" entrava
-- na base de dados sem uma queixa, e a partir dai todos os desvios de consumo
-- calculados contra essa rota eram lixo -- com ar de rigor, que e pior.
--
-- Passa a haver um motor de rotas (OSRM) que responde com o caminho pelas
-- estradas reais. Fica em casa, no servidor da empresa: a rota de uma frota diz
-- onde estao os clientes e por onde andam as viaturas, e isso nao se manda para
-- fora a cada consulta.

-- ---- onde vive o motor, por empresa --------------------------------------
ALTER TABLE integration_settings ADD COLUMN routing_url VARCHAR(300);
ALTER TABLE integration_settings ADD COLUMN routing_checked_at TIMESTAMP;
ALTER TABLE integration_settings ADD COLUMN routing_ok BOOLEAN;
ALTER TABLE integration_settings ADD COLUMN routing_last_error VARCHAR(500);

-- ---- o que o motor devolveu, guardado na rota ----------------------------

-- De onde veio a distancia que la esta. Sem isto, um numero calculado e um
-- numero inventado ficam iguais na tabela, e quem le o relatorio nao sabe em
-- qual pode confiar.
--   MANUAL   - escrito por uma pessoa
--   ENGINE   - calculado pelo motor de rotas, pelas estradas
--   STRAIGHT - linha reta com fator de estrada; e uma aproximacao e diz-se
ALTER TABLE routes ADD COLUMN distance_source VARCHAR(20) DEFAULT 'MANUAL';

-- O tracado devolvido pelo motor, em GeoJSON, para o mapa poder desenha-lo.
ALTER TABLE routes ADD COLUMN path_geojson TEXT;

ALTER TABLE routes ADD COLUMN computed_at TIMESTAMP;
