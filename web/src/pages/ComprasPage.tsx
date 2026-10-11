import { Badge, SegmentedControl, Stack, Text } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura } from '../components/Procura';
import { Devolucoes } from './compras/Devolucoes';
import { NovaOrdem } from './compras/NovaOrdem';
import { COR_ESTADO, type Ordem } from './compras/tipos';
import { fmtDate, fmtMoney } from '../lib/format';

/**
 * As ordens de compra.
 *
 * <p>Antes disto, a compra era um telefonema: alguém olhava para a lista do
 * que falta, ligava ao fornecedor, e o que foi pedido ficava na memória de
 * quem ligou. Quando a peça não chegava, não havia a quem mostrar nada.
 *
 * <p>O filtro começa em «por aprovar» de propósito: é o único estado em que
 * alguém está à espera de uma pessoa, e é o que faz uma encomenda parar uma
 * semana sem ninguém dar por isso.
 */
export function ComprasPage() {
  const navegar = useNavigate();
  const [estado, setEstado] = useState('PENDING_APPROVAL');
  const [procura, setProcura] = useState('');
  const [nova, setNova] = useState(false);

  const { data } = useQuery({
    queryKey: ['compras', estado],
    queryFn: () =>
      api<Ordem[]>(estado === 'TODAS' ? '/purchase-orders' : `/purchase-orders?status=${estado}`),
  });

  const linhas = (data ?? []).filter((o) =>
    procura.trim()
      ? `${o.number} ${o.supplierName}`.toLowerCase().includes(procura.toLowerCase())
      : true,
  );

  return (
    <Stack gap="lg">
      <Devolucoes />

      <Painel
        titulo="Ordens de compra"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNova(true)} destaque>
              Nova ordem
            </BotaoBarra>
            <SeparadorBarra />
            <SegmentedControl
              size="xs"
              value={estado}
              onChange={setEstado}
              data={[
                { value: 'PENDING_APPROVAL', label: 'Por aprovar' },
                { value: 'APPROVED', label: 'Aprovadas' },
                { value: 'SENT', label: 'Enviadas' },
                { value: 'DRAFT', label: 'Rascunhos' },
                { value: 'TODAS', label: 'Todas' },
              ]}
            />
            <SeparadorBarra />
            <CampoProcura
              valor={procura}
              aoMudar={setProcura}
              placeholder="Número ou fornecedor…"
            />
          </>
        }
      >
        <Grelha
          id="compras"
          linhas={linhas}
          chave={(o) => o.id}
          larguraMinima={940}
          aoAbrir={(o) => navegar(`/compras/${o.id}`)}
          vazio={
            estado === 'PENDING_APPROVAL'
              ? 'Nada à espera de aprovação.'
              : 'Nenhuma ordem neste estado.'
          }
          colunas={[
            {
              id: 'numero',
              titulo: 'Número',
              fixa: true,
              largura: 130,
              valor: (o) => o.number,
              render: (o) => (
                <Text size="sm" fw={700} ff="monospace">
                  {o.number}
                </Text>
              ),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 130,
              valor: (o) => o.statusLabel,
              render: (o) => (
                <Badge size="sm" variant="light" color={COR_ESTADO[o.status]}>
                  {o.statusLabel}
                </Badge>
              ),
            },
            { id: 'fornecedor', titulo: 'Fornecedor', valor: (o) => o.supplierName },
            {
              id: 'linhas',
              titulo: 'Artigos',
              largura: 80,
              alinhar: 'right',
              valor: (o) => o.lines.length,
            },
            {
              id: 'total',
              titulo: 'Total',
              largura: 140,
              alinhar: 'right',
              valor: (o) => o.total,
              render: (o) => (
                <Text size="sm" fw={600}>
                  {fmtMoney(o.total)}
                </Text>
              ),
            },
            {
              id: 'transferir',
              titulo: 'A transferir',
              largura: 140,
              alinhar: 'right',
              escondida: true,
              valor: (o) => o.payable,
              render: (o) => fmtMoney(o.payable),
            },
            {
              id: 'entrega',
              titulo: 'Entrega até',
              largura: 120,
              valor: (o) => o.expectedAt ?? null,
              render: (o) => (o.expectedAt ? fmtDate(o.expectedAt) : '—'),
            },
            {
              id: 'quem',
              titulo: 'Criada por',
              largura: 150,
              valor: (o) => o.createdByLabel ?? null,
            },
          ]}
        />
      </Painel>

      <NovaOrdem aberta={nova} aoFechar={() => setNova(false)} />
    </Stack>
  );
}
