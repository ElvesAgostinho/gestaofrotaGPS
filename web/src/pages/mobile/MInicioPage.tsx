import { Badge, Card, Group, Loader, SimpleGrid, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { IconAlertTriangle, IconChecklist, IconChevronRight, IconClipboardList, IconGasStation } from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useHome } from './comum';

/**
 * O início: quatro botões grandes e a lista das minhas viaturas. Nada de
 * indicadores nem gráficos — quem está aqui quer fazer, não analisar.
 */
export function MInicioPage() {
  const { data, isLoading } = useHome();
  const { has } = useAuth();
  const mecanico = has('WORKORDERS_MANAGE');
  const minhas = (data?.assets ?? []).filter((a) => a.mine);

  const botoes = [
    { to: '/m/avaria', label: 'Comunicar avaria', desc: 'Algo não está bem na viatura', icon: IconAlertTriangle, cor: 'red' },
    { to: '/m/inspecao', label: 'Inspeção diária', desc: 'Antes de sair: pneus, luzes, níveis', icon: IconChecklist, cor: 'gold' },
    ...(mecanico
      ? [{ to: '/m/ordens', label: 'As minhas ordens', desc: data ? `${data.myOpenOrders} por fazer` : '', icon: IconClipboardList, cor: 'blue' }]
      : []),
    { to: '/m/abastecer', label: 'Abastecer', desc: 'Registar litros e contador', icon: IconGasStation, cor: 'green' },
  ];

  return (
    <Stack gap="md">
      <div>
        <Title order={2} style={{ fontFamily: 'Barlow Condensed, sans-serif' }}>
          Olá, {data?.userName?.split(' ')[0] ?? ''}
        </Title>
        <Text c="dimmed" size="sm">
          O que vai fazer?
        </Text>
      </div>
      {isLoading && <Loader />}
      <SimpleGrid cols={2} spacing="sm">
        {botoes.map((b) => (
          <UnstyledButton key={b.to} component={Link} to={b.to}>
            <Card padding="md" radius="md" withBorder style={{ height: '100%' }}>
              <Stack gap={6}>
                <b.icon size={32} style={{ color: `var(--mantine-color-${b.cor}-6)` }} />
                <Text fw={700} size="md" lh={1.2}>
                  {b.label}
                </Text>
                {b.desc && (
                  <Text size="xs" c="dimmed">
                    {b.desc}
                  </Text>
                )}
              </Stack>
            </Card>
          </UnstyledButton>
        ))}
      </SimpleGrid>

      {minhas.length > 0 && (
        <div>
          <Text fw={600} size="sm" mb={6}>
            As minhas viaturas
          </Text>
          <Stack gap={6}>
            {minhas.map((a) => (
              <Card key={a.id} padding="sm" radius="md" withBorder component={Link} to={`/m/inspecao?ativo=${a.id}`}>
                <Group justify="space-between" wrap="nowrap">
                  <div>
                    <Text fw={700}>{a.tag}</Text>
                    <Text size="xs" c="dimmed">
                      {a.plate ? a.plate + ' · ' : ''}
                      {a.name}
                    </Text>
                  </div>
                  <Group gap={6} wrap="nowrap">
                    {a.status !== 'OPERATIONAL' && (
                      <Badge size="xs" color="orange" variant="light">
                        {a.status === 'MAINTENANCE' ? 'em manutenção' : a.status === 'DOWN' ? 'parada' : a.status.toLowerCase()}
                      </Badge>
                    )}
                    <IconChevronRight size={18} style={{ color: 'var(--mantine-color-dimmed)' }} />
                  </Group>
                </Group>
              </Card>
            ))}
          </Stack>
        </div>
      )}
      {data && data.assets.length === 0 && (
        <Text c="dimmed" size="sm">
          A sua empresa ainda não tem viaturas registadas.
        </Text>
      )}
    </Stack>
  );
}
