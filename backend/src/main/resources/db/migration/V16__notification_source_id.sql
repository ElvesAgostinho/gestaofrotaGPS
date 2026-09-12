-- A origem de uma notificação nem sempre é um UUID simples.
--
-- Os avisos de validade de documentos precisam de distinguir o marco a que
-- correspondem ("este documento, aviso dos 30 dias") para poderem avisar mais do
-- que uma vez sem repetir o mesmo aviso — e "<uuid>:30" não cabe em 36
-- caracteres. `source_id` é uma chave lógica, não uma chave estrangeira.

ALTER TABLE notifications ALTER COLUMN source_id SET DATA TYPE VARCHAR(80);
