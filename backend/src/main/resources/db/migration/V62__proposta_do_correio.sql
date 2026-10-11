-- A proposta que chegou por email fica guardada com o pedido.
--
-- Até aqui a resposta do fornecedor chegava ao correio e os preços copiavam-se
-- à mão para o pedido de cotação. O PDF da proposta ficava na caixa de entrada
-- — e daí a um mês, quando alguém pergunta «mas ele cotou mesmo esse preço?»,
-- a resposta está enterrada em quatrocentas mensagens.
--
-- Três colunas: o ficheiro da proposta, e de que mensagem veio. A segunda não
-- é decoração: é o que permite voltar ao original e ler o que ele escreveu à
-- volta do número.
ALTER TABLE rfq_suppliers ADD COLUMN file_id VARCHAR(36);
ALTER TABLE rfq_suppliers ADD COLUMN file_name VARCHAR(300);
ALTER TABLE rfq_suppliers ADD COLUMN source_folder VARCHAR(190);
ALTER TABLE rfq_suppliers ADD COLUMN source_uid BIGINT;
