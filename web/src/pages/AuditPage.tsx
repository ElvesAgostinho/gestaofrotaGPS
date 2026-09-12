import { Alert, Badge, Button, Card, Group, Select, Stack, Text, TextInput } from '@mantine/core';
import { IconSearch, IconShieldLock } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { fmtDateTime } from '../lib/format';

interface AuditEntry {
  id: string;
  at: string;
  userId?: string | null;
  userName?: string | null;
  action: string;
  actionLabel: string;
  entityType?: string | null;
  entityId?: string | null;
  summary?: string | null;
  ip?: string | null;
}

interface AuditAction {
  code: string;
  label: string;
}

interface Paged<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const PAGE_SIZE = 50;

/**
 * Registo de auditoria da empresa.
 *
 * <p>O servidor gravava estas linhas desde o início e nunca houve por onde as
 * ler. Pior: a tabela não sabia a que empresa pertencia cada linha, por isso
 * mostrá-las teria revelado a atividade de todas as empresas ao mesmo tempo.
 * A coluna existe agora e a consulta filtra sempre por empresa — o que esta
 * página mostra é, por construção, só o que aconteceu aqui dentro.
 */
export function AuditPage() {
  const [page, setPage] = useState(1);
  const [action, setAction] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [applied, setApplied] = useState('');

  const query = new URLSearchParams({
    page: String(page - 1),
    size: String(PAGE_SIZE),
  });
  if (action) query.set('action', action);
  if (applied.trim()) query.set('search', applied.trim());

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['audit', page, action, applied],
    queryFn: () => api<Paged<AuditEntry>>(`/audit?${query.toString()}`),
    placeholderData: keepPreviousData,
  });

  const { data: actions } = useQuery({
    queryKey: ['audit', 'actions'],
    queryFn: () => api<AuditAction[]>('/audit/actions'),
  });

  const rows = data?.content ?? [];

  function applySearch() {
    setApplied(search);
    setPage(1);
  }

  return (
    <Stack gap="lg">
      <div>
        <Text c="dimmed" size="sm">
          Quem fez o quê, quando e a partir de que endereço — apenas nesta empresa.
        </Text>
      </div>

      <Alert variant="light" color="blue" icon={<IconShieldLock size={18} />}>
        Este registo é <b>só da sua empresa</b>. A atividade de outras empresas que usem o
        IMBONDEIRO OS nunca aparece aqui, nem através da procura.
      </Alert>

      <Painel titulo="Registo de auditoria" semPadding>
        <Group align="flex-end" gap="sm">
          <Select
            label="Ação"
            placeholder="Todas"
            clearable
            searchable
            value={action}
            onChange={(value) => {
              setAction(value);
              setPage(1);
            }}
            data={(actions ?? []).map((a) => ({ value: a.code, label: a.label }))}
            style={{ minWidth: 260 }}
          />
          <TextInput
            label="Procurar no detalhe"
            placeholder="matrícula, nome, número da ordem…"
            value={search}
            leftSection={<IconSearch size={14} />}
            onChange={(e) => setSearch(e.currentTarget.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') applySearch();
            }}
            style={{ flex: 1, minWidth: 220 }}
          />
          <Button onClick={applySearch}>Procurar</Button>
        </Group>
            </Painel>

      {isError && (
        <Alert color="red" variant="light">
          Não foi possível carregar o registo. {(error as Error)?.message}
          <Group mt="sm">
            <Button size="xs" variant="default" onClick={() => refetch()}>
              Tentar outra vez
            </Button>
          </Group>
        </Alert>
      )}

      <Card p={0}>
        <Grelha
          id="auditoria"
          linhas={rows}
          chave={(e) => e.id}
          carregando={isLoading}
          larguraMinima={900}
          porPagina={PAGE_SIZE}
          servidor={{
            pagina: page,
            totalPaginas: data?.totalPages ?? 1,
            total: data?.totalElements ?? 0,
            aoMudarPagina: setPage,
          }}
          vazio={applied || action ? 'Nada corresponde a este filtro.' : 'Ainda não há atividade registada.'}
          colunas={[
            {
              id: 'quando',
              titulo: 'Quando',
              largura: 160,
              semQuebra: true,
              fixa: true,
              valor: (e) => e.at,
              render: (e) => fmtDateTime(e.at),
            },
            {
              id: 'quem',
              titulo: 'Quem',
              largura: 180,
              valor: (e) => e.userName ?? null,
              /* Uma conta eliminada deixa a linha sem nome: o registo
                 sobrevive à pessoa, que é o ponto de o ter. */
              render: (e) => e.userName ?? <Text span c="dimmed">conta removida</Text>,
            },
            {
              id: 'acao',
              titulo: 'Ação',
              largura: 240,
              valor: (e) => e.actionLabel,
              render: (e) => (
                <Badge variant="light" color={colour(e.action)} style={{ maxWidth: '100%' }}>
                  {e.actionLabel}
                </Badge>
              ),
            },
            {
              id: 'detalhe',
              titulo: 'Detalhe',
              valor: (e) => e.summary ?? null,
              render: (e) => (
                <Text size="sm" lineClamp={2}>
                  {e.summary ?? '—'}
                </Text>
              ),
            },
            {
              id: 'ip',
              titulo: 'Endereço',
              largura: 130,
              valor: (e) => e.ip ?? null,
              render: (e) => (
                <Text size="xs" c="dimmed" ff="monospace">
                  {e.ip ?? '—'}
                </Text>
              ),
            },
          ]}
        />
      </Card>

    </Stack>
  );
}

/**
 * Cor por família de ação. O bloqueio de motor destaca-se de propósito: numa
 * lista longa, é a linha que alguém a auditar a empresa procura primeiro.
 */
function colour(action: string) {
  if (action.startsWith('command.')) return 'red';
  if (action.startsWith('team.') || action.startsWith('user.')) return 'grape';
  if (action.startsWith('work_order.') || action.startsWith('failure.')) return 'blue';
  if (action.endsWith('.delete') || action.includes('remove')) return 'orange';
  return 'gray';
}
