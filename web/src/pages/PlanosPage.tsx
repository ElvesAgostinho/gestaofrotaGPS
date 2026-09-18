import {
  ActionIcon,
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
import {
  IconBook,
  IconCheck,
  IconPlus,
  IconTrash,
  IconTruck,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { AssetSummary, AssetTypeView, Paged } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { BotaoBarra, Painel, SeccaoForm, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { Kpi } from '../components/Kpi';
import { CampoProcura, filtrar } from '../components/Procura';
import { fmtDate, fmtNumber } from '../lib/format';

interface Gatilho {
  type: 'METER_INTERVAL' | 'CALENDAR_DAYS' | string;
  meterKind?: string | null;
  interval: number;
  tolerance?: number | null;
}

interface Tarefa {
  id: string;
  systemCode?: string | null;
  systemName?: string | null;
  title: string;
  instructions?: string | null;
  estimatedMinutes?: number | null;
  tools?: string | null;
  sortOrder: number;
  triggers: Gatilho[];
  parts: { name: string; quantity?: number | null; unit?: string | null }[];
}

interface Plano {
  id: string;
  name: string;
  assetTypeId?: string | null;
  assetTypeName?: string | null;
  description?: string | null;
  notes?: string | null;
  active: boolean;
  objective?: string | null;
  sourceReference?: string | null;
  preparedByLabel?: string | null;
  preparedAt?: string | null;
  approvedByLabel?: string | null;
  approvedAt?: string | null;
  approved: boolean;
  taskCount: number;
  /** Vazio na lista; o detalhe (GET /{id}) traz as tarefas. */
  tasks: Tarefa[];
  intervals: Gatilho[];
}

interface Modelo {
  code: string;
  name: string;
  description: string;
  taskCount: number;
}

/** «cada 250 h», «cada 5 000 km», «cada 90 dias». */
export function fraseGatilho(g: Gatilho): string {
  if (g.type === 'CALENDAR_DAYS') return `cada ${fmtNumber(g.interval, 0)} dias`;
  return `cada ${fmtNumber(g.interval, 0)} ${g.meterKind === 'HOURMETER' ? 'h' : 'km'}`;
}

function fraseGatilhos(t: Tarefa): string {
  return t.triggers.map(fraseGatilho).join(' ou ') || '—';
}

/**
 * Planos de manutenção preventiva: o que se faz, a cada quantos km/horas/dias.
 *
 * <p>Um plano é um documento técnico reutilizável (por tipo de equipamento);
 * atribui-se a cada ativo, e a partir daí o contador do ativo — à mão ou pelo
 * GPS — faz as tarefas vencer. O limite simples de um ativo («revisão a cada
 * 5 000 km», definido na ficha) também aparece aqui, como plano de uma tarefa.
 */
export function PlanosPage() {
  const { has } = useAuth();
  const [procura, setProcura] = useState('');
  const [aberto, setAberto] = useState<Plano | null>(null);
  // Abrir já com o pedido de aprovação à vista, quando se carrega em «por aprovar».
  const [abrirAAprovar, setAbrirAAprovar] = useState(false);
  const [novo, setNovo] = useState(false);
  const [catalogo, setCatalogo] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['plans'],
    queryFn: () => api<Plano[]>('/maintenance-plans'),
  });
  const planos = data ?? [];
  const rows = useMemo(
    () => filtrar(planos, procura, (p) => [p.name, p.assetTypeName, p.objective, p.sourceReference]),
    [planos, procura],
  );
  const aprovados = planos.filter((p) => p.approved).length;
  const tarefas = planos.reduce((n, p) => n + p.taskCount, 0);

  return (
    <Stack gap="lg">
      <Group gap="sm" wrap="wrap">
        <Kpi label="Planos" value={planos.length} tone="brand" />
        <Kpi
          label="Aprovados"
          value={aprovados}
          tone={aprovados < planos.length ? 'warning' : 'good'}
          hint="Um plano por aprovar aplica-se na mesma, mas numa auditoria a pergunta é quem decidiu estes intervalos."
        />
        <Kpi label="Tarefas planeadas" value={tarefas} />
      </Group>

      <Painel
        titulo="Planos de manutenção"
        semPadding
        acoes={
          <>
            {has('PLANS_MANAGE') && (
              <>
                <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
                  Novo plano
                </BotaoBarra>
                <BotaoBarra icone={<IconBook size={15} />} onClick={() => setCatalogo(true)}>
                  Modelo do catálogo
                </BotaoBarra>
                <SeparadorBarra />
              </>
            )}
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Nome, tipo de equipamento…" />
          </>
        }
      >
        <Grelha
          id="planos"
          linhas={rows}
          chave={(p) => p.id}
          carregando={isLoading}
          aoAbrir={(p) => setAberto(p)}
          vazio="Ainda não há planos. Defina o limite na ficha de um ativo, crie um plano ou aplique um modelo do catálogo."
          colunas={[
            {
              id: 'nome',
              titulo: 'Plano',
              fixa: true,
              valor: (p) => p.name,
              render: (p) => (
                <div>
                  <Text size="sm" fw={600}>
                    {p.name}
                  </Text>
                  {p.objective && (
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      {p.objective}
                    </Text>
                  )}
                </div>
              ),
            },
            { id: 'tipo', titulo: 'Tipo de equipamento', largura: 180, valor: (p) => p.assetTypeName ?? 'qualquer' },
            {
              id: 'intervalos',
              titulo: 'Intervalos',
              valor: (p) => (p.intervals ?? []).map(fraseGatilho).join(', ') || null,
              render: (p) => {
                const todos = [...new Set((p.intervals ?? []).map(fraseGatilho))];
                return (
                  <Text size="xs" c="dimmed" lineClamp={1}>
                    {todos.slice(0, 4).join(' · ')}
                    {todos.length > 4 ? ` · +${todos.length - 4}` : ''}
                  </Text>
                );
              },
            },
            { id: 'tarefas', titulo: 'Tarefas', largura: 80, alinhar: 'right', valor: (p) => p.taskCount },
            {
              id: 'aprovado',
              titulo: 'Aprovação',
              largura: 150,
              valor: (p) => (p.approved ? 1 : 0),
              render: (p) =>
                p.approved ? (
                  <Badge variant="light" color="green" size="sm">
                    {p.approvedByLabel ? `aprovado · ${p.approvedByLabel}` : 'aprovado'}
                  </Badge>
                ) : has('PLANS_MANAGE') ? (
                  <Button
                    size="compact-xs"
                    variant="light"
                    color="yellow"
                    onClick={(e) => {
                      e.stopPropagation();
                      setAbrirAAprovar(true);
                      setAberto(p);
                    }}
                  >
                    Por aprovar · aprovar agora
                  </Button>
                ) : (
                  <Badge variant="light" color="yellow" size="sm">
                    por aprovar
                  </Badge>
                ),
            },
            { id: 'origem', titulo: 'Origem', escondida: true, valor: (p) => p.sourceReference ?? null },
          ]}
        />
      </Painel>

      {aberto && (
        <PlanoModal
          planoId={aberto.id}
          resumo={aberto}
          aprovarLogo={abrirAAprovar}
          fechar={() => {
            setAberto(null);
            setAbrirAAprovar(false);
          }}
        />
      )}
      <NovoPlanoModal aberto={novo} fechar={() => setNovo(false)} />
      <CatalogoModal aberto={catalogo} fechar={() => setCatalogo(false)} />
    </Stack>
  );
}

function PlanoModal({ planoId, resumo, fechar, aprovarLogo = false }: { planoId: string; resumo: Plano; fechar: () => void; aprovarLogo?: boolean }) {
  const { has } = useAuth();
  const queryClient = useQueryClient();
  // A lista não traz as tarefas (um plano de fabricante tem dezenas); o detalhe traz.
  const { data: detalhe } = useQuery({
    queryKey: ['plans', planoId],
    queryFn: () => api<Plano>(`/maintenance-plans/${planoId}`),
  });
  const plano = detalhe ?? resumo;
  const [atribuir, setAtribuir] = useState(false);
  const [aprovar, setAprovar] = useState(aprovarLogo);
  const [assinatura, setAssinatura] = useState('');

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['plans'] });
    queryClient.invalidateQueries({ queryKey: ['assets'] });
  };

  const aprovarM = useMutation({
    mutationFn: () =>
      api(`/maintenance-plans/${plano.id}/approve`, {
        method: 'POST',
        body: { approvedByLabel: assinatura.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Plano aprovado', message: plano.name, color: 'green' });
      setAprovar(false);
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível aprovar', message: e.message, color: 'red' }),
  });

  const eliminar = useMutation({
    mutationFn: () => api(`/maintenance-plans/${plano.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      notifications.show({ title: 'Plano eliminado', message: plano.name, color: 'gray' });
      invalidar();
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível eliminar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened onClose={fechar} title={plano.name} size="xl" centered>
      <Stack gap="sm">
        <Group gap="xl" wrap="wrap">
          <Text size="sm">
            <b>Tipo:</b> {plano.assetTypeName ?? 'qualquer'}
          </Text>
          {plano.sourceReference && (
            <Text size="sm">
              <b>Origem:</b> {plano.sourceReference}
            </Text>
          )}
          <Text size="sm">
            <b>Elaborado:</b> {plano.preparedByLabel ?? '—'}
            {plano.preparedAt ? ` · ${fmtDate(plano.preparedAt)}` : ''}
          </Text>
          <Text size="sm">
            <b>Aprovação:</b>{' '}
            {plano.approved ? `${plano.approvedByLabel ?? 'aprovado'}${plano.approvedAt ? ` · ${fmtDate(plano.approvedAt)}` : ''}` : 'por aprovar'}
          </Text>
        </Group>
        {plano.objective && (
          <Text size="sm" c="dimmed">
            {plano.objective}
          </Text>
        )}

        <Table striped highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>#</Table.Th>
              <Table.Th>Sistema</Table.Th>
              <Table.Th>Tarefa</Table.Th>
              <Table.Th>Intervalo</Table.Th>
              <Table.Th>Peças</Table.Th>
              <Table.Th>Tempo</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {plano.tasks.map((t, i) => (
              <Table.Tr key={t.id}>
                <Table.Td>{i + 1}</Table.Td>
                <Table.Td>{t.systemName ?? '—'}</Table.Td>
                <Table.Td>
                  <Text size="sm">{t.title}</Text>
                  {t.instructions && (
                    <Text size="xs" c="dimmed" lineClamp={2}>
                      {t.instructions}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{fraseGatilhos(t)}</Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed">
                    {t.parts.map((p) => `${p.name}${p.quantity ? ` ×${p.quantity}` : ''}`).join(', ') || '—'}
                  </Text>
                </Table.Td>
                <Table.Td>{t.estimatedMinutes ? `${t.estimatedMinutes} min` : '—'}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>

        {has('PLANS_MANAGE') && (
          <Group justify="space-between" wrap="wrap">
            <Group gap="xs">
              <Button size="xs" leftSection={<IconTruck size={14} />} onClick={() => setAtribuir(true)}>
                Atribuir a um ativo
              </Button>
              {!plano.approved && (
                <Button size="xs" variant="default" leftSection={<IconCheck size={14} />} onClick={() => setAprovar(true)}>
                  Aprovar
                </Button>
              )}
            </Group>
            <Button
              size="xs"
              variant="subtle"
              color="red"
              leftSection={<IconTrash size={14} />}
              loading={eliminar.isPending}
              onClick={() => {
                if (window.confirm(`Eliminar o plano «${plano.name}»? Os ativos que o usam perdem o acompanhamento.`)) {
                  eliminar.mutate();
                }
              }}
            >
              Eliminar
            </Button>
          </Group>
        )}
      </Stack>

      <Modal opened={aprovar} onClose={() => setAprovar(false)} title="Aprovar o plano" centered>
        <Stack gap="sm">
          <Text size="sm">
            A aprovação assina o documento: numa auditoria, a pergunta é quem decidiu estes intervalos.
          </Text>
          <TextInput label="Aprovado por" placeholder="Eng. Domingos, Diretor de Manutenção" value={assinatura} onChange={(e) => setAssinatura(e.currentTarget.value)} data-autofocus />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAprovar(false)}>
              Cancelar
            </Button>
            <Button onClick={() => aprovarM.mutate()} loading={aprovarM.isPending}>
              Aprovar
            </Button>
          </Group>
        </Stack>
      </Modal>

      {atribuir && <AtribuirModal plano={plano} fechar={() => setAtribuir(false)} />}
    </Modal>
  );
}

function AtribuirModal({ plano, fechar }: { plano: Plano; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [assetId, setAssetId] = useState<string | null>(null);
  const [ultima, setUltima] = useState<number | string>('');
  const { data } = useQuery({
    queryKey: ['assets', 'para-plano'],
    queryFn: () => api<Paged<AssetSummary>>('/assets?size=200'),
  });
  const ativos = (data?.content ?? []).filter((a) => !plano.assetTypeName || a.assetTypeName === plano.assetTypeName);

  const atribuir = useMutation({
    mutationFn: () =>
      api(`/assets/${assetId}/maintenance-plans`, {
        method: 'POST',
        body: { planId: plano.id, startFromNow: true, lastDoneMeter: ultima === '' ? null : Number(ultima) },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Plano atribuído', message: 'As tarefas começam a contar a partir do contador do ativo.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['asset-plan', assetId] });
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível atribuir', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened onClose={fechar} title={`Atribuir «${plano.name}»`} centered>
      <Stack gap="sm">
        <Select
          label="Ativo"
          placeholder={ativos.length ? 'Escolha o equipamento' : 'Nenhum ativo deste tipo'}
          searchable
          data={ativos.map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
          value={assetId}
          onChange={setAssetId}
          data-autofocus
        />
        <NumberInput
          label="Última revisão feita aos (contador)"
          description="Vazio = as tarefas contam a partir do contador de hoje."
          min={0}
          thousandSeparator=" "
          value={ultima}
          onChange={(v) => setUltima(typeof v === 'number' ? v : '')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={!assetId} loading={atribuir.isPending} onClick={() => atribuir.mutate()}>
            Atribuir
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

interface LinhaTarefa {
  chave: number;
  title: string;
  systemName: string;
  cadaContador: number | string;
  meterKind: 'ODOMETER' | 'HOURMETER';
  cadaDias: number | string;
  instructions: string;
}

let proximaChave = 1;
function linhaVazia(meterKind: 'ODOMETER' | 'HOURMETER'): LinhaTarefa {
  return { chave: proximaChave++, title: '', systemName: '', cadaContador: '', meterKind, cadaDias: '', instructions: '' };
}

function NovoPlanoModal({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [assetTypeId, setAssetTypeId] = useState<string | null>(null);
  const [objective, setObjective] = useState('');
  const [sourceReference, setSourceReference] = useState('');
  const [linhas, setLinhas] = useState<LinhaTarefa[]>([linhaVazia('ODOMETER')]);

  const { data: tipos } = useQuery({
    queryKey: ['asset-types'],
    queryFn: () => api<AssetTypeView[]>('/asset-types'),
  });
  const tipo = (tipos ?? []).find((t) => t.id === assetTypeId);
  const kindDoTipo: 'ODOMETER' | 'HOURMETER' = tipo?.primaryMeter === 'HOURMETER' ? 'HOURMETER' : 'ODOMETER';

  const mudar = (chave: number, patch: Partial<LinhaTarefa>) =>
    setLinhas((ls) => ls.map((l) => (l.chave === chave ? { ...l, ...patch } : l)));

  const criar = useMutation({
    mutationFn: () =>
      api('/maintenance-plans', {
        method: 'POST',
        body: {
          name: name.trim(),
          assetTypeId,
          objective: objective.trim() || null,
          sourceReference: sourceReference.trim() || null,
          tasks: linhas
            .filter((l) => l.title.trim())
            .map((l) => ({
              title: l.title.trim(),
              systemName: l.systemName.trim() || null,
              instructions: l.instructions.trim() || null,
              triggers: [
                ...(l.cadaContador !== '' ? [{ type: 'METER_INTERVAL', meterKind: kindDoTipo, interval: Number(l.cadaContador) }] : []),
                ...(l.cadaDias !== '' ? [{ type: 'CALENDAR_DAYS', interval: Number(l.cadaDias) }] : []),
              ],
              parts: [],
            })),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Plano criado', message: name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['plans'] });
      setName('');
      setObjective('');
      setSourceReference('');
      setLinhas([linhaVazia(kindDoTipo)]);
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível criar', message: e.message, color: 'red' }),
  });

  const tarefasValidas = linhas.filter((l) => l.title.trim() && (l.cadaContador !== '' || l.cadaDias !== ''));
  const valido = name.trim().length >= 2 && tarefasValidas.length > 0 && tarefasValidas.length === linhas.filter((l) => l.title.trim()).length;

  return (
    <Modal opened={aberto} onClose={fechar} title="Novo plano de manutenção" size="xl" centered>
      <Stack gap="xs">
        <SeccaoForm titulo="Documento">
          <Group grow align="flex-start">
            <TextInput label="Nome do plano" required placeholder="Camiões pesados — plano do fabricante" value={name} onChange={(e) => setName(e.currentTarget.value)} data-autofocus />
            <Select
              label="Tipo de equipamento"
              description="Decide se os intervalos contam em km ou em horas."
              placeholder="qualquer"
              clearable
              data={(tipos ?? []).map((t) => ({ value: t.id, label: t.name }))}
              value={assetTypeId}
              onChange={(v) => {
                setAssetTypeId(v);
                const k = (tipos ?? []).find((t) => t.id === v)?.primaryMeter === 'HOURMETER' ? 'HOURMETER' : 'ODOMETER';
                setLinhas((ls) => ls.map((l) => ({ ...l, meterKind: k })));
              }}
            />
          </Group>
          <Group grow mt="xs">
            <TextInput label="Objetivo" placeholder="Manter a frota dentro dos intervalos do fabricante" value={objective} onChange={(e) => setObjective(e.currentTarget.value)} />
            <TextInput label="Origem (manual, norma)" placeholder="Manual Mercedes-Benz Actros, cap. 4" value={sourceReference} onChange={(e) => setSourceReference(e.currentTarget.value)} />
          </Group>
        </SeccaoForm>

        <SeccaoForm titulo="Tarefas" descricao={`Cada tarefa precisa de um intervalo: a cada N ${kindDoTipo === 'HOURMETER' ? 'horas' : 'km'}, a cada N dias, ou os dois (vence o que chegar primeiro).`}>
          <Stack gap="xs">
            {linhas.map((l, i) => (
              <Group key={l.chave} align="flex-end" wrap="nowrap" gap="xs">
                <Text size="xs" c="dimmed" w={18} ta="right">
                  {i + 1}
                </Text>
                <TextInput label={i === 0 ? 'Tarefa' : undefined} placeholder="Mudar óleo e filtros" style={{ flex: 2 }} value={l.title} onChange={(e) => mudar(l.chave, { title: e.currentTarget.value })} />
                <TextInput label={i === 0 ? 'Sistema' : undefined} placeholder="Motor" style={{ flex: 1 }} value={l.systemName} onChange={(e) => mudar(l.chave, { systemName: e.currentTarget.value })} />
                <NumberInput label={i === 0 ? `A cada (${kindDoTipo === 'HOURMETER' ? 'h' : 'km'})` : undefined} placeholder={kindDoTipo === 'HOURMETER' ? '250' : '5000'} min={1} w={120} thousandSeparator=" " value={l.cadaContador} onChange={(v) => mudar(l.chave, { cadaContador: typeof v === 'number' ? v : '' })} />
                <NumberInput label={i === 0 ? 'A cada (dias)' : undefined} placeholder="180" min={1} w={110} value={l.cadaDias} onChange={(v) => mudar(l.chave, { cadaDias: typeof v === 'number' ? v : '' })} />
                <ActionIcon variant="subtle" color="red" aria-label="Remover tarefa" disabled={linhas.length === 1} onClick={() => setLinhas((ls) => ls.filter((x) => x.chave !== l.chave))}>
                  <IconTrash size={15} />
                </ActionIcon>
              </Group>
            ))}
            <Button size="xs" variant="default" leftSection={<IconPlus size={13} />} onClick={() => setLinhas((ls) => [...ls, linhaVazia(kindDoTipo)])} style={{ alignSelf: 'flex-start' }}>
              Mais uma tarefa
            </Button>
          </Stack>
        </SeccaoForm>

        {linhas.length > 0 && linhas[0].title && (
          <Textarea label={`Instruções da tarefa 1 (opcional)`} value={linhas[0].instructions} onChange={(e) => mudar(linhas[0].chave, { instructions: e.currentTarget.value })} autosize minRows={2} />
        )}

        <Group justify="flex-end" mt="xs">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={!valido}>
            Criar plano
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function CatalogoModal({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [code, setCode] = useState<string | null>(null);
  const [assetTypeId, setAssetTypeId] = useState<string | null>(null);
  const [assetId, setAssetId] = useState<string | null>(null);

  const { data: modelos } = useQuery({
    queryKey: ['plans', 'catalog'],
    queryFn: () => api<Modelo[]>('/maintenance-plans/catalog'),
    enabled: aberto,
  });
  const { data: tipos } = useQuery({
    queryKey: ['asset-types'],
    queryFn: () => api<AssetTypeView[]>('/asset-types'),
    enabled: aberto,
  });
  const { data: ativos } = useQuery({
    queryKey: ['assets', 'para-plano'],
    queryFn: () => api<Paged<AssetSummary>>('/assets?size=200'),
    enabled: aberto,
  });
  const modelo = (modelos ?? []).find((m) => m.code === code);

  const aplicar = useMutation({
    mutationFn: () => {
      const q = new URLSearchParams();
      if (assetTypeId) q.set('assetTypeId', assetTypeId);
      if (assetId) q.set('assetId', assetId);
      return api<{ planName: string; taskCount: number; checklistItemCount: number; predictiveProgramCount: number; partCount: number }>(
        `/maintenance-plans/from-catalog/${code}/apply?${q.toString()}`,
        { method: 'POST' },
      );
    },
    onSuccess: (r) => {
      notifications.show({
        title: 'Modelo aplicado',
        message: `${r.planName}: ${r.taskCount} tarefas, ${r.checklistItemCount} pontos de inspeção, ${r.predictiveProgramCount} programas preditivos, ${r.partCount} peças.`,
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['plans'] });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      if (assetId) queryClient.invalidateQueries({ queryKey: ['asset-plan', assetId] });
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível aplicar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Aplicar um modelo do catálogo" centered>
      <Stack gap="sm">
        <Select
          label="Modelo"
          placeholder="Escolha o equipamento"
          data={(modelos ?? []).map((m) => ({ value: m.code, label: `${m.name} (${m.taskCount} tarefas)` }))}
          value={code}
          onChange={setCode}
          data-autofocus
        />
        {modelo && (
          <Alert variant="light" color="gray">
            {modelo.description}
          </Alert>
        )}
        <Select
          label="Tipo de equipamento a que se aplica"
          placeholder="opcional"
          clearable
          data={(tipos ?? []).map((t) => ({ value: t.id, label: t.name }))}
          value={assetTypeId}
          onChange={setAssetTypeId}
        />
        <Select
          label="Atribuir já a um ativo"
          description="Opcional. O plano fica atribuído e os programas preditivos são criados para ele."
          placeholder="nenhum por agora"
          clearable
          searchable
          data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
          value={assetId}
          onChange={setAssetId}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={!code} loading={aplicar.isPending} onClick={() => aplicar.mutate()}>
            Aplicar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
