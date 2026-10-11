import {
  ActionIcon,
  Alert,
  Button,
  Grid,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconInfoCircle, IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import { SeccaoForm } from '../../components/erp';
import { fmtMoney } from '../../lib/format';
import type { ConfiguracaoCompras, Ordem } from './tipos';

/**
 * Escrever uma ordem de compra.
 *
 * <p>Ao escolher o fornecedor e a peça, o preço aparece sozinho: foi cotado uma
 * vez na ficha dele e não há razão para o escrever outra vez — nem para o
 * escrever diferente por engano. A referência <i>dele</i> vem junto, porque é
 * com ela que o armazém do fornecedor encontra a peça, e é por não ir que
 * chega a peça errada.
 *
 * <p>O total é calculado aqui só para a pessoa o ver enquanto escreve. O que
 * vale é o que o servidor soma — um total vindo do ecrã seria uma forma de
 * aprovar dez milhões com linhas de dez mil.
 */

interface PecaSimples {
  id: string;
  name: string;
  partNumber?: string | null;
  unit?: string | null;
  specification?: string | null;
}

interface FornecedorSimples {
  id: string;
  name: string;
  active: boolean;
  email?: string | null;
}

interface PrecoSimples {
  id: string;
  partId: string;
  partName: string;
  supplierPartNumber?: string | null;
  price: number;
  leadTimeDays?: number | null;
}

interface AtivoSimples {
  id: string;
  tag: string;
  name: string;
}

interface LinhaEmEdicao {
  partId: string | null;
  description: string;
  specification: string;
  supplierPartNumber: string;
  quantity: string;
  unit: string;
  unitPrice: string;
  assetId: string | null;
}

const LINHA_VAZIA: LinhaEmEdicao = {
  partId: null,
  description: '',
  specification: '',
  supplierPartNumber: '',
  quantity: '1',
  unit: '',
  unitPrice: '0',
  assetId: null,
};

export function NovaOrdem({
  aberta,
  aoFechar,
  linhasIniciais,
  fornecedorInicial,
}: {
  aberta: boolean;
  aoFechar: () => void;
  linhasIniciais?: Partial<LinhaEmEdicao>[];
  fornecedorInicial?: string;
}) {
  const qc = useQueryClient();
  const navegar = useNavigate();
  const [supplierId, setSupplierId] = useState<string | null>(fornecedorInicial ?? null);
  const [expectedAt, setExpectedAt] = useState('');
  const [notes, setNotes] = useState('');
  const [linhas, setLinhas] = useState<LinhaEmEdicao[]>([{ ...LINHA_VAZIA }]);

  useEffect(() => {
    if (aberta) {
      setSupplierId(fornecedorInicial ?? null);
      setLinhas(
        linhasIniciais && linhasIniciais.length > 0
          ? linhasIniciais.map((l) => ({ ...LINHA_VAZIA, ...l }))
          : [{ ...LINHA_VAZIA }],
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aberta]);

  const { data: fornecedores } = useQuery({
    queryKey: ['fornecedores'],
    queryFn: () => api<FornecedorSimples[]>('/suppliers'),
    enabled: aberta,
  });

  const { data: pecas } = useQuery({
    queryKey: ['parts'],
    queryFn: () => api<PecaSimples[]>('/parts'),
    enabled: aberta,
  });

  const { data: ativos } = useQuery({
    queryKey: ['assets', 'simples'],
    queryFn: () => api<{ content?: AtivoSimples[] } | AtivoSimples[]>('/assets'),
    enabled: aberta,
  });

  const listaDeAtivos: AtivoSimples[] = Array.isArray(ativos)
    ? ativos
    : ((ativos as { content?: AtivoSimples[] })?.content ?? []);

  const { data: precos } = useQuery({
    queryKey: ['fornecedores', supplierId, 'precos'],
    queryFn: () => api<PrecoSimples[]>(`/suppliers/${supplierId}/prices`),
    enabled: aberta && !!supplierId,
  });

  const { data: cfg } = useQuery({
    queryKey: ['compras', 'configuracao'],
    queryFn: () => api<ConfiguracaoCompras>('/purchase-orders/settings'),
    enabled: aberta,
  });

  /** Ao escolher a peça, traz o que já se sabe dela neste fornecedor. */
  const escolherPeca = (i: number, partId: string | null) => {
    const p = (pecas ?? []).find((x) => x.id === partId);
    const cotacao = (precos ?? []).find((x) => x.partId === partId);
    setLinhas((l) =>
      l.map((linha, j) =>
        j === i
          ? {
              ...linha,
              partId,
              description: p?.name ?? linha.description,
              specification: p?.specification ?? linha.specification,
              unit: p?.unit ?? linha.unit,
              supplierPartNumber: cotacao?.supplierPartNumber ?? linha.supplierPartNumber,
              unitPrice: cotacao ? String(cotacao.price) : linha.unitPrice,
            }
          : linha,
      ),
    );
  };

  const mudar = (i: number, campo: keyof LinhaEmEdicao, valor: string | null) =>
    setLinhas((l) => l.map((linha, j) => (j === i ? { ...linha, [campo]: valor } : linha)));

  const total = linhas.reduce(
    (soma, l) => soma + (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0),
    0,
  );

  const aprovaSozinha =
    cfg?.autoApproveBelow != null && total > 0 && total < cfg.autoApproveBelow;
  const precisaDoDono = cfg?.ownerApprovalAbove != null && total > cfg.ownerApprovalAbove;

  const criar = useMutation({
    mutationFn: () =>
      api<Ordem>('/purchase-orders', {
        method: 'POST',
        body: {
          supplierId,
          expectedAt: expectedAt || null,
          notes: notes.trim() || null,
          lines: linhas
            .filter((l) => l.description.trim())
            .map((l) => ({
              partId: l.partId,
              description: l.description.trim(),
              specification: l.specification.trim() || null,
              supplierPartNumber: l.supplierPartNumber.trim() || null,
              quantity: Number(l.quantity) || 1,
              unit: l.unit.trim() || null,
              unitPrice: Number(l.unitPrice) || 0,
              assetId: l.assetId,
            })),
        },
      }),
    onSuccess: (o) => {
      notifications.show({ color: 'green', message: `Ordem ${o.number} criada.` });
      void qc.invalidateQueries({ queryKey: ['compras'] });
      aoFechar();
      navegar(`/compras/${o.id}`);
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível criar',
        message: e instanceof ApiError ? e.message : 'Tente de novo.',
      }),
  });

  const valida = !!supplierId && linhas.some((l) => l.description.trim());

  return (
    <Modal opened={aberta} onClose={aoFechar} title="Nova ordem de compra" size="90%">
      <Stack gap="sm">
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <Select
              label="Fornecedor"
              placeholder="Escolher"
              required
              searchable
              value={supplierId}
              onChange={setSupplierId}
              data={(fornecedores ?? [])
                .filter((f) => f.active)
                .map((f) => ({ value: f.id, label: f.name }))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Entrega até"
              type="date"
              value={expectedAt}
              onChange={(e) => setExpectedAt(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Observações"
              value={notes}
              onChange={(e) => setNotes(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>

        <SeccaoForm titulo="Artigos">
          <Table withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: '24%' }}>Peça do armazém</Table.Th>
                <Table.Th>Designação</Table.Th>
                <Table.Th style={{ width: '12%' }}>Ref. dele</Table.Th>
                <Table.Th style={{ width: '9%' }}>Qtd.</Table.Th>
                <Table.Th style={{ width: '12%' }}>Preço unit.</Table.Th>
                <Table.Th style={{ width: '14%' }}>Para</Table.Th>
                <Table.Th style={{ width: '11%' }}>Total</Table.Th>
                <Table.Th w={40} />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {linhas.map((l, i) => (
                <Table.Tr key={i}>
                  <Table.Td>
                    <Select
                      size="xs"
                      placeholder="opcional"
                      searchable
                      clearable
                      value={l.partId}
                      onChange={(v) => escolherPeca(i, v)}
                      data={(pecas ?? []).map((p) => ({
                        value: p.id,
                        label: p.partNumber ? `${p.name} (${p.partNumber})` : p.name,
                      }))}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      value={l.description}
                      onChange={(e) => mudar(i, 'description', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      value={l.supplierPartNumber}
                      onChange={(e) => mudar(i, 'supplierPartNumber', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      type="number"
                      value={l.quantity}
                      onChange={(e) => mudar(i, 'quantity', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      type="number"
                      value={l.unitPrice}
                      onChange={(e) => mudar(i, 'unitPrice', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Select
                      size="xs"
                      placeholder="equipamento"
                      searchable
                      clearable
                      value={l.assetId}
                      onChange={(v) => mudar(i, 'assetId', v)}
                      data={listaDeAtivos.map((a) => ({ value: a.id, label: a.tag }))}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {fmtMoney((Number(l.quantity) || 0) * (Number(l.unitPrice) || 0))}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      aria-label="Retirar linha"
                      disabled={linhas.length === 1}
                      onClick={() => setLinhas((x) => x.filter((_, j) => j !== i))}
                    >
                      <IconTrash size={15} />
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>

          <Group justify="space-between" mt="xs">
            <Button
              size="xs"
              variant="default"
              leftSection={<IconPlus size={14} />}
              onClick={() => setLinhas((l) => [...l, { ...LINHA_VAZIA }])}
            >
              Mais uma linha
            </Button>
            <Text fw={700}>Total: {fmtMoney(total)}</Text>
          </Group>
        </SeccaoForm>

        {(aprovaSozinha || precisaDoDono) && (
          <Alert
            color={precisaDoDono ? 'orange' : 'blue'}
            variant="light"
            icon={<IconInfoCircle size={18} />}
          >
            {precisaDoDono
              ? `Acima de ${fmtMoney(cfg?.ownerApprovalAbove ?? 0)}, esta ordem só é aprovada pelo dono.`
              : `Abaixo de ${fmtMoney(cfg?.autoApproveBelow ?? 0)}, fica aprovada assim que for submetida.`}
          </Alert>
        )}

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button disabled={!valida} loading={criar.isPending} onClick={() => criar.mutate()}>
            Criar ordem
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
