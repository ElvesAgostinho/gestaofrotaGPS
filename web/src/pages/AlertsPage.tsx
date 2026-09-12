import { Badge, Button, SegmentedControl, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { fmtDateTime, fmtNumber } from '../lib/format';

interface Alert {
  id: string;
  kind: string;
  assetId?: string | null;
  assetTag?: string | null;
  geofenceName?: string | null;
  startedAt: string;
  endedAt?: string | null;
  limitValue?: number | null;
  peakValue?: number | null;
  message?: string | null;
  open: boolean;
  acknowledged: boolean;
}

const KIND: Record<string, { label: string; color: string }> = {
  SPEEDING: { label: 'Excesso de velocidade', color: 'orange' },
  COMMS_LOST: { label: 'Aparelho sem comunicar', color: 'red' },
};

export function AlertsPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState('todos');

  const { data } = useQuery({
    queryKey: ['telemetry', 'alerts', filter],
    queryFn: () =>
      api<{ content: Alert[] }>(
        filter === 'todos'
          ? '/telemetry/alerts?size=200'
          : filter === 'abertos'
            ? '/telemetry/alerts?open=true&size=200'
            : `/telemetry/alerts?kind=${filter}&size=200`,
      ),
  });

  const acknowledge = useMutation({
    mutationFn: (id: string) => api(`/telemetry/alerts/${id}/acknowledge`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({ message: 'Alerta marcado como visto.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['telemetry', 'alerts'] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  const rows = data?.content ?? [];

  return (
    <Stack gap="lg">

      <Painel
        titulo="Alertas e ocorrências"
        semPadding
        acoes={
          <>
            <SegmentedControl
            size="xs"
            value={filter}
            onChange={setFilter}
            data={[
            { value: 'todos', label: 'Todos' },
            { value: 'abertos', label: 'A decorrer' },
            { value: 'SPEEDING', label: 'Velocidade' },
            { value: 'COMMS_LOST', label: 'Comunicação' },
            ]}
            />
          </>
        }
      >
        <Grelha
          id="alertas"
          linhas={rows}
          chave={(a) => a.id}
          larguraMinima={880}
          vazio="Nenhum alerta neste filtro."
          colunas={[
            {
              id: 'tipo',
              titulo: 'Tipo',
              largura: 150,
              valor: (a) => KIND[a.kind]?.label ?? a.kind,
              render: (a) => (
                <Badge variant="light" color={KIND[a.kind]?.color}>
                  {KIND[a.kind]?.label ?? a.kind}
                </Badge>
              ),
            },
            {
              id: 'ativo',
              titulo: 'Ativo',
              largura: 110,
              valor: (a) => a.assetTag ?? null,
              render: (a) =>
                a.assetId ? (
                  <Text component={Link} to={`/ativos/${a.assetId}`} fw={600} size="sm">
                    {a.assetTag}
                  </Text>
                ) : (
                  '—'
                ),
            },
            {
              id: 'detalhe',
              titulo: 'Detalhe',
              valor: (a) => a.message,
              render: (a) => (
                <>
                  <Text size="sm">{a.message}</Text>
                  {a.peakValue != null && a.limitValue != null && (
                    <Text size="xs" c="dimmed">
                      Pico {fmtNumber(a.peakValue)} km/h · limite {fmtNumber(a.limitValue)} km/h
                      {a.geofenceName ? ` · ${a.geofenceName}` : ''}
                    </Text>
                  )}
                </>
              ),
            },
            {
              id: 'inicio',
              titulo: 'Início',
              largura: 150,
              valor: (a) => a.startedAt,
              render: (a) => fmtDateTime(a.startedAt),
            },
            {
              id: 'fim',
              titulo: 'Fim',
              largura: 150,
              valor: (a) => a.endedAt ?? null,
              render: (a) => (a.endedAt ? fmtDateTime(a.endedAt) : '—'),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 110,
              valor: (a) => (a.open ? 'A decorrer' : 'Terminado'),
              render: (a) => (
                <Badge variant="light" color={a.open ? 'red' : 'gray'}>
                  {a.open ? 'A decorrer' : 'Terminado'}
                </Badge>
              ),
            },
            {
              id: 'visto',
              titulo: '',
              largura: 120,
              render: (a) =>
                !a.acknowledged ? (
                  <Button
                    size="xs"
                    variant="subtle"
                    onClick={() => acknowledge.mutate(a.id)}
                    loading={acknowledge.isPending}
                  >
                    Marcar visto
                  </Button>
                ) : (
                  <Text size="xs" c="dimmed">
                    Visto
                  </Text>
                ),
            },
          ]}
        />
            </Painel>
    </Stack>
  );
}
