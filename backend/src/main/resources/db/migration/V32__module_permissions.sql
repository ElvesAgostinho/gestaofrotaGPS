-- Fatia 25: permissoes por modulo, por cima do papel.
--
-- Os quatro papeis sao um ponto de partida. O contabilista ve custos mas nao
-- abre ordens; o chefe de oficina fecha ordens mas nao ve o que custam; o
-- motorista lanca combustivel e mais nada. Cada membro pode receber ou perder
-- permissoes uma a uma.
--
-- Texto separado por virgulas, e nao tabela de juncao: sao meia duzia de
-- codigos por pessoa, lidos em todos os pedidos. NULL significa «so o papel».

ALTER TABLE memberships ADD COLUMN permissions_granted VARCHAR(1000);
ALTER TABLE memberships ADD COLUMN permissions_denied VARCHAR(1000);
