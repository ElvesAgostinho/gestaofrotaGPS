import {
  Alert,
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconTruck } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { MapaPercurso, type LinhaReal, type PontoRota } from '../../components/MapaPercurso';
import { useAuth } from '../../auth/AuthContext';
import { fmtDate, fmtDateTime, fmtNumber } from '../../lib/format';

export interface Atribuicao {
  id: string;
  assetId: string;
  assetTag: string;
  assetName: string;
  driverId?: string | null;
  driverName?: string | null;
  plannedFor?: string | null;
  notes?: string | null;
}

export interface RotaDetalhe {
  id: string;
  code?: string | null;
  name: string;
  originName: string;
  destinationName: string;
  expectedDistanceKm?: number | null;
  expectedDurationMinutes?: number | null;
  expectedFuelLiters?: number | null;
  tolerancePercent: number;
  pathGeojson?: string | null;
  waypoints: { id: string; label: string; latitude?: number | null; longitude?: number | null }[];
  assignments?: Atribuicao[];
}

interface Comparacao {
  tripId: string;
  assetTag: string;
  startedAt: string;
  endedAt?: string | null;
  actualDistanceKm?: number | null;
  actualDurationMinutes?: number | null;
  distanceDeltaKm?: number | null;
  durationDeltaMinutes?: number | null;
  outOfTolerance?: boolean | null;
  track: [number, number][];
}

/**
 * A ficha da rota: o percurso desenhado, quem o faz, e o que as viagens
 * mostraram na estrada.
 *
 * <p>A linha dourada é o previsto; a azul tracejada é o que a viatura andou.
 * Quando as duas se separam, está lá a explicação de um consumo que não
 * batia certo — e deixa de ser preciso acreditar em ninguém.
 */
export function FichaRota({ rotaId, fechar }: { rotaId: string; fechar: () => void }) {
  const { has } = useAuth();
  const queryClient = useQueryClient();
  const [verReal, setVerReal] = useState(true);
  const [atribuir, setAtribuir] = useState(false);
  const [assetId, setAssetId] = useState<string | null>(null);
  const [driverId, setDriverId] = useState<string | null>(null);
  const [plannedFor, setPlannedFor] = useState('');

  const { data: rota, isLoading } = useQuery({
    queryKey: ['routes', rotaId],
    queryFn: () => api<RotaDetalhe>(`/routes/${rotaId}`),
  });
  const { data: comparacoes } = useQuery({
    queryKey: ['routes', rotaId, 'comparison'],
    queryFn: () => api<Comparacao[]>(`/routes/${rotaId}/comparison?limit=5`),
  });
  const { data: ativos } = useQuery({
    queryKey: ['assets', 'rotas'],
    queryFn: () => api<{ content: { id: string; tag: string; name: string }[] }>('/assets?size=200'),
    enabled: atribuir,
  });
  const { data: motoristas } = useQuery({
    queryKey: ['drivers', 'rotas'],
    queryFn: () => api<{ content: { id: string; name: string }[] }>('/drivers?size=200'),
    enabled: atribuir,
  });

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['routes'] });

  const atribuirM = useMutation({
    mutationFn: () =>
      api(`/routes/${rotaId}/assignments`, {
        method: 'POST',
        body: { assetId, driverId: driverId || undefined, plannedFor: plannedFor || undefined },
      }),
    onSuccess: () => {
      notifications.show({ message: 'Viatura atribuída.', color: 'green' });
      setAtribuir(false);
      setAssetId(null);
      setDriverId(null);
      setPlannedFor('');
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível atribuir', message: e.message, color: 'red' }),
  });
  const retirar = useMutation({
    mutationFn: (id: string) => api(`/route-assignments/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      notifications.show({ message: 'Atribuição retirada.', color: 'gray' });
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível retirar', message: e.message, color: 'red' }),
  });

  const pontos: PontoRota[] = [];
  if (rota) {
    rota.waypoints.forEach((w, i) => {
      if (w.latitude != null && w.longitude != null) {
        pontos.push({ latitude: Number(w.latitude), longitude: Number(w.longitude), label: w.label, tipo: 'passagem' });
      } else if (i === -1) {
        /* sem coordenadas não se marca nada: não se inventa um ponto */
      }
    });
  }
  const reais: LinhaReal[] = verReal
    ? (comparacoes ?? []).filter((c) => c.track.length > 1).map((c) => ({ coords: c.track, label: c.assetTag }))
    : [];

  return (
    <Modal opened onClose={fechar} title={rota?.name ?? 'Rota'} size="xl" centered>
      {isLoading && <Loader />}
      {rota && (
        <Stack gap="sm">
          <Group gap="xs">
            <Badge variant="light">{rota.originName} → {rota.destinationName}</Badge>
            {rota.expectedDistanceKm != null && (
              <Badge variant="light" color="gold">
                {fmtNumber(rota.expectedDistanceKm, 1)} km previstos
              </Badge>
            )}
            {rota.expectedDurationMinutes != null && (
              <Badge variant="light" color="gray">
                {Math.floor(rota.expectedDurationMinutes / 60)} h {String(rota.expectedDurationMinutes % 60).padStart(2, '0')} min
              </Badge>
            )}
            {rota.expectedFuelLiters != null && (
              <Badge variant="light" color="green">
                {fmtNumber(rota.expectedFuelLiters, 0)} L previstos
              </Badge>
            )}
            <Badge variant="outline" color="gray">
              tolerância {fmtNumber(rota.tolerancePercent, 0)} %
            </Badge>
          </Group>

          <MapaPercurso pathGeojson={rota.pathGeojson} pontos={pontos} reais={reais} altura={330} />

          {!rota.pathGeojson && (
            <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={16} />}>
              Esta rota não tem traçado calculado — foi criada com distância escrita à mão, ou antes do motor de rotas.
              Edite-a e carregue em «Calcular percurso» para desenhar a linha.
            </Alert>
          )}

          <Group justify="space-between">
            <Text fw={700} size="sm">
              Viaturas atribuídas
            </Text>
            {has('DRIVERS_MANAGE') && (
              <Button size="compact-xs" leftSection={<IconTruck size={14} />} onClick={() => setAtribuir((v) => !v)}>
                Atribuir viatura
              </Button>
            )}
          </Group>
          {atribuir && (
            <Group align="flex-end" gap="xs">
              <Select
                label="Viatura"
                placeholder="Escolher…"
                data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
                value={assetId}
                onChange={setAssetId}
                searchable
                w={240}
              />
              <Select
                label="Motorista"
                placeholder="Por definir"
                data={(motoristas?.content ?? []).map((m) => ({ value: m.id, label: m.name }))}
                value={driverId}
                onChange={setDriverId}
                searchable
                clearable
                w={200}
              />
              <TextInput type="date" label="Dia previsto" value={plannedFor} onChange={(e) => setPlannedFor(e.currentTarget.value)} />
              <Button loading={atribuirM.isPending} disabled={!assetId} onClick={() => atribuirM.mutate()}>
                Atribuir
              </Button>
            </Group>
          )}
          <Table fz="sm">
            <Table.Tbody>
              {(rota.assignments ?? []).map((a) => (
                <Table.Tr key={a.id}>
                  <Table.Td fw={600}>{a.assetTag}</Table.Td>
                  <Table.Td>{a.assetName}</Table.Td>
                  <Table.Td>{a.driverName ?? <Text c="dimmed">motorista por definir</Text>}</Table.Td>
                  <Table.Td>{a.plannedFor ? fmtDate(a.plannedFor) : <Text c="dimmed">recorrente</Text>}</Table.Td>
                  <Table.Td ta="right">
                    {has('DRIVERS_MANAGE') && (
                      <Button size="compact-xs" variant="subtle" color="red" onClick={() => retirar.mutate(a.id)}>
                        Retirar
                      </Button>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>

          <Group justify="space-between">
            <Text fw={700} size="sm">
              Últimas viagens nesta rota — previsto contra o andado
            </Text>
            {(comparacoes ?? []).some((c) => c.track.length > 1) && (
              <Button size="compact-xs" variant="subtle" onClick={() => setVerReal((v) => !v)}>
                {verReal ? 'Esconder o percurso real' : 'Mostrar o percurso real'}
              </Button>
            )}
          </Group>
          {(comparacoes ?? []).length === 0 && (
            <Text size="sm" c="dimmed">
              Ainda nenhuma viagem do GPS foi associada a esta rota. Assim que uma viatura a fizer com rastreador, o
              percurso real aparece aqui por cima do previsto.
            </Text>
          )}
          {(comparacoes ?? []).length > 0 && (
            <Table fz="sm">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Viatura</Table.Th>
                  <Table.Th>Quando</Table.Th>
                  <Table.Th ta="right">Andou</Table.Th>
                  <Table.Th ta="right">Diferença</Table.Th>
                  <Table.Th ta="right">Tempo</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(comparacoes ?? []).map((c) => (
                  <Table.Tr key={c.tripId}>
                    <Table.Td fw={600}>{c.assetTag}</Table.Td>
                    <Table.Td>{fmtDateTime(c.startedAt)}</Table.Td>
                    <Table.Td ta="right">{c.actualDistanceKm != null ? `${fmtNumber(c.actualDistanceKm, 1)} km` : '—'}</Table.Td>
                    <Table.Td ta="right">
                      {c.distanceDeltaKm == null ? (
                        '—'
                      ) : (
                        <Text span c={c.outOfTolerance ? 'red' : undefined} fw={c.outOfTolerance ? 700 : 400}>
                          {c.distanceDeltaKm > 0 ? '+' : ''}
                          {fmtNumber(c.distanceDeltaKm, 1)} km
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td ta="right">
                      {c.actualDurationMinutes != null ? `${c.actualDurationMinutes} min` : '—'}
                      {c.durationDeltaMinutes != null && (
                        <Text span size="xs" c="dimmed">
                          {' '}
                          ({c.durationDeltaMinutes > 0 ? '+' : ''}
                          {c.durationDeltaMinutes})
                        </Text>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Stack>
      )}
    </Modal>
  );
}
