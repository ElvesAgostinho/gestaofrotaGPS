import { Alert, Badge, Group, Stack, Text } from '@mantine/core';
import { IconAlertTriangle, IconBuildingWarehouse, IconPlus, IconTransfer } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura } from '../components/Procura';
import { MovimentoStockForm, NovaPecaForm, NovoArmazemForm } from './parts/FormulariosStock';
import { fmtMoney, fmtNumber } from '../lib/format';

interface Part {
  id: string;
  name: string;
  partNumber?: string | null;
  systemCode?: string | null;
  category?: string | null;
  unit?: string | null;
  minQuantity?: number | null;
  averageCost?: number | null;
  currency: string;
  totalQuantity?: number | null;
  /** Calculado pelo servidor. Recalcular aqui daria duas regras que divergem. */
  lowStock: boolean;
}

export function PartsPage() {
  const [search, setSearch] = useState('');
  const [novaPeca, setNovaPeca] = useState(false);
  const [novoArmazem, setNovoArmazem] = useState(false);
  const [movimento, setMovimento] = useState(false);

  const { data: parts } = useQuery({
    queryKey: ['parts'],
    queryFn: () => api<Part[]>('/parts'),
  });

  const { data: low } = useQuery({
    queryKey: ['parts', 'low-stock'],
    queryFn: () => api<Part[]>('/parts/low-stock'),
  });

  const rows = (parts ?? []).filter((p) =>
    search.trim()
      ? `${p.name} ${p.partNumber ?? ''}`.toLowerCase().includes(search.toLowerCase())
      : true,
  );

  return (
    <Stack gap="lg">

      {!!low?.length && (
        <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
          <b>{low.length} peça(s) abaixo do stock mínimo:</b>{' '}
          {low.map((p) => p.name).join(', ')}
        </Alert>
      )}

      <Painel
        titulo="Peças e armazém"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovaPeca(true)} destaque>
              Nova peça
            </BotaoBarra>
            <BotaoBarra icone={<IconTransfer size={15} />} onClick={() => setMovimento(true)}>
              Movimento de stock
            </BotaoBarra>
            <BotaoBarra
              icone={<IconBuildingWarehouse size={15} />}
              onClick={() => setNovoArmazem(true)}
            >
              Novo armazém
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura
              valor={search}
              aoMudar={setSearch}
              placeholder="Nome ou número de peça…"
            />
          </>
        }
      >

        <Grelha
          id="pecas"
          linhas={rows}
          chave={(p) => p.id}
          larguraMinima={720}
          vazio={search ? 'Nenhuma peça corresponde à procura.' : 'Ainda não há peças.'}
          colunas={[
            {
              id: 'nome',
              titulo: 'Peça',
              fixa: true,
              valor: (p) => p.name,
              render: (p) => (
                <Group gap={6} wrap="nowrap">
                  <Text fw={600} size="sm">
                    {p.name}
                  </Text>
                  {p.lowStock && (
                    <Badge size="xs" color="orange" variant="light">
                      abaixo do mínimo
                    </Badge>
                  )}
                </Group>
              ),
            },
            { id: 'numero', titulo: 'Nº de peça', largura: 140, valor: (p) => p.partNumber ?? null },
            { id: 'sistema', titulo: 'Sistema', largura: 110, valor: (p) => p.systemCode ?? null },
            { id: 'categoria', titulo: 'Categoria', largura: 120, escondida: true, valor: (p) => p.category ?? null },
            {
              id: 'stock',
              titulo: 'Em stock',
              largura: 110,
              alinhar: 'right',
              valor: (p) => p.totalQuantity ?? 0,
              render: (p) => (
                <Text size="sm" fw={600}>
                  {fmtNumber(p.totalQuantity ?? 0)} {p.unit ?? ''}
                </Text>
              ),
            },
            {
              id: 'minimo',
              titulo: 'Mínimo',
              largura: 90,
              alinhar: 'right',
              valor: (p) => p.minQuantity ?? null,
              render: (p) => (p.minQuantity != null ? fmtNumber(p.minQuantity) : '—'),
            },
            {
              id: 'custo',
              titulo: 'Custo médio',
              largura: 120,
              alinhar: 'right',
              valor: (p) => p.averageCost ?? null,
              render: (p) => (p.averageCost != null ? fmtMoney(p.averageCost) : '—'),
            },
          ]}
        />
      </Painel>

      <NovaPecaForm opened={novaPeca} onClose={() => setNovaPeca(false)} />
      <NovoArmazemForm opened={novoArmazem} onClose={() => setNovoArmazem(false)} />
      <MovimentoStockForm opened={movimento} onClose={() => setMovimento(false)} />
    </Stack>
  );
}
