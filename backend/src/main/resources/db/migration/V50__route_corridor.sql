-- Fatia 44: corredor da rota, para o alerta de desvio.
--
-- Largo de propósito: obras, desvios de transito e o erro do GPS nao sao
-- desvios. O que se quer apanhar e a viatura que foi a outro sitio.
ALTER TABLE routes ADD COLUMN corridor_meters INTEGER NOT NULL DEFAULT 500;
