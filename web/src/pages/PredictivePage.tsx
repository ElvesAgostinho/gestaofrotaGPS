import { Badge, SegmentedControl, Stack, Table, Text } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { PrevisaoAvarias } from '../components/PrevisaoAvarias';
import { fmtDate } from '../lib/format';

interface Program {
  id: string;
  assetId: string;
  assetTag: string;
  techniqueLabel: string;
  frequencyLabel: string;
  components?: string | null;
  lastDoneAt?: string | null;
  nextDueAt?: string | null;
  status: string;
  lastResult?: string | null;
}

const RESULT: Record<string, { label: string; color: string }> = {
  NORMAL: { label: 'Normal', color: 'green' },
  ATTENTION: { label: 'Atenção', color: 'orange' },
  CRITICAL: { label: 'Crítico', color: 'red' },
};

export function PredictivePage() {
  const [status, setStatus] = useState('');

  const { data } = useQuery({
    queryKey: ['predictive', 'fleet', status],
    queryFn: () => api<Program[]>(status ? `/predictive?status=${status}` : '/predictive'),
  });

  return (
    <Stack gap="lg">
      <PrevisaoAvarias />

      <Painel
        titulo="Manutenção preditiva"
        semPadding
        acoes={
          <SegmentedControl
            size="xs"
            value={status}
            onChange={setStatus}
            data={[
              { value: '', label: 'Todos' },
              { value: 'OVERDUE', label: 'Vencidos' },
              { value: 'DUE_SOON', label: 'A chegar' },
              { value: 'OK', label: 'Em dia' },
            ]}
          />
        }
      >
        <Table.ScrollContainer minWidth={860}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Ativo</Table.Th>
                <Table.Th>Técnica</Table.Th>
                <Table.Th>Periodicidade</Table.Th>
                <Table.Th>Componentes</Table.Th>
                <Table.Th>Última</Table.Th>
                <Table.Th>Próxima</Table.Th>
                <Table.Th>Resultado</Table.Th>
                <Table.Th>Estado</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(data ?? []).length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={8}>
                    <Text c="dimmed" ta="center" py="xl">
                      Nenhum programa neste filtro. Aplique o conjunto de referência na ficha de um
                      ativo.
                    </Text>
                  </Table.Td>
                </Table.Tr>
              )}
              {(data ?? []).map((p) => (
                <Table.Tr key={p.id}>
                  <Table.Td>
                    <Text component={Link} to={`/ativos/${p.assetId}`} fw={600} size="sm">
                      {p.assetTag}
                    </Text>
                  </Table.Td>
                  <Table.Td>{p.techniqueLabel}</Table.Td>
                  <Table.Td>{p.frequencyLabel}</Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed" lineClamp={1}>
                      {p.components ?? '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>{fmtDate(p.lastDoneAt)}</Table.Td>
                  <Table.Td>{fmtDate(p.nextDueAt)}</Table.Td>
                  <Table.Td>
                    {p.lastResult ? (
                      <Badge variant="light" color={RESULT[p.lastResult]?.color}>
                        {RESULT[p.lastResult]?.label}
                      </Badge>
                    ) : (
                      <Text size="sm" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Badge
                      variant="light"
                      color={
                        p.status === 'OVERDUE'
                          ? 'red'
                          : p.status === 'DUE_SOON'
                            ? 'yellow'
                            : 'green'
                      }
                    >
                      {p.status === 'OVERDUE'
                        ? 'Vencida'
                        : p.status === 'DUE_SOON'
                          ? 'A chegar'
                          : 'Em dia'}
                    </Badge>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
            </Painel>
    </Stack>
  );
}
