import {
  Alert,
  Badge,
  Button,
  Divider,
  Group,
  Loader,
  Modal,
  NumberInput,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Select,
  Tabs,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconBulb,
  IconCheck,
  IconInfoCircle,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconPrinter,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, apiBlob, checkUploadSize } from '../api/client';
import { Painel } from '../components/erp';
import {
  AssinaturasPainel,
  CodigosPainel,
  FluidosPainel,
  MedicoesPainel,
  type FaultCode,
  type Fluid,
  type Measurement,
  type Signature,
} from './workorders/ChaoOficina';
import {
  SegurancaEnsaioPainel,
  type ChaoOficinaCampos,
} from './workorders/SegurancaEnsaio';
import { useAuth } from '../auth/AuthContext';
import { Kpi } from '../components/Kpi';
import { fmtDateTime, fmtNumber } from '../lib/format';

interface Task {
  id: string;
  title: string;
  systemName?: string | null;
  done: boolean;
}
interface Labor {
  id: string;
  technicianLabel?: string | null;
  hours: number;
  hourlyRate?: number | null;
}
interface Part {
  id: string;
  partName: string;
  quantity: number;
  unitCost?: number | null;
}
interface ExternalService {
  id: string;
  supplier: string;
  description: string;
  invoiceNumber?: string | null;
  cost: number;
  currency: string;
  warrantyMonths?: number | null;
  warrantyUntil?: string | null;
  underWarrantyNow: boolean;
}
interface Quote {
  id: string;
  supplierId?: string | null;
  supplierLabel: string;
  quoteNumber?: string | null;
  quotedAt?: string | null;
  validUntil?: string | null;
  expired: boolean;
  partsAmount: number;
  laborAmount: number;
  otherAmount: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  selected: boolean;
  notes?: string | null;
}

interface StatusChange {
  fromStatus?: string | null;
  fromLabel?: string | null;
  toStatus: string;
  toLabel: string;
  changedByLabel?: string | null;
  note?: string | null;
  minutesInPrevious?: number | null;
  changedAt: string;
}

interface Attachment {
  id: string;
  url: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  kind: string;
  kindLabel: string;
  caption?: string | null;
  uploadedAt: string;
}

interface Insight {
  code: string;
  severity: string;
  title: string;
  detail: string;
}

interface WorkOrder extends ChaoOficinaCampos {
  measurements?: Measurement[];
  fluids?: Fluid[];
  faultCodes?: FaultCode[];
  signatures?: Signature[];
  id: string;
  number: string;
  assetId: string;
  assetTag: string;
  assetName: string;
  type: string;
  status: string;
  priority: string;
  title: string;
  description?: string | null;
  resolution?: string | null;
  assignedTo?: string | null;
  openedAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  verifiedAt?: string | null;
  verifiedByName?: string | null;
  currency: string;

  dueAt?: string | null;
  slaMet?: boolean | null;
  estimatedHours?: number | null;
  estimatedCost?: number | null;
  totalLaborHours?: number | null;
  totalLaborCost?: number | null;
  totalPartsCost?: number | null;
  totalExternalCost?: number | null;
  totalCost?: number | null;
  costOverrunPercent?: number | null;
  downtimeHours?: number | null;
  downtimeCost?: number | null;
  underWarranty: boolean;
  warrantyReference?: string | null;
  warrantyRecovered?: number | null;
  rootCause?: string | null;
  correctiveAction?: string | null;
  cancellationReason?: string | null;
  closingMeterValue?: number | null;
  systemCode?: string | null;
  branchName?: string | null;
  driverName?: string | null;
  requiresShutdown: boolean;
  safetyNotes?: string | null;

  tasks: Task[];
  labor: Labor[];
  parts: Part[];
  externalServices: ExternalService[];
  insights: Insight[];
  /** Cronómetros a correr: quem está nesta ordem agora e há quantos minutos. */
  timers?: { userId: string; userName: string; startedAt: string; minutes: number }[];

  statusLabel: string;
  typeLabel: string;
  execution: string;
  supplierId?: string | null;
  supplierName?: string | null;
  symptom?: string | null;
  diagnosis?: string | null;
  probableCause?: string | null;
  recommendedAction?: string | null;
  diagnosedByLabel?: string | null;
  diagnosedAt?: string | null;
  approvedAmount?: number | null;
  approvalNote?: string | null;
  approvedByName?: string | null;
  approvedAt?: string | null;
  rejectionReason?: string | null;
  rejectedAt?: string | null;
  closedAt?: string | null;
  orderYear?: number | null;
  quotes: Quote[];
  statusHistory: StatusChange[];
  /** Estados para onde a ordem pode seguir. Vem do servidor: duas copias da
   *  maquina de estados acabam por discordar, e a errada seria esta. */
  nextStatuses: string[];
}

/**
 * Cor por estado. O rotulo vem do servidor (`statusLabel`): manter uma segunda
 * lista de nomes aqui garantia que, mais cedo ou mais tarde, o ecra chamava
 * uma coisa ao que o servidor chamava outra.
 */
const STATUS_COLOUR: Record<string, string> = {
  OPEN: 'blue',
  PLANNED: 'cyan',
  DIAGNOSIS: 'indigo',
  QUOTING: 'violet',
  AWAITING_APPROVAL: 'orange',
  APPROVED: 'lime',
  IN_PROGRESS: 'yellow',
  AWAITING_PARTS: 'orange',
  TESTING: 'cyan',
  DONE: 'green',
  VERIFIED: 'teal',
  CLOSED: 'gray',
  REJECTED: 'red',
  CANCELLED: 'gray',
};

const PRIORITY: Record<string, { label: string; color: string }> = {
  LOW: { label: 'Baixa', color: 'gray' },
  NORMAL: { label: 'Normal', color: 'blue' },
  HIGH: { label: 'Alta', color: 'orange' },
  URGENT: { label: 'Urgente', color: 'red' },
};

/**
 * A ficha de uma ordem de manutenção.
 *
 * <p>É o sítio onde o custo <b>real</b> aparece: mão de obra valorizada, peças e
 * oficina externa somadas, mais as horas de paragem — que não estão em fatura
 * nenhuma e costumam ser a parte cara. Sem isto, decidia-se reparar ou
 * substituir um ativo com uma fração do número verdadeiro.
 */
export function WorkOrderDetailPage() {
  const { id } = useParams();
  const { can, has, user } = useAuth();
  const [complete, setComplete] = useState(false);
  const [cancel, setCancel] = useState(false);
  const [external, setExternal] = useState(false);
  const [quote, setQuote] = useState(false);
  const [diagnosis, setDiagnosis] = useState(false);
  const [approve, setApprove] = useState(false);
  const [reject, setReject] = useState(false);

  const { data: w, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['work-order', id],
    queryFn: () => api<WorkOrder>(`/work-orders/${id}`),
  });

  const queryClient = useQueryClient();
  const selectQuote = useMutation({
    mutationFn: (quoteId: string) =>
      api(`/work-orders/${id}/quotes/${quoteId}/select`, {
        method: 'POST',
        body: {},
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível escolher', message: e.message, color: 'red' }),
  });

  const act = useMutation({
    mutationFn: (action: string) =>
      api(`/work-orders/${id}/${action}`, { method: 'POST', body: {} }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  if (isError) {
    return (
      <Alert color="red" variant="light">
        Não foi possível carregar a ordem. {(error as Error)?.message}
        <Group mt="sm">
          <Button size="xs" variant="default" onClick={() => refetch()}>
            Tentar outra vez
          </Button>
        </Group>
      </Alert>
    );
  }
  if (isLoading || !w) {
    return (
      <Group justify="center" py="xl">
        <Loader />
      </Group>
    );
  }

  const podeGerir = can('MANAGER');
  // O servidor já não manda valores a quem não os pode ver; o separador
  // esconde-se para não mostrar uma página de traços.
  const podeVerCustos = has('COSTS_VIEW');

  // Uma ordem fechada ou anulada é registo histórico: mexer-lhe apagaria a
  // prova de como as coisas estavam.
  const editavel = podeGerir && (w?.nextStatuses?.length ?? 0) > 0;

  const medicoesAAgir = (w?.measurements ?? []).filter((x) => x.needsAction).length;
  const emCurso = w.status === 'IN_PROGRESS';
  const porIniciar = w.status === 'OPEN' || w.status === 'PLANNED';
  const meuCronometro = (w.timers ?? []).find((t) => t.userId === user?.id);
  const podeCronometrar = has('WORKORDERS_MANAGE') && (w.nextStatuses?.length ?? 0) > 0 && w.status !== 'DONE';

  return (
    <Stack gap="sm">
      <Group gap="xs">
        <Button
          component={Link}
          to="/ordens"
          variant="subtle"
          size="xs"
          leftSection={<IconArrowLeft size={14} />}
        >
          Ordens de serviço
        </Button>
      </Group>

      {/* Barra de identificação: o número da ordem numa chapa preta, como o
          cabeçalho de um documento de oficina. */}
      <Group
        justify="space-between"
        align="flex-start"
        wrap="nowrap"
        style={{
          background: 'var(--erp-grafite)',
          borderBottom: '3px solid var(--erp-dourado)',
          padding: '10px 14px',
        }}
      >
        <div>
          <Group gap="sm">
            <Text
              fw={700}
              c="#fff"
              style={{
                fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                fontSize: 26,
                letterSpacing: '0.03em',
              }}
            >
              {w.number}
            </Text>
            <Badge variant="light" color={STATUS_COLOUR[w.status] ?? 'gray'}>
              {w.statusLabel}
            </Badge>
            <Badge variant="light" color={PRIORITY[w.priority]?.color}>
              {PRIORITY[w.priority]?.label ?? w.priority}
            </Badge>
            {w.underWarranty && (
              <Badge variant="light" color="teal">
                Em garantia
              </Badge>
            )}
          </Group>
          <Text size="lg" fw={600} mt={4} c="#fff">
            {w.title}
          </Text>
          <Text size="sm" c="#a1a1aa">
            <Text
              component={Link}
              to={`/ativos/${w.assetId}`}
              inherit
              c="var(--erp-dourado)"
            >
              {w.assetTag}
            </Text>{' '}
            — {w.assetName}
            {w.systemCode ? ` · sistema ${w.systemCode}` : ''}
            {w.branchName ? ` · ${w.branchName}` : ''}
          </Text>
        </div>

        <Group gap="xs" wrap="nowrap">
          {/* Abre numa aba nova: o token vai no cabecalho, por isso passa pelo
              fetch e nao por um <a href>, que sairia sem autenticacao. */}
          <Button
            size="sm"
            variant="default"
            leftSection={<IconPrinter size={15} />}
            onClick={() => openPdf(id!)}
          >
            Imprimir
          </Button>
          {podeGerir && (
            <>
            {porIniciar && (
              <Button size="sm" onClick={() => act.mutate('start')} loading={act.isPending}>
                Iniciar
              </Button>
            )}
            {emCurso && (
              <Button size="sm" onClick={() => setComplete(true)}>
                Concluir
              </Button>
            )}
            {podeCronometrar && (
              meuCronometro ? (
                <Button size="sm" variant="light" color="orange" leftSection={<IconPlayerPause size={15} />} onClick={() => act.mutate('timer/stop')} loading={act.isPending}>
                  Parar o meu tempo ({meuCronometro.minutes} min)
                </Button>
              ) : (
                <Button size="sm" variant="light" leftSection={<IconPlayerPlay size={15} />} onClick={() => act.mutate('timer/start')} loading={act.isPending}>
                  Iniciar o meu tempo
                </Button>
              )
            )}
            {w.status === 'DONE' && (
              <Button
                size="sm"
                leftSection={<IconCheck size={15} />}
                onClick={() => act.mutate('verify')}
                loading={act.isPending}
              >
                Verificar
              </Button>
            )}
            {w.nextStatuses.includes('AWAITING_APPROVAL') && (
              <Button size="sm" variant="default" onClick={() => act.mutate('request-approval')}>
                Pedir aprovação
              </Button>
            )}
            {w.status === 'AWAITING_APPROVAL' && can('OWNER') && (
              <>
                <Button size="sm" onClick={() => setApprove(true)}>
                  Aprovar
                </Button>
                <Button size="sm" variant="default" color="red" onClick={() => setReject(true)}>
                  Rejeitar
                </Button>
              </>
            )}
            {w.nextStatuses.includes('CLOSED') && (
              <Button size="sm" variant="default" onClick={() => act.mutate('close')}>
                Fechar
              </Button>
            )}
            {w.nextStatuses.includes('CANCELLED') && (
              <Button size="sm" variant="default" onClick={() => setCancel(true)}>
                Anular
              </Button>
            )}
            </>
          )}
        </Group>
      </Group>

      {/* Os avisos vêm antes dos números: é o que alguém precisa de ver primeiro. */}
      {w.insights.length > 0 && (
        <Stack gap="xs">
          {w.insights.map((i) => (
            <Alert
              key={i.code}
              variant="light"
              color={i.severity === 'WARNING' ? 'orange' : 'blue'}
              icon={i.severity === 'WARNING' ? <IconAlertTriangle size={18} /> : <IconBulb size={18} />}
              title={i.title}
            >
              {i.detail}
            </Alert>
          ))}
        </Stack>
      )}

      <Group gap="sm" wrap="wrap">
        <Kpi
          label="Custo total"
          value={fmtNumber(w.totalCost ?? 0, 0)}
          unit={w.currency}
          tone="brand"
          hint="Mão de obra valorizada + peças + oficina externa, menos o que a garantia devolveu."
          footnote={
            w.estimatedCost != null
              ? `orçamentado ${fmtNumber(w.estimatedCost, 0)}`
              : 'sem orçamento definido'
          }
        />
        <Kpi
          label="Desvio ao orçamento"
          value={w.costOverrunPercent != null ? `${w.costOverrunPercent > 0 ? '+' : ''}${fmtNumber(w.costOverrunPercent, 0)}` : '—'}
          unit={w.costOverrunPercent != null ? '%' : undefined}
          tone={
            w.costOverrunPercent != null && w.costOverrunPercent > 25 ? 'warning' : 'neutral'
          }
          hint="Ausente quando não houve orçamento — zero sugeriria que se acertou em cheio."
        />
        <Kpi
          label="Paragem"
          value={w.downtimeHours != null ? fmtNumber(w.downtimeHours, 1) : '—'}
          unit={w.downtimeHours != null ? 'h' : undefined}
          tone={w.downtimeCost != null ? 'warning' : 'neutral'}
          footnote={
            w.downtimeCost != null
              ? `${fmtNumber(w.downtimeCost, 0)} ${w.currency} que não estão em fatura nenhuma`
              : 'sem custo/hora definido no ativo'
          }
        />
        <Kpi
          label="Prazo"
          value={w.slaMet == null ? '—' : w.slaMet ? 'Cumprido' : 'Falhado'}
          tone={w.slaMet == null ? 'neutral' : w.slaMet ? 'good' : 'critical'}
          footnote={w.dueAt ? `até ${fmtDateTime(w.dueAt)}` : 'sem prazo definido'}
        />
        <Kpi
          label="Mão de obra"
          value={fmtNumber(w.totalLaborHours ?? 0, 1)}
          unit="h"
          footnote={
            w.estimatedHours != null ? `estimadas ${fmtNumber(w.estimatedHours, 1)} h` : undefined
          }
        />
      </Group>

      {(w.timers ?? []).length > 0 && (
        <Alert color="blue" variant="light" icon={<IconPlayerPlay size={18} />}>
          A trabalhar agora:{' '}
          {(w.timers ?? []).map((t) => `${t.userName} (${t.minutes} min)`).join(', ')}. As horas ficam na mão de obra
          quando cada um parar o seu tempo.
        </Alert>
      )}

      {w.requiresShutdown && w.safetyNotes && (
        <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />} title="Segurança">
          Exige paragem do ativo. {w.safetyNotes}
        </Alert>
      )}

      {/* Separadores como num sistema de gestao: densos, sem espaco desperdicado,
          cada um com o que se precisa de ver junto. */}
      <Tabs defaultValue="geral" keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="geral">Geral</Tabs.Tab>
          <Tabs.Tab value="diagnostico">
            Diagnóstico{w.diagnosis ? '' : ' *'}
          </Tabs.Tab>
          {podeVerCustos && <Tabs.Tab value="custos">Custos</Tabs.Tab>}
          <Tabs.Tab value="orcamentos">
            Orçamentos {w.quotes.length > 0 ? `(${w.quotes.length})` : ''}
          </Tabs.Tab>
          <Tabs.Tab value="medicoes">
            Medições{medicoesAAgir > 0 ? ` (${medicoesAAgir} ⚠)` : ''}
          </Tabs.Tab>
          <Tabs.Tab value="fluidos">Fluidos e códigos</Tabs.Tab>
          <Tabs.Tab value="seguranca">Segurança e ensaio</Tabs.Tab>
          <Tabs.Tab value="documentos">Documentos e fotos</Tabs.Tab>
          <Tabs.Tab value="historico">Histórico</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="geral" pt="md">
          <Stack gap="md">
            <Painel titulo="Tarefas">
              <Group justify="space-between" mb="xs">
                <Text size="xs" c="dimmed">
                  {w.typeLabel} · {w.execution === 'EXTERNAL' ? 'oficina externa' : 'equipa interna'}
                  {w.supplierName ? ` · ${w.supplierName}` : ''}
                </Text>
              </Group>
              {w.tasks.length === 0 ? (
                <Text size="sm" c="dimmed">
                  Sem tarefas nesta ordem.
                </Text>
              ) : (
                <Stack gap={4}>
                  {w.tasks.map((t) => (
                    <Group key={t.id} gap="xs">
                      {t.done ? (
                        <IconCheck size={15} style={{ color: '#1a7f4b' }} />
                      ) : (
                        <IconInfoCircle size={15} style={{ color: '#a1a1aa' }} />
                      )}
                      <Text
                        size="sm"
                        td={t.done ? 'line-through' : undefined}
                        c={t.done ? 'dimmed' : undefined}
                      >
                        {t.title}
                      </Text>
                      {t.systemName && (
                        <Badge variant="light" color="gray" size="sm">
                          {t.systemName}
                        </Badge>
                      )}
                    </Group>
                  ))}
                </Stack>
              )}
              <Divider my="md" />
              <Group align="flex-start" gap="xl">
                <Timeline w={w} />
              </Group>
            </Painel>

            {(w.rootCause || w.correctiveAction || w.resolution) && (
              <Painel titulo="O que se apurou">
                <Stack gap="sm">
                  {w.resolution && <Labelled label="Resolução" value={w.resolution} />}
                  {w.rootCause && <Labelled label="Causa raiz" value={w.rootCause} />}
                  {w.correctiveAction && (
                    <Labelled label="Ação corretiva" value={w.correctiveAction} />
                  )}
                </Stack>
              </Painel>
            )}

            {w.cancellationReason && (
              <Alert color="gray" variant="light" title="Ordem anulada">
                {w.cancellationReason}
              </Alert>
            )}
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="diagnostico" pt="md">
          <Painel titulo="Diagnóstico">
            <Group justify="space-between" mb="md">
              {podeGerir && w.nextStatuses.length > 0 && (
                <Button size="xs" variant="default" onClick={() => setDiagnosis(true)}>
                  {w.diagnosis ? 'Alterar' : 'Registar'}
                </Button>
              )}
            </Group>
            {!w.symptom && !w.diagnosis ? (
              <Text size="sm" c="dimmed">
                Ainda sem diagnóstico. Sintoma, diagnóstico, causa e solução ficam em campos
                separados: é isso que permite perceber, meses depois, que o problema volta
                sempre pela mesma razão.
              </Text>
            ) : (
              <Stack gap="md">
                {w.symptom && <Labelled label="Sintoma relatado" value={w.symptom} />}
                {w.diagnosis && <Labelled label="Diagnóstico técnico" value={w.diagnosis} />}
                {w.probableCause && <Labelled label="Causa provável" value={w.probableCause} />}
                {w.recommendedAction && (
                  <Labelled label="Solução recomendada" value={w.recommendedAction} />
                )}
                {w.diagnosedByLabel && (
                  <Text size="xs" c="dimmed">
                    Por {w.diagnosedByLabel} em {fmtDateTime(w.diagnosedAt)}
                  </Text>
                )}
              </Stack>
            )}
          </Painel>
        </Tabs.Panel>

        <Tabs.Panel value="custos" pt="md">
          <Group align="flex-start" gap="md" wrap="wrap">
            <Painel titulo="Custo, parcela a parcela">
              <Table>
                <Table.Tbody>
                  <CostRow label="Mão de obra" value={w.totalLaborCost} currency={w.currency} />
                  <CostRow label="Peças" value={w.totalPartsCost} currency={w.currency} />
                  <CostRow
                    label="Oficina externa"
                    value={w.totalExternalCost}
                    currency={w.currency}
                  />
                  {w.warrantyRecovered != null && w.warrantyRecovered > 0 && (
                    <CostRow
                      label="Recuperado pela garantia"
                      value={-w.warrantyRecovered}
                      currency={w.currency}
                    />
                  )}
                  <Table.Tr>
                    <Table.Td>
                      <Text size="sm" fw={700}>
                        Total
                      </Text>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text
                        size="sm"
                        fw={700}
                        style={{ fontVariantNumeric: 'tabular-nums' }}
                      >
                        {w.totalCost != null
                          ? `${fmtNumber(w.totalCost, 0)} ${w.currency}`
                          : 'sem permissão'}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                </Table.Tbody>
              </Table>
              {w.approvedAmount != null && (
                <Text size="xs" c="dimmed" mt="sm">
                  Aprovado {fmtNumber(w.approvedAmount, 0)} {w.currency}
                  {w.approvedByName ? ` por ${w.approvedByName}` : ''}
                  {w.approvedAt ? ` em ${fmtDateTime(w.approvedAt)}` : ''}.
                  {w.approvalNote ? ` ${w.approvalNote}` : ''}
                </Text>
              )}
            </Painel>

            <Painel titulo="Oficina externa">
              <Group justify="space-between" mb="xs">
                {podeGerir && w.nextStatuses.length > 0 && (
                  <Button
                    size="xs"
                    variant="default"
                    leftSection={<IconPlus size={13} />}
                    onClick={() => setExternal(true)}
                  >
                    Registar
                  </Button>
                )}
              </Group>
              {w.externalServices.length === 0 ? (
                <Text size="sm" c="dimmed" py="sm">
                  Nenhum serviço externo. Em Angola grande parte da manutenção pesada vai
                  para fora — sem estas linhas o custo da ordem fica abaixo do real.
                </Text>
              ) : (
                <Table>
                  <Table.Tbody>
                    {w.externalServices.map((sv) => (
                      <Table.Tr key={sv.id}>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {sv.supplier}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {sv.description}
                          </Text>
                          <Group gap={6} mt={2}>
                            {sv.invoiceNumber && (
                              <Text size="xs" c="dimmed">
                                {sv.invoiceNumber}
                              </Text>
                            )}
                            {sv.warrantyUntil && (
                              <Tooltip
                                label={`Garantia até ${fmtDateTime(sv.warrantyUntil)}`}
                                withArrow
                              >
                                <Badge
                                  variant="light"
                                  color={sv.underWarrantyNow ? 'teal' : 'gray'}
                                  size="sm"
                                >
                                  {sv.underWarrantyNow ? 'em garantia' : 'garantia expirada'}
                                </Badge>
                              </Tooltip>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td ta="right">
                          <Text
                            size="sm"
                            fw={600}
                            style={{ fontVariantNumeric: 'tabular-nums' }}
                          >
                            {fmtNumber(sv.cost, 0)} {sv.currency}
                          </Text>
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              )}
            </Painel>
          </Group>
        </Tabs.Panel>

        <Tabs.Panel value="orcamentos" pt="md">
          <Painel titulo="Orçamentos">
            <Group justify="space-between" mb="xs">
              {podeGerir && w.nextStatuses.length > 0 && (
                <Button
                  size="xs"
                  variant="default"
                  leftSection={<IconPlus size={13} />}
                  onClick={() => setQuote(true)}
                >
                  Registar orçamento
                </Button>
              )}
            </Group>
            {w.quotes.length === 0 ? (
              <Text size="sm" c="dimmed" py="sm">
                Sem orçamentos. Registar propostas de oficinas diferentes é o que permite
                comparar antes de decidir.
              </Text>
            ) : (
              <Table>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Fornecedor</Table.Th>
                    <Table.Th style={{ width: 110 }}>Peças</Table.Th>
                    <Table.Th style={{ width: 110 }}>Mão de obra</Table.Th>
                    <Table.Th style={{ width: 110 }}>Desconto</Table.Th>
                    <Table.Th style={{ width: 130 }}>Total</Table.Th>
                    <Table.Th style={{ width: 110 }} />
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {w.quotes.map((q) => (
                    <Table.Tr key={q.id}>
                      <Table.Td>
                        <Group gap={6}>
                          <Text size="sm" fw={600}>
                            {q.supplierLabel}
                          </Text>
                          {q.selected && (
                            <Badge variant="light" color="gold" size="sm">
                              escolhido
                            </Badge>
                          )}
                          {q.expired && (
                            <Badge variant="light" color="red" size="sm">
                              caducado
                            </Badge>
                          )}
                        </Group>
                        {q.quoteNumber && (
                          <Text size="xs" c="dimmed">
                            {q.quoteNumber}
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{fmtNumber(q.partsAmount, 0)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{fmtNumber(q.laborAmount, 0)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm" c={q.discountAmount ? undefined : 'dimmed'}>
                          {q.discountAmount ? `-${fmtNumber(q.discountAmount, 0)}` : '—'}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text
                          size="sm"
                          fw={700}
                          style={{ fontVariantNumeric: 'tabular-nums' }}
                        >
                          {fmtNumber(q.totalAmount, 0)} {q.currency}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        {podeGerir && !q.selected && (
                          <Button
                            size="xs"
                            variant="default"
                            onClick={() => selectQuote.mutate(q.id)}
                          >
                            Escolher
                          </Button>
                        )}
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}
          </Painel>
        </Tabs.Panel>

        <Tabs.Panel value="medicoes" pt="md">
          <MedicoesPainel id={id!} medicoes={w.measurements ?? []} editavel={editavel} />
        </Tabs.Panel>

        <Tabs.Panel value="fluidos" pt="md">
          <Stack gap="sm">
            <FluidosPainel
              id={id!}
              fluidos={w.fluids ?? []}
              editavel={editavel}
              podeVerCustos={w.totalCost != null || (w.fluids ?? []).some((f) => f.totalCost != null)}
            />
            <CodigosPainel id={id!} codigos={w.faultCodes ?? []} editavel={editavel} />
            <AssinaturasPainel id={id!} assinaturas={w.signatures ?? []} editavel={editavel} />
          </Stack>
        </Tabs.Panel>

        <Tabs.Panel value="seguranca" pt="md">
          <SegurancaEnsaioPainel id={id!} w={w} editavel={editavel} />
        </Tabs.Panel>

        <Tabs.Panel value="documentos" pt="md">
          <AttachmentsPanel id={id!} editable={w.nextStatuses.length > 0} />
        </Tabs.Panel>

        <Tabs.Panel value="historico" pt="md">
          <Painel titulo="Percurso da ordem">
            {w.statusHistory.length === 0 ? (
              <Text size="sm" c="dimmed">
                Ainda sem mudanças de estado registadas.
              </Text>
            ) : (
              <Table>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th style={{ width: 170 }}>Quando</Table.Th>
                    <Table.Th>Passou a</Table.Th>
                    <Table.Th style={{ width: 160 }}>Quem</Table.Th>
                    <Table.Th style={{ width: 150 }}>Esteve antes</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {w.statusHistory.map((h, idx) => (
                    <Table.Tr key={`${h.changedAt}-${idx}`}>
                      <Table.Td>
                        <Text size="sm">{fmtDateTime(h.changedAt)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Badge
                          variant="light"
                          color={STATUS_COLOUR[h.toStatus] ?? 'gray'}
                          size="sm"
                        >
                          {h.toLabel}
                        </Badge>
                        {h.note && (
                          <Text size="xs" c="dimmed" mt={2}>
                            {h.note}
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{h.changedByLabel ?? '—'}</Text>
                      </Table.Td>
                      <Table.Td>
                        {/* E isto que explica que uma reparacao de duas horas
                            levou tres semanas porque a peca nao chegava. */}
                        <Text size="sm" c="dimmed">
                          {h.minutesInPrevious != null
                            ? `${h.fromLabel ?? ''} ${formatMinutes(h.minutesInPrevious)}`.trim()
                            : '—'}
                        </Text>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}
          </Painel>
        </Tabs.Panel>
      </Tabs>

      <CompleteModal opened={complete} onClose={() => setComplete(false)} id={id!} type={w.type} />
      <CancelModal opened={cancel} onClose={() => setCancel(false)} id={id!} />
      <ExternalModal opened={external} onClose={() => setExternal(false)} id={id!} />
      <DiagnosisModal opened={diagnosis} onClose={() => setDiagnosis(false)} id={id!} w={w} />
      <QuoteModal opened={quote} onClose={() => setQuote(false)} id={id!} />
      <ApprovalModal
        opened={approve}
        onClose={() => setApprove(false)}
        id={id!}
        action="approve"
        suggested={w.quotes.find((q) => q.selected)?.totalAmount ?? w.estimatedCost ?? null}
        currency={w.currency}
      />
      <ApprovalModal
        opened={reject}
        onClose={() => setReject(false)}
        id={id!}
        action="reject"
        suggested={null}
        currency={w.currency}
      />
    </Stack>
  );
}

function CostRow({
  label,
  value,
  currency,
}: {
  label: string;
  value?: number | null;
  currency: string;
}) {
  return (
    <Table.Tr>
      <Table.Td>
        <Text size="sm">{label}</Text>
      </Table.Td>
      <Table.Td ta="right">
        <Text size="sm" c={value ? undefined : 'dimmed'} style={{ fontVariantNumeric: 'tabular-nums' }}>
          {value ? `${fmtNumber(value, 0)} ${currency}` : '—'}
        </Text>
      </Table.Td>
    </Table.Tr>
  );
}

function Labelled({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <Text size="10px" c="dimmed" tt="uppercase" fw={700} style={{ letterSpacing: '0.05em' }}>
        {label}
      </Text>
      <Text size="sm" lh={1.5}>
        {value}
      </Text>
    </div>
  );
}

function Timeline({ w }: { w: WorkOrder }) {
  const marcos: [string, string | null | undefined][] = [
    ['Aberta', w.openedAt],
    ['Iniciada', w.startedAt],
    ['Concluída', w.completedAt],
    ['Verificada', w.verifiedAt],
  ];
  return (
    <>
      {marcos.map(([label, at]) => (
        <div key={label}>
          <Text size="10px" c="dimmed" tt="uppercase" fw={700} style={{ letterSpacing: '0.05em' }}>
            {label}
          </Text>
          <Text size="sm" c={at ? undefined : 'dimmed'}>
            {at ? fmtDateTime(at) : '—'}
          </Text>
          {label === 'Verificada' && w.verifiedByName && (
            <Text size="xs" c="dimmed">
              por {w.verifiedByName}
            </Text>
          )}
        </div>
      ))}
    </>
  );
}

function CompleteModal({
  opened,
  onClose,
  id,
  type,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
  type: string;
}) {
  const queryClient = useQueryClient();
  const [resolution, setResolution] = useState('');
  const [rootCause, setRootCause] = useState('');
  const [correctiveAction, setCorrectiveAction] = useState('');
  const [meter, setMeter] = useState<number | string>('');

  const complete = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/complete`, {
        method: 'POST',
        body: {
          resolution,
          rootCause: rootCause || null,
          correctiveAction: correctiveAction || null,
          closingMeterValue: meter === '' ? null : Number(meter),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Ordem concluída', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível concluir', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Concluir ordem" size="lg" centered>
      <Stack gap="sm">
        <Textarea
          label="O que foi feito"
          minRows={2}
          autosize
          value={resolution}
          onChange={(e) => setResolution(e.currentTarget.value)}
        />
        {type === 'CORRECTIVE' && (
          <>
            <Textarea
              label="Causa raiz"
              description="Sem causa registada, a avaria volta e ninguém saberá porquê."
              placeholder="Ex.: óleo da caixa abaixo do nível durante meses"
              minRows={2}
              autosize
              value={rootCause}
              onChange={(e) => setRootCause(e.currentTarget.value)}
            />
            <Textarea
              label="Ação corretiva"
              placeholder="Ex.: verificação de níveis passa a ser semanal na inspeção diária"
              minRows={2}
              autosize
              value={correctiveAction}
              onChange={(e) => setCorrectiveAction(e.currentTarget.value)}
            />
          </>
        )}
        <NumberInput
          label="Leitura do medidor no fecho"
          description="Diferente da de abertura — é o que mede o que o ativo andou durante a intervenção."
          value={meter}
          onChange={setMeter}
          min={0}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button loading={complete.isPending} onClick={() => complete.mutate()}>
            Concluir
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function CancelModal({
  opened,
  onClose,
  id,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
}) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const cancel = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/cancel`, {
        method: 'POST',
        body: { reason },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Ordem anulada', message: '', color: 'gray' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível anular', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Anular ordem" centered>
      <Stack gap="sm">
        <Textarea
          label="Porquê"
          description="Obrigatório. Uma ordem anulada sem razão é indistinguível de trabalho escondido."
          minRows={3}
          autosize
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Voltar
          </Button>
          <Button
            color="red"
            disabled={reason.trim().length < 5}
            loading={cancel.isPending}
            onClick={() => cancel.mutate()}
          >
            Anular
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function ExternalModal({
  opened,
  onClose,
  id,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
}) {
  const queryClient = useQueryClient();
  const [supplier, setSupplier] = useState('');
  const [description, setDescription] = useState('');
  const [invoice, setInvoice] = useState('');
  const [cost, setCost] = useState<number | string>('');
  const [warranty, setWarranty] = useState<number | string>('');

  const add = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/external-services`, {
        method: 'POST',
        body: {
          supplier,
          description,
          invoiceNumber: invoice || null,
          cost: Number(cost),
          warrantyMonths: warranty === '' ? null : Number(warranty),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Serviço registado', message: supplier, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      setSupplier('');
      setDescription('');
      setInvoice('');
      setCost('');
      setWarranty('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Serviço de oficina externa" centered>
      <Stack gap="sm">
        <TextInput
          label="Fornecedor"
          required
          value={supplier}
          onChange={(e) => setSupplier(e.currentTarget.value)}
        />
        <TextInput
          label="Serviço"
          required
          placeholder="Retificação da caixa de velocidades"
          value={description}
          onChange={(e) => setDescription(e.currentTarget.value)}
        />
        <Group grow>
          <TextInput
            label="Fatura"
            value={invoice}
            onChange={(e) => setInvoice(e.currentTarget.value)}
          />
          <NumberInput label="Custo" required value={cost} onChange={setCost} min={0} />
        </Group>
        <NumberInput
          label="Garantia (meses)"
          description="Uma avaria que volte dentro do prazo não se paga duas vezes — se alguém se lembrar de a invocar."
          value={warranty}
          onChange={setWarranty}
          min={0}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!supplier.trim() || !description.trim() || cost === ''}
            loading={add.isPending}
            onClick={() => add.mutate()}
          >
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** Minutos em algo que se lê: "3 dias", "4 h", "25 min". */
function formatMinutes(minutes: number) {
  if (minutes < 60) return `${minutes} min`;
  if (minutes < 60 * 24) return `${Math.round(minutes / 60)} h`;
  const dias = Math.round(minutes / (60 * 24));
  return `${dias} dia${dias === 1 ? '' : 's'}`;
}

/**
 * Diagnóstico em quatro campos.
 *
 * <p>Um único campo de texto livre juntava o que o condutor sente, o que o
 * técnico vê e o que se vai fazer — e depois não havia forma de responder a
 * "quantas vezes este problema foi causado pela mesma coisa?".
 */
function DiagnosisModal({
  opened,
  onClose,
  id,
  w,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
  w: WorkOrder;
}) {
  const queryClient = useQueryClient();
  const [symptom, setSymptom] = useState(w.symptom ?? '');
  const [diagnosis, setDiagnosis] = useState(w.diagnosis ?? '');
  const [cause, setCause] = useState(w.probableCause ?? '');
  const [action, setAction] = useState(w.recommendedAction ?? '');

  const save = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/diagnosis`, {
        method: 'POST',
        body: {
          symptom: symptom || null,
          diagnosis: diagnosis || null,
          probableCause: cause || null,
          recommendedAction: action || null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Diagnóstico registado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível gravar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Diagnóstico" size="lg" centered>
      <Stack gap="sm">
        <Textarea
          label="Sintoma relatado"
          description="O que quem conduz descreve."
          placeholder="Viatura vibra durante a travagem a partir dos 60 km/h"
          minRows={2}
          autosize
          value={symptom}
          onChange={(e) => setSymptom(e.currentTarget.value)}
        />
        <Textarea
          label="Diagnóstico técnico"
          description="O que o técnico observou."
          placeholder="Discos dianteiros com desgaste irregular"
          minRows={2}
          autosize
          value={diagnosis}
          onChange={(e) => setDiagnosis(e.currentTarget.value)}
        />
        <Textarea
          label="Causa provável"
          description="Porque é que aconteceu. É este campo que revela padrões."
          placeholder="Travagem prolongada em descida com carga máxima"
          minRows={2}
          autosize
          value={cause}
          onChange={(e) => setCause(e.currentTarget.value)}
        />
        <Textarea
          label="Solução recomendada"
          minRows={2}
          autosize
          value={action}
          onChange={(e) => setAction(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Gravar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function QuoteModal({
  opened,
  onClose,
  id,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
}) {
  const queryClient = useQueryClient();
  const [supplierId, setSupplierId] = useState<string | null>(null);
  const [supplierLabel, setSupplierLabel] = useState('');
  const [quoteNumber, setQuoteNumber] = useState('');
  const [parts, setParts] = useState<number | string>('');
  const [labor, setLabor] = useState<number | string>('');
  const [other, setOther] = useState<number | string>('');
  const [discount, setDiscount] = useState<number | string>('');

  const { data: suppliers } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => api<{ id: string; name: string }[]>('/suppliers'),
    enabled: opened,
  });

  const total =
    (Number(parts) || 0) + (Number(labor) || 0) + (Number(other) || 0) - (Number(discount) || 0);

  const add = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/quotes`, {
        method: 'POST',
        body: {
          supplierId,
          supplierLabel: supplierLabel || null,
          quoteNumber: quoteNumber || null,
          partsAmount: Number(parts) || 0,
          laborAmount: Number(labor) || 0,
          otherAmount: Number(other) || 0,
          discountAmount: Number(discount) || 0,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Orçamento registado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      setSupplierId(null);
      setSupplierLabel('');
      setQuoteNumber('');
      setParts('');
      setLabor('');
      setOther('');
      setDiscount('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Registar orçamento" centered>
      <Stack gap="sm">
        <Select
          label="Fornecedor registado"
          placeholder="escolher"
          clearable
          searchable
          data={(suppliers ?? []).map((f) => ({ value: f.id, label: f.name }))}
          value={supplierId}
          onChange={setSupplierId}
        />
        <TextInput
          label="Ou escrever o nome"
          description="Para quem ainda não está registado como fornecedor."
          value={supplierLabel}
          onChange={(e) => setSupplierLabel(e.currentTarget.value)}
        />
        <TextInput
          label="Nº do orçamento"
          value={quoteNumber}
          onChange={(e) => setQuoteNumber(e.currentTarget.value)}
        />
        <Group grow>
          <NumberInput label="Peças" value={parts} onChange={setParts} min={0} />
          <NumberInput label="Mão de obra" value={labor} onChange={setLabor} min={0} />
        </Group>
        <Group grow>
          <NumberInput label="Outros" value={other} onChange={setOther} min={0} />
          <NumberInput label="Desconto" value={discount} onChange={setDiscount} min={0} />
        </Group>
        {/* O total mostrado aqui é só orientação: quem o calcula é o servidor,
            para o que fica registado nunca divergir das parcelas. */}
        <Text size="sm" c="dimmed">
          Total: <b>{fmtNumber(total, 0)}</b> — confirmado pelo servidor ao gravar.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!supplierId && !supplierLabel.trim()}
            loading={add.isPending}
            onClick={() => add.mutate()}
          >
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function ApprovalModal({
  opened,
  onClose,
  id,
  action,
  suggested,
  currency,
}: {
  opened: boolean;
  onClose: () => void;
  id: string;
  action: 'approve' | 'reject';
  suggested: number | null;
  currency: string;
}) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState<number | string>(suggested ?? '');
  const [note, setNote] = useState('');
  const aprovar = action === 'approve';

  const run = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/${action}`, {
        method: 'POST',
        body: {
          amount: aprovar && amount !== '' ? Number(amount) : null,
          note: note || null,
        },
      }),
    onSuccess: () => {
      notifications.show({
        title: aprovar ? 'Despesa aprovada' : 'Orçamento rejeitado',
        message: '',
        color: aprovar ? 'green' : 'orange',
      });
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      setNote('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={aprovar ? 'Aprovar despesa' : 'Rejeitar orçamento'}
      centered
    >
      <Stack gap="sm">
        {aprovar && (
          <NumberInput
            label={`Valor aprovado (${currency})`}
            description="Gastar acima disto exige nova aprovação."
            value={amount}
            onChange={setAmount}
            min={0}
          />
        )}
        <Textarea
          label={aprovar ? 'Observação' : 'Porquê'}
          description={
            aprovar
              ? undefined
              : 'Obrigatório. Quem pediu precisa de saber o que mudar para voltar a pedir.'
          }
          minRows={3}
          autosize
          value={note}
          onChange={(e) => setNote(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            color={aprovar ? undefined : 'red'}
            disabled={!aprovar && note.trim().length < 5}
            loading={run.isPending}
            onClick={() => run.mutate()}
          >
            {aprovar ? 'Aprovar' : 'Rejeitar'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/**
 * Documentos e fotografias da ordem.
 *
 * <p>A fotografia do antes é o que resolve a discussão meses depois: sem ela,
 * "a viatura já vinha assim" e "estragaram-na na oficina" são duas afirmações
 * igualmente indemonstráveis.
 */
function AttachmentsPanel({ id, editable }: { id: string; editable: boolean }) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<string | null>('BEFORE');
  const [caption, setCaption] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['work-order', id, 'attachments'],
    queryFn: () => api<Attachment[]>(`/work-orders/${id}/attachments`),
  });

  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      if (kind) form.append('kind', kind);
      if (caption.trim()) form.append('caption', caption.trim());
      return api<Attachment>(`/work-orders/${id}/attachments`, {
        method: 'POST',
        body: form,
      });
    },
    onSuccess: () => {
      notifications.show({ title: 'Anexo adicionado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id, 'attachments'] });
      setCaption('');
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível anexar', message: e.message, color: 'red' }),
  });

  const remove = useMutation({
    mutationFn: (attachmentId: string) =>
      api(`/work-orders/${id}/attachments/${attachmentId}`, { method: 'DELETE' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['work-order', id, 'attachments'] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const rows = data ?? [];

  return (
    <Painel titulo="Documentos e fotografias">
      {editable && (
        <Group align="flex-end" gap="sm" mb="md">
          <Select
            label="Tipo"
            data={[
              { value: 'BEFORE', label: 'Antes' },
              { value: 'AFTER', label: 'Depois' },
              { value: 'INVOICE', label: 'Fatura' },
              { value: 'REPORT', label: 'Relatório' },
              { value: 'PART', label: 'Peça' },
              { value: 'OTHER', label: 'Outro' },
            ]}
            value={kind}
            onChange={setKind}
            allowDeselect={false}
            style={{ width: 150 }}
          />
          <TextInput
            label="Legenda"
            placeholder="Discos dianteiros antes da intervenção"
            value={caption}
            onChange={(e) => setCaption(e.currentTarget.value)}
            style={{ flex: 1, minWidth: 220 }}
          />
          <Button
            component="label"
            variant="default"
            leftSection={<IconPlus size={14} />}
            loading={upload.isPending}
          >
            Anexar ficheiro
            <input
              type="file"
              hidden
              accept="image/*,application/pdf"
              onChange={(e) => {
                const f = e.currentTarget.files?.[0];
                e.currentTarget.value = '';
                if (!f) return;
                // Recusar aqui poupa o envio inteiro por uma ligação lenta.
                const recusa = checkUploadSize(f);
                if (recusa) {
                  notifications.show({
                    title: 'Ficheiro não aceite',
                    message: recusa,
                    color: 'red',
                  });
                  return;
                }
                upload.mutate(f);
              }}
            />
          </Button>
        </Group>
      )}

      {rows.length === 0 ? (
        <Text size="sm" c="dimmed" py="sm">
          {isLoading
            ? 'A carregar…'
            : 'Sem anexos. A fotografia do antes é o que resolve a discussão meses depois.'}
        </Text>
      ) : (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 110 }}>Tipo</Table.Th>
              <Table.Th>Ficheiro</Table.Th>
              <Table.Th style={{ width: 160 }}>Quando</Table.Th>
              <Table.Th style={{ width: 110 }} />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((a) => (
              <Table.Tr key={a.id}>
                <Table.Td>
                  <Badge variant="light" color="gray" size="sm">
                    {a.kindLabel}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text
                    component="a"
                    href={a.url}
                    target="_blank"
                    rel="noreferrer"
                    size="sm"
                    fw={600}
                  >
                    {a.name}
                  </Text>
                  {a.caption && (
                    <Text size="xs" c="dimmed">
                      {a.caption}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {fmtDateTime(a.uploadedAt)}
                  </Text>
                </Table.Td>
                <Table.Td>
                  {editable && (
                    <Button
                      size="xs"
                      variant="subtle"
                      color="red"
                      onClick={() => remove.mutate(a.id)}
                    >
                      Remover
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Painel>
  );
}

/**
 * Abre o PDF numa aba nova.
 *
 * <p>Passa pelo `fetch` e não por um `<a href>` porque o token vai no cabeçalho:
 * uma ligação directa sairia sem autenticação e devolvia 401.
 */
async function openPdf(id: string) {
  try {
    const blob = await apiBlob(`/work-orders/${id}/print.pdf`);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    // Libertar depois de o browser ter tido tempo de abrir a aba.
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    notifications.show({
      title: 'Não foi possível abrir o PDF',
      message: (e as Error).message,
      color: 'red',
    });
  }
}
