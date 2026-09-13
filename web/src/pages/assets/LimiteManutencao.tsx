import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconTool } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { AssetView, MeterKind } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { BotaoBarra, Painel } from '../../components/erp';
import { fmtDate, fmtNumber } from '../../lib/format';

/** Uma tarefa de plano com o vencimento já calculado pelo servidor. */
export interface TarefaPlano {
  id: string;
  title: string;
  systemName?: string | null;
  status: 'OK' | 'DUE_SOON' | 'OVERDUE' | string;
  nextDueAt?: string | null;
  nextDueMeter?: number | null;
  nextDueMeterKind?: string | null;
  remainingMeter?: number | null;
  remainingDays?: number | null;
  lastDoneAt?: string | null;
  lastDoneMeter?: number | null;
}

export interface PlanoDoAtivo {
  id: string;
  planId: string;
  planName: string;
  active: boolean;
  tasks: TarefaPlano[];
}

/** «Próxima manutenção» — o resumo que a lista de ativos traz do servidor. */
export interface ProximaManutencao {
  title: string;
  status: string;
  remainingMeter?: number | null;
  meterKind?: string | null;
  remainingDays?: number | null;
  nextDueMeter?: number | null;
  nextDueAt?: string | null;
}

const ORDEM_URGENCIA: Record<string, number> = { OVERDUE: 0, DUE_SOON: 1 };

/** A tarefa que vence primeiro, entre todos os planos do ativo. */
export function tarefaMaisUrgente(planos: PlanoDoAtivo[] | undefined): TarefaPlano | null {
  const tarefas = (planos ?? []).filter((p) => p.active).flatMap((p) => p.tasks ?? []);
  if (tarefas.length === 0) return null;
  return [...tarefas].sort((a, b) => {
    const ua = ORDEM_URGENCIA[a.status] ?? 2;
    const ub = ORDEM_URGENCIA[b.status] ?? 2;
    if (ua !== ub) return ua - ub;
    const da = a.remainingDays ?? Number.MAX_SAFE_INTEGER;
    const db = b.remainingDays ?? Number.MAX_SAFE_INTEGER;
    if (da !== db) return da - db;
    return (a.remainingMeter ?? Number.MAX_SAFE_INTEGER) - (b.remainingMeter ?? Number.MAX_SAFE_INTEGER);
  })[0];
}

export function unidade(kind?: string | null) {
  return kind === 'HOURMETER' ? 'h' : 'km';
}

/** «em 1 250 km» / «vencida há 150 km» / «em 12 dias» — a frase curta da grelha. */
export function fraseRestante(p: {
  status: string;
  remainingMeter?: number | null;
  meterKind?: string | null;
  remainingDays?: number | null;
}): string {
  if (p.remainingMeter != null) {
    const u = unidade(p.meterKind);
    return p.remainingMeter < 0
      ? `passou ${fmtNumber(-p.remainingMeter, 0)} ${u}`
      : `em ${fmtNumber(p.remainingMeter, 0)} ${u}`;
  }
  if (p.remainingDays != null) {
    return p.remainingDays < 0 ? `passou ${-p.remainingDays} dias` : `em ${p.remainingDays} dias`;
  }
  return p.status === 'OVERDUE' ? 'vencida' : '';
}

export function EstadoTarefa({ status }: { status: string }) {
  return (
    <Badge
      variant="light"
      color={status === 'OVERDUE' ? 'red' : status === 'DUE_SOON' ? 'yellow' : 'green'}
    >
      {status === 'OVERDUE' ? 'Vencida' : status === 'DUE_SOON' ? 'A vencer' : 'Em dia'}
    </Badge>
  );
}

/**
 * O painel da ficha: «Próxima manutenção: revisão geral em 1 250 km», com o
 * botão para definir o limite. É a resposta a «onde ponho o limite que a
 * viatura deve atingir?».
 */
export function PainelProximaManutencao({ asset }: { asset: AssetView }) {
  const { has } = useAuth();
  const [definir, setDefinir] = useState(false);
  const { data } = useQuery({
    queryKey: ['asset-plan', asset.id],
    queryFn: () => api<PlanoDoAtivo[]>(`/assets/${asset.id}/maintenance-plans`),
  });
  const proxima = tarefaMaisUrgente(data);
  const totalTarefas = (data ?? []).filter((p) => p.active).reduce((n, p) => n + (p.tasks?.length ?? 0), 0);

  return (
    <Painel
      titulo="Próxima manutenção"
      acoes={
        has('PLANS_MANAGE') ? (
          <BotaoBarra icone={<IconTool size={13} />} onClick={() => setDefinir(true)} destaque={!proxima}>
            Definir limite
          </BotaoBarra>
        ) : undefined
      }
    >
      <LimiteManutencaoModal asset={asset} aberto={definir} fechar={() => setDefinir(false)} />
      {!proxima ? (
        <Text size="sm" c="dimmed">
          Sem limite definido. Diga a cada quantos km, horas ou dias este equipamento vai à
          manutenção: o sistema conta a partir do contador (à mão ou pelo GPS) e avisa quando chegar lá.
        </Text>
      ) : (
        <Group justify="space-between" align="flex-end" wrap="wrap">
          <div>
            <Group gap={8} align="baseline">
              <Text fw={800} style={{ fontSize: 26 }} c={proxima.status === 'OVERDUE' ? 'red' : proxima.status === 'DUE_SOON' ? 'orange' : undefined}>
                {fraseRestante({
                  status: proxima.status,
                  remainingMeter: proxima.remainingMeter,
                  meterKind: proxima.nextDueMeterKind,
                  remainingDays: proxima.remainingDays,
                })}
              </Text>
              <EstadoTarefa status={proxima.status} />
            </Group>
            <Text size="sm">
              {proxima.title}
              {proxima.systemName ? ` · ${proxima.systemName}` : ''}
            </Text>
            <Text size="xs" c="dimmed">
              {proxima.nextDueMeter != null
                ? `Aos ${fmtNumber(proxima.nextDueMeter, 0)} ${unidade(proxima.nextDueMeterKind)}`
                : ''}
              {proxima.nextDueAt ? `${proxima.nextDueMeter != null ? ' · ' : ''}previsto para ${fmtDate(proxima.nextDueAt)}` : ''}
              {proxima.lastDoneMeter != null
                ? ` · última aos ${fmtNumber(proxima.lastDoneMeter, 0)} ${unidade(proxima.nextDueMeterKind)}`
                : ''}
            </Text>
          </div>
          <Text size="xs" c="dimmed">
            {totalTarefas} tarefa(s) planeada(s) — ver separador «Plano»
          </Text>
        </Group>
      )}
    </Painel>
  );
}

/**
 * O formulário do limite. Mostra só a unidade do contador principal do
 * ativo (km ou horas): pedir horas a um camião que conta por km seria pedir
 * um número que o sistema nunca iria comparar com nada.
 */
export function LimiteManutencaoModal({
  asset,
  aberto,
  fechar,
}: {
  asset: AssetView;
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const meter = asset.meters?.find((m) => m.primary) ?? asset.meters?.[0];
  const kind: MeterKind | undefined = meter?.kind;
  const u = unidade(kind);

  const [titulo, setTitulo] = useState('Revisão geral');
  const [cada, setCada] = useState<number | string>('');
  const [cadaDias, setCadaDias] = useState<number | string>('');
  const [ultimaLeitura, setUltimaLeitura] = useState<number | string>('');
  const [ultimaData, setUltimaData] = useState<Date | null>(null);
  const [notas, setNotas] = useState('');

  const guardar = useMutation({
    mutationFn: () =>
      api(`/assets/${asset.id}/maintenance-interval`, {
        method: 'POST',
        body: {
          title: titulo.trim() || null,
          everyKm: kind === 'ODOMETER' && cada !== '' ? Number(cada) : null,
          everyHours: kind === 'HOURMETER' && cada !== '' ? Number(cada) : null,
          everyDays: cadaDias !== '' ? Number(cadaDias) : null,
          lastDoneMeter: ultimaLeitura !== '' ? Number(ultimaLeitura) : null,
          lastDoneAt: ultimaData ? ultimaData.toISOString() : null,
          notes: notas.trim() || null,
        },
      }),
    onSuccess: () => {
      notifications.show({
        title: 'Limite definido',
        message: `${titulo || 'Revisão geral'} — o sistema avisa quando o contador lá chegar.`,
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['asset-plan', asset.id] });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['plans'] });
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível definir', message: e.message, color: 'red' }),
  });

  const valido = cada !== '' || cadaDias !== '';

  return (
    <Modal opened={aberto} onClose={fechar} title={`Limite de manutenção — ${asset.tag}`} centered>
      <Stack gap="sm">
        {!meter && (
          <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={16} />}>
            Este ativo ainda não tem contador: só é possível um limite por dias. Registe a primeira
            leitura na ficha, ou ligue-lhe um rastreador GPS, para contar por {u}.
          </Alert>
        )}
        <TextInput
          label="O que se faz"
          placeholder="Revisão geral, mudança de óleo, travões…"
          value={titulo}
          onChange={(e) => setTitulo(e.currentTarget.value)}
          data-autofocus
        />
        <Group grow align="flex-start">
          {meter && (
            <NumberInput
              label={`A cada quantos ${kind === 'HOURMETER' ? 'horas' : 'km'}`}
              description={`Contador atual: ${fmtNumber(meter.currentValue, 0)} ${u}${meter.dailyAverage ? ` · média ${fmtNumber(meter.dailyAverage, 0)} ${u}/dia` : ''}`}
              placeholder={kind === 'HOURMETER' ? '250' : '5000'}
              min={1}
              thousandSeparator=" "
              value={cada}
              onChange={(v) => setCada(typeof v === 'number' ? v : '')}
            />
          )}
          <NumberInput
            label="Ou a cada quantos dias"
            description="Opcional. Vence o que chegar primeiro."
            placeholder="180"
            min={1}
            value={cadaDias}
            onChange={(v) => setCadaDias(typeof v === 'number' ? v : '')}
          />
        </Group>
        <Group grow align="flex-start">
          {meter && (
            <NumberInput
              label={`Última vez feita aos (${u})`}
              description="Vazio = conta a partir do contador de hoje."
              min={0}
              max={meter.currentValue ?? undefined}
              thousandSeparator=" "
              value={ultimaLeitura}
              onChange={(v) => setUltimaLeitura(typeof v === 'number' ? v : '')}
            />
          )}
          <DateInput
            label="Última vez feita em"
            description="Vazio = hoje."
            value={ultimaData}
            onChange={setUltimaData}
            valueFormat="DD/MM/YYYY"
            maxDate={new Date()}
            clearable
          />
        </Group>
        <Textarea
          label="Instruções (opcional)"
          placeholder="Óleo 15W40, filtros de óleo e gasóleo, verificar correias…"
          value={notas}
          onChange={(e) => setNotas(e.currentTarget.value)}
          autosize
          minRows={2}
        />
        <Text size="xs" c="dimmed">
          Quando o contador chegar ao limite, a tarefa fica «vencida», quem gere a manutenção recebe o
          aviso e a ordem preventiva é aberta automaticamente na manhã seguinte.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button onClick={() => guardar.mutate()} loading={guardar.isPending} disabled={!valido}>
            Guardar limite
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
