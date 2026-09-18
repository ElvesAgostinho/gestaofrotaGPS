import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  FileInput,
  Group,
  Loader,
  Modal,
  NumberInput,
  Progress,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Tabs,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconClipboardList,
  IconClipboardPlus,
  IconHistory,
  IconMapPin,
  IconPencil,
  IconPrinter,
  IconDroplet,
  IconWheel,
  IconChecklist,
  IconFileText,
  IconPlus,
  IconGauge,
  IconInfoCircle,
  IconLock,
  IconPhoto,
  IconTool,
  IconWaveSine,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { checkUploadSize, api, openFile } from '../api/client';
import { BarraEstado, BotaoBarra, COR_ESTADO_OM, COR_PRIORIDADE_OM, Painel, Ponto, SeparadorBarra } from '../components/erp';
import { CabecalhoFicha, CamposFicha } from '../components/Ficha';
import { Grelha } from '../components/Grelha';
import { NovaOrdemForm } from './workorders/NovaOrdemForm';
import { IconeAtivo } from '../components/Equipamento';
import { FotografiasPorParte, type Foto } from './assets/FotografiasPorParte';
import { PontosDeServico } from './assets/PontosDeServico';
import { EditarAtivoForm } from './assets/EditarAtivoForm';
import { PneusTab } from './assets/PneusTab';
import { InspecoesTab } from './assets/InspecoesTab';
import { PrevisaoAvarias } from '../components/PrevisaoAvarias';
import { EstadoTarefa, LimiteManutencaoModal, PainelProximaManutencao, unidade } from './assets/LimiteManutencao';
import type { AssetView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber, statusLabel } from '../lib/format';
import { LockPanel } from './lock/LockPanel';
import { CRITICALITY } from '../theme';

export function AssetDetailPage() {
  const { id = '' } = useParams();
  const [params] = useSearchParams();
  // O painel «Hoje» abre a ficha já no separador certo (?tab=plano).
  const separadorInicial = params.get('tab') ?? 'ordens';
  const { can, has } = useAuth();
  const queryClient = useQueryClient();
  const [reading, setReading] = useState<number | ''>('');
  const [novaOrdem, setNovaOrdem] = useState(false);
  const [editar, setEditar] = useState(false);
  const navigate = useNavigate();

  const { data: asset, isLoading } = useQuery({
    queryKey: ['asset', id],
    queryFn: () => api<AssetView>(`/assets/${id}`),
    enabled: !!id,
  });

  const addReading = useMutation({
    mutationFn: (value: number) =>
      api(`/assets/${id}/meters/${asset?.meters[0]?.kind}/readings`, {
        method: 'POST',
        body: { value },
      }),
    onSuccess: (result: unknown) => {
      const flagged = (result as { reading?: { flagged?: boolean; flagReason?: string } })?.reading;
      if (flagged?.flagged) {
        // O backend aceita a leitura mas assinala-a. Esconder isso levaria a
        // decisões de manutenção tomadas sobre um número errado.
        notifications.show({
          title: 'Leitura registada com aviso',
          message: flagged.flagReason ?? 'Valor inconsistente com o histórico.',
          color: 'yellow',
          autoClose: 8000,
        });
      } else {
        notifications.show({ message: 'Leitura registada.', color: 'green' });
      }
      setReading('');
      queryClient.invalidateQueries({ queryKey: ['asset', id] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  if (isLoading || !asset) {
    return (
      <Group justify="center" py="xl">
        <Loader />
      </Group>
    );
  }

  const meter = asset.meters[0];
  const crit = asset.criticality;

  // A primeira fotografia carregada pelo cliente é a que representa a máquina.
  // É a dele, do parque dele — vale mais do que qualquer imagem de catálogo.
  const foto = asset.photos?.[0]?.url ?? null;

  return (
    <Stack gap="lg">
      <NovaOrdemForm aberto={novaOrdem} fechar={() => setNovaOrdem(false)} assetIdFixo={id} />
      <EditarAtivoForm ativo={asset} aberto={editar} fechar={() => setEditar(false)} />

      <CabecalhoFicha
        imagem={
          /* Fotografia quando existe, silhueta quando ainda não foi carregada
             nenhuma. Nunca uma imagem de catálogo — seria mostrar uma máquina
             que não é a desta empresa. */
          foto ? (
            <img
              src={foto}
              alt={`${asset.tag} — ${asset.name}`}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <IconeAtivo tipo={asset.assetTypeName} size={54} />
          )
        }
        identificador={asset.tag}
        titulo={asset.name}
        subtitulo={
          [asset.assetTypeName, asset.manufacturer, asset.model, asset.modelYear]
            .filter(Boolean)
            .join(' · ') || undefined
        }
        etiquetas={
          <>
            <Badge variant="filled" color={asset.status === 'DOWN' ? 'red' : 'gray'}>
              {statusLabel[asset.status] ?? asset.status}
            </Badge>
            {crit?.overall && (
              <Badge color={CRITICALITY[crit.overall]?.color} variant="filled">
                {CRITICALITY[crit.overall]?.label}
              </Badge>
            )}
            {asset.archived && (
              <Badge variant="filled" color="dark">
                arquivado
              </Badge>
            )}
          </>
        }
        acoes={
          <>
            {has('WORKORDERS_MANAGE') && (
              <BotaoBarra destaque icone={<IconClipboardPlus size={13} />} onClick={() => setNovaOrdem(true)}>
                Nova ordem
              </BotaoBarra>
            )}
            {has('ASSETS_MANAGE') && (
              <BotaoBarra icone={<IconPencil size={13} />} onClick={() => setEditar(true)}>
                Editar
              </BotaoBarra>
            )}
            <BotaoBarra
              icone={<IconPrinter size={13} />}
              onClick={() =>
                openFile(`/assets/${id}/sheet.pdf`).catch((e: Error) =>
                  notifications.show({ title: 'Não foi possível abrir', message: e.message, color: 'red' }),
                )
              }
            >
              Imprimir ficha
            </BotaoBarra>
            <BotaoBarra
              icone={<IconHistory size={13} />}
              destaque={false}
              titulo="Todas as ordens feitas nesta viatura, com peças e custos — a pasta física, em PDF"
              onClick={() =>
                openFile(`/assets/${id}/history.pdf`).catch((e: Error) =>
                  notifications.show({ title: 'Não foi possível abrir', message: e.message, color: 'red' }),
                )
              }
            >
              Histórico (PDF)
            </BotaoBarra>
            <BotaoBarra
              icone={<IconFileText size={13} />}
              onClick={() =>
                openFile(`/assets/${id}/maintenance-plan.pdf`).catch((e: Error) =>
                  notifications.show({ title: 'Não foi possível abrir', message: e.message, color: 'red' }),
                )
              }
            >
              Plano em PDF
            </BotaoBarra>
            {asset.latitude != null && (
              <BotaoBarra icone={<IconMapPin size={13} />} onClick={() => navigate('/mapa')}>
                Ver no mapa
              </BotaoBarra>
            )}
            <SeparadorBarra />
            <BotaoBarra icone={<IconArrowLeft size={13} />} onClick={() => navigate('/ativos')}>
              Parque
            </BotaoBarra>
          </>
        }
      />

      <CamposFicha
        campos={[
          { rotulo: 'Tipo', valor: asset.assetTypeName },
          { rotulo: 'Local', valor: asset.locationName },
          { rotulo: 'Responsável', valor: asset.responsibleLabel },
          { rotulo: 'Matrícula', valor: asset.plate },
          { rotulo: 'Fabricante', valor: asset.manufacturer },
          { rotulo: 'Modelo', valor: asset.model },
          { rotulo: 'Ano', valor: asset.modelYear },
          { rotulo: 'Nº de série', valor: asset.serialNumber },
          {
            rotulo: meter ? (meter.kind === 'HOURMETER' ? 'Horímetro' : 'Hodómetro') : 'Medidor',
            valor: meter ? `${fmtNumber(meter.currentValue)} ${meter.kind === 'HOURMETER' ? 'h' : 'km'}` : null,
          },
          { rotulo: 'Aquisição', valor: asset.acquisitionDate ? fmtDate(asset.acquisitionDate) : null },
          /* O valor só chega do servidor a quem pode vê-lo; aqui mostra-se o
             que veio. A regra não é do ecrã, é da API. */
          {
            rotulo: 'Valor de aquisição',
            valor: asset.acquisitionValue != null ? fmtMoney(asset.acquisitionValue) : null,
          },
          {
            rotulo: 'Última posição',
            valor: asset.positionAt ? fmtDateTime(asset.positionAt) : null,
          },
          {
            rotulo: 'Depósito (sensor GPS)',
            valor:
              asset.fuelLevelLiters != null
                ? `${fmtNumber(asset.fuelLevelLiters, 0)} L${asset.tankCapacityLiters ? ` de ${fmtNumber(asset.tankCapacityLiters, 0)}` : ''}`
                : null,
          },
        ]}
      />

      <div style={{ display: 'grid', gridTemplateColumns: meter ? 'repeat(auto-fit, minmax(340px, 1fr))' : '1fr', gap: 16 }}>
      {meter && (
        <Painel titulo="Leitura do medidor">
          <Group justify="space-between" align="flex-end">
            <div>
              <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
                {meter.kind === 'HOURMETER' ? 'Horímetro' : 'Hodómetro'}
              </Text>
              <Group gap={6} align="baseline">
                <Text fw={800} style={{ fontSize: 30 }}>
                  {fmtNumber(meter.currentValue)}
                </Text>
                <Text c="dimmed">{meter.kind === 'HOURMETER' ? 'h' : 'km'}</Text>
              </Group>
              {meter.dailyAverage != null && (
                <Text size="xs" c="dimmed">
                  Média: {fmtNumber(meter.dailyAverage)} por dia
                </Text>
              )}
            </div>

            {can('TECHNICIAN') && (
              <Group gap="xs" align="flex-end">
                <NumberInput
                  label="Nova leitura"
                  placeholder={String(meter.currentValue ?? 0)}
                  min={0}
                  w={160}
                  value={reading}
                  onChange={(v) => setReading(typeof v === 'number' ? v : '')}
                />
                <Button
                  disabled={reading === ''}
                  loading={addReading.isPending}
                  onClick={() => reading !== '' && addReading.mutate(reading)}
                >
                  Registar
                </Button>
              </Group>
            )}
          </Group>
        </Painel>
      )}
      <PainelProximaManutencao asset={asset} />
      </div>

      <Tabs defaultValue={separadorInicial} keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="ordens" leftSection={<IconClipboardList size={16} />}>
            Ordens de serviço
          </Tabs.Tab>
          <Tabs.Tab value="plano" leftSection={<IconTool size={16} />}>
            Plano
          </Tabs.Tab>
          <Tabs.Tab value="pneus" leftSection={<IconWheel size={16} />}>
            Pneus
          </Tabs.Tab>
          <Tabs.Tab value="inspecoes" leftSection={<IconChecklist size={16} />}>
            Inspeções
          </Tabs.Tab>
          <Tabs.Tab value="pontos" leftSection={<IconDroplet size={16} />}>
            Pontos de serviço
          </Tabs.Tab>
          <Tabs.Tab value="preditiva" leftSection={<IconWaveSine size={16} />}>
            Preditiva
          </Tabs.Tab>
          <Tabs.Tab value="combustivel" leftSection={<IconDroplet size={16} />}>
            Combustível
          </Tabs.Tab>
          <Tabs.Tab value="documentos" leftSection={<IconFileText size={16} />}>
            Documentos
          </Tabs.Tab>
          <Tabs.Tab value="fotos" leftSection={<IconPhoto size={16} />}>
            Fotografias
          </Tabs.Tab>
          <Tabs.Tab value="criticidade" leftSection={<IconGauge size={16} />}>
            Criticidade
          </Tabs.Tab>
          {can('OWNER') && (
            <Tabs.Tab value="bloqueio" leftSection={<IconLock size={16} />}>
              Bloqueio
            </Tabs.Tab>
          )}
          <Tabs.Tab value="historico" leftSection={<IconHistory size={16} />}>
            Histórico
          </Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="ordens" pt="md">
          <OrdensTab assetId={id} />
        </Tabs.Panel>
        <Tabs.Panel value="plano" pt="md">
          <PlanTab assetId={id} asset={asset} />
        </Tabs.Panel>
        <Tabs.Panel value="pneus" pt="md">
          <PneusTab assetId={id} unidade={meter?.kind === 'HOURMETER' ? 'h' : 'km'} />
        </Tabs.Panel>
        <Tabs.Panel value="inspecoes" pt="md">
          <InspecoesTab assetId={id} />
        </Tabs.Panel>
        <Tabs.Panel value="pontos" pt="md">
          <PontosDeServico tipo={asset.assetTypeName} />
        </Tabs.Panel>

        <Tabs.Panel value="preditiva" pt="md">
          <Stack gap="md">
            <PrevisaoAvarias assetId={id} />
            <PredictiveTab assetId={id} />
          </Stack>
        </Tabs.Panel>
        <Tabs.Panel value="combustivel" pt="md">
          <FuelTab assetId={id} />
        </Tabs.Panel>
        <Tabs.Panel value="documentos" pt="md">
          <DocumentsTab assetId={id} />
        </Tabs.Panel>
        <Tabs.Panel value="fotos" pt="md">
          <PhotosTab asset={asset} />
        </Tabs.Panel>
        <Tabs.Panel value="criticidade" pt="md">
          <CriticalityTab asset={asset} />
        </Tabs.Panel>
        {can('OWNER') && (
          <Tabs.Panel value="bloqueio" pt="md">
            <LockPanel
              assetId={id}
              asset={{
                tag: asset.tag,
                name: asset.name,
                plate: asset.plate,
                responsible: asset.responsibleLabel,
                latitude: asset.latitude,
                longitude: asset.longitude,
              }}
            />
          </Tabs.Panel>
        )}
        <Tabs.Panel value="historico" pt="md">
          <HistoricoTab assetId={id} />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  );
}

// ---- separadores ----------------------------------------------------------

/** As ordens desta máquina, da mais recente para a mais antiga. */
function OrdensTab({ assetId }: { assetId: string }) {
  const navigate = useNavigate();
  const { data, isLoading } = useQuery({
    queryKey: ['work-orders', 'asset', assetId],
    queryFn: () =>
      api<{ content: OrdemResumo[]; totalElements: number }>(`/work-orders?assetId=${assetId}&size=200`),
  });
  const linhas = data?.content ?? [];
  const abertas = linhas.filter((w) => w.status !== 'CLOSED' && w.status !== 'CANCELLED').length;

  return (
    <Painel
      titulo="Ordens de serviço desta máquina"
      semPadding
      rodape={<BarraEstado itens={[{ rotulo: 'Total', valor: linhas.length }, { rotulo: 'Por fechar', valor: abertas }]} />}
    >
      <Grelha
        id="ativo-ordens"
        linhas={linhas}
        chave={(w) => w.id}
        carregando={isLoading}
        altura="52vh"
        aoAbrir={(w) => navigate(`/ordens/${w.id}`)}
        vazio="Ainda não há ordens para esta máquina."
        colunas={[
          {
            id: 'numero',
            titulo: 'Nº',
            largura: 130,
            fixa: true,
            semQuebra: true,
            valor: (w) => w.number,
            render: (w) => (
              <Text component={Link} to={`/ordens/${w.id}`} fw={700} size="xs" c="var(--erp-dourado-escuro)">
                {w.number}
              </Text>
            ),
          },
          { id: 'titulo', titulo: 'Título', valor: (w) => w.title },
          { id: 'tipo', titulo: 'Tipo', largura: 120, valor: (w) => w.typeLabel ?? w.type },
          {
            id: 'prioridade',
            titulo: 'Prioridade',
            largura: 100,
            valor: (w) => w.priorityLabel ?? w.priority,
            render: (w) => (
              <>
                <Ponto cor={COR_PRIORIDADE_OM[w.priority] ?? '#6b7280'} />
                {w.priorityLabel ?? w.priority}
              </>
            ),
          },
          { id: 'responsavel', titulo: 'Responsável', largura: 150, valor: (w) => w.assignedTo ?? null },
          {
            id: 'aberta',
            titulo: 'Aberta em',
            largura: 100,
            alinhar: 'right',
            valor: (w) => w.openedAt,
            render: (w) => fmtDate(w.openedAt),
          },
          {
            id: 'fechada',
            titulo: 'Concluída',
            largura: 100,
            alinhar: 'right',
            valor: (w) => w.completedAt ?? null,
            render: (w) => (w.completedAt ? fmtDate(w.completedAt) : '—'),
          },
          {
            id: 'estado',
            titulo: 'Estado',
            largura: 120,
            valor: (w) => w.statusLabel ?? w.status,
            render: (w) => (
              <>
                <Ponto cor={COR_ESTADO_OM[w.status] ?? '#6b7280'} />
                {w.statusLabel ?? w.status}
              </>
            ),
          },
        ]}
      />
    </Painel>
  );
}

interface OrdemResumo {
  id: string;
  number: string;
  title: string;
  type: string;
  typeLabel?: string;
  status: string;
  statusLabel?: string;
  priority: string;
  priorityLabel?: string;
  assignedTo?: string | null;
  openedAt: string;
  completedAt?: string | null;
}

/**
 * Tudo o que aconteceu a esta máquina, pelo registo de auditoria.
 *
 * <p>É a resposta a «quem mudou isto e quando» sem ter de ir ao ecrã de
 * auditoria e filtrar. Numa ficha de ERP, o histórico está na ficha.
 */
function HistoricoTab({ assetId }: { assetId: string }) {
  const [pagina, setPagina] = useState(1);
  const { data, isLoading } = useQuery({
    queryKey: ['audit', 'asset', assetId, pagina],
    queryFn: () =>
      api<{ content: Evento[]; totalPages: number; totalElements: number }>(
        `/audit?entityType=Asset&entityId=${assetId}&page=${pagina - 1}&size=50`,
      ),
  });

  return (
    <Painel titulo="Histórico de alterações" semPadding>
      <Grelha
        id="ativo-historico"
        linhas={data?.content ?? []}
        chave={(e) => e.id}
        carregando={isLoading}
        altura="52vh"
        porPagina={50}
        servidor={{
          pagina,
          totalPaginas: data?.totalPages ?? 1,
          total: data?.totalElements ?? 0,
          aoMudarPagina: setPagina,
        }}
        vazio="Ainda não há alterações registadas."
        colunas={[
          {
            id: 'quando',
            titulo: 'Quando',
            largura: 160,
            fixa: true,
            semQuebra: true,
            valor: (e) => e.at,
            render: (e) => fmtDateTime(e.at),
          },
          { id: 'quem', titulo: 'Quem', largura: 180, valor: (e) => e.userName ?? 'conta removida' },
          { id: 'acao', titulo: 'Ação', largura: 220, valor: (e) => e.actionLabel },
          { id: 'detalhe', titulo: 'Detalhe', valor: (e) => e.summary ?? null },
        ]}
      />
    </Painel>
  );
}

interface Evento {
  id: string;
  at: string;
  userName?: string | null;
  actionLabel: string;
  summary?: string | null;
}

function PlanTab({ assetId, asset }: { assetId: string; asset: AssetView }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { has } = useAuth();
  const [definir, setDefinir] = useState(false);

  const { data } = useQuery({
    queryKey: ['asset-plan', assetId],
    queryFn: () => api<{ tasks: PlanTask[] }[]>(`/assets/${assetId}/maintenance-plans`),
  });
  const tasks = (data ?? []).flatMap((p) => p.tasks ?? []);
  const vencidas = tasks.filter((t) => t.status === 'OVERDUE');
  const aVencer = tasks.filter((t) => t.status === 'DUE_SOON');

  /**
   * Abre a ordem com as tarefas vencidas já lá dentro.
   *
   * <p>Antes disto, o ecrã dizia «Vencida» e ficava-se por aí: o técnico tinha
   * de ir a outro sítio abrir a ordem e copiar as tarefas à mão. É aqui, com a
   * lista à frente, que se decide intervir.
   */
  const abrirOrdem = useMutation({
    mutationFn: (incluirAVencer: boolean) =>
      api<{ id: string; number: string }>('/work-orders/from-due', {
        method: 'POST',
        body: {
          assetId,
          statuses: incluirAVencer ? ['OVERDUE', 'DUE_SOON'] : ['OVERDUE'],
        },
      }),
    onSuccess: (om) => {
      notifications.show({
        title: `Ordem ${om.number} aberta`,
        message: 'As tarefas vencidas entraram como tarefas da ordem.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['asset-plan', assetId] });
      queryClient.invalidateQueries({ queryKey: ['work-orders'] });
      navigate(`/ordens/${om.id}`);
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível abrir', message: e.message, color: 'red' }),
  });

  if (tasks.length === 0) {
    return (
      <Alert variant="light" icon={<IconInfoCircle size={18} />}>
        <Group justify="space-between" wrap="wrap" gap="sm">
          <div>
            Este ativo ainda não tem plano de manutenção atribuído.
            <Text size="xs" c="dimmed">
              Defina o limite (a cada N km, horas ou dias) ou atribua um plano completo em «Planos de manutenção».
            </Text>
          </div>
          {has('PLANS_MANAGE') && (
            <Button size="xs" leftSection={<IconTool size={14} />} onClick={() => setDefinir(true)}>
              Definir limite
            </Button>
          )}
        </Group>
        <LimiteManutencaoModal asset={asset} aberto={definir} fechar={() => setDefinir(false)} />
      </Alert>
    );
  }

  return (
    <>
      <LimiteManutencaoModal asset={asset} aberto={definir} fechar={() => setDefinir(false)} />
      <Group justify="flex-end" mb="xs">
        {has('PLANS_MANAGE') && (
          <Button size="xs" variant="default" leftSection={<IconTool size={14} />} onClick={() => setDefinir(true)}>
            Acrescentar limite
          </Button>
        )}
        <Button size="xs" variant="subtle" onClick={() => navigate('/planos')}>
          Planos de manutenção
        </Button>
      </Group>
      {(vencidas.length > 0 || aVencer.length > 0) && (
        <Alert
          color={vencidas.length > 0 ? 'red' : 'yellow'}
          variant="light"
          p="sm"
          mb="sm"
          icon={<IconAlertTriangle size={17} />}
        >
          <Group justify="space-between" wrap="wrap" gap="sm">
            <div>
              <Text size="sm" fw={600}>
                {vencidas.length > 0
                  ? `${vencidas.length} tarefa(s) de manutenção vencida(s)`
                  : `${aVencer.length} tarefa(s) a vencer`}
              </Text>
              <Text size="xs">
                {vencidas.length > 0
                  ? 'Uma máquina que passa do intervalo custa mais a reparar do que a manter.'
                  : 'Ainda dentro do intervalo, mas já a chegar ao limite.'}
              </Text>
            </div>
            <Group gap="xs">
              {vencidas.length > 0 && (
                <Button
                  size="xs"
                  leftSection={<IconClipboardList size={14} />}
                  onClick={() => abrirOrdem.mutate(false)}
                  loading={abrirOrdem.isPending}
                >
                  Abrir ordem das vencidas ({vencidas.length})
                </Button>
              )}
              {aVencer.length > 0 && (
                <Button
                  size="xs"
                  variant="default"
                  onClick={() => abrirOrdem.mutate(true)}
                  loading={abrirOrdem.isPending}
                >
                  Incluir as que estão a vencer ({vencidas.length + aVencer.length})
                </Button>
              )}
            </Group>
          </Group>
        </Alert>
      )}

      <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Sistema</Table.Th>
          <Table.Th>Tarefa</Table.Th>
          <Table.Th>Última</Table.Th>
          <Table.Th>Próxima</Table.Th>
          <Table.Th>Falta</Table.Th>
          <Table.Th>Estado</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {tasks.map((t) => (
          <Table.Tr key={t.id}>
            <Table.Td>{t.systemName ?? '—'}</Table.Td>
            <Table.Td>{t.title}</Table.Td>
            <Table.Td>
              <Text size="sm" c="dimmed">
                {t.lastDoneMeter != null
                  ? `${fmtNumber(t.lastDoneMeter, 0)} ${unidade(t.nextDueMeterKind)}`
                  : t.lastDoneAt
                    ? fmtDate(t.lastDoneAt)
                    : '—'}
              </Text>
            </Table.Td>
            <Table.Td>
              {t.nextDueMeter != null
                ? `${fmtNumber(t.nextDueMeter, 0)} ${unidade(t.nextDueMeterKind)}`
                : fmtDate(t.nextDueAt)}
            </Table.Td>
            <Table.Td>
              <Text size="sm" c={t.status === 'OVERDUE' ? 'red' : t.status === 'DUE_SOON' ? 'orange' : undefined} fw={600}>
                {t.remainingMeter != null
                  ? `${fmtNumber(t.remainingMeter, 0)} ${unidade(t.nextDueMeterKind)}`
                  : t.remainingDays != null
                    ? `${t.remainingDays} dias`
                    : '—'}
              </Text>
            </Table.Td>
            <Table.Td>
              <EstadoTarefa status={t.status} />
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
      </Table>
    </>
  );
}

interface PlanTask {
  id: string;
  title: string;
  systemName?: string | null;
  nextDueAt?: string | null;
  nextDueMeter?: number | null;
  nextDueMeterKind?: string | null;
  remainingMeter?: number | null;
  remainingDays?: number | null;
  lastDoneAt?: string | null;
  lastDoneMeter?: number | null;
  status: string;
}

function PredictiveTab({ assetId }: { assetId: string }) {
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const { data } = useQuery({
    queryKey: ['predictive', assetId],
    queryFn: () => api<PredictiveProgram[]>(`/assets/${assetId}/predictive`),
  });

  const applyStandard = useMutation({
    mutationFn: () => api(`/assets/${assetId}/predictive/standard`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({ message: 'Programas do documento de referência aplicados.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['predictive', assetId] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  if (!data?.length) {
    return (
      <Stack align="flex-start">
        <Alert variant="light" icon={<IconInfoCircle size={18} />}>
          Sem monitorização de condição. O conjunto de referência é análise de vibração mensal,
          termografia trimestral e análise de óleo semestral.
        </Alert>
        {can('MANAGER') && (
          <Button onClick={() => applyStandard.mutate()} loading={applyStandard.isPending}>
            Aplicar conjunto de referência
          </Button>
        )}
      </Stack>
    );
  }

  return (
    <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Técnica</Table.Th>
          <Table.Th>Periodicidade</Table.Th>
          <Table.Th>Última</Table.Th>
          <Table.Th>Próxima</Table.Th>
          <Table.Th>Estado</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {data.map((p) => (
          <Table.Tr key={p.id}>
            <Table.Td fw={600}>{p.techniqueLabel}</Table.Td>
            <Table.Td>{p.frequencyLabel}</Table.Td>
            <Table.Td>{fmtDate(p.lastDoneAt)}</Table.Td>
            <Table.Td>{fmtDate(p.nextDueAt)}</Table.Td>
            <Table.Td>
              <Badge
                variant="light"
                color={p.status === 'OVERDUE' ? 'red' : p.status === 'DUE_SOON' ? 'yellow' : 'green'}
              >
                {p.status === 'OVERDUE' ? 'Vencida' : p.status === 'DUE_SOON' ? 'A chegar' : 'Em dia'}
              </Badge>
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

interface PredictiveProgram {
  id: string;
  techniqueLabel: string;
  frequencyLabel: string;
  lastDoneAt?: string | null;
  nextDueAt?: string | null;
  status: string;
}

function FuelTab({ assetId }: { assetId: string }) {
  const { data: summary } = useQuery({
    queryKey: ['fuel-summary', assetId],
    queryFn: () => api<FuelSummary>(`/assets/${assetId}/fuel/summary`),
  });
  const { data: records } = useQuery({
    queryKey: ['fuel', assetId],
    queryFn: () => api<{ content: FuelRecord[] }>(`/assets/${assetId}/fuel`),
  });

  return (
    <Stack>
      {/* O aviso vem do servidor e é mostrado tal como ele o escreve: é a
          diferença entre contabilidade e vigilância, e o utilizador tem de a
          conhecer antes de tirar conclusões. */}
      {summary?.disclaimer && (
        <Alert variant="light" color="blue" icon={<IconInfoCircle size={18} />}>
          {summary.disclaimer}
        </Alert>
      )}

      {summary && summary.samples > 0 && (
        <CamposFicha
          campos={[
            { rotulo: `Último (${summary.unit})`, valor: fmtNumber(summary.last) },
            { rotulo: `Média (${summary.unit})`, valor: fmtNumber(summary.average) },
            { rotulo: 'Melhor', valor: fmtNumber(summary.best) },
            { rotulo: 'Pior', valor: fmtNumber(summary.worst) },
          ]}
        />
      )}

      {summary && !summary.reliable && summary.samples > 0 && (
        <Text size="sm" c="dimmed">
          Só {summary.samples} abastecimento(s) completo(s) com consumo calculado — ainda poucos
          para a média valer como referência.
        </Text>
      )}

      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Data</Table.Th>
            <Table.Th ta="right">Litros</Table.Th>
            <Table.Th ta="right">Custo</Table.Th>
            <Table.Th ta="right">Medidor</Table.Th>
            <Table.Th ta="right">Consumo</Table.Th>
            <Table.Th>Depósito</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(records?.content ?? []).length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={6}>
                <Text c="dimmed" ta="center" py="md">
                  Nenhum abastecimento registado.
                </Text>
              </Table.Td>
            </Table.Tr>
          )}
          {(records?.content ?? []).map((r) => (
            <Table.Tr key={r.id}>
              <Table.Td>{fmtDate(r.filledAt)}</Table.Td>
              <Table.Td ta="right">{fmtNumber(r.liters)}</Table.Td>
              <Table.Td ta="right">
                {r.totalCost != null ? `${fmtNumber(r.totalCost)} ${r.currency}` : '—'}
              </Table.Td>
              <Table.Td ta="right">{fmtNumber(r.meterValue)}</Table.Td>
              <Table.Td ta="right">
                {r.consumption != null ? `${fmtNumber(r.consumption)} ${r.consumptionUnit}` : '—'}
              </Table.Td>
              <Table.Td>
                <Badge variant="light" color={r.fullTank ? 'blue' : 'gray'}>
                  {r.fullTank ? 'Cheio' : 'Parcial'}
                </Badge>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  );
}

interface FuelSummary {
  unit?: string;
  last?: number;
  average?: number;
  best?: number;
  worst?: number;
  samples: number;
  reliable: boolean;
  disclaimer: string;
}

interface FuelRecord {
  id: string;
  filledAt: string;
  liters: number;
  totalCost?: number;
  currency: string;
  meterValue?: number;
  consumption?: number;
  consumptionUnit?: string;
  fullTank: boolean;
}

function DocumentsTab({ assetId }: { assetId: string }) {
  const queryClient = useQueryClient();
  const { can } = useAuth();
  const { data } = useQuery({
    queryKey: ['documents', assetId],
    queryFn: () => api<DocumentView[]>(`/assets/${assetId}/documents`),
  });
  const { data: tipos } = useQuery({
    queryKey: ['documents', 'kinds'],
    queryFn: () => api<{ code: string; label: string }[]>('/documents/kinds'),
  });
  const [novo, setNovo] = useState(false);
  const [renovar, setRenovar] = useState<DocumentView | null>(null);
  const [titulo, setTitulo] = useState('');
  const [kind, setKind] = useState<string | null>('INSURANCE');
  const [referencia, setReferencia] = useState('');
  const [emissor, setEmissor] = useState('');
  // AAAA-MM-DD do campo nativo de data: sem ambiguidade de formato, e funciona no telemóvel.
  const [validade, setValidade] = useState('');
  const [ficheiro, setFicheiro] = useState<File | null>(null);
  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['documents'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };
  const limpar = () => {
    setTitulo('');
    setReferencia('');
    setEmissor('');
    setValidade('');
    setFicheiro(null);
  };
  const criar = useMutation({
    mutationFn: async () => {
      const exp = validade ? new Date(validade + 'T23:59:00').toISOString() : undefined;
      if (ficheiro) {
        const recusa = checkUploadSize(ficheiro);
        if (recusa) throw new Error(recusa);
        const fd = new FormData();
        fd.append('file', ficheiro);
        fd.append('title', titulo.trim());
        if (kind) fd.append('kind', kind);
        if (referencia.trim()) fd.append('reference', referencia.trim());
        if (emissor.trim()) fd.append('issuer', emissor.trim());
        if (exp) fd.append('expiresAt', exp);
        return api(`/assets/${assetId}/documents/upload`, { method: 'POST', body: fd });
      }
      return api(`/assets/${assetId}/documents`, {
        method: 'POST',
        body: { title: titulo.trim(), kind, reference: referencia.trim() || undefined, issuer: emissor.trim() || undefined, expiresAt: exp },
      });
    },
    onSuccess: () => {
      notifications.show({ message: 'Documento guardado.', color: 'green' });
      setNovo(false);
      limpar();
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });
  const renovarM = useMutation({
    mutationFn: () => {
      if (!renovar || !validade) throw new Error('Indique a nova validade.');
      const exp = new Date(validade + 'T23:59:00').toISOString();
      return api(`/documents/${renovar.id}`, {
        method: 'PATCH',
        body: { title: renovar.title, expiresAt: exp, reference: referencia.trim() || undefined },
      });
    },
    onSuccess: () => {
      notifications.show({ message: 'Validade renovada.', color: 'green' });
      setRenovar(null);
      limpar();
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível renovar', message: e.message, color: 'red' }),
  });
  const remover = useMutation({
    mutationFn: (id: string) => api(`/documents/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      notifications.show({ message: 'Documento removido.', color: 'gray' });
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const formulario = (
    <Modal opened={novo} onClose={() => setNovo(false)} title="Adicionar documento" centered>
      <Stack gap="sm">
        <Select label="Tipo" data={(tipos ?? []).map((t) => ({ value: t.code, label: t.label }))} value={kind} onChange={setKind} allowDeselect={false} />
        <TextInput label="Título" placeholder="Ex.: Seguro automóvel 2026" value={titulo} onChange={(e) => setTitulo(e.currentTarget.value)} required />
        <Group grow>
          <TextInput label="Referência / nº da apólice" value={referencia} onChange={(e) => setReferencia(e.currentTarget.value)} />
          <TextInput label="Emitido por" placeholder="Seguradora, entidade…" value={emissor} onChange={(e) => setEmissor(e.currentTarget.value)} />
        </Group>
        <TextInput
          type="date"
          label="Válido até"
          description="Deixe vazio se não caduca (manual, fatura). Com data, o sistema avisa 30, 15 e 7 dias antes e no dia."
          value={validade}
          onChange={(e) => setValidade(e.currentTarget.value)}
        />
        <FileInput label="Ficheiro (PDF ou fotografia)" placeholder="Escolher…" accept="application/pdf,image/*" value={ficheiro} onChange={setFicheiro} clearable />
        <Group justify="flex-end">
          <Button variant="default" onClick={() => setNovo(false)}>
            Cancelar
          </Button>
          <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={titulo.trim().length < 2}>
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
  const renovacao = (
    <Modal opened={!!renovar} onClose={() => setRenovar(null)} title={renovar ? `Renovar «${renovar.title}»` : ''} centered>
      <Stack gap="sm">
        <TextInput type="date" label="Nova validade" value={validade} onChange={(e) => setValidade(e.currentTarget.value)} required />
        <TextInput label="Nova referência (opcional)" placeholder="Nº da apólice nova" value={referencia} onChange={(e) => setReferencia(e.currentTarget.value)} />
        <Text size="xs" c="dimmed">
          Para guardar o PDF novo, adicione-o como documento novo — o antigo fica no histórico.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={() => setRenovar(null)}>
            Cancelar
          </Button>
          <Button onClick={() => renovarM.mutate()} loading={renovarM.isPending} disabled={!validade}>
            Renovar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
  const cabecalho = can('TECHNICIAN') ? (
    <Group justify="flex-end" mb="sm">
      <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setNovo(true)}>
        Adicionar documento
      </Button>
    </Group>
  ) : null;

  if (!data?.length) {
    return (
      <>
        {cabecalho}
        {formulario}
        <Text c="dimmed">
          Nenhum documento associado a este ativo. Registe o seguro, a inspeção, o livrete e a licença com a data de
          validade — o sistema avisa antes de caducarem.
        </Text>
      </>
    );
  }

  return (
    <>
    {cabecalho}
    {formulario}
    {renovacao}
    <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Tipo</Table.Th>
          <Table.Th>Título</Table.Th>
          <Table.Th>Referência</Table.Th>
          <Table.Th>Validade</Table.Th>
          <Table.Th>Ficheiro</Table.Th>
          <Table.Th />
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {data.map((d) => (
          <Table.Tr key={d.id}>
            <Table.Td>{d.kindLabel}</Table.Td>
            <Table.Td>{d.title}</Table.Td>
            <Table.Td>{d.reference ?? '—'}</Table.Td>
            <Table.Td>
              {d.state === 'SEM_VALIDADE' ? (
                <Text c="dimmed" size="sm">
                  Não caduca
                </Text>
              ) : (
                <Badge
                  variant="light"
                  color={
                    d.state === 'CADUCADO' ? 'red' : d.state === 'A_CADUCAR' ? 'orange' : 'green'
                  }
                >
                  {d.expiryLabel}
                </Badge>
              )}
            </Table.Td>
            <Table.Td>
              {d.fileUrl ? (
                <Anchor href={d.fileUrl} target="_blank" size="sm">
                  {d.fileName ?? 'Abrir'}
                </Anchor>
              ) : (
                '—'
              )}
            </Table.Td>
            <Table.Td>
              {can('TECHNICIAN') && (
                <Group gap={4} justify="flex-end" wrap="nowrap">
                  <Button
                    size="compact-xs"
                    variant="light"
                    onClick={() => {
                      setValidade('');
                      setReferencia('');
                      setRenovar(d);
                    }}
                  >
                    Renovar
                  </Button>
                  {can('MANAGER') && (
                    <Button size="compact-xs" variant="subtle" color="red" onClick={() => remover.mutate(d.id)}>
                      Remover
                    </Button>
                  )}
                </Group>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
    </>
  );
}

interface DocumentView {
  id: string;
  kindLabel: string;
  title: string;
  reference?: string | null;
  state: string;
  expiryLabel?: string | null;
  fileUrl?: string | null;
  fileName?: string | null;
}

function PhotosTab({ asset }: { asset: AssetView }) {
  return (
    <FotografiasPorParte
      assetId={asset.id}
      fotos={(asset.photos ?? []) as Foto[]}
      editavel={!asset.archived}
    />
  );
}

function CriticalityTab({ asset }: { asset: AssetView }) {
  const c = asset.criticality;
  if (!c) return <Text c="dimmed">Criticidade não avaliada.</Text>;

  return (
    <Stack>
      <SimpleGrid cols={{ base: 1, md: 3 }}>
        <Impact label="Impacto na produção" value={c.productionImpact} />
        <Impact label="Impacto na segurança" value={c.safetyImpact} />
        <Impact label="Impacto financeiro" value={c.financialImpact} />
      </SimpleGrid>
      <Painel titulo="Criticidade geral">
        <Group justify="space-between">
          <Text fw={600}>Resultado da avaliação</Text>
          <Badge size="lg" color={CRITICALITY[c.overall]?.color}>
            {CRITICALITY[c.overall]?.label}
          </Badge>
        </Group>
        <Text size="xs" c="dimmed" mt="xs">
          Corresponde ao pior dos três impactos — um ativo perigoso é crítico mesmo que a produção
          não pare.
        </Text>
      </Painel>
    </Stack>
  );
}

function Impact({ label, value }: { label: string; value: number }) {
  return (
    <Card p="sm">
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Group gap={4} mt={4}>
        <Text fw={800} style={{ fontSize: 22 }}>
          {value}
        </Text>
        <Text c="dimmed">/ 5</Text>
      </Group>
      <Progress value={(value / 5) * 100} size="sm" mt="xs" color={value >= 4 ? 'red' : 'blue'} />
    </Card>
  );
}
