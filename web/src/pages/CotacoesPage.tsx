import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  SegmentedControl,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconInfoCircle,
  IconMailForward,
  IconPaperclip,
  IconPlus,
  IconTrash,
  IconTrophy,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { BotaoBarra, Painel, SeccaoForm, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { fmtDate, fmtMoney, fmtNumber } from '../lib/format';
import { RegistarResposta } from './compras/RegistarResposta';
import type { Pedido, Proposta } from './compras/tiposCotacao';

/**
 * Pedidos de cotação, e a comparação das propostas.
 *
 * <p>Numa obra, «pediram-se três preços» é uma frase que se diz e que ninguém
 * consegue provar. Aqui fica a quem se pediu, quando, o que cada um cobrou — e
 * a quem se adjudicou, com o motivo quando não foi ao mais barato.
 *
 * <p>A comparação mostra-se como uma matriz, que é como se compara: as linhas
 * do pedido em baixo e os fornecedores ao lado. Marca-se o melhor preço de cada
 * linha, porque é aí que se vê que um fornecedor é barato no filtro e caro no
 * óleo — coisa que um total sozinho esconde.
 */
export function CotacoesPage() {
  const qc = useQueryClient();
  const [estado, setEstado] = useState('OPEN');
  const [novo, setNovo] = useState(false);
  const [aberto, setAberto] = useState<string | null>(null);

  const { data } = useQuery({
    queryKey: ['cotacoes', estado],
    queryFn: () =>
      api<Pedido[]>(estado === 'TODAS' ? '/rfqs' : `/rfqs?status=${estado}`),
  });

  return (
    <Stack gap="lg">
      <Painel
        titulo="Pedidos de cotação"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
              Novo pedido
            </BotaoBarra>
            <SeparadorBarra />
            <SegmentedControl
              size="xs"
              value={estado}
              onChange={setEstado}
              data={[
                { value: 'OPEN', label: 'Em curso' },
                { value: 'AWARDED', label: 'Adjudicados' },
                { value: 'TODAS', label: 'Todos' },
              ]}
            />
          </>
        }
        rodape={
          <Text size="xs" c="dimmed">
            A especificação de cada linha vai no pedido: sem ela, três fornecedores cotam três
            coisas diferentes e a comparação não compara nada.
          </Text>
        }
      >
        <Grelha
          id="cotacoes"
          linhas={data ?? []}
          chave={(p) => p.id}
          larguraMinima={880}
          aoAbrir={(p) => setAberto(p.id)}
          vazio={estado === 'OPEN' ? 'Nenhum pedido em curso.' : 'Nada aqui.'}
          colunas={[
            {
              id: 'numero',
              titulo: 'Número',
              fixa: true,
              largura: 130,
              valor: (p) => p.number,
              render: (p) => (
                <Text size="sm" fw={700} ff="monospace">
                  {p.number}
                </Text>
              ),
            },
            { id: 'titulo', titulo: 'Pedido', valor: (p) => p.title },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 120,
              valor: (p) => p.statusLabel,
              render: (p) => (
                <Badge
                  size="sm"
                  variant="light"
                  color={p.status === 'AWARDED' ? 'green' : p.status === 'OPEN' ? 'blue' : 'gray'}
                >
                  {p.statusLabel}
                </Badge>
              ),
            },
            {
              id: 'respostas',
              titulo: 'Respostas',
              largura: 110,
              alinhar: 'right',
              valor: (p) => p.propostas.filter((x) => x.respondedAt).length,
              render: (p) => (
                <Text size="sm">
                  {p.propostas.filter((x) => x.respondedAt).length} de {p.propostas.length}
                </Text>
              ),
            },
            {
              id: 'prazo',
              titulo: 'Responder até',
              largura: 130,
              valor: (p) => p.dueDate ?? null,
              render: (p) => (p.dueDate ? fmtDate(p.dueDate) : '—'),
            },
            {
              id: 'adjudicado',
              titulo: 'Adjudicado a',
              largura: 180,
              valor: (p) => p.awardedSupplierName ?? null,
            },
          ]}
        />
      </Painel>

      <NovoPedido aberto={novo} aoFechar={() => setNovo(false)} />
      {aberto && (
        <Comparacao
          id={aberto}
          aoFechar={() => {
            setAberto(null);
            void qc.invalidateQueries({ queryKey: ['cotacoes'] });
          }}
        />
      )}
    </Stack>
  );
}

// ==== Novo pedido ==========================================================

interface Simples {
  id: string;
  name: string;
  active?: boolean;
  partNumber?: string | null;
  unit?: string | null;
  specification?: string | null;
}

function NovoPedido({ aberto, aoFechar }: { aberto: boolean; aoFechar: () => void }) {
  const qc = useQueryClient();
  const [title, setTitle] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [fornecedores, setFornecedores] = useState<string[]>([]);
  const [linhas, setLinhas] = useState([
    { partId: null as string | null, description: '', specification: '', quantity: '1', unit: '' },
  ]);

  const { data: lista } = useQuery({
    queryKey: ['fornecedores'],
    queryFn: () => api<Simples[]>('/suppliers'),
    enabled: aberto,
  });
  const { data: pecas } = useQuery({
    queryKey: ['parts'],
    queryFn: () => api<Simples[]>('/parts'),
    enabled: aberto,
  });

  const escolherPeca = (i: number, partId: string | null) => {
    const p = (pecas ?? []).find((x) => x.id === partId);
    setLinhas((l) =>
      l.map((linha, j) =>
        j === i
          ? {
              ...linha,
              partId,
              description: p?.name ?? linha.description,
              specification: p?.specification ?? linha.specification,
              unit: p?.unit ?? linha.unit,
            }
          : linha,
      ),
    );
  };

  const criar = useMutation({
    mutationFn: () =>
      api<Pedido>('/rfqs', {
        method: 'POST',
        body: {
          title: title.trim(),
          dueDate: dueDate || null,
          supplierIds: fornecedores,
          lines: linhas
            .filter((l) => l.description.trim())
            .map((l) => ({
              partId: l.partId,
              description: l.description.trim(),
              specification: l.specification.trim() || null,
              quantity: Number(l.quantity) || 1,
              unit: l.unit.trim() || null,
            })),
        },
      }),
    onSuccess: (p) => {
      notifications.show({ color: 'green', message: `Pedido ${p.number} criado.` });
      void qc.invalidateQueries({ queryKey: ['cotacoes'] });
      setTitle('');
      setFornecedores([]);
      setLinhas([
        { partId: null, description: '', specification: '', quantity: '1', unit: '' },
      ]);
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        message: e instanceof ApiError ? e.message : 'Não foi possível criar.',
      }),
  });

  return (
    <Modal opened={aberto} onClose={aoFechar} title="Novo pedido de cotação" size="xl">
      <Stack gap="sm">
        <Group grow>
          <TextInput
            label="Pedido"
            placeholder="Filtros para a frota de camiões"
            required
            value={title}
            onChange={(e) => setTitle(e.currentTarget.value)}
          />
          <TextInput
            label="Responder até"
            type="date"
            description="Sem data, o pedido fica aberto para sempre"
            value={dueDate}
            onChange={(e) => setDueDate(e.currentTarget.value)}
          />
        </Group>

        <Select
          label="A quem pedir"
          placeholder="Escolher fornecedores"
          searchable
          // Escolhe-se um de cada vez e vai para as etiquetas em baixo: o que
          // interessa é ver quem já está na lista, e isso lê-se melhor assim.
          value={null}
          onChange={(v) => {
            if (v && !fornecedores.includes(v)) {
              setFornecedores((f) => [...f, v]);
            }
          }}
          data={(lista ?? [])
            .filter((f) => f.active !== false && !fornecedores.includes(f.id))
            .map((f) => ({ value: f.id, label: f.name }))}
        />
        {fornecedores.length > 0 && (
          <Group gap={6}>
            {fornecedores.map((id) => (
              <Badge
                key={id}
                variant="light"
                rightSection={
                  <IconTrash
                    size={11}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setFornecedores((f) => f.filter((x) => x !== id))}
                  />
                }
              >
                {(lista ?? []).find((f) => f.id === id)?.name ?? id}
              </Badge>
            ))}
          </Group>
        )}

        <SeccaoForm
          titulo="O que se pede"
          descricao="A especificação vai no pedido: é o que faz os três cotarem a mesma coisa."
        >
          <Table withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: '26%' }}>Peça do armazém</Table.Th>
                <Table.Th>Designação</Table.Th>
                <Table.Th style={{ width: '22%' }}>Especificação</Table.Th>
                <Table.Th style={{ width: '12%' }}>Qtd.</Table.Th>
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
                      onChange={(e) => {
                        const v = e.currentTarget.value;
                        setLinhas((x) =>
                          x.map((y, j) => (j === i ? { ...y, description: v } : y)),
                        );
                      }}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      value={l.specification}
                      onChange={(e) => {
                        const v = e.currentTarget.value;
                        setLinhas((x) =>
                          x.map((y, j) => (j === i ? { ...y, specification: v } : y)),
                        );
                      }}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      type="number"
                      value={l.quantity}
                      onChange={(e) => {
                        const v = e.currentTarget.value;
                        setLinhas((x) => x.map((y, j) => (j === i ? { ...y, quantity: v } : y)));
                      }}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      color="red"
                      disabled={linhas.length === 1}
                      onClick={() => setLinhas((x) => x.filter((_, j) => j !== i))}
                    >
                      <IconTrash size={13} />
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Button
            size="xs"
            variant="default"
            mt="xs"
            leftSection={<IconPlus size={14} />}
            onClick={() =>
              setLinhas((l) => [
                ...l,
                { partId: null, description: '', specification: '', quantity: '1', unit: '' },
              ])
            }
          >
            Mais uma linha
          </Button>
        </SeccaoForm>

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button
            disabled={!title.trim() || fornecedores.length === 0}
            loading={criar.isPending}
            onClick={() => criar.mutate()}
          >
            Criar pedido
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ==== Comparação ===========================================================

function Comparacao({ id, aoFechar }: { id: string; aoFechar: () => void }) {
  const qc = useQueryClient();
  const [resposta, setResposta] = useState<Proposta | null>(null);
  const [motivo, setMotivo] = useState('');

  const { data: p } = useQuery({
    queryKey: ['cotacoes', 'pedido', id],
    queryFn: () => api<Pedido>(`/rfqs/${id}`),
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['cotacoes'] });
    void qc.invalidateQueries({ queryKey: ['compras'] });
  };

  const falhou = (e: unknown) =>
    notifications.show({
      color: 'red',
      title: 'Não foi possível',
      message: e instanceof ApiError ? e.message : 'Tente de novo.',
    });

  const enviar = useMutation({
    mutationFn: (supplierId: string) =>
      api(`/rfqs/${id}/suppliers/${supplierId}/send`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Pedido enviado.' });
      recarregar();
    },
    onError: falhou,
  });

  const adjudicar = useMutation({
    mutationFn: (supplierId: string) =>
      api(`/rfqs/${id}/award`, { method: 'POST', body: { supplierId, motivo } }),
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Adjudicado. A ordem de compra foi criada.' });
      setMotivo('');
      recarregar();
      aoFechar();
    },
    onError: falhou,
  });

  if (!p) {
    return null;
  }

  const comResposta = p.propostas.filter((x) => x.respondedAt && !x.declined);

  return (
    <Modal opened onClose={aoFechar} title={`${p.number} · ${p.title}`} size="95%">
      <Stack gap="sm">
        {p.leitura && (
          <Alert color="blue" variant="light" icon={<IconInfoCircle size={18} />}>
            {p.leitura}
          </Alert>
        )}
        {p.status === 'AWARDED' && (
          <Alert color="green" variant="light" icon={<IconTrophy size={18} />}>
            Adjudicado a <b>{p.awardedSupplierName}</b>
            {p.awardedReason ? ` — ${p.awardedReason}` : ''}.{' '}
            {p.awardedOrderId && (
              <Text component={Link} to={`/compras/${p.awardedOrderId}`} size="sm" td="underline">
                ver a ordem de compra
              </Text>
            )}
          </Alert>
        )}

        <Table.ScrollContainer minWidth={760}>
          <Table withTableBorder highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ minWidth: 220 }}>Linha do pedido</Table.Th>
                {p.propostas.map((f) => (
                  <Table.Th key={f.id} ta="right">
                    <Stack gap={2} align="flex-end">
                      <Text size="sm" fw={700}>
                        {f.supplierName}
                      </Text>
                      <Group gap={4}>
                        {f.maisBarata && (
                          <Badge size="xs" color="green" variant="light">
                            mais barato
                          </Badge>
                        )}
                        {f.maisRapida && (
                          <Badge size="xs" color="blue" variant="light">
                            mais rápido
                          </Badge>
                        )}
                        {f.declined && (
                          <Badge size="xs" color="gray" variant="light">
                            não fornece
                          </Badge>
                        )}
                        {!f.respondedAt && (
                          <Badge size="xs" color="orange" variant="light">
                            sem resposta
                          </Badge>
                        )}
                      </Group>
                    </Stack>
                  </Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {p.lines.map((l) => (
                <Table.Tr key={l.id}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {l.description}
                    </Text>
                    <Group gap={6}>
                      <Text size="xs" c="dimmed">
                        {fmtNumber(l.quantity)} {l.unit ?? ''}
                      </Text>
                      {l.specification && (
                        <Badge size="xs" variant="outline" color="gray">
                          {l.specification}
                        </Badge>
                      )}
                    </Group>
                  </Table.Td>
                  {p.propostas.map((f) => {
                    const q = f.precos.find((x) => x.lineId === l.id);
                    return (
                      <Table.Td key={f.id} ta="right">
                        {!q || (q.unitPrice == null && !q.unavailable) ? (
                          <Text size="xs" c="dimmed">
                            —
                          </Text>
                        ) : q.unavailable ? (
                          <Text size="xs" c="dimmed">
                            não tem
                          </Text>
                        ) : (
                          <Stack gap={0} align="flex-end">
                            <Text size="sm" fw={q.melhorDaLinha ? 700 : 500}
                              c={q.melhorDaLinha ? 'green.7' : undefined}>
                              {fmtMoney(q.unitPrice ?? 0)}
                            </Text>
                            {q.acimaDoMelhorPct != null && q.acimaDoMelhorPct > 0 && (
                              <Text size="xs" c="dimmed">
                                +{q.acimaDoMelhorPct}%
                              </Text>
                            )}
                          </Stack>
                        )}
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
              ))}
              <Table.Tr>
                <Table.Td>
                  <Text size="sm" fw={700}>
                    Total
                  </Text>
                </Table.Td>
                {p.propostas.map((f) => (
                  <Table.Td key={f.id} ta="right">
                    <Text size="sm" fw={700} c={f.maisBarata ? 'green.7' : undefined}>
                      {f.total != null ? fmtMoney(f.total) : '—'}
                    </Text>
                    {f.leadTimeDays != null && (
                      <Text size="xs" c="dimmed">
                        {f.leadTimeDays} dias
                      </Text>
                    )}
                  </Table.Td>
                ))}
              </Table.Tr>
              <Table.Tr>
                <Table.Td />
                {p.propostas.map((f) => (
                  <Table.Td key={f.id} ta="right">
                    <Stack gap={4} align="flex-end">
                      {!f.sentAt && p.status === 'OPEN' && (
                        <Button
                          size="compact-xs"
                          variant="default"
                          leftSection={<IconMailForward size={12} />}
                          loading={enviar.isPending}
                          onClick={() => enviar.mutate(f.supplierId)}
                        >
                          Enviar
                        </Button>
                      )}
                      {p.status === 'OPEN' && (
                        <Button
                          size="compact-xs"
                          variant="light"
                          onClick={() => setResposta(f)}
                        >
                          {f.respondedAt ? 'Alterar resposta' : 'Registar resposta'}
                        </Button>
                      )}
                      {f.fileName && (
                        <Tooltip label={f.fileName} withArrow>
                          <Badge
                            size="xs"
                            variant="outline"
                            color="gray"
                            leftSection={<IconPaperclip size={9} />}
                          >
                            proposta
                          </Badge>
                        </Tooltip>
                      )}
                      {p.status === 'OPEN' && f.respondedAt && !f.declined && (
                        <Tooltip
                          label="Cria a ordem de compra com os preços dele"
                          withArrow
                        >
                          <Button
                            size="compact-xs"
                            color="teal"
                            leftSection={<IconTrophy size={12} />}
                            loading={adjudicar.isPending}
                            onClick={() => adjudicar.mutate(f.supplierId)}
                          >
                            Adjudicar
                          </Button>
                        </Tooltip>
                      )}
                    </Stack>
                  </Table.Td>
                ))}
              </Table.Tr>
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>

        {p.status === 'OPEN' && comResposta.length > 1 && (
          <Textarea
            label="Motivo da escolha"
            description="Obrigatório se não escolher o mais barato. Pode ser a decisão certa — mas tem de ficar escrito."
            autosize
            minRows={2}
            value={motivo}
            onChange={(e) => setMotivo(e.currentTarget.value)}
          />
        )}
      </Stack>

      {resposta && (
        <RegistarResposta
          pedidoId={p.id}
          supplierId={resposta.supplierId}
          supplierName={resposta.supplierName}
          aoFechar={() => {
            setResposta(null);
            recarregar();
          }}
        />
      )}
    </Modal>
  );
}
