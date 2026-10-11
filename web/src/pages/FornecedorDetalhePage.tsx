import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Grid,
  Group,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconCoin,
  IconFileText,
  IconPencil,
  IconPlus,
  IconShip,
  IconTrash,
  IconUsers,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { BarraEstado, Painel, SeccaoForm } from '../components/erp';
import { fmtDate, fmtMoney, fmtNumber } from '../lib/format';
import { FormularioFornecedor } from './fornecedores/FormularioFornecedor';
import {
  COR_ESTADO_DOC,
  PAPEIS_CONTACTO,
  ROTULO_ESTADO_DOC,
  TIPOS_DOCUMENTO,
  type Contacto,
  type Documento,
  type Fornecedor,
  type Preco,
} from './fornecedores/tipos';

/**
 * A ficha de um fornecedor.
 *
 * <p>Quatro separadores que respondem às quatro perguntas de quem compra: quem
 * é, com quem se fala, se os papéis estão em ordem e a que preço vende.
 *
 * <p>Os documentos não estão aqui por burocracia. Comprar a quem tem a
 * certidão caducada é um problema que só aparece meses depois, na
 * contabilidade, e já não tem como se desfazer — por isso o estado de cada um
 * está à vista e expirado é vermelho.
 */
export function FornecedorDetalhePage() {
  const { id = '' } = useParams();
  const { has } = useAuth();
  const podeEditar = has('COSTS_VIEW');
  const [editar, setEditar] = useState(false);

  const { data: todos } = useQuery({
    queryKey: ['fornecedores'],
    queryFn: () => api<Fornecedor[]>('/suppliers'),
  });
  const f = (todos ?? []).find((x) => x.id === id);

  if (!f) {
    return (
      <Painel titulo="Fornecedor">
        <Text size="sm" c="dimmed">
          Fornecedor não encontrado.
        </Text>
      </Painel>
    );
  }

  return (
    <Stack gap="lg">
      <Painel
        titulo={f.name}
        acoes={
          <Group gap="xs" style={{ width: '100%' }}>
            <Button
              size="xs"
              variant="default"
              component={Link}
              to="/fornecedores"
              leftSection={<IconArrowLeft size={14} />}
            >
              Fornecedores
            </Button>
            {podeEditar && (
              <Button size="xs" leftSection={<IconPencil size={14} />} onClick={() => setEditar(true)}>
                Alterar
              </Button>
            )}
            {f.importer && (
              <Badge variant="light" color="blue" leftSection={<IconShip size={11} />}>
                importador {f.incoterm ? `· ${f.incoterm}` : ''}
              </Badge>
            )}
            {!f.active && <Badge color="gray">inactivo</Badge>}
          </Group>
        }
      >
        <BarraEstado
          itens={[
            { rotulo: 'NIF', valor: f.taxId ?? '—' },
            { rotulo: 'Tipo', valor: f.kindLabel },
            { rotulo: 'Telefone', valor: f.phone ?? '—' },
            { rotulo: 'Email', valor: f.email ?? '—' },
            {
              rotulo: 'Prazo total',
              valor: f.prazoTotalDias != null ? `${f.prazoTotalDias} dias` : '—',
            },
            { rotulo: 'Pagamento', valor: f.paymentTerms ?? '—' },
            { rotulo: 'IBAN', valor: f.iban ?? '—' },
          ]}
        />
      </Painel>

      <Tabs defaultValue="contactos" keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="contactos" leftSection={<IconUsers size={15} />}>
            Contactos
          </Tabs.Tab>
          <Tabs.Tab value="documentos" leftSection={<IconFileText size={15} />}>
            Documentos
          </Tabs.Tab>
          {podeEditar && (
            <Tabs.Tab value="precos" leftSection={<IconCoin size={15} />}>
              Preços
            </Tabs.Tab>
          )}
        </Tabs.List>

        <Tabs.Panel value="contactos" pt="md">
          <Contactos supplierId={id} podeEditar={podeEditar} />
        </Tabs.Panel>
        <Tabs.Panel value="documentos" pt="md">
          <Documentos supplierId={id} podeEditar={podeEditar} />
        </Tabs.Panel>
        <Tabs.Panel value="precos" pt="md">
          <Precos supplierId={id} />
        </Tabs.Panel>
      </Tabs>

      <FormularioFornecedor aberto={editar} aoFechar={() => setEditar(false)} existente={f} />
    </Stack>
  );
}

// ==== Contactos ============================================================

function Contactos({ supplierId, podeEditar }: { supplierId: string; podeEditar: boolean }) {
  const qc = useQueryClient();
  const [novo, setNovo] = useState({
    name: '',
    role: 'SALES',
    phone: '',
    email: '',
    primaryContact: false,
  });

  const { data } = useQuery({
    queryKey: ['fornecedores', supplierId, 'contactos'],
    queryFn: () => api<Contacto[]>(`/suppliers/${supplierId}/contacts`),
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['fornecedores', supplierId] });
    void qc.invalidateQueries({ queryKey: ['correio', 'directorio'] });
  };

  const criar = useMutation({
    mutationFn: () =>
      api(`/suppliers/${supplierId}/contacts`, {
        method: 'POST',
        body: {
          name: novo.name.trim(),
          role: novo.role,
          phone: novo.phone.trim() || null,
          email: novo.email.trim() || null,
          primaryContact: novo.primaryContact,
        },
      }),
    onSuccess: () => {
      setNovo({ name: '', role: 'SALES', phone: '', email: '', primaryContact: false });
      recarregar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        message: e instanceof ApiError ? e.message : 'Não foi possível guardar.',
      }),
  });

  const apagar = useMutation({
    mutationFn: (id: string) => api(`/supplier-contacts/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
  });

  return (
    <Painel
      titulo="Pessoas"
      rodape={
        <Text size="xs" c="dimmed">
          O contacto principal é a quem a ordem de compra vai. O correio da empresa também passa a
          reconhecer estes endereços e a mostrar o nome em vez do email.
        </Text>
      }
    >
      <Stack gap="sm">
        {(data ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Ainda não há ninguém. Um fornecedor tem o comercial que recebe a encomenda, o
            financeiro que quer a factura e o armazém onde se vai buscar a peça — e raramente são a
            mesma pessoa.
          </Text>
        ) : (
          <Table withTableBorder highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Nome</Table.Th>
                <Table.Th>Função</Table.Th>
                <Table.Th>Telefone</Table.Th>
                <Table.Th>Email</Table.Th>
                {podeEditar && <Table.Th w={50} />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data?.map((c) => (
                <Table.Tr key={c.id}>
                  <Table.Td>
                    <Group gap={6} wrap="nowrap">
                      <Text size="sm" fw={600}>
                        {c.name}
                      </Text>
                      {c.primaryContact && (
                        <Badge size="xs" color="blue" variant="light">
                          principal
                        </Badge>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{c.roleLabel}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{c.phone ?? '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{c.email ?? '—'}</Text>
                  </Table.Td>
                  {podeEditar && (
                    <Table.Td>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        aria-label="Apagar contacto"
                        onClick={() => apagar.mutate(c.id)}
                      >
                        <IconTrash size={15} />
                      </ActionIcon>
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}

        {podeEditar && (
          <SeccaoForm titulo="Acrescentar">
            <Grid gutter="xs" align="flex-end">
              <Grid.Col span={{ base: 12, sm: 3 }}>
                <TextInput
                  label="Nome"
                  value={novo.name}
                  onChange={(e) => {
                    const valor = e.currentTarget.value;
                    setNovo((f) => ({ ...f, name: valor }));
                  }}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 6, sm: 2 }}>
                <Select
                  label="Função"
                  data={PAPEIS_CONTACTO}
                  value={novo.role}
                  onChange={(x) => setNovo((f) => ({ ...f, role: x ?? 'SALES' }))}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 6, sm: 2 }}>
                <TextInput
                  label="Telefone"
                  value={novo.phone}
                  onChange={(e) => {
                    const valor = e.currentTarget.value;
                    setNovo((f) => ({ ...f, phone: valor }));
                  }}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, sm: 3 }}>
                <TextInput
                  label="Email"
                  value={novo.email}
                  onChange={(e) => {
                    const valor = e.currentTarget.value;
                    setNovo((f) => ({ ...f, email: valor }));
                  }}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, sm: 2 }}>
                <Button
                  fullWidth
                  leftSection={<IconPlus size={14} />}
                  disabled={!novo.name.trim()}
                  loading={criar.isPending}
                  onClick={() => criar.mutate()}
                >
                  Juntar
                </Button>
              </Grid.Col>
            </Grid>
            <Switch
              mt="xs"
              label="É o contacto principal (recebe as ordens de compra)"
              checked={novo.primaryContact}
              onChange={(e) => setNovo((f) => ({ ...f, primaryContact: e.currentTarget.checked }))}
            />
          </SeccaoForm>
        )}
      </Stack>
    </Painel>
  );
}

// ==== Documentos ===========================================================

function Documentos({ supplierId, podeEditar }: { supplierId: string; podeEditar: boolean }) {
  const qc = useQueryClient();
  const [novo, setNovo] = useState({ kind: 'ALVARA', number: '', expiresAt: '' });

  const { data } = useQuery({
    queryKey: ['fornecedores', supplierId, 'documentos'],
    queryFn: () => api<Documento[]>(`/suppliers/${supplierId}/documents`),
  });

  const recarregar = () =>
    void qc.invalidateQueries({ queryKey: ['fornecedores', supplierId] });

  const criar = useMutation({
    mutationFn: () =>
      api(`/suppliers/${supplierId}/documents`, {
        method: 'POST',
        body: {
          kind: novo.kind,
          number: novo.number.trim() || null,
          expiresAt: novo.expiresAt || null,
        },
      }),
    onSuccess: () => {
      setNovo({ kind: 'ALVARA', number: '', expiresAt: '' });
      recarregar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        message: e instanceof ApiError ? e.message : 'Não foi possível guardar.',
      }),
  });

  const apagar = useMutation({
    mutationFn: (id: string) => api(`/supplier-documents/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
  });

  const problemas = (data ?? []).filter(
    (d) => d.estado === 'EXPIRADO' || d.estado === 'A_EXPIRAR',
  );

  return (
    <Painel titulo="Documentos e validades">
      <Stack gap="sm">
        {problemas.length > 0 && (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
            <b>{problemas.length} documento(s) fora de prazo ou a expirar.</b> Comprar a um
            fornecedor com os papéis caducados cria um problema na contabilidade que já não se
            desfaz.
          </Alert>
        )}

        {(data ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Sem documentos registados.
          </Text>
        ) : (
          <Table withTableBorder highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Documento</Table.Th>
                <Table.Th>Número</Table.Th>
                <Table.Th>Validade</Table.Th>
                <Table.Th>Estado</Table.Th>
                {podeEditar && <Table.Th w={50} />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data?.map((d) => (
                <Table.Tr key={d.id}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {d.kindLabel}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{d.number ?? '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{d.expiresAt ? fmtDate(d.expiresAt) : '—'}</Text>
                    {d.diasAteExpirar != null && (
                      <Text size="xs" c="dimmed">
                        {d.diasAteExpirar < 0
                          ? `há ${Math.abs(d.diasAteExpirar)} dias`
                          : `daqui a ${d.diasAteExpirar} dias`}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Badge size="sm" variant="light" color={COR_ESTADO_DOC[d.estado]}>
                      {ROTULO_ESTADO_DOC[d.estado]}
                    </Badge>
                  </Table.Td>
                  {podeEditar && (
                    <Table.Td>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        aria-label="Apagar documento"
                        onClick={() => apagar.mutate(d.id)}
                      >
                        <IconTrash size={15} />
                      </ActionIcon>
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}

        {podeEditar && (
          <SeccaoForm titulo="Acrescentar">
            <Grid gutter="xs" align="flex-end">
              <Grid.Col span={{ base: 12, sm: 4 }}>
                <Select
                  label="Documento"
                  data={TIPOS_DOCUMENTO}
                  value={novo.kind}
                  onChange={(x) => setNovo((f) => ({ ...f, kind: x ?? 'ALVARA' }))}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 6, sm: 3 }}>
                <TextInput
                  label="Número"
                  value={novo.number}
                  onChange={(e) => {
                    const valor = e.currentTarget.value;
                    setNovo((f) => ({ ...f, number: valor }));
                  }}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 6, sm: 3 }}>
                <TextInput
                  label="Válido até"
                  type="date"
                  value={novo.expiresAt}
                  onChange={(e) => {
                    const valor = e.currentTarget.value;
                    setNovo((f) => ({ ...f, expiresAt: valor }));
                  }}
                />
              </Grid.Col>
              <Grid.Col span={{ base: 12, sm: 2 }}>
                <Button
                  fullWidth
                  leftSection={<IconPlus size={14} />}
                  loading={criar.isPending}
                  onClick={() => criar.mutate()}
                >
                  Juntar
                </Button>
              </Grid.Col>
            </Grid>
          </SeccaoForm>
        )}
      </Stack>
    </Painel>
  );
}

// ==== Preços ===============================================================

interface PecaSimples {
  id: string;
  name: string;
  partNumber?: string | null;
  unit?: string | null;
}

function Precos({ supplierId }: { supplierId: string }) {
  const qc = useQueryClient();
  const [novo, setNovo] = useState({
    partId: null as string | null,
    supplierPartNumber: '',
    price: '' as number | string,
    leadTimeDays: '' as number | string,
    minOrderQuantity: '' as number | string,
    validUntil: '',
  });

  const { data } = useQuery({
    queryKey: ['fornecedores', supplierId, 'precos'],
    queryFn: () => api<Preco[]>(`/suppliers/${supplierId}/prices`),
  });

  const { data: pecas } = useQuery({
    queryKey: ['parts'],
    queryFn: () => api<PecaSimples[]>('/parts'),
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['fornecedores', supplierId] });
    void qc.invalidateQueries({ queryKey: ['consumiveis'] });
  };

  const guardar = useMutation({
    mutationFn: () =>
      api(`/suppliers/${supplierId}/prices`, {
        method: 'POST',
        body: {
          partId: novo.partId,
          supplierPartNumber: novo.supplierPartNumber.trim() || null,
          price: Number(novo.price),
          leadTimeDays: novo.leadTimeDays === '' ? null : Number(novo.leadTimeDays),
          minOrderQuantity:
            novo.minOrderQuantity === '' ? null : Number(novo.minOrderQuantity),
          validUntil: novo.validUntil || null,
        },
      }),
    onSuccess: () => {
      setNovo({
        partId: null,
        supplierPartNumber: '',
        price: '',
        leadTimeDays: '',
        minOrderQuantity: '',
        validUntil: '',
      });
      recarregar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        message: e instanceof ApiError ? e.message : 'Não foi possível guardar.',
      }),
  });

  const apagar = useMutation({
    mutationFn: (id: string) => api(`/supplier-prices/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
  });

  return (
    <Painel
      titulo="Lista de preços"
      rodape={
        <Text size="xs" c="dimmed">
          É este elo que faz a lista de compras deixar de dizer só «faltam quatro filtros» e passar
          a dizer quanto custam, a quem, e em quantos dias chegam.
        </Text>
      }
    >
      <Stack gap="sm">
        {(data ?? []).length === 0 ? (
          <Text size="sm" c="dimmed">
            Sem preços registados para este fornecedor.
          </Text>
        ) : (
          <Table withTableBorder highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Peça</Table.Th>
                <Table.Th>Ref. dele</Table.Th>
                <Table.Th>Preço</Table.Th>
                <Table.Th>Mínimo</Table.Th>
                <Table.Th>Prazo</Table.Th>
                <Table.Th>Validade</Table.Th>
                <Table.Th w={50} />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data?.map((p) => (
                <Table.Tr key={p.id}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {p.partName}
                    </Text>
                    {p.partNumber && (
                      <Text size="xs" c="dimmed">
                        ref. {p.partNumber}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" ff="monospace">
                      {p.supplierPartNumber ?? '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={700}>
                      {fmtMoney(p.price)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">
                      {p.minOrderQuantity != null ? fmtNumber(p.minOrderQuantity) : '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{p.leadTimeDays != null ? `${p.leadTimeDays} d` : '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Group gap={6} wrap="nowrap">
                      <Text size="sm">{p.validUntil ? fmtDate(p.validUntil) : '—'}</Text>
                      {p.expirada && (
                        <Tooltip
                          label="Cotação fora de validade: não concorre para «o mais barato»"
                          withArrow
                        >
                          <Badge size="xs" color="orange" variant="light">
                            expirada
                          </Badge>
                        </Tooltip>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      aria-label="Apagar preço"
                      onClick={() => apagar.mutate(p.id)}
                    >
                      <IconTrash size={15} />
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}

        <SeccaoForm
          titulo="Cotar uma peça"
          descricao="Cotar de novo substitui a cotação anterior — um fornecedor tem um preço por peça, não dez linhas que ninguém sabe qual vale."
        >
          <Grid gutter="xs" align="flex-end">
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <Select
                label="Peça"
                placeholder="Escolher do armazém"
                searchable
                value={novo.partId}
                onChange={(x) => setNovo((f) => ({ ...f, partId: x }))}
                data={(pecas ?? []).map((p) => ({
                  value: p.id,
                  label: p.partNumber ? `${p.name} (${p.partNumber})` : p.name,
                }))}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 2 }}>
              <TextInput
                label="Ref. dele"
                placeholder="WK 842/2"
                value={novo.supplierPartNumber}
                onChange={(e) => {
                  const valor = e.currentTarget.value;
                  setNovo((f) => ({ ...f, supplierPartNumber: valor }));
                }}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 2 }}>
              <TextInput
                label="Preço"
                type="number"
                value={String(novo.price)}
                onChange={(e) => {
                  const valor = e.currentTarget.value;
                  setNovo((f) => ({ ...f, price: valor }));
                }}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 4, sm: 1 }}>
              <TextInput
                label="Mín."
                type="number"
                value={String(novo.minOrderQuantity)}
                onChange={(e) => {
                  const valor = e.currentTarget.value;
                  setNovo((f) => ({ ...f, minOrderQuantity: valor }));
                }}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 4, sm: 1 }}>
              <TextInput
                label="Prazo"
                type="number"
                value={String(novo.leadTimeDays)}
                onChange={(e) => {
                  const valor = e.currentTarget.value;
                  setNovo((f) => ({ ...f, leadTimeDays: valor }));
                }}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 4, sm: 2 }}>
              <Button
                fullWidth
                leftSection={<IconPlus size={14} />}
                disabled={!novo.partId || !String(novo.price).trim()}
                loading={guardar.isPending}
                onClick={() => guardar.mutate()}
              >
                Guardar
              </Button>
            </Grid.Col>
          </Grid>
        </SeccaoForm>
      </Stack>
    </Painel>
  );
}
