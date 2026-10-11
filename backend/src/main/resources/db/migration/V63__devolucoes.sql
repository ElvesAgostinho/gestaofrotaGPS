-- O que veio mal deixa de ficar pelo caminho.
--
-- A quantidade recusada numa entrega já ficava registada e já contava na
-- qualidade do fornecedor. Mas não gerava nada a seguir: não havia lista de «a
-- devolver», ninguém sabia quanto é que o fornecedor nos devia, e a caixa com
-- os filtros molhados ficava a um canto do armazém até alguém tropeçar nela.
--
-- Uma devolução acaba sempre de uma de quatro maneiras, e todas têm de caber:
-- devolveu-se, ele substituiu, ele creditou, ou ficámos com aquilo à mesma —
-- às vezes com desconto, às vezes porque não vale a pena o frete de volta.
ALTER TABLE purchase_receipt_lines ADD COLUMN resolution VARCHAR(20) DEFAULT 'PENDING' NOT NULL;
ALTER TABLE purchase_receipt_lines ADD COLUMN resolved_at TIMESTAMP;
ALTER TABLE purchase_receipt_lines ADD COLUMN resolved_by VARCHAR(36) REFERENCES users (id);
ALTER TABLE purchase_receipt_lines ADD COLUMN resolution_note VARCHAR(500);
-- A nota de crédito que ele emitiu, quando a emite.
ALTER TABLE purchase_receipt_lines ADD COLUMN credit_note VARCHAR(80);
ALTER TABLE purchase_receipt_lines ADD COLUMN credit_amount NUMERIC(14, 2);

CREATE INDEX ix_purchase_receipt_lines_devolucao
    ON purchase_receipt_lines (resolution, rejected_quantity);
