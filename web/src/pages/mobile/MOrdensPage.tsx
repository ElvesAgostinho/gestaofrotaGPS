import { Badge, Card, Group, Loader, SegmentedControl, Stack, Text, Title } from '@mantine/core';
import { IconChevronRight } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import { fmtDate } from '../../lib/format';

interface Resumo {
  id: string;
  number: string;
  assetTag: string;
  assetName: string;
  title: string;
  status: string;
  statusLabel: string;
  priority: string;
  priorityLabel: string;
  scheduledFor?: string | null;
  taskCount: number;
  tasksDone: number;
}

const COR_PRIORIDADE: Record<string, string> = { LOW: 'gray', NORMAL: 'blue', HIGH: 'orange', URGENT: 'red' };
const FECHADAS = new Set(['DONE', 'VERIFIED', 'CLOSED', 'CANCELLED', 'REJECTED']);

/** As ordens atribuídas a mim: as por fazer primeiro, as urgentes no topo. */
export function MOrdensPage() {
  const [filtro, setFiltro] = useState<'abertas' | 'todas'>('abertas');
  const { data, isLoading } = useQuery({
    queryKey: ['mobile', 'ordens'],
    queryFn: () => api<{ content: Resumo[] }>('/work-orders?assignedTo=me&size=100'),
  });
  const ordem = ['URGENT', 'HIGH', 'NORMAL', 'LOW'];
  const lista = (data?.content ?? [])
    .filter((o) => filtro === 'todas' || !FECHADAS.has(o.status))
    .sort((a, b) => {
      const fa = FECHADAS.has(a.status) ? 1 : 0;
      const fb = FECHADAS.has(b.status) ? 1 : 0;
      if (fa !== fb) return fa - fb;
      return ordem.indexOf(a.priority) - ordem.indexOf(b.priority);
    });

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Title order={3}>As minhas ordens</Title>
        <SegmentedControl
          size="xs"
          value={filtro}
          onChange={(v) => setFiltro(v as 'abertas' | 'todas')}
          data={[
            { value: 'abertas', label: 'Por fazer' },
            { value: 'todas', label: 'Todas' },
          ]}
        />
      </Group>
      {isLoading && <Loader />}
      {!isLoading && lista.length === 0 && (
        <Text c="dimmed">Nenhuma ordem {filtro === 'abertas' ? 'por fazer' : ''} atribuída a si.</Text>
      )}
      <Stack gap={8}>
        {lista.map((o) => (
          <Card key={o.id} padding="sm" radius="md" withBorder component={Link} to={`/m/ordens/${o.id}`}>
            <Group justify="space-between" wrap="nowrap" align="flex-start">
              <div style={{ minWidth: 0 }}>
                <Group gap={6} mb={2}>
                  <Text size="xs" c="dimmed">
                    {o.number}
                  </Text>
                  <Badge size="xs" color={COR_PRIORIDADE[o.priority] ?? 'gray'} variant="light">
                    {o.priorityLabel}
                  </Badge>
                  <Badge size="xs" variant="outline" color={FECHADAS.has(o.status) ? 'green' : 'gray'}>
                    {o.statusLabel}
                  </Badge>
                </Group>
                <Text fw={700} lh={1.2}>
                  {o.title}
                </Text>
                <Text size="sm" c="dimmed">
                  {o.assetTag} · {o.assetName}
                  {o.scheduledFor ? ` · ${fmtDate(o.scheduledFor)}` : ''}
                  {o.taskCount ? ` · ${o.tasksDone}/${o.taskCount} tarefas` : ''}
                </Text>
              </div>
              <IconChevronRight size={18} style={{ color: 'var(--mantine-color-dimmed)', flexShrink: 0 }} />
            </Group>
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}
