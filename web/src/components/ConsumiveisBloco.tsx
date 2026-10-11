import { Badge, Card, Group, Text } from '@mantine/core';
import { IconChevronRight, IconDroplet, IconFilter, IconShoppingCart, IconTir } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';

/**
 * Pneus, óleos e filtros no painel de entrada.
 *
 * <p>Está aqui — e não escondido num menu — porque é o controlo que o dono da
 * frota perde primeiro e o que lhe custa mais caro perder. Quatro números e um
 * clique: o suficiente para, de manhã, saber se há alguma coisa a travar hoje.
 *
 * <p>Usa a mesma chave de cache do ecrã completo, portanto não é um pedido a
 * mais: quem clica entra num ecrã já carregado.
 */
interface Resumo {
  pneusCriticos: number;
  pneusAviso: number;
  pneusSemMedicao: number;
  oleosVencidos: number;
  filtrosVencidos: number;
  pecasEmFalta: number;
  encomendasUrgentes: number;
}

export function ConsumiveisBloco() {
  const { data } = useQuery({
    queryKey: ['consumiveis', '30'],
    queryFn: () => api<{ resumo: Resumo }>('/consumables?dias=30'),
  });
  const r = data?.resumo;
  if (!r) {
    return null;
  }

  const nada =
    r.pneusCriticos === 0 &&
    r.oleosVencidos === 0 &&
    r.filtrosVencidos === 0 &&
    r.encomendasUrgentes === 0;

  const itens = [
    { icone: IconTir, valor: r.pneusCriticos, rotulo: 'pneus urgentes', cor: 'red' },
    { icone: IconDroplet, valor: r.oleosVencidos, rotulo: 'óleos vencidos', cor: 'red' },
    { icone: IconFilter, valor: r.filtrosVencidos, rotulo: 'filtros vencidos', cor: 'red' },
    {
      icone: IconShoppingCart,
      valor: r.encomendasUrgentes,
      rotulo: 'a encomendar hoje',
      cor: 'orange',
    },
  ];

  return (
    <Card
      p="md"
      component={Link}
      to="/consumiveis"
      style={{
        borderLeft: `3px solid var(--mantine-color-${nada ? 'green' : 'red'}-6)`,
        textDecoration: 'none',
        color: 'inherit',
      }}
    >
      <Group justify="space-between" wrap="nowrap" mb={nada ? 0 : 'xs'}>
        <Text fw={700} size="sm" style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Pneus, óleos e filtros
        </Text>
        <Group gap={4} wrap="nowrap">
          {r.pneusSemMedicao > 0 && (
            <Badge size="xs" color="yellow" variant="light">
              {r.pneusSemMedicao} pneu(s) sem medição
            </Badge>
          )}
          {r.pecasEmFalta > 0 && (
            <Badge size="xs" color="orange" variant="light">
              {r.pecasEmFalta} peça(s) a comprar
            </Badge>
          )}
          <IconChevronRight size={16} />
        </Group>
      </Group>

      {nada ? (
        <Text size="xs" c="dimmed">
          Nada vencido nos próximos 30 dias. Toque para ver o desgaste dos pneus e o que está a
          chegar ao intervalo.
        </Text>
      ) : (
        <Group gap="lg">
          {itens
            .filter((i) => i.valor > 0)
            .map((i) => (
              <Group key={i.rotulo} gap={6} wrap="nowrap">
                <i.icone size={18} color={`var(--mantine-color-${i.cor}-6)`} />
                <Text fw={700} style={{ fontSize: 22, lineHeight: 1 }}>
                  {i.valor}
                </Text>
                <Text size="xs" c="dimmed">
                  {i.rotulo}
                </Text>
              </Group>
            ))}
        </Group>
      )}
    </Card>
  );
}
