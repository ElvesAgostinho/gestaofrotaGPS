/**
 * Os dois quadros que fecham a folha de manutenção: o programa de
 * monitorização de condição e os indicadores que provam que ele resultou.
 *
 * <p>A promessa e a medição da promessa, lado a lado — que é como o documento
 * de referência as apresenta, e é como se defendem perante um cliente: «mede-se
 * isto, com esta periodicidade, para isto; e a máquina esteve disponível 94 %
 * do tempo».
 */
import { Badge, Button, Group, Loader, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAdjustments,
  IconBolt,
  IconClipboardList,
  IconClockHour4,
  IconDroplet,
  IconGauge,
  IconInfoCircle,
  IconTemperature,
  IconTool,
  IconWaveSine,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDate, fmtNumber } from '../../lib/format';
import { BarraFicha, CaixaFicha, PRETO } from './FichaTecnica';

interface Programa {
  id: string;
  technique: string;
  techniqueLabel: string;
  frequencyMonths: number;
  frequencyLabel: string;
  components?: string | null;
  goal?: string | null;
  lastDoneAt?: string | null;
  nextDueAt?: string | null;
  status: string;
  lastResult?: string | null;
}

interface Metrica {
  key: string;
  name: string;
  value?: number | null;
  unit: string;
  target?: number | null;
  targetDirection: 'MIN' | 'MAX' | string;
  meetsTarget: boolean;
  formula: string;
}

interface Relatorio {
  scopeName?: string | null;
  metrics: Metrica[];
  operatingHours: number;
  failures: number;
  repairs: number;
  downtimeHours: number;
  plannedOrders: number;
  executedOrders: number;
}

/** Cada técnica com o seu símbolo, como no manual. */
const SIMBOLO: Record<string, typeof IconWaveSine> = {
  VIBRATION: IconWaveSine,
  THERMOGRAPHY: IconTemperature,
  OIL_ANALYSIS: IconDroplet,
  ALIGNMENT: IconAdjustments,
  INSULATION: IconBolt,
};

const SIMBOLO_KPI: Record<string, typeof IconGauge> = {
  availability: IconGauge,
  mtbf: IconClockHour4,
  mttr: IconTool,
  plan_compliance: IconClipboardList,
};

const ESTADO: Record<string, { texto: string; cor: string }> = {
  OVERDUE: { texto: 'Vencida', cor: 'red' },
  DUE_SOON: { texto: 'A chegar', cor: 'yellow' },
};

const RESULTADO: Record<string, { texto: string; cor: string }> = {
  NORMAL: { texto: 'Normal', cor: 'green' },
  ATTENTION: { texto: 'Atenção', cor: 'orange' },
  CRITICAL: { texto: 'Crítico', cor: 'red' },
};

/** A coluna da esquerda do manual: o símbolo e a periodicidade em maiúsculas. */
function Periodicidade({ tecnica, texto }: { tecnica: string; texto: string }) {
  const Icone = SIMBOLO[tecnica] ?? IconWaveSine;
  return (
    <Group gap={8} wrap="nowrap" style={{ minWidth: 150 }}>
      <Icone size={26} stroke={1.6} style={{ color: PRETO, flexShrink: 0 }} />
      <Text
        fw={700}
        style={{
          fontFamily: '"Barlow Condensed", Barlow, sans-serif',
          fontSize: 15,
          letterSpacing: '0.04em',
          textTransform: 'uppercase',
        }}
      >
        {texto}
      </Text>
    </Group>
  );
}

/** Uma linha do quadro: símbolo | o que se mede | objetivo | estado. */
function LinhaFicha({ children, ultima }: { children: React.ReactNode; ultima: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 14,
        alignItems: 'flex-start',
        padding: '9px 0',
        borderBottom: ultima ? 'none' : '1px solid #d4d4d8',
        flexWrap: 'wrap',
      }}
    >
      {children}
    </div>
  );
}

/**
 * O programa de monitorização desta máquina.
 *
 * <p>Enquanto não existir, mostra-se o conjunto de referência da família —
 * vibração mensal, termografia trimestral, análise de óleo semestral — com o
 * botão que o cria. Uma empresa não devia ter de saber de cor o que se mede
 * numa retroescavadora para começar a medi-lo.
 */
export function ProgramaPreditivoFicha({ assetId }: { assetId: string }) {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['predictive', assetId],
    queryFn: () => api<Programa[]>(`/assets/${assetId}/predictive`),
  });

  const aplicar = useMutation({
    mutationFn: () => api(`/assets/${assetId}/predictive/standard`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({ message: 'Programa de monitorização criado para esta máquina.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['predictive', assetId] });
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível criar', message: e.message, color: 'red' }),
  });

  if (isLoading) return <Loader size="sm" />;
  // Do mais frequente para o mais raro, como o manual os arruma: quem lê a
  // folha começa pelo que tem de fazer já este mês.
  const lista = [...(data ?? [])].sort((a, b) => a.frequencyMonths - b.frequencyMonths);

  return (
    <div>
      <BarraFicha
        titulo="Manutenção preditiva"
        direita={lista.length > 0 ? `${lista.length} programa(s) de monitorização` : undefined}
      />
      <CaixaFicha>
        {lista.length === 0 ? (
          <Stack align="flex-start" gap="xs">
            <Group gap={6} wrap="nowrap">
              <IconInfoCircle size={16} style={{ color: '#a1a1aa' }} />
              <Text size="sm" c="dimmed">
                Esta máquina ainda não tem monitorização de condição. O conjunto de referência para a família é análise
                de vibração mensal, termografia trimestral e análise de óleo semestral.
              </Text>
            </Group>
            {can('MANAGER') && (
              <Button size="compact-sm" loading={aplicar.isPending} onClick={() => aplicar.mutate()}>
                Aplicar conjunto de referência
              </Button>
            )}
          </Stack>
        ) : (
          lista.map((p, i) => (
            <LinhaFicha key={p.id} ultima={i === lista.length - 1}>
              <Periodicidade tecnica={p.technique} texto={p.frequencyLabel} />

              <div style={{ flex: '2 1 260px', minWidth: 220 }}>
                <Text size="sm" fw={700}>
                  {p.techniqueLabel}:
                </Text>
                {p.components ? (
                  <ul style={{ margin: '2px 0 0 16px', padding: 0 }}>
                    {p.components.split(',').map((c) => (
                      <li key={c.trim()}>
                        <Text size="sm">{c.trim()}</Text>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <Text size="sm" c="dimmed">
                    Componentes por indicar.
                  </Text>
                )}
              </div>

              <div style={{ flex: '2 1 240px', minWidth: 200 }}>
                <Text size="sm">
                  <b>Objetivo:</b> {p.goal ?? 'Avaliar a condição antes de haver avaria.'}
                </Text>
              </div>

              <div style={{ flex: '1 1 170px', minWidth: 160 }}>
                <Group gap={6} mb={2}>
                  {ESTADO[p.status] ? (
                    <Badge size="xs" color={ESTADO[p.status].cor} variant="light">
                      {ESTADO[p.status].texto}
                    </Badge>
                  ) : (
                    <Badge size="xs" color="green" variant="light">
                      Em dia
                    </Badge>
                  )}
                  {p.lastResult && RESULTADO[p.lastResult] && (
                    <Badge size="xs" color={RESULTADO[p.lastResult].cor} variant="light">
                      {RESULTADO[p.lastResult].texto}
                    </Badge>
                  )}
                </Group>
                <Text size="xs" c="dimmed">
                  Última: {fmtDate(p.lastDoneAt) || '—'}
                </Text>
                <Text size="xs" c="dimmed">
                  Próxima: {fmtDate(p.nextDueAt) || '—'}
                </Text>
              </div>
            </LinhaFicha>
          ))
        )}
      </CaixaFicha>
    </div>
  );
}

/** O valor medido, com a cor do que cumpre e do que não cumpre a meta. */
function Valor({ m }: { m: Metrica }) {
  if (m.value == null) {
    return (
      <Text size="sm" c="dimmed">
        por apurar
      </Text>
    );
  }
  return (
    <Group gap={6} wrap="nowrap">
      <Text fw={700} size="lg" style={{ fontFamily: '"Barlow Condensed", Barlow, sans-serif' }}>
        {fmtNumber(m.value)}
        {m.unit === '%' ? ' %' : ` ${m.unit}`}
      </Text>
      <Badge size="xs" variant="light" color={m.meetsTarget ? 'green' : 'red'}>
        {m.meetsTarget ? 'cumpre' : 'abaixo da meta'}
      </Badge>
    </Group>
  );
}

/**
 * Os quatro indicadores, com a meta, a fórmula e o número real.
 *
 * <p>A fórmula fica à vista de propósito: um indicador que ninguém sabe como
 * é calculado não se discute numa reunião — acredita-se ou não, e é aí que os
 * relatórios perdem valor. Aqui vê-se de onde sai cada número.
 */
export function IndicadoresFicha({ assetId, meses = 12 }: { assetId?: string; meses?: number }) {
  const desde = new Date();
  desde.setMonth(desde.getMonth() - meses);
  const de = desde.toISOString();

  const { data, isLoading } = useQuery({
    queryKey: ['kpis', assetId ?? 'org', meses],
    queryFn: () => api<Relatorio>(`/kpis?from=${encodeURIComponent(de)}${assetId ? `&assetId=${assetId}` : ''}`),
  });

  if (isLoading) return <Loader size="sm" />;
  const metricas = data?.metrics ?? [];

  return (
    <div>
      <BarraFicha
        titulo="Indicadores de desempenho"
        direita={`Últimos ${meses} meses${data?.scopeName ? ` · ${data.scopeName}` : ''}`}
      />
      <CaixaFicha>
        {metricas.map((m, i) => {
          const Icone = SIMBOLO_KPI[m.key] ?? IconGauge;
          return (
            <LinhaFicha key={m.key} ultima={i === metricas.length - 1}>
              <Group gap={8} wrap="nowrap" style={{ minWidth: 40 }}>
                <Icone size={26} stroke={1.6} style={{ color: PRETO, flexShrink: 0 }} />
              </Group>

              <div style={{ flex: '2 1 280px', minWidth: 240 }}>
                <Text
                  fw={700}
                  style={{
                    fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                    fontSize: 16,
                    letterSpacing: '0.03em',
                    textTransform: 'uppercase',
                  }}
                >
                  {m.name}
                </Text>
                <Text size="sm">
                  <b>Meta:</b> {m.targetDirection === 'MAX' ? '≤' : '≥'} {fmtNumber(m.target)}
                  {m.unit === '%' ? ' %' : ` ${m.unit}`}
                </Text>
                <Text size="sm" c="dimmed">
                  <b>Fórmula:</b> {m.formula}
                </Text>
              </div>

              <div style={{ flex: '1 1 180px', minWidth: 170 }}>
                <Valor m={m} />
              </div>
            </LinhaFicha>
          );
        })}

        {data && (
          <Text size="xs" c="dimmed" mt={8}>
            Calculado com {fmtNumber(data.operatingHours)} h de operação, {data.failures} avaria(s), {data.repairs}{' '}
            reparação(ões), {fmtNumber(data.downtimeHours)} h de paragem e {data.executedOrders} de {data.plannedOrders}{' '}
            ordens preventivas concluídas. Onde não há dados suficientes, o indicador fica «por apurar» — não se inventa
            um número.
          </Text>
        )}
      </CaixaFicha>
    </div>
  );
}
