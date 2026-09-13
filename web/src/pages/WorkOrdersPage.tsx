/**
 * Lista de ordens de manutenção, em painel ERP.
 *
 * <p>Os rótulos de estado, tipo e prioridade vêm do servidor. O ecrã tinha o
 * seu próprio mapa e ficou a faltar-lhe metade dos estados quando o módulo de
 * manutenção os acrescentou — uma ordem em orçamento aparecia ao utilizador
 * como «QUOTING». Os mapas locais só ficaram para a cor.
 */
import { SegmentedControl, Stack, Text, TextInput } from '@mantine/core';
import { IconFileExport, IconPlus, IconPrinter, IconSearch } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Grelha } from '../components/Grelha';
import { api, downloadFile, openFile } from '../api/client';
import {
  BarraEstado,
  BotaoBarra,
  COR_ESTADO_OM,
  COR_PRIORIDADE_OM,
  Painel,
  Ponto,
  SeparadorBarra,
} from '../components/erp';
import { fmtDate } from '../lib/format';
import { NovaOrdemForm } from './workorders/NovaOrdemForm';

interface WorkOrder {
  id: string;
  number: string;
  assetId: string;
  assetTag: string;
  type: string;
  status: string;
  priority: string;
  title: string;
  statusLabel?: string | null;
  typeLabel?: string | null;
  priorityLabel?: string | null;
  openedAt: string;
  assignedTo?: string | null;
  taskCount: number;
  tasksDone: number;
}

export function WorkOrdersPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('todas');
  const [procura, setProcura] = useState('');
  const [novaAberta, setNovaAberta] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['work-orders', filter],
    queryFn: () => api<{ content: WorkOrder[]; totalElements: number }>(pathFor(filter)),
  });

  const todas = data?.content ?? [];

  // Procura local: a lista traz até 200 linhas, e filtrar no browser responde
  // enquanto se escreve em vez de ir ao servidor a cada tecla.
  const rows = useMemo(() => {
    const t = procura.trim().toLowerCase();
    if (!t) return todas;
    return todas.filter((w) =>
      [w.number, w.assetTag, w.title, w.assignedTo ?? '', w.statusLabel ?? '']
        .join(' ')
        .toLowerCase()
        .includes(t),
    );
  }, [todas, procura]);

  const porFechar = rows.filter((r) => r.status !== 'CLOSED' && r.status !== 'CANCELLED').length;

  return (
    <Stack gap="sm">
      <NovaOrdemForm aberto={novaAberta} fechar={() => setNovaAberta(false)} />

      <Painel
        titulo="Ordens de manutenção"
        semPadding
        acoes={
          <>
            <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setNovaAberta(true)}>
              Nova ordem
            </BotaoBarra>
            <SeparadorBarra />
            <BotaoBarra
              icone={<IconFileExport size={13} />}
              onClick={() => descarregar('work-orders.xlsx')}
              titulo="Todas as ordens em Excel"
            >
              Excel
            </BotaoBarra>
            <BotaoBarra
              icone={<IconPrinter size={13} />}
              onClick={() => openFile('/reports/work-orders.pdf').catch(() => undefined)}
              titulo="Todas as ordens em PDF, com o timbre da empresa"
            >
              PDF
            </BotaoBarra>
            <BotaoBarra
              icone={<IconPrinter size={13} />}
              onClick={() => openFile('/reports/maintenance-by-asset.pdf').catch(() => undefined)}
              titulo="Custo de manutenção por ativo (PDF)"
            >
              Custo por viatura
            </BotaoBarra>
            <SeparadorBarra />
            <SegmentedControl
              size="xs"
              value={filter}
              onChange={setFilter}
              data={[
                { value: 'todas', label: 'Todas' },
                { value: 'minhas', label: 'As minhas' },
                { value: 'OPEN', label: 'Abertas' },
                { value: 'IN_PROGRESS', label: 'Em manutenção' },
                { value: 'DONE', label: 'Concluídas' },
              ]}
            />
            <TextInput
              size="xs"
              placeholder="Procurar…"
              leftSection={<IconSearch size={12} />}
              value={procura}
              onChange={(e) => setProcura(e.currentTarget.value)}
              style={{ marginLeft: 'auto', width: 190 }}
            />
          </>
        }
        rodape={
          <BarraEstado
            itens={[
              { rotulo: 'Registos', valor: rows.length },
              { rotulo: 'Por fechar', valor: porFechar },
              { rotulo: 'Urgentes', valor: rows.filter((r) => r.priority === 'URGENT').length },
            ]}
          />
        }
      >
        <Grelha
          id="ordens"
          linhas={rows}
          chave={(w) => w.id}
          carregando={isLoading}
          larguraMinima={980}
          aoAbrir={(w) => navigate(`/ordens/${w.id}`)}
          vazio={
            procura
              ? 'Nada corresponde a essa procura.'
              : 'Nenhuma ordem neste filtro. Use «Nova ordem» para abrir a primeira.'
          }
          rodape={
            (data?.totalElements ?? 0) > 200 ? (
              <Text size="xs" c="orange">
                Só as 200 mais recentes de {data?.totalElements} estão carregadas — use os filtros.
              </Text>
            ) : undefined
          }
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
            {
              id: 'ativo',
              titulo: 'Ativo',
              largura: 110,
              semQuebra: true,
              valor: (w) => w.assetTag,
              render: (w) => (
                <Text component={Link} to={`/ativos/${w.assetId}`} size="xs" c="var(--erp-dourado-escuro)">
                  {w.assetTag}
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
              id: 'tarefas',
              titulo: 'Tarefas',
              largura: 80,
              alinhar: 'right',
              valor: (w) => (w.taskCount > 0 ? w.tasksDone / w.taskCount : null),
              render: (w) => (w.taskCount > 0 ? `${w.tasksDone}/${w.taskCount}` : '—'),
            },
            {
              id: 'aberta',
              titulo: 'Aberta em',
              largura: 100,
              alinhar: 'right',
              valor: (w) => w.openedAt,
              render: (w) => fmtDate(w.openedAt),
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
    </Stack>
  );
}

async function descarregar(ficheiro: string) {
  try {
    await downloadFile(`/reports/${ficheiro}`, ficheiro);
  } catch {
    // O cliente já mostra a razão ao utilizador; aqui não há nada a acrescentar.
  }
}

function pathFor(filter: string) {
  if (filter === 'todas') return '/work-orders?size=200';
  if (filter === 'minhas') return '/work-orders?assignedTo=me&size=200';
  return `/work-orders?status=${filter}&size=200`;
}
