import { Badge, Button, Group, Modal, NumberInput, Progress, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { AssetSummary, LocationView, Paged } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { Kpi } from '../components/Kpi';
import { fmtMoney } from '../lib/format';

interface Orcamento {
  id: string;
  year: number;
  scope: 'ORG' | 'LOCATION' | 'ASSET' | string;
  assetId?: string | null;
  locationId?: string | null;
  target: string;
  category: string;
  categoryLabel: string;
  amount: number;
  currency: string;
  actual: number;
  remaining: number;
  percent: number;
  yearPercent: number;
  status: 'OK' | 'AHEAD' | 'WARNING' | 'EXCEEDED' | string;
  notes?: string | null;
}

const ESTADO: Record<string, { label: string; color: string }> = {
  OK: { label: 'Dentro do previsto', color: 'green' },
  AHEAD: { label: 'A gastar depressa', color: 'yellow' },
  WARNING: { label: 'Acima de 80 %', color: 'orange' },
  EXCEEDED: { label: 'Esgotado', color: 'red' },
};

const AMBITO: Record<string, string> = { ORG: 'Toda a frota', LOCATION: 'Filial / centro de custo', ASSET: 'Viatura' };

/**
 * Orçamento anual: o previsto contra o real, por frota, filial ou viatura.
 * O real não se escreve — vem das ordens concluídas e dos abastecimentos.
 */
export function OrcamentosPage() {
  const { has } = useAuth();
  const queryClient = useQueryClient();
  const anoAtual = new Date().getFullYear();
  const [ano, setAno] = useState(anoAtual);
  const [novo, setNovo] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['budgets', ano],
    queryFn: () => api<{ year: number; items: Orcamento[]; years: number[] }>(`/budgets?year=${ano}`),
  });
  const itens = data?.items ?? [];
  const anos = [...new Set([anoAtual, anoAtual + 1, anoAtual - 1, ...(data?.years ?? [])])].sort((a, b) => b - a);
  const previsto = itens.reduce((s, b) => s + b.amount, 0);
  const real = itens.reduce((s, b) => s + b.actual, 0);
  const podeGerir = has('SETTINGS_MANAGE');

  const apagar = useMutation({
    mutationFn: (b: Orcamento) => api(`/budgets/${b.id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
    onError: (e: Error) => notifications.show({ title: 'Não foi possível apagar', message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="lg">
      <Group gap="sm" wrap="wrap">
        <Kpi label={`Previsto ${ano}`} value={fmtMoney(previsto)} tone="brand" />
        <Kpi label="Gasto até hoje" value={fmtMoney(real)} tone={previsto > 0 && real > previsto ? 'critical' : 'neutral'} />
        <Kpi
          label="Orçamentos com aviso"
          value={itens.filter((b) => b.status === 'WARNING' || b.status === 'EXCEEDED').length}
          tone={itens.some((b) => b.status === 'EXCEEDED') ? 'critical' : itens.some((b) => b.status === 'WARNING') ? 'warning' : 'good'}
          hint="Os gestores recebem um aviso quando um orçamento passa dos 80 % e outro quando esgota."
        />
        <Kpi label="Ano decorrido" value={`${itens[0]?.yearPercent ?? Math.min(100, Math.round((new Date().getTime() - new Date(anoAtual, 0, 1).getTime()) / 315_360_000))} %`} />
      </Group>

      <Painel
        titulo="Orçamentos"
        semPadding
        acoes={
          <>
            {podeGerir && (
              <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
                Novo orçamento
              </BotaoBarra>
            )}
            <SeparadorBarra />
            <Select size="xs" w={110} data={anos.map((a) => ({ value: String(a), label: String(a) }))} value={String(ano)} onChange={(v) => v && setAno(Number(v))} allowDeselect={false} />
          </>
        }
      >
        <Grelha
          id="orcamentos"
          linhas={itens}
          chave={(b) => b.id}
          carregando={isLoading}
          vazio={`Sem orçamentos para ${ano}. Defina o primeiro: por viatura, por filial ou para toda a frota.`}
          colunas={[
            { id: 'ambito', titulo: 'Âmbito', largura: 170, valor: (b) => AMBITO[b.scope] ?? b.scope },
            {
              id: 'alvo',
              titulo: 'Para',
              fixa: true,
              valor: (b) => b.target,
              render: (b) => (
                <div>
                  <Text size="sm" fw={600}>
                    {b.target}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {b.categoryLabel}
                  </Text>
                </div>
              ),
            },
            { id: 'previsto', titulo: 'Previsto', largura: 140, alinhar: 'right', valor: (b) => b.amount, render: (b) => fmtMoney(b.amount) },
            { id: 'real', titulo: 'Gasto', largura: 140, alinhar: 'right', valor: (b) => b.actual, render: (b) => fmtMoney(b.actual) },
            {
              id: 'restante',
              titulo: 'Restante',
              largura: 140,
              alinhar: 'right',
              valor: (b) => b.remaining,
              render: (b) => <Text size="sm" c={b.remaining < 0 ? 'red' : undefined}>{fmtMoney(b.remaining)}</Text>,
            },
            {
              id: 'progresso',
              titulo: 'Execução',
              largura: 220,
              valor: (b) => b.percent,
              render: (b) => (
                <div>
                  <Progress.Root size="lg">
                    <Progress.Section value={Math.min(100, b.percent)} color={ESTADO[b.status]?.color ?? 'gray'}>
                      <Progress.Label>{b.percent} %</Progress.Label>
                    </Progress.Section>
                  </Progress.Root>
                  <Text size="xs" c="dimmed">
                    ano a {b.yearPercent} %
                  </Text>
                </div>
              ),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 150,
              valor: (b) => ESTADO[b.status]?.label ?? b.status,
              render: (b) => (
                <Badge variant="light" color={ESTADO[b.status]?.color ?? 'gray'} size="sm">
                  {ESTADO[b.status]?.label ?? b.status}
                </Badge>
              ),
            },
            { id: 'notas', titulo: 'Notas', escondida: true, valor: (b) => b.notes ?? null },
            ...(podeGerir
              ? [
                  {
                    id: 'acoes',
                    titulo: '',
                    largura: 90,
                    render: (b: Orcamento) => (
                      <Button size="compact-xs" variant="subtle" color="red" leftSection={<IconTrash size={12} />} onClick={() => window.confirm('Apagar este orçamento?') && apagar.mutate(b)}>
                        Apagar
                      </Button>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Painel>

      <NovoOrcamentoModal aberto={novo} fechar={() => setNovo(false)} anoInicial={ano} />
    </Stack>
  );
}

function NovoOrcamentoModal({ aberto, fechar, anoInicial }: { aberto: boolean; fechar: () => void; anoInicial: number }) {
  const queryClient = useQueryClient();
  const [year, setYear] = useState<number | string>(anoInicial);
  const [scope, setScope] = useState<string | null>('ORG');
  const [assetId, setAssetId] = useState<string | null>(null);
  const [locationId, setLocationId] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>('TOTAL');
  const [amount, setAmount] = useState<number | string>('');
  const [notes, setNotes] = useState('');
  const { data: ativos } = useQuery({ queryKey: ['assets', 'opcoes'], queryFn: () => api<Paged<AssetSummary>>('/assets?size=200'), enabled: aberto });
  const { data: locais } = useQuery({ queryKey: ['locations'], queryFn: () => api<LocationView[]>('/locations'), enabled: aberto });

  const criar = useMutation({
    mutationFn: () =>
      api('/budgets', {
        method: 'POST',
        body: { year: Number(year), scope, assetId: scope === 'ASSET' ? assetId : null, locationId: scope === 'LOCATION' ? locationId : null, category, amount: Number(amount), notes: notes.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Orçamento definido', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      setAmount('');
      fechar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível definir', message: e.message, color: 'red' }),
  });

  const valido = amount !== '' && Number(amount) > 0 && (scope === 'ORG' || (scope === 'ASSET' && assetId) || (scope === 'LOCATION' && locationId));
  return (
    <Modal opened={aberto} onClose={fechar} title="Novo orçamento" centered>
      <Stack gap="sm">
        <Group grow>
          <NumberInput label="Ano" min={2020} max={2100} value={year} onChange={(v) => setYear(typeof v === 'number' ? v : '')} />
          <Select label="Âmbito" data={Object.entries(AMBITO).map(([value, label]) => ({ value, label }))} value={scope} onChange={setScope} allowDeselect={false} />
        </Group>
        {scope === 'ASSET' && (
          <Select label="Viatura" searchable data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))} value={assetId} onChange={setAssetId} />
        )}
        {scope === 'LOCATION' && (
          <Select label="Filial / centro de custo" searchable data={(locais ?? []).map((l) => ({ value: l.id, label: l.name }))} value={locationId} onChange={setLocationId} />
        )}
        <Group grow>
          <Select
            label="Categoria"
            data={[
              { value: 'TOTAL', label: 'Manutenção + combustível' },
              { value: 'MAINTENANCE', label: 'Manutenção' },
              { value: 'FUEL', label: 'Combustível' },
            ]}
            value={category}
            onChange={setCategory}
            allowDeselect={false}
          />
          <NumberInput label="Valor anual" thousandSeparator=" " min={0} value={amount} onChange={(v) => setAmount(typeof v === 'number' ? v : '')} data-autofocus />
        </Group>
        <TextInput label="Notas" value={notes} onChange={(e) => setNotes(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button disabled={!valido} loading={criar.isPending} onClick={() => criar.mutate()}>
            Definir
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
