-- Controlo de pneus, óleos e filtros: o que falta para prever em vez de reagir.
--
-- Três faltas, três colunas:
--
-- 1. A tarefa do plano dizia «Filtro de óleo do motor» por extenso, e o
--    armazém tinha um artigo com referência. Ninguém conseguia responder à
--    pergunta que interessa — «a troca é daqui a 800 km; tenho o filtro?» —
--    porque não havia ligação entre os dois. Agora há.
--
-- 2. A especificação vivia dentro do texto da tarefa. «15W-40 API CJ-4» é o
--    que distingue comprar o óleo certo de comprar óleo: passa a ser um campo
--    do artigo, que aparece na requisição e na ordem.
--
-- 3. O prazo de entrega é o que decide quando se encomenda. Em Angola, um
--    filtro que demora três semanas tem de ser pedido três semanas antes —
--    e isso não se adivinha a olhar para um mínimo de stock.
ALTER TABLE plan_task_parts ADD COLUMN part_id VARCHAR(36) REFERENCES parts (id);
ALTER TABLE parts ADD COLUMN specification VARCHAR(300);
ALTER TABLE parts ADD COLUMN lead_time_days INT;

-- A vida que se espera de um pneu, quando o fabricante a declara. Serve de
-- referência enquanto não houver medições de sulco suficientes para calcular
-- o desgaste real desta viatura, nesta estrada.
ALTER TABLE tyres ADD COLUMN expected_life_km NUMERIC(12, 2);

CREATE INDEX ix_plan_task_parts_part ON plan_task_parts (part_id);
