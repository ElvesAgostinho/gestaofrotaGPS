import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCalendarEvent,
  IconDeviceMobile,
  IconGavel,
  IconId,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDate, fmtDateTime, fmtMoney } from '../../lib/format';
import { AcessoApp } from './AcessoApp';

export interface MotoristaFicha {
  id: string;
  name: string;
  employeeNumber?: string | null;
  phone?: string | null;
  email?: string | null;
  nationalId?: string | null;
  licenseNumber?: string | null;
  licenseCategories?: string | null;
  licenseExpiresAt?: string | null;
  cardNumber?: string | null;
  cardExpiresAt?: string | null;
  medicalExpiresAt?: string | null;
  status: string;
  statusLabel: string;
  canDrive: boolean;
  warnings?: string[];
  notes?: string | null;
  version?: number | null;
}

interface Infracao {
  id: string;
  occurredAt: string;
  kind: string;
  kindLabel: string;
  description?: string | null;
  points: number;
  fineAmount?: number | null;
  paid: boolean;
  reference?: string | null;
  assetTag?: string | null;
}

interface Turno {
  id: string;
  startsAt: string;
  endsAt: string;
  kind: string;
  kindLabel: string;
  assetTag?: string | null;
  assetId?: string | null;
  notes?: string | null;
}

interface AtivoOpcao {
  id: string;
  tag: string;
  name: string;
}

const TIPOS_INFRACAO = [
  { value: 'SPEEDING', label: 'Excesso de velocidade' },
  { value: 'ACCIDENT', label: 'Acidente' },
  { value: 'FINE', label: 'Multa' },
  { value: 'MISUSE', label: 'Uso indevido da viatura' },
  { value: 'DOCUMENT', label: 'Documentação em falta' },
  { value: 'OTHER', label: 'Outra' },
];

const TIPOS_TURNO = [
  { value: 'DAY', label: 'Diurno' },
  { value: 'NIGHT', label: 'Noturno' },
  { value: 'TRIP', label: 'Viagem' },
  { value: 'STANDBY', label: 'Prevenção' },
];

function paraLocal(iso: string) {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * A ficha do motorista: os documentos que caducam (carta, cartão, exame
 * médico), as infrações com pontos e multas, e a escala. Abre-se com duplo
 * clique na lista.
 */
export function FichaMotorista({ motoristaId, fechar }: { motoristaId: string; fechar: () => void }) {
  const { has } = useAuth();
  const podeGerir = has('DRIVERS_MANAGE');
  const { data: m } = useQuery({
    queryKey: ['drivers', motoristaId],
    queryFn: () => api<MotoristaFicha>(`/drivers/${motoristaId}`),
  });

  return (
    <Modal opened onClose={fechar} title={m ? m.name : 'Motorista'} size="xl" centered>
      {m && (
        <Stack gap="sm">
          <Group gap="xs">
            <Badge variant="light" color={m.status === 'ACTIVE' ? 'green' : m.status === 'SUSPENDED' ? 'orange' : 'gray'}>
              {m.statusLabel}
            </Badge>
            <Badge variant="light" color={m.canDrive ? 'green' : 'red'}>
              {m.canDrive ? 'Pode conduzir' : 'Não pode conduzir'}
            </Badge>
            {m.employeeNumber && (
              <Text size="xs" c="dimmed">
                Nº {m.employeeNumber}
              </Text>
            )}
            {m.phone && (
              <Text size="xs" c="dimmed">
                · {m.phone}
              </Text>
            )}
          </Group>
          {(m.warnings ?? []).length > 0 && (
            <Alert color={m.canDrive ? 'yellow' : 'red'} variant="light" p="xs" icon={<IconAlertTriangle size={16} />}>
              {(m.warnings ?? []).map((w) => (
                <Text size="sm" key={w}>
                  {w}
                </Text>
              ))}
            </Alert>
          )}

          <Tabs defaultValue="documentos" keepMounted={false}>
            <Tabs.List>
              <Tabs.Tab value="documentos" leftSection={<IconId size={15} />}>
                Documentos
              </Tabs.Tab>
              <Tabs.Tab value="infracoes" leftSection={<IconGavel size={15} />}>
                Infrações
              </Tabs.Tab>
              <Tabs.Tab value="escala" leftSection={<IconCalendarEvent size={15} />}>
                Escala
              </Tabs.Tab>
              <Tabs.Tab value="acesso" leftSection={<IconDeviceMobile size={15} />}>
                Acesso à app
              </Tabs.Tab>
            </Tabs.List>
            <Tabs.Panel value="documentos" pt="sm">
              <DocumentosTab m={m} podeGerir={podeGerir} />
            </Tabs.Panel>
            <Tabs.Panel value="infracoes" pt="sm">
              <InfracoesTab motoristaId={m.id} podeGerir={podeGerir} />
            </Tabs.Panel>
            <Tabs.Panel value="escala" pt="sm">
              <EscalaTab motoristaId={m.id} podeGerir={podeGerir} />
            </Tabs.Panel>
            <Tabs.Panel value="acesso" pt="sm">
              <AcessoApp motoristaId={m.id} nome={m.name} />
            </Tabs.Panel>
          </Tabs>
        </Stack>
      )}
    </Modal>
  );
}

function DocumentosTab({ m, podeGerir }: { m: MotoristaFicha; podeGerir: boolean }) {
  const queryClient = useQueryClient();
  const [f, setF] = useState<Record<string, string>>({});
  useEffect(() => {
    setF({
      name: m.name ?? '',
      phone: m.phone ?? '',
      employeeNumber: m.employeeNumber ?? '',
      nationalId: m.nationalId ?? '',
      licenseNumber: m.licenseNumber ?? '',
      licenseCategories: m.licenseCategories ?? '',
      licenseExpiresAt: m.licenseExpiresAt ?? '',
      cardNumber: m.cardNumber ?? '',
      cardExpiresAt: m.cardExpiresAt ?? '',
      medicalExpiresAt: m.medicalExpiresAt ?? '',
      status: m.status,
      notes: m.notes ?? '',
    });
  }, [m]);
  // Ler o valor antes do actualizador: lá dentro o evento já foi reciclado.
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const valor = e.currentTarget.value;
    setF((x) => ({ ...x, [k]: valor }));
  };

  const guardar = useMutation({
    mutationFn: () =>
      api(`/drivers/${m.id}`, {
        method: 'PUT',
        body: {
          name: f.name,
          phone: f.phone || null,
          employeeNumber: f.employeeNumber || null,
          nationalId: f.nationalId || null,
          licenseNumber: f.licenseNumber || null,
          licenseCategories: f.licenseCategories || null,
          licenseExpiresAt: f.licenseExpiresAt || null,
          cardNumber: f.cardNumber || null,
          cardExpiresAt: f.cardExpiresAt || null,
          medicalExpiresAt: f.medicalExpiresAt || null,
          status: f.status,
          notes: f.notes || null,
          version: m.version,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Motorista guardado', message: f.name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['drivers'] });
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const ro = !podeGerir;
  return (
    <Stack gap="xs">
      <Group grow>
        <TextInput label="Nome" value={f.name ?? ''} onChange={set('name')} readOnly={ro} />
        <TextInput label="Telefone" value={f.phone ?? ''} onChange={set('phone')} readOnly={ro} />
        <TextInput label="Nº de funcionário" value={f.employeeNumber ?? ''} onChange={set('employeeNumber')} readOnly={ro} />
      </Group>
      <Group grow>
        <TextInput label="Bilhete de identidade" value={f.nationalId ?? ''} onChange={set('nationalId')} readOnly={ro} />
        <Select
          label="Estado"
          data={[
            { value: 'ACTIVE', label: 'Ativo' },
            { value: 'SUSPENDED', label: 'Suspenso' },
            { value: 'INACTIVE', label: 'Inativo' },
          ]}
          value={f.status ?? 'ACTIVE'}
          onChange={(v) => setF((x) => ({ ...x, status: v ?? 'ACTIVE' }))}
          readOnly={ro}
          allowDeselect={false}
        />
      </Group>
      <Text size="xs" fw={700} tt="uppercase" c="dimmed" mt="xs">
        Carta de condução
      </Text>
      <Group grow>
        <TextInput label="Número" value={f.licenseNumber ?? ''} onChange={set('licenseNumber')} readOnly={ro} />
        <TextInput label="Categorias" placeholder="B, C, D" value={f.licenseCategories ?? ''} onChange={set('licenseCategories')} readOnly={ro} />
        <TextInput label="Validade" type="date" value={f.licenseExpiresAt ?? ''} onChange={set('licenseExpiresAt')} readOnly={ro} />
      </Group>
      <Text size="xs" fw={700} tt="uppercase" c="dimmed" mt="xs">
        Cartão de motorista e exame médico
      </Text>
      <Group grow>
        <TextInput label="Nº do cartão de motorista" value={f.cardNumber ?? ''} onChange={set('cardNumber')} readOnly={ro} />
        <TextInput label="Validade do cartão" type="date" value={f.cardExpiresAt ?? ''} onChange={set('cardExpiresAt')} readOnly={ro} />
        <TextInput label="Validade do exame médico" type="date" value={f.medicalExpiresAt ?? ''} onChange={set('medicalExpiresAt')} readOnly={ro} />
      </Group>
      <Textarea label="Notas" value={f.notes ?? ''} onChange={(e) => {
          const valor = e.currentTarget.value;
          setF((x) => ({ ...x, notes: valor }));
        }} autosize minRows={2} readOnly={ro} />
      <Text size="xs" c="dimmed">
        O sistema avisa os gestores 30, 15 e 7 dias antes de cada validade e no dia em que caduca. Com um documento
        caducado o motorista deixa de poder ser atribuído a viaturas.
      </Text>
      {podeGerir && (
        <Group justify="flex-end">
          <Button loading={guardar.isPending} onClick={() => guardar.mutate()}>
            Guardar
          </Button>
        </Group>
      )}
    </Stack>
  );
}

function InfracoesTab({ motoristaId, podeGerir }: { motoristaId: string; podeGerir: boolean }) {
  const queryClient = useQueryClient();
  const [nova, setNova] = useState(false);
  const { data } = useQuery({
    queryKey: ['drivers', motoristaId, 'infractions'],
    queryFn: () => api<{ items: Infracao[]; pointsLastYear: number }>(`/drivers/${motoristaId}/infractions`),
  });
  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: AtivoOpcao[] }>('/assets?size=200'),
    enabled: nova,
  });
  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['drivers', motoristaId, 'infractions'] });

  const [kind, setKind] = useState<string | null>('SPEEDING');
  const [occurredAt, setOccurredAt] = useState(paraLocal(new Date().toISOString()));
  const [assetId, setAssetId] = useState<string | null>(null);
  const [description, setDescription] = useState('');
  const [points, setPoints] = useState<number | string>(0);
  const [fineAmount, setFineAmount] = useState<number | string>('');
  const [paid, setPaid] = useState(false);
  const [reference, setReference] = useState('');

  const criar = useMutation({
    mutationFn: () =>
      api(`/drivers/${motoristaId}/infractions`, {
        method: 'POST',
        body: {
          kind,
          occurredAt: new Date(occurredAt).toISOString(),
          assetId,
          description: description.trim() || null,
          points: points === '' ? 0 : Number(points),
          fineAmount: fineAmount === '' ? null : Number(fineAmount),
          paid,
          reference: reference.trim() || null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Infração registada', message: '', color: 'green' });
      invalidar();
      setNova(false);
      setDescription('');
      setFineAmount('');
      setReference('');
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });
  const pagar = useMutation({
    mutationFn: (i: Infracao) => api(`/driver-infractions/${i.id}/paid?paid=${!i.paid}`, { method: 'POST' }),
    onSuccess: invalidar,
  });
  const apagar = useMutation({
    mutationFn: (i: Infracao) => api(`/driver-infractions/${i.id}`, { method: 'DELETE' }),
    onSuccess: invalidar,
  });

  const itens = data?.items ?? [];
  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text size="sm">
          <b>{data?.pointsLastYear ?? 0}</b> pontos nos últimos 12 meses · {itens.length} infração(ões)
        </Text>
        {podeGerir && (
          <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setNova(true)}>
            Registar infração
          </Button>
        )}
      </Group>
      {itens.length === 0 ? (
        <Text size="sm" c="dimmed">
          Sem infrações registadas.
        </Text>
      ) : (
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Data</Table.Th>
              <Table.Th>Tipo</Table.Th>
              <Table.Th>Descrição</Table.Th>
              <Table.Th>Viatura</Table.Th>
              <Table.Th style={{ textAlign: 'right' }}>Pontos</Table.Th>
              <Table.Th style={{ textAlign: 'right' }}>Multa</Table.Th>
              <Table.Th></Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {itens.map((i) => (
              <Table.Tr key={i.id}>
                <Table.Td>{fmtDate(i.occurredAt)}</Table.Td>
                <Table.Td>{i.kindLabel}</Table.Td>
                <Table.Td>
                  <Text size="sm">{i.description ?? '—'}</Text>
                  {i.reference && (
                    <Text size="xs" c="dimmed">
                      Ref. {i.reference}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{i.assetTag ?? '—'}</Table.Td>
                <Table.Td style={{ textAlign: 'right' }}>{i.points}</Table.Td>
                <Table.Td style={{ textAlign: 'right' }}>
                  {i.fineAmount != null ? (
                    <>
                      {fmtMoney(i.fineAmount)}{' '}
                      <Badge size="xs" variant="light" color={i.paid ? 'green' : 'red'}>
                        {i.paid ? 'paga' : 'por pagar'}
                      </Badge>
                    </>
                  ) : (
                    '—'
                  )}
                </Table.Td>
                <Table.Td>
                  {podeGerir && (
                    <Group gap={4} wrap="nowrap">
                      {i.fineAmount != null && (
                        <Button size="compact-xs" variant="subtle" onClick={() => pagar.mutate(i)}>
                          {i.paid ? 'Por pagar' : 'Paga'}
                        </Button>
                      )}
                      <Button size="compact-xs" variant="subtle" color="red" leftSection={<IconTrash size={12} />} onClick={() => window.confirm('Apagar esta infração?') && apagar.mutate(i)}>
                        Apagar
                      </Button>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <Modal opened={nova} onClose={() => setNova(false)} title="Registar infração" centered>
        <Stack gap="sm">
          <Group grow>
            <Select label="Tipo" data={TIPOS_INFRACAO} value={kind} onChange={setKind} allowDeselect={false} data-autofocus />
            <TextInput label="Quando" type="datetime-local" value={occurredAt} onChange={(e) => setOccurredAt(e.currentTarget.value)} />
          </Group>
          <Select
            label="Viatura"
            placeholder="opcional"
            clearable
            searchable
            data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
            value={assetId}
            onChange={setAssetId}
          />
          <Textarea label="Descrição" placeholder="132 km/h na EN-100, auto nº…" value={description} onChange={(e) => setDescription(e.currentTarget.value)} autosize minRows={2} />
          <Group grow>
            <NumberInput label="Pontos" description="Segundo a política da empresa" min={0} max={50} value={points} onChange={(v) => setPoints(typeof v === 'number' ? v : '')} />
            <NumberInput label="Multa" thousandSeparator=" " min={0} value={fineAmount} onChange={(v) => setFineAmount(typeof v === 'number' ? v : '')} />
            <TextInput label="Referência" placeholder="Nº do auto" value={reference} onChange={(e) => setReference(e.currentTarget.value)} />
          </Group>
          <Switch label="Multa já paga" checked={paid} onChange={(e) => setPaid(e.currentTarget.checked)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setNova(false)}>
              Cancelar
            </Button>
            <Button loading={criar.isPending} onClick={() => criar.mutate()}>
              Registar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

function EscalaTab({ motoristaId, podeGerir }: { motoristaId: string; podeGerir: boolean }) {
  const queryClient = useQueryClient();
  const [novo, setNovo] = useState(false);
  const { data } = useQuery({
    queryKey: ['drivers', motoristaId, 'shifts'],
    queryFn: () => api<Turno[]>(`/drivers/${motoristaId}/shifts`),
  });
  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: AtivoOpcao[] }>('/assets?size=200'),
    enabled: novo,
  });
  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['drivers', motoristaId, 'shifts'] });
    queryClient.invalidateQueries({ queryKey: ['roster'] });
  };
  const amanha8 = (() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    d.setHours(8, 0, 0, 0);
    return d;
  })();
  const [startsAt, setStartsAt] = useState(paraLocal(amanha8.toISOString()));
  const [endsAt, setEndsAt] = useState(paraLocal(new Date(amanha8.getTime() + 9 * 3600e3).toISOString()));
  const [assetId, setAssetId] = useState<string | null>(null);
  const [kind, setKind] = useState<string | null>('DAY');
  const [notes, setNotes] = useState('');

  const criar = useMutation({
    mutationFn: () =>
      api(`/drivers/${motoristaId}/shifts`, {
        method: 'POST',
        body: { startsAt: new Date(startsAt).toISOString(), endsAt: new Date(endsAt).toISOString(), assetId, kind, notes: notes.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Turno escalado', message: '', color: 'green' });
      invalidar();
      setNovo(false);
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível escalar', message: e.message, color: 'red' }),
  });
  const apagar = useMutation({
    mutationFn: (t: Turno) => api(`/driver-shifts/${t.id}`, { method: 'DELETE' }),
    onSuccess: invalidar,
  });

  const turnos = data ?? [];
  return (
    <Stack gap="xs">
      <Group justify="space-between">
        <Text size="sm" c="dimmed">
          Últimos 7 dias e próximos 30. A escala de toda a empresa está em Motoristas → Escala.
        </Text>
        {podeGerir && (
          <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setNovo(true)}>
            Escalar turno
          </Button>
        )}
      </Group>
      {turnos.length === 0 ? (
        <Text size="sm" c="dimmed">
          Sem turnos neste período.
        </Text>
      ) : (
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Início</Table.Th>
              <Table.Th>Fim</Table.Th>
              <Table.Th>Tipo</Table.Th>
              <Table.Th>Viatura</Table.Th>
              <Table.Th>Notas</Table.Th>
              <Table.Th></Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {turnos.map((t) => (
              <Table.Tr key={t.id}>
                <Table.Td>{fmtDateTime(t.startsAt)}</Table.Td>
                <Table.Td>{fmtDateTime(t.endsAt)}</Table.Td>
                <Table.Td>{t.kindLabel}</Table.Td>
                <Table.Td>{t.assetTag ?? '—'}</Table.Td>
                <Table.Td>{t.notes ?? ''}</Table.Td>
                <Table.Td>
                  {podeGerir && (
                    <Button size="compact-xs" variant="subtle" color="red" onClick={() => apagar.mutate(t)}>
                      Apagar
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      <Modal opened={novo} onClose={() => setNovo(false)} title="Escalar turno" centered>
        <Stack gap="sm">
          <Group grow>
            <TextInput label="Início" type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.currentTarget.value)} data-autofocus />
            <TextInput label="Fim" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.currentTarget.value)} />
          </Group>
          <Group grow>
            <Select label="Tipo" data={TIPOS_TURNO} value={kind} onChange={setKind} allowDeselect={false} />
            <Select
              label="Viatura"
              placeholder="opcional"
              clearable
              searchable
              data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
              value={assetId}
              onChange={setAssetId}
            />
          </Group>
          <TextInput label="Notas" value={notes} onChange={(e) => setNotes(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setNovo(false)}>
              Cancelar
            </Button>
            <Button loading={criar.isPending} onClick={() => criar.mutate()}>
              Escalar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
