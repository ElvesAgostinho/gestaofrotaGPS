import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  FileButton,
  Grid,
  Group,
  Modal,
  Progress,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconPaperclip,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, ApiError, tokens } from '../../api/client';
import { Painel, SeccaoForm } from '../../components/erp';
import { fmtDate, fmtMoney } from '../../lib/format';
import {
  COR_FACTURA,
  COR_MEDIDA,
  type Classificacao,
  type Factura,
} from '../compras/tiposCotacao';

/**
 * As facturas de um fornecedor, com o papel digitalizado.
 *
 * <p>Hoje vivem numa pasta, e essa pasta não responde a duas perguntas: «já
 * pagámos esta?» e «quanto é que esta empresa nos facturou este ano?». A
 * primeira faz pagar duas vezes; a segunda faz negociar às cegas.
 *
 * <p>Quando a factura está ligada a uma ordem, o sistema compara o valor e
 * diz-o em cima da linha. Uma factura acima da encomenda é a forma mais comum
 * de pagar a mais, e descobre-se sempre tarde de mais.
 */
export function Facturas({ supplierId }: { supplierId: string }) {
  const qc = useQueryClient();
  const [nova, setNova] = useState(false);

  const { data } = useQuery({
    queryKey: ['fornecedores', supplierId, 'facturas'],
    queryFn: () => api<Factura[]>(`/suppliers/${supplierId}/invoices`),
  });

  const recarregar = () =>
    void qc.invalidateQueries({ queryKey: ['fornecedores', supplierId] });

  const pagar = useMutation({
    mutationFn: (id: string) => api(`/supplier-invoices/${id}/pay`, { method: 'POST' }),
    onSuccess: recarregar,
  });

  const apagar = useMutation({
    mutationFn: (id: string) => api(`/supplier-invoices/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        message: e instanceof ApiError ? e.message : 'Não foi possível apagar.',
      }),
  });

  /** O anexo vai por uma rota autenticada: um href directo não leva o token. */
  const descarregar = async (fileId: string, nome: string) => {
    const r = await fetch(`/api/v1/files/${fileId}`, {
      headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
    });
    if (!r.ok) {
      notifications.show({ color: 'red', message: 'Não foi possível abrir o ficheiro.' });
      return;
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = nome;
    a.click();
    URL.revokeObjectURL(url);
  };

  const porPagar = (data ?? []).filter((f) => f.status === 'PENDING');
  const vencidas = porPagar.filter((f) => (f.diasAteVencer ?? 1) < 0);
  const totalPorPagar = porPagar.reduce((s, f) => s + (f.aPagar ?? f.amount ?? 0), 0);

  return (
    <Painel
      titulo="Facturas"
      acoes={
        <>
          <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setNova(true)}>
            Registar factura
          </Button>
          <div style={{ marginLeft: 'auto' }}>
            <Text size="xs" c="dimmed">
              {porPagar.length} por pagar · {fmtMoney(totalPorPagar)}
            </Text>
          </div>
        </>
      }
    >
      <Stack gap="sm">
        {vencidas.length > 0 && (
          <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
            <b>{vencidas.length} factura(s) vencida(s).</b> Pagar fora de prazo é a forma mais
            rápida de perder a prioridade de um fornecedor.
          </Alert>
        )}

        {(data ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Nenhuma factura registada.
          </Text>
        ) : (
          <Table.ScrollContainer minWidth={900}>
            <Table highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Factura</Table.Th>
                  <Table.Th>Encomenda</Table.Th>
                  <Table.Th>Emitida</Table.Th>
                  <Table.Th>Vence</Table.Th>
                  <Table.Th ta="right">Valor</Table.Th>
                  <Table.Th ta="right">A pagar</Table.Th>
                  <Table.Th>Estado</Table.Th>
                  <Table.Th w={90} />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data?.map((f) => (
                  <Table.Tr key={f.id}>
                    <Table.Td>
                      <Group gap={6} wrap="nowrap">
                        <Text size="sm" fw={600}>
                          {f.number}
                        </Text>
                        {f.fileName && (
                          <Tooltip label={f.fileName} withArrow>
                            <ActionIcon
                              size="sm"
                              variant="subtle"
                              aria-label="Descarregar factura"
                              onClick={() => void descarregar(f.fileId!, f.fileName!)}
                            >
                              <IconPaperclip size={14} />
                            </ActionIcon>
                          </Tooltip>
                        )}
                      </Group>
                      {f.conferencia && (
                        <Text
                          size="xs"
                          c={f.conferencia.includes('ACIMA') ? 'red.7' : 'dimmed'}
                          style={{ lineHeight: 1.25 }}
                        >
                          {f.conferencia}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm" ff="monospace">
                        {f.orderNumber ?? '—'}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{f.issuedAt ? fmtDate(f.issuedAt) : '—'}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{f.dueAt ? fmtDate(f.dueAt) : '—'}</Text>
                      {f.diasAteVencer != null && (
                        <Text size="xs" c={f.diasAteVencer < 0 ? 'red.7' : 'dimmed'}>
                          {f.diasAteVencer < 0
                            ? `vencida há ${Math.abs(f.diasAteVencer)} dias`
                            : `daqui a ${f.diasAteVencer} dias`}
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text size="sm">{f.amount != null ? fmtMoney(f.amount) : '—'}</Text>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text size="sm" fw={600}>
                        {f.aPagar != null ? fmtMoney(f.aPagar) : '—'}
                      </Text>
                      {f.withholdingAmount ? (
                        <Text size="xs" c="dimmed">
                          retido {fmtMoney(f.withholdingAmount)}
                        </Text>
                      ) : null}
                    </Table.Td>
                    <Table.Td>
                      <Badge size="sm" variant="light" color={COR_FACTURA[f.status]}>
                        {f.statusLabel}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Group gap={2} wrap="nowrap">
                        {f.status === 'PENDING' && (
                          <Tooltip label="Marcar como paga" withArrow>
                            <ActionIcon
                              variant="subtle"
                              color="green"
                              aria-label="Pagar"
                              onClick={() => pagar.mutate(f.id)}
                            >
                              <IconCheck size={15} />
                            </ActionIcon>
                          </Tooltip>
                        )}
                        {f.status !== 'PAID' && (
                          <ActionIcon
                            variant="subtle"
                            color="red"
                            aria-label="Apagar factura"
                            onClick={() => apagar.mutate(f.id)}
                          >
                            <IconTrash size={15} />
                          </ActionIcon>
                        )}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Stack>

      <NovaFactura
        supplierId={supplierId}
        aberta={nova}
        aoFechar={() => {
          setNova(false);
          recarregar();
        }}
      />
    </Painel>
  );
}

// ==== Registar ==============================================================

function NovaFactura({
  supplierId,
  aberta,
  aoFechar,
}: {
  supplierId: string;
  aberta: boolean;
  aoFechar: () => void;
}) {
  const [v, setV] = useState({
    number: '',
    orderId: null as string | null,
    issuedAt: '',
    dueAt: '',
    amount: '',
    withholdingAmount: '',
  });
  const [ficheiro, setFicheiro] = useState<File | null>(null);

  const { data: ordens } = useQuery({
    queryKey: ['compras', 'do-fornecedor', supplierId],
    queryFn: () => api<{ id: string; number: string; supplierId?: string | null }[]>(
      '/purchase-orders',
    ),
    enabled: aberta,
  });

  const guardar = useMutation({
    mutationFn: async () => {
      const dados = {
        number: v.number.trim(),
        orderId: v.orderId,
        issuedAt: v.issuedAt || null,
        dueAt: v.dueAt || null,
        amount: v.amount ? Number(v.amount) : null,
        withholdingAmount: v.withholdingAmount ? Number(v.withholdingAmount) : null,
      };
      if (!ficheiro) {
        return api(`/suppliers/${supplierId}/invoices`, { method: 'POST', body: dados });
      }
      const form = new FormData();
      form.append(
        'dados',
        new Blob([JSON.stringify(dados)], { type: 'application/json' }),
      );
      form.append('ficheiro', ficheiro);
      const r = await fetch(`/api/v1/suppliers/${supplierId}/invoices`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
        body: form,
      });
      if (!r.ok) {
        const erro = (await r.json().catch(() => null)) as { message?: string } | null;
        throw new Error(erro?.message ?? 'Não foi possível registar.');
      }
      return r.json();
    },
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Factura registada.' });
      setV({
        number: '',
        orderId: null,
        issuedAt: '',
        dueAt: '',
        amount: '',
        withholdingAmount: '',
      });
      setFicheiro(null);
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível registar',
        message: e instanceof Error ? e.message : 'Tente de novo.',
      }),
  });

  const campo = (k: keyof typeof v) => ({
    value: v[k] as string,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => {
      const valor = e.currentTarget.value;
      setV((f) => ({ ...f, [k]: valor }));
    },
  });

  return (
    <Modal opened={aberta} onClose={aoFechar} title="Registar factura" size="lg">
      <Stack gap="sm">
        <SeccaoForm
          titulo="A factura"
          descricao="Ligue-a à encomenda sempre que houver: é assim que o sistema confere o valor antes de alguém pagar."
        >
          <Grid gutter="xs">
            <Grid.Col span={{ base: 12, sm: 5 }}>
              <TextInput label="Número" required placeholder="FT 2026/1180" {...campo('number')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 7 }}>
              <Select
                label="Encomenda"
                placeholder="opcional"
                searchable
                clearable
                value={v.orderId}
                onChange={(x) => setV((f) => ({ ...f, orderId: x }))}
                data={(ordens ?? [])
                  .filter((o) => o.supplierId === supplierId)
                  .map((o) => ({ value: o.id, label: o.number }))}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <TextInput label="Emitida" type="date" {...campo('issuedAt')} />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <TextInput label="Vence" type="date" {...campo('dueAt')} />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <TextInput label="Valor" type="number" {...campo('amount')} />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <TextInput
                label="Retenção"
                type="number"
                description="Sai do que se transfere"
                {...campo('withholdingAmount')}
              />
            </Grid.Col>
          </Grid>
        </SeccaoForm>

        <Group>
          <FileButton onChange={setFicheiro} accept="application/pdf,image/*">
            {(props) => (
              <Button {...props} variant="default" leftSection={<IconPaperclip size={15} />}>
                {ficheiro ? 'Trocar ficheiro' : 'Anexar o papel'}
              </Button>
            )}
          </FileButton>
          {ficheiro && (
            <Text size="sm" c="dimmed">
              {ficheiro.name}
            </Text>
          )}
        </Group>

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button
            disabled={!v.number.trim()}
            loading={guardar.isPending}
            onClick={() => guardar.mutate()}
          >
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ==== Classificação =========================================================

/**
 * Como é que este fornecedor se tem portado.
 *
 * <p>Sem nota global, de propósito: uma média esconde exactamente o que
 * interessa. Um fornecedor pontual que factura a mais é um problema diferente
 * de um barato que atrasa, e tratá-los com o mesmo número é não os tratar.
 */
export function ClassificacaoFornecedor({ supplierId }: { supplierId: string }) {
  const { data } = useQuery({
    queryKey: ['fornecedores', supplierId, 'classificacao'],
    queryFn: () => api<Classificacao>(`/suppliers/${supplierId}/scorecard`),
  });

  if (!data) {
    return null;
  }

  return (
    <Painel
      titulo="Como se tem portado"
      rodape={
        <Text size="xs" c="dimmed">
          Tudo calculado do que já aconteceu — nada escrito à mão. Sem nota global: uma média
          esconde o que interessa.
        </Text>
      }
    >
      <Stack gap="md">
        <Text size="sm">{data.leitura}</Text>

        <Group gap="lg">
          <div>
            <Text size="xs" c="dimmed" tt="uppercase">
              Encomendas
            </Text>
            <Text fw={700} size="xl">
              {data.ordens}
            </Text>
          </div>
          <div>
            <Text size="xs" c="dimmed" tt="uppercase">
              Recebidas
            </Text>
            <Text fw={700} size="xl">
              {data.ordensRecebidas}
            </Text>
          </div>
          <div>
            <Text size="xs" c="dimmed" tt="uppercase">
              Comprado
            </Text>
            <Text fw={700} size="xl">
              {fmtMoney(data.totalComprado)}
            </Text>
          </div>
        </Group>

        <Stack gap="sm">
          {data.medidas.map((m) => (
            <div key={m.nome}>
              <Group justify="space-between" mb={3}>
                <Text size="sm" fw={600}>
                  {m.nome}
                </Text>
                <Badge size="sm" variant="light" color={COR_MEDIDA[m.estado]}>
                  {m.valor != null ? `${m.valor}%` : 'sem dados'}
                </Badge>
              </Group>
              <Progress
                value={m.valor ?? 0}
                color={COR_MEDIDA[m.estado]}
                size="sm"
                mb={3}
              />
              <Text size="xs" c="dimmed">
                {m.detalhe}
              </Text>
            </div>
          ))}
        </Stack>
      </Stack>
    </Painel>
  );
}
