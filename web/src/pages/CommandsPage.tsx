import { Alert, Badge, Group, Stack, Table, Text } from '@mantine/core';
import { IconAlertTriangle, IconInfoCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { fmtDateTime, fmtNumber } from '../lib/format';
import type { CommandView } from './lock/LockPanel';

interface ProviderHealth {
  configured: boolean;
  reachable: boolean;
  name: string;
  version?: string | null;
  failureReason?: string | null;
}

/**
 * Histórico de comandos de bloqueio de toda a frota.
 *
 * <p>A auditoria encontrou este registo a existir no servidor sem nenhuma forma
 * de o consultar. Um registo de auditoria a que ninguém chega não serve de
 * auditoria a ninguém.
 */
export function CommandsPage() {
  const { data } = useQuery({
    queryKey: ['commands', 'fleet'],
    queryFn: () => api<{ content: CommandView[] }>('/commands?size=100'),
  });

  const { data: health } = useQuery({
    queryKey: ['traccar', 'status'],
    queryFn: () => api<ProviderHealth>('/telemetry/traccar/status'),
  });

  const rows = data?.content ?? [];

  return (
    <Stack gap="lg">
      <div>
        <Text c="dimmed" size="sm">
          Todos os pedidos de imobilização e desbloqueio, com quem pediu, porquê e o que
          aconteceu.
        </Text>
      </div>

      {health && !health.configured && (
        <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
          <b>Sem servidor de comandos.</b> {health.failureReason}
        </Alert>
      )}
      {health?.configured && !health.reachable && (
        <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
          <b>O servidor de comandos não responde.</b> {health.failureReason}
        </Alert>
      )}
      {health?.configured && health.reachable && (
        <Alert color="green" variant="light" icon={<IconInfoCircle size={18} />}>
          Ligado a {health.name}
          {health.version ? ` (versão ${health.version})` : ''}.
        </Alert>
      )}

      <Painel titulo="Imobilização de viaturas" semPadding>
        <Table.ScrollContainer minWidth={980}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Data</Table.Th>
                <Table.Th>Viatura</Table.Th>
                <Table.Th>Ação</Table.Th>
                <Table.Th>Motivo</Table.Th>
                <Table.Th>Pedido por</Table.Th>
                <Table.Th>Aprovado por</Table.Th>
                <Table.Th>Velocidade</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th>Confirmação</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rows.length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={9}>
                    <Text c="dimmed" ta="center" py="xl">
                      Nunca foi pedido nenhum bloqueio nesta empresa.
                    </Text>
                  </Table.Td>
                </Table.Tr>
              )}
              {rows.map((c) => (
                <Table.Tr key={c.id}>
                  <Table.Td>{fmtDateTime(c.requestedAt)}</Table.Td>
                  <Table.Td>
                    <Text fw={600} size="sm">
                      {c.assetTag}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={c.kind === 'ENGINE_STOP' ? 'red' : 'green'}>
                      {c.kind === 'ENGINE_STOP' ? 'Bloqueio' : 'Desbloqueio'}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{c.reasonCategoryLabel ?? '—'}</Text>
                    <Text size="xs" c="dimmed" lineClamp={2}>
                      {c.reason}
                    </Text>
                  </Table.Td>
                  <Table.Td>{c.requestedByName ?? '—'}</Table.Td>
                  <Table.Td>{c.approvedByName ?? '—'}</Table.Td>
                  <Table.Td>
                    {/* A velocidade no momento do pedido é o que permite a uma
                        auditoria julgar a decisão, não só vê-la. */}
                    {c.requestSpeedKph != null ? `${fmtNumber(c.requestSpeedKph)} km/h` : '—'}
                  </Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={colour(c.status)}>
                      {c.statusLabel}
                    </Badge>
                    {c.failureReason && (
                      <Text size="xs" c="dimmed" lineClamp={2}>
                        {c.failureReason}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    {c.confirmationLabel ? (
                      <Text
                        size="xs"
                        c={c.confirmationSource === 'MANUAL' ? 'orange' : 'green'}
                        fw={c.confirmationSource === 'MANUAL' ? 600 : 400}
                      >
                        {c.confirmationLabel}
                      </Text>
                    ) : (
                      <Text size="xs" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
            </Painel>

      <Group gap={6}>
        <Text size="xs" c="dimmed">
          Para pedir um bloqueio, abra a ficha da viatura em
        </Text>
        <Text component={Link} to="/ativos" size="xs" fw={600}>
          Ativos
        </Text>
        <Text size="xs" c="dimmed">
          e use o separador Bloqueio.
        </Text>
      </Group>
    </Stack>
  );
}

function colour(status: string) {
  switch (status) {
    case 'CONFIRMED':
      return 'green';
    case 'FAILED':
    case 'EXPIRED':
      return 'red';
    case 'CANCELLED':
    case 'SUPERSEDED':
      return 'gray';
    case 'SENT':
      return 'blue';
    case 'UNCONFIRMED':
      return 'orange';
    default:
      return 'yellow';
  }
}
