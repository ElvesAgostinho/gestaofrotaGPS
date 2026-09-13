import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconArrowsExchange, IconPlus, IconRuler, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDate, fmtMoney, fmtNumber } from '../../lib/format';

export interface Pneu {
  id: string;
  assetId?: string | null;
  assetTag?: string | null;
  position?: string | null;
  brand?: string | null;
  model?: string | null;
  size?: string | null;
  serialNumber?: string | null;
  status: 'INSTALLED' | 'STOCK' | 'RETIRED' | string;
  cost?: number | null;
  currency?: string;
  installedAt?: string | null;
  installedMeter?: number | null;
  removedAt?: string | null;
  removedMeter?: number | null;
  removalReason?: string | null;
  targetPressure?: number | null;
  lastPressure?: number | null;
  lastTreadMm?: number | null;
  minTreadMm?: number | null;
  lastMeasuredAt?: string | null;
  notes?: string | null;
  distanceRun?: number | null;
  costPerUnit?: number | null;
  alert?: string | null;
}

/** Posições habituais: FE/FD à frente; TE1/TD1, TE2/TD2 nos eixos traseiros (interior/exterior com i/e). */
const POSICOES = ['FE', 'FD', 'TE1', 'TD1', 'TE1i', 'TD1i', 'TE2', 'TD2', 'TE2i', 'TD2i', 'TE3', 'TD3', 'SOB'];

const ESTADO: Record<string, { label: string; color: string }> = {
  INSTALLED: { label: 'Montado', color: 'green' },
  STOCK: { label: 'Em stock', color: 'blue' },
  RETIRED: { label: 'Abatido', color: 'gray' },
};

const MOTIVO: Record<string, string> = {
  WORN: 'Desgaste',
  DAMAGED: 'Dano',
  PUNCTURE: 'Furo',
  ROTATION: 'Rotação',
  RETREAD: 'Recauchutagem',
  OTHER: 'Outro',
};

/**
 * Os pneus de uma viatura: posição a posição, com o que cada um já andou e a
 * quanto está a sair o quilómetro. É o separador que responde à pergunta da
 * compra — que marca dura mais — e que avisa do sulco antes de rebentar.
 */
export function PneusTab({ assetId, unidade }: { assetId: string; unidade: 'km' | 'h' }) {
  const { has } = useAuth();
  const queryClient = useQueryClient();
  const [montar, setMontar] = useState(false);
  const [medir, setMedir] = useState<Pneu | null>(null);
  const [desmontar, setDesmontar] = useState<Pneu | null>(null);
  const [rodar, setRodar] = useState<Pneu | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['tyres', assetId],
    queryFn: () => api<Pneu[]>(`/assets/${assetId}/tyres`),
  });
  const pneus = data ?? [];
  const montados = pneus.filter((p) => p.status === 'INSTALLED');
  const comAlerta = montados.filter((p) => p.alert);
  const podeMexer = has('WORKORDERS_MANAGE');
  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['tyres', assetId] });

  const apagar = useMutation({
    mutationFn: (p: Pneu) => api(`/tyres/${p.id}`, { method: 'DELETE' }),
    onSuccess: invalidar,
    onError: (e: Error) => notifications.show({ title: 'Não foi possível apagar', message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="sm">
      {comAlerta.length > 0 && (
        <Alert color="orange" variant="light" icon={<IconAlertTriangle size={17} />} p="sm">
          <Text size="sm" fw={600}>
            {comAlerta.length} pneu(s) a pedir atenção
          </Text>
          <Text size="xs">{comAlerta.map((p) => `${p.position}: ${p.alert}`).join(' · ')}</Text>
        </Alert>
      )}

      <Group justify="space-between">
        <Text size="sm" c="dimmed">
          {montados.length} montado(s) · {pneus.filter((p) => p.status === 'STOCK').length} em stock ·{' '}
          {pneus.filter((p) => p.status === 'RETIRED').length} abatido(s)
        </Text>
        {podeMexer && (
          <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setMontar(true)}>
            Montar pneu
          </Button>
        )}
      </Group>

      {isLoading ? null : pneus.length === 0 ? (
        <Alert variant="light">
          Ainda não há pneus registados nesta viatura. Monte o primeiro com a posição, a marca e o custo: a
          partir daí o sistema conta os {unidade} de cada um e o custo por {unidade}.
        </Alert>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Posição</Table.Th>
              <Table.Th>Pneu</Table.Th>
              <Table.Th>Estado</Table.Th>
              <Table.Th>Montado</Table.Th>
              <Table.Th style={{ textAlign: 'right' }}>{unidade} feitos</Table.Th>
              <Table.Th style={{ textAlign: 'right' }}>Custo</Table.Th>
              <Table.Th style={{ textAlign: 'right' }}>Por {unidade}</Table.Th>
              <Table.Th>Sulco / pressão</Table.Th>
              <Table.Th></Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {pneus.map((p) => (
              <Table.Tr key={p.id} style={p.alert ? { background: 'rgba(255, 152, 0, 0.08)' } : undefined}>
                <Table.Td>
                  <Text fw={700}>{p.position ?? '—'}</Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{[p.brand, p.model].filter(Boolean).join(' ') || '—'}</Text>
                  <Text size="xs" c="dimmed">
                    {[p.size, p.serialNumber].filter(Boolean).join(' · ')}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Badge variant="light" color={ESTADO[p.status]?.color} size="sm">
                    {ESTADO[p.status]?.label ?? p.status}
                  </Badge>
                  {p.removalReason && (
                    <Text size="xs" c="dimmed">
                      {MOTIVO[p.removalReason] ?? p.removalReason}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm">{fmtDate(p.installedAt)}</Text>
                  <Text size="xs" c="dimmed">
                    aos {p.installedMeter != null ? fmtNumber(p.installedMeter, 0) : '—'} {unidade}
                  </Text>
                </Table.Td>
                <Table.Td style={{ textAlign: 'right' }}>{p.distanceRun != null ? fmtNumber(p.distanceRun, 0) : '—'}</Table.Td>
                <Table.Td style={{ textAlign: 'right' }}>{p.cost != null ? fmtMoney(p.cost) : '—'}</Table.Td>
                <Table.Td style={{ textAlign: 'right' }}>{p.costPerUnit != null ? fmtNumber(p.costPerUnit, 2) : '—'}</Table.Td>
                <Table.Td>
                  <Text size="sm" c={p.alert ? 'orange' : undefined}>
                    {p.lastTreadMm != null ? `${fmtNumber(p.lastTreadMm, 1)} mm` : '—'}
                    {p.lastPressure != null ? ` · ${fmtNumber(p.lastPressure, 1)} bar` : ''}
                  </Text>
                  {p.alert && (
                    <Text size="xs" c="orange">
                      {p.alert}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  {podeMexer && p.status === 'INSTALLED' && (
                    <Group gap={4} wrap="nowrap">
                      <Button size="compact-xs" variant="subtle" leftSection={<IconRuler size={13} />} onClick={() => setMedir(p)}>
                        Medir
                      </Button>
                      <Button size="compact-xs" variant="subtle" leftSection={<IconArrowsExchange size={13} />} onClick={() => setRodar(p)}>
                        Rodar
                      </Button>
                      <Button size="compact-xs" variant="subtle" color="red" onClick={() => setDesmontar(p)}>
                        Desmontar
                      </Button>
                    </Group>
                  )}
                  {podeMexer && p.status !== 'INSTALLED' && (
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      color="gray"
                      leftSection={<IconTrash size={13} />}
                      onClick={() => window.confirm('Apagar este registo de pneu?') && apagar.mutate(p)}
                    >
                      Apagar
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <MontarModal assetId={assetId} unidade={unidade} aberto={montar} fechar={() => setMontar(false)} ocupadas={montados.map((p) => p.position ?? '')} />
      {medir && <MedirModal pneu={medir} unidade={unidade} fechar={() => setMedir(null)} aoGuardar={invalidar} />}
      {desmontar && <DesmontarModal pneu={desmontar} unidade={unidade} fechar={() => setDesmontar(null)} aoGuardar={invalidar} />}
      {rodar && <RodarModal pneu={rodar} outros={montados.filter((p) => p.id !== rodar.id)} fechar={() => setRodar(null)} aoGuardar={invalidar} />}
    </Stack>
  );
}

function MontarModal({ assetId, unidade, aberto, fechar, ocupadas }: { assetId: string; unidade: string; aberto: boolean; fechar: () => void; ocupadas: string[] }) {
  const queryClient = useQueryClient();
  const [position, setPosition] = useState<string | null>(null);
  const [brand, setBrand] = useState('');
  const [model, setModel] = useState('');
  const [size, setSize] = useState('');
  const [serialNumber, setSerialNumber] = useState('');
  const [cost, setCost] = useState<number | string>('');
  const [installedMeter, setInstalledMeter] = useState<number | string>('');
  const [targetPressure, setTargetPressure] = useState<number | string>(8.5);
  const [treadMm, setTreadMm] = useState<number | string>('');
  const [minTreadMm, setMinTreadMm] = useState<number | string>(3);

  const montar = useMutation({
    mutationFn: () =>
      api(`/assets/${assetId}/tyres`, {
        method: 'POST',
        body: {
          position,
          brand: brand.trim() || null,
          model: model.trim() || null,
          size: size.trim() || null,
          serialNumber: serialNumber.trim() || null,
          cost: cost === '' ? null : Number(cost),
          installedMeter: installedMeter === '' ? null : Number(installedMeter),
          targetPressure: targetPressure === '' ? null : Number(targetPressure),
          lastTreadMm: treadMm === '' ? null : Number(treadMm),
          minTreadMm: minTreadMm === '' ? null : Number(minTreadMm),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Pneu montado', message: `${position} · ${brand}`, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['tyres', assetId] });
      setPosition(null);
      setSerialNumber('');
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível montar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Montar pneu" centered>
      <Stack gap="sm">
        <Select
          label="Posição"
          description="FE/FD à frente; TE1/TD1 no 1.º eixo traseiro (i = interior); SOB = sobressalente."
          data={POSICOES.map((p) => ({ value: p, label: p, disabled: ocupadas.includes(p) }))}
          value={position}
          onChange={setPosition}
          searchable
          data-autofocus
        />
        <Group grow>
          <TextInput label="Marca" placeholder="Michelin" value={brand} onChange={(e) => setBrand(e.currentTarget.value)} />
          <TextInput label="Modelo" placeholder="X Multi Z" value={model} onChange={(e) => setModel(e.currentTarget.value)} />
        </Group>
        <Group grow>
          <TextInput label="Medida" placeholder="315/80 R22.5" value={size} onChange={(e) => setSize(e.currentTarget.value)} />
          <TextInput label="Nº de série (DOT)" value={serialNumber} onChange={(e) => setSerialNumber(e.currentTarget.value)} />
        </Group>
        <Group grow>
          <NumberInput label="Custo" thousandSeparator=" " min={0} value={cost} onChange={(v) => setCost(typeof v === 'number' ? v : '')} />
          <NumberInput label={`Contador na montagem (${unidade})`} description="Vazio = contador atual" thousandSeparator=" " min={0} value={installedMeter} onChange={(v) => setInstalledMeter(typeof v === 'number' ? v : '')} />
        </Group>
        <Group grow>
          <NumberInput label="Sulco novo (mm)" placeholder="16" min={0} max={40} value={treadMm} onChange={(v) => setTreadMm(typeof v === 'number' ? v : '')} />
          <NumberInput label="Sulco mínimo (mm)" min={0} max={40} step={0.5} value={minTreadMm} onChange={(v) => setMinTreadMm(typeof v === 'number' ? v : '')} />
          <NumberInput label="Pressão alvo (bar)" min={0} max={20} step={0.5} value={targetPressure} onChange={(v) => setTargetPressure(typeof v === 'number' ? v : '')} />
        </Group>
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={!position} loading={montar.isPending} onClick={() => montar.mutate()}>
            Montar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function MedirModal({ pneu, unidade, fechar, aoGuardar }: { pneu: Pneu; unidade: string; fechar: () => void; aoGuardar: () => void }) {
  const [pressure, setPressure] = useState<number | string>(pneu.lastPressure ?? '');
  const [treadMm, setTreadMm] = useState<number | string>('');
  const [note, setNote] = useState('');
  const medir = useMutation({
    mutationFn: () =>
      api(`/tyres/${pneu.id}/readings`, {
        method: 'POST',
        body: { pressure: pressure === '' ? null : Number(pressure), treadMm: treadMm === '' ? null : Number(treadMm), note: note.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Medição registada', message: pneu.position ?? '', color: 'green' });
      aoGuardar();
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });
  return (
    <Modal opened onClose={fechar} title={`Medir pneu ${pneu.position}`} centered>
      <Stack gap="sm">
        <Text size="xs" c="dimmed">
          Já fez {pneu.distanceRun != null ? fmtNumber(pneu.distanceRun, 0) : '—'} {unidade}. Sulco mínimo {fmtNumber(pneu.minTreadMm ?? 3, 1)} mm
          {pneu.targetPressure ? ` · pressão alvo ${fmtNumber(pneu.targetPressure, 1)} bar` : ''}.
        </Text>
        <Group grow>
          <NumberInput label="Sulco (mm)" min={0} max={40} step={0.5} value={treadMm} onChange={(v) => setTreadMm(typeof v === 'number' ? v : '')} data-autofocus />
          <NumberInput label="Pressão (bar)" min={0} max={20} step={0.1} value={pressure} onChange={(v) => setPressure(typeof v === 'number' ? v : '')} />
        </Group>
        <Textarea label="Nota" value={note} onChange={(e) => setNote(e.currentTarget.value)} autosize minRows={1} />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={treadMm === '' && pressure === ''} loading={medir.isPending} onClick={() => medir.mutate()}>
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function DesmontarModal({ pneu, unidade, fechar, aoGuardar }: { pneu: Pneu; unidade: string; fechar: () => void; aoGuardar: () => void }) {
  const [reason, setReason] = useState<string | null>('WORN');
  const [retire, setRetire] = useState<string | null>('sim');
  const [note, setNote] = useState('');
  const desmontar = useMutation({
    mutationFn: () => api(`/tyres/${pneu.id}/remove`, { method: 'POST', body: { reason, retire: retire === 'sim', note: note.trim() || null } }),
    onSuccess: () => {
      notifications.show({ title: 'Pneu desmontado', message: pneu.position ?? '', color: 'green' });
      aoGuardar();
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível desmontar', message: e.message, color: 'red' }),
  });
  return (
    <Modal opened onClose={fechar} title={`Desmontar pneu ${pneu.position}`} centered>
      <Stack gap="sm">
        <Text size="sm">
          Fez {pneu.distanceRun != null ? fmtNumber(pneu.distanceRun, 0) : '—'} {unidade} nesta montagem
          {pneu.costPerUnit != null ? ` — ${fmtNumber(pneu.costPerUnit, 2)} por ${unidade}` : ''}.
        </Text>
        <Select label="Motivo" data={Object.entries(MOTIVO).map(([value, label]) => ({ value, label }))} value={reason} onChange={setReason} allowDeselect={false} />
        <Select
          label="Destino"
          data={[
            { value: 'sim', label: 'Fim de vida (abater)' },
            { value: 'nao', label: 'Fica em stock (reserva / recauchutar)' },
          ]}
          value={retire}
          onChange={setRetire}
          allowDeselect={false}
        />
        <Textarea label="Nota" value={note} onChange={(e) => setNote(e.currentTarget.value)} autosize minRows={1} />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button color="red" loading={desmontar.isPending} onClick={() => desmontar.mutate()}>
            Desmontar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function RodarModal({ pneu, outros, fechar, aoGuardar }: { pneu: Pneu; outros: Pneu[]; fechar: () => void; aoGuardar: () => void }) {
  const [outro, setOutro] = useState<string | null>(null);
  const rodar = useMutation({
    mutationFn: () => api(`/tyres/${pneu.id}/rotate`, { method: 'POST', body: { otherTyreId: outro } }),
    onSuccess: () => {
      notifications.show({ title: 'Rotação feita', message: '', color: 'green' });
      aoGuardar();
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível rodar', message: e.message, color: 'red' }),
  });
  return (
    <Modal opened onClose={fechar} title={`Rodar pneu ${pneu.position}`} centered>
      <Stack gap="sm">
        <Select
          label="Trocar de posição com"
          data={outros.map((o) => ({ value: o.id, label: `${o.position} — ${[o.brand, o.model].filter(Boolean).join(' ')}` }))}
          value={outro}
          onChange={setOutro}
          data-autofocus
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={!outro} loading={rodar.isPending} onClick={() => rodar.mutate()}>
            Rodar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
