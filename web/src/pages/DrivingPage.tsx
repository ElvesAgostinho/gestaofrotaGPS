import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  Textarea,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconInfoCircle } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { Kpi, MagnitudeBar } from '../components/Kpi';
import { fmtDateTime, fmtNumber } from '../lib/format';

interface Event {
  id: string;
  kind: string;
  kindLabel: string;
  severity: string;
  occurredAt: string;
  assetTag: string;
  driverName?: string | null;
  measuredValue?: number | null;
  thresholdValue?: number | null;
  unit?: string | null;
  speedKph?: number | null;
  placeName?: string | null;
  penaltyPoints: number;
  description?: string | null;
  dismissed: boolean;
  dismissReason?: string | null;
}

interface Score {
  driverId: string;
  driverName: string;
  distanceKm: number;
  tripCount: number;
  overspeedCount: number;
  harshBrakeCount: number;
  harshAccelCount: number;
  harshCornerCount: number;
  idlingCount: number;
  nightCount: number;
  totalPenalty: number;
  score?: number | null;
  band?: string | null;
  bandLabel?: string | null;
  insufficientData: boolean;
  explanation: string;
  formula: string;
}

interface Summary {
  periodStart: string;
  periodEnd: string;
  totalEvents: number;
  overspeed: number;
  harshBrake: number;
  harshAcceleration: number;
  harshCornering: number;
  idling: number;
  nightDriving: number;
  ranking: Score[];
}

/**
 * Condução: infrações e pontuação.
 *
 * <p>A pontuação é mostrada com a fórmula à vista, de propósito. Um número que
 * afeta a vida de alguém tem de poder ser refeito à mão com o que está no ecrã —
 * caso contrário é apenas a palavra do sistema contra a da pessoa.
 */
export function DrivingPage() {
  const [toDismiss, setToDismiss] = useState<Event | null>(null);

  const { data: summary } = useQuery({
    queryKey: ['driving', 'summary'],
    queryFn: () => api<Summary>('/driving/summary'),
  });

  const { data: events, isLoading } = useQuery({
    queryKey: ['driving', 'events'],
    queryFn: () => api<{ content: Event[] }>('/driving-events?size=100'),
  });

  const rows = events?.content ?? [];
  const ranking = summary?.ranking ?? [];
  const piorPenalizacao = Math.max(1, ...ranking.map((s) => s.totalPenalty));

  return (
    <Stack gap="lg">

      <Group gap="sm" wrap="wrap">
        <Kpi label="Infrações no período" value={summary?.totalEvents ?? 0} />
        <Kpi
          label="Excesso de velocidade"
          value={summary?.overspeed ?? 0}
          tone={(summary?.overspeed ?? 0) > 0 ? 'critical' : 'neutral'}
          hint="Vem dos alertas de telemetria, que aplicam os limites por zona, ativo e empresa. Não é detetado duas vezes."
        />
        <Kpi
          label="Travagens bruscas"
          value={summary?.harshBrake ?? 0}
          tone={(summary?.harshBrake ?? 0) > 0 ? 'warning' : 'neutral'}
        />
        <Kpi label="Acelerações bruscas" value={summary?.harshAcceleration ?? 0} />
        <Kpi label="Curvas bruscas" value={summary?.harshCornering ?? 0} />
        <Kpi
          label="Ralenti"
          value={summary?.idling ?? 0}
          hint="Motor ligado e parado mais de 5 minutos seguidos. Queima combustível sem andar."
        />
        <Kpi label="Condução noturna" value={summary?.nightDriving ?? 0} />
      </Group>

      <Painel titulo="Comportamento de condução" semPadding>
        <Group justify="space-between" p="md" pb="xs">
          <Text fw={700}>Motoristas</Text>
          {ranking.length > 0 && (
            <Tooltip label={ranking[0].formula} withArrow>
              <Text size="xs" c="dimmed" style={{ cursor: 'help' }}>
                como é calculada a pontuação?
              </Text>
            </Tooltip>
          )}
        </Group>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Motorista</Table.Th>
              <Table.Th style={{ width: 130 }}>Pontuação</Table.Th>
              <Table.Th style={{ width: 120 }}>Percorrido</Table.Th>
              <Table.Th>Infrações</Table.Th>
              <Table.Th style={{ width: 150 }}>Penalização</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {ranking.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed" ta="center" py="lg" size="sm">
                    Ainda não há viagens atribuídas a motoristas no período.
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {ranking.map((s) => (
              <Table.Tr key={s.driverId}>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {s.driverName}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {s.tripCount} viagem(ns)
                  </Text>
                </Table.Td>
                <Table.Td>
                  {s.insufficientData ? (
                    <Tooltip label={s.explanation} multiline w={280} withArrow>
                      <Badge variant="light" color="gray" size="sm" style={{ cursor: 'help' }}>
                        sem dados que cheguem
                      </Badge>
                    </Tooltip>
                  ) : (
                    <Group gap={6} align="baseline">
                      <Text
                        fw={700}
                        style={{
                          fontSize: 20,
                          color: bandColour(s.band),
                          fontVariantNumeric: 'tabular-nums',
                        }}
                      >
                        {fmtNumber(s.score, 0)}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {s.bandLabel}
                      </Text>
                    </Group>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {fmtNumber(s.distanceKm, 0)} km
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Group gap={4} wrap="wrap">
                    <Count n={s.overspeedCount} label="velocidade" color="red" />
                    <Count n={s.harshBrakeCount} label="travagem" color="orange" />
                    <Count n={s.harshAccelCount} label="aceleração" color="gray" />
                    <Count n={s.harshCornerCount} label="curva" color="orange" />
                    <Count n={s.idlingCount} label="ralenti" color="gray" />
                    <Count n={s.nightCount} label="noite" color="gray" />
                  </Group>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {fmtNumber(s.totalPenalty, 0)} pts
                  </Text>
                  <MagnitudeBar
                    value={s.totalPenalty}
                    max={piorPenalizacao}
                    tone={s.band === 'CRITICAL' ? 'critical' : 'neutral'}
                  />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
            </Painel>

      <Alert variant="light" color="blue" icon={<IconInfoCircle size={18} />}>
        Uma travagem violenta nem sempre é má condução — travar a fundo para não atropelar
        alguém é o que se quer que aconteça. Anule a infração com a razão escrita e ela deixa
        de contar para a pontuação.
      </Alert>

      <Card p={0}>
        <Text fw={700} p="md" pb="xs">
          Infrações
        </Text>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 150 }}>Quando</Table.Th>
              <Table.Th style={{ width: 100 }}>Ativo</Table.Th>
              <Table.Th style={{ width: 150 }}>Motorista</Table.Th>
              <Table.Th>O que aconteceu</Table.Th>
              <Table.Th style={{ width: 120 }}>Medido</Table.Th>
              <Table.Th style={{ width: 110 }} />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed" ta="center" py="xl" size="sm">
                    {isLoading ? 'A carregar…' : 'Nenhuma infração registada.'}
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {rows.map((e) => (
              <Table.Tr key={e.id} style={{ opacity: e.dismissed ? 0.55 : 1 }}>
                <Table.Td>
                  <Text size="sm">{fmtDateTime(e.occurredAt)}</Text>
                  {e.placeName && (
                    <Text size="xs" c="dimmed">
                      {e.placeName}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {e.assetTag}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">
                    {e.driverName ?? (
                      <Text span c="dimmed" size="xs">
                        sem motorista atribuído
                      </Text>
                    )}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Badge variant="light" color={severityColour(e.severity)} size="sm">
                    {e.kindLabel}
                  </Badge>
                  {e.description && (
                    <Text size="xs" c="dimmed" mt={2} lh={1.4}>
                      {e.description}
                    </Text>
                  )}
                  {e.dismissed && (
                    <Text size="xs" c="dimmed" fs="italic" mt={2}>
                      Anulada: {e.dismissReason}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  {/* Medido e limiar juntos: a infração tem de poder ser
                      contestada com números, não com a palavra do sistema. */}
                  {e.measuredValue != null ? (
                    <>
                      <Text size="sm" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {fmtNumber(e.measuredValue, 1)}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {e.thresholdValue != null ? `limite ${fmtNumber(e.thresholdValue, 0)} ` : ''}
                        {e.unit}
                      </Text>
                    </>
                  ) : (
                    <Text size="xs" c="dimmed">
                      —
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  {!e.dismissed && (
                    <Button size="xs" variant="default" onClick={() => setToDismiss(e)}>
                      Anular
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>

      <DismissModal event={toDismiss} onClose={() => setToDismiss(null)} />
    </Stack>
  );
}

function Count({ n, label, color }: { n: number; label: string; color: string }) {
  if (!n) return null;
  return (
    <Badge variant="light" color={color} size="sm">
      {n} {label}
    </Badge>
  );
}

function DismissModal({ event, onClose }: { event: Event | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const dismiss = useMutation({
    mutationFn: () =>
      api(`/driving-events/${event!.id}/dismiss`, {
        method: 'POST',
        body: { reason },
      }),
    onSuccess: () => {
      notifications.show({
        title: 'Infração anulada',
        message: 'Deixa de contar para a pontuação. A razão fica registada.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['driving'] });
      setReason('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível anular', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={!!event} onClose={onClose} title="Anular infração" centered>
      <Stack gap="md">
        {event && (
          <Alert variant="light" color={severityColour(event.severity)}>
            <b>{event.kindLabel}</b> — {event.assetTag}
            {event.driverName ? `, ${event.driverName}` : ''}
            <br />
            {event.description}
          </Alert>
        )}
        <Textarea
          label="Porquê"
          description="Fica registado quem anulou e porquê — sem isso seria uma forma silenciosa de apagar o que não convém."
          placeholder="Ex.: travou a fundo para não atropelar uma criança que atravessou"
          minRows={3}
          autosize
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={reason.trim().length < 5}
            loading={dismiss.isPending}
            onClick={() => dismiss.mutate()}
          >
            Anular infração
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function bandColour(band?: string | null) {
  switch (band) {
    case 'EXCELLENT':
      return '#1a7f4b';
    case 'GOOD':
      return '#27272a';
    case 'NEEDS_IMPROVEMENT':
      return '#b45309';
    case 'CRITICAL':
      return '#b42318';
    default:
      return '#71717a';
  }
}

function severityColour(severity: string) {
  if (severity === 'CRITICAL') return 'red';
  if (severity === 'WARNING') return 'orange';
  return 'gray';
}
