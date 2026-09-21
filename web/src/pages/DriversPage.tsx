import { Alert, Badge, Button, Card, Group, Modal, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconPlus, IconSearch } from '@tabler/icons-react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura, filtrar } from '../components/Procura';
import { useAuth } from '../auth/AuthContext';
import { Kpi } from '../components/Kpi';
import { FichaMotorista } from './drivers/FichaMotorista';
import { fmtDate, fmtDateTime } from '../lib/format';

interface Assignment {
  id: string;
  assetId: string;
  assetTag: string;
  assetName: string;
  startedAt: string;
  endedAt?: string | null;
  primaryDriver: boolean;
  open: boolean;
}

interface Driver {
  id: string;
  name: string;
  employeeNumber?: string | null;
  phone?: string | null;
  email?: string | null;
  branchName?: string | null;
  licenseNumber?: string | null;
  licenseCategories?: string | null;
  licenseExpiresAt?: string | null;
  licenseExpiresInDays?: number | null;
  licenseExpired: boolean;
  canDrive: boolean;
  status: string;
  statusLabel: string;
  currentAssets: Assignment[];
  cardExpiresAt?: string | null;
  medicalExpiresAt?: string | null;
  warnings?: string[];
}

interface Summary {
  active: number;
  suspended: number;
  inactive: number;
  licensesExpired: number;
  licensesExpiringSoon: number;
  withoutAsset: number;
}

interface Asset {
  id: string;
  tag: string;
  name: string;
}

/**
 * Motoristas.
 *
 * <p>A ficha existe por duas razões práticas: saber quem responde por uma
 * viagem meses depois, e não deixar ninguém conduzir com a carta caducada —
 * porque nesse caso quem responde é a empresa, não a pessoa.
 */
export function DriversPage() {
  const { can } = useAuth();
  const [search, setSearch] = useState('');
  const [applied, setApplied] = useState('');
  const [novo, setNovo] = useState(false);
  const [assign, setAssign] = useState<Driver | null>(null);
  const [ficha, setFicha] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['drivers', applied],
    queryFn: () =>
      api<{ content: Driver[] }>(
        `/drivers?size=200${applied ? `&search=${encodeURIComponent(applied)}` : ''}`,
      ),
    placeholderData: keepPreviousData,
  });

  const { data: summary } = useQuery({
    queryKey: ['drivers', 'summary'],
    queryFn: () => api<Summary>('/drivers/summary'),
  });

  const [procura, setProcura] = useState('');
  const todasRows = data?.content ?? [];
  // Procura que ignora acentos e aceita as palavras por qualquer ordem.
  const rows = useMemo(
    () => filtrar(todasRows, procura, (d) => [d.name, d.employeeNumber, d.phone, d.licenseNumber, d.branchName, d.statusLabel]),
    [todasRows, procura],
  );
  const podeGerir = can('MANAGER');

  return (
    <Stack gap="lg">

      <Group gap="sm" wrap="wrap">
        <Kpi label="Ativos" value={summary?.active ?? 0} />
        <Kpi
          label="Cartas caducadas"
          value={summary?.licensesExpired ?? 0}
          tone={(summary?.licensesExpired ?? 0) > 0 ? 'critical' : 'good'}
          hint="Um motorista a conduzir com a carta caducada é responsabilidade da empresa."
        />
        <Kpi
          label="A caducar em 30 dias"
          value={summary?.licensesExpiringSoon ?? 0}
          tone={(summary?.licensesExpiringSoon ?? 0) > 0 ? 'warning' : 'neutral'}
        />
        <Kpi label="Sem ativo atribuído" value={summary?.withoutAsset ?? 0} />
        <Kpi label="Suspensos" value={summary?.suspended ?? 0} />
      </Group>

      {(summary?.licensesExpired ?? 0) > 0 && (
        <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
          <b>
            {summary!.licensesExpired} motorista(s) com a carta caducada.
          </b>{' '}
          O sistema recusa atribuí-los a um ativo enquanto o documento não for atualizado.
        </Alert>
      )}

      <Painel
        titulo="Motoristas"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
              Novo motorista
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Nome, número, carta…" />
          </>
        }
      >
        <Group align="flex-end" gap="sm">
          <TextInput
            label="Procurar"
            placeholder="nome, número de funcionário ou carta"
            leftSection={<IconSearch size={14} />}
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            onKeyDown={(e) => e.key === 'Enter' && setApplied(search)}
            style={{ flex: 1, maxWidth: 380 }}
          />
          <Button onClick={() => setApplied(search)}>Procurar</Button>
        </Group>
            </Painel>

      <Card p={0}>
        <Grelha
          id="motoristas"
          linhas={rows}
          chave={(d) => d.id}
          carregando={isLoading}
          aoAbrir={(d) => setFicha(d.id)}
          vazio="Ainda não há motoristas registados."
          colunas={[
            {
              id: 'nome',
              titulo: 'Motorista',
              fixa: true,
              valor: (d) => d.name,
              render: (d) => (
                <>
                  <Text size="sm" fw={600}>
                    {d.name}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {[d.employeeNumber, d.phone, d.branchName].filter(Boolean).join(' · ') || '—'}
                  </Text>
                </>
              ),
            },
            { id: 'numero', titulo: 'Nº', largura: 90, escondida: true, valor: (d) => d.employeeNumber ?? null },
            { id: 'telefone', titulo: 'Telefone', largura: 130, escondida: true, valor: (d) => d.phone ?? null },
            { id: 'filial', titulo: 'Filial', largura: 150, escondida: true, valor: (d) => d.branchName ?? null },
            {
              id: 'carta',
              titulo: 'Carta',
              largura: 170,
              valor: (d) => d.licenseNumber ?? null,
              render: (d) => (
                <>
                  <Text size="sm">{d.licenseNumber ?? '—'}</Text>
                  {d.licenseCategories && (
                    <Text size="xs" c="dimmed">
                      Cat. {d.licenseCategories}
                    </Text>
                  )}
                </>
              ),
            },
            {
              id: 'validade',
              titulo: 'Validade',
              largura: 130,
              valor: (d) => d.licenseExpiresAt ?? null,
              render: (d) => <LicenceBadge driver={d} />,
            },
            {
              id: 'documentos',
              titulo: 'Cartão / exame médico',
              largura: 190,
              valor: (d) => (d.warnings ?? []).length,
              render: (d) =>
                (d.warnings ?? []).length > 0 ? (
                  <Text size="xs" c={d.canDrive ? 'orange' : 'red'}>
                    {(d.warnings ?? []).join(' · ')}
                  </Text>
                ) : (
                  <Text size="xs" c="dimmed">
                    {d.cardExpiresAt ? `cartão até ${fmtDate(d.cardExpiresAt)}` : 'sem cartão registado'}
                  </Text>
                ),
            },
            {
              id: 'conduz',
              titulo: 'Conduz',
              valor: (d) => d.currentAssets.map((a) => a.assetTag).join(', ') || null,
              render: (d) =>
                d.currentAssets.length === 0 ? (
                  <Text size="xs" c="dimmed">
                    nenhum ativo
                  </Text>
                ) : (
                  <Group gap={4}>
                    {d.currentAssets.map((a) => (
                      <Badge key={a.id} variant="light" color={a.primaryDriver ? 'gold' : 'gray'} size="sm">
                        {a.assetTag}
                      </Badge>
                    ))}
                  </Group>
                ),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 110,
              valor: (d) => d.statusLabel,
              render: (d) => (
                <Badge
                  variant="light"
                  color={d.status === 'ACTIVE' ? 'green' : d.status === 'SUSPENDED' ? 'orange' : 'gray'}
                  size="sm"
                >
                  {d.statusLabel}
                </Badge>
              ),
            },
            ...(podeGerir
              ? [
                  {
                    id: 'acoes',
                    titulo: '',
                    largura: 100,
                    render: (d: Driver) => (
                      <Group gap={4} wrap="nowrap">
                        <Button size="xs" variant="default" onClick={() => setFicha(d.id)}>
                          Ficha
                        </Button>
                        <Button size="xs" variant="default" disabled={!d.canDrive} onClick={() => setAssign(d)}>
                          Atribuir
                        </Button>
                      </Group>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Card>

      <EscalaDaEmpresa />

      <NewDriverModal opened={novo} onClose={() => setNovo(false)} />
      <AssignModal driver={assign} onClose={() => setAssign(null)} />
      {ficha && <FichaMotorista motoristaId={ficha} fechar={() => setFicha(null)} />}
    </Stack>
  );
}

/**
 * Validade da carta.
 *
 * <p>Os dias vêm do servidor. Se cada ecrã fizesse a conta a partir da data,
 * bastava um fuso diferente para dois ecrãs discordarem sobre se uma carta
 * ainda é válida.
 */
function LicenceBadge({ driver }: { driver: Driver }) {
  if (!driver.licenseExpiresAt) {
    return (
      <Text size="xs" c="dimmed">
        sem registo
      </Text>
    );
  }
  const dias = driver.licenseExpiresInDays ?? 0;
  const cor = driver.licenseExpired ? 'red' : dias <= 30 ? 'orange' : 'gray';
  return (
    <>
      <Text size="sm">{fmtDate(driver.licenseExpiresAt)}</Text>
      <Badge variant="light" color={cor} size="sm">
        {driver.licenseExpired ? `caducou há ${Math.abs(dias)} d` : `faltam ${dias} d`}
      </Badge>
    </>
  );
}

function NewDriverModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Record<string, string>>({});

  // O valor tem de ser lido AQUI, e não lá dentro.
  //
  // A função que se passa ao `setState` não corre no momento do evento: corre
  // na renderização seguinte, e nessa altura o React já reciclou o evento e
  // `currentTarget` é nulo. Resultado: «Cannot read properties of null» à
  // primeira letra escrita, e o ecrã em branco. Lê-se antes, guarda-se numa
  // variável, e o actualizador só usa o que já está em mão.
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const valor = e.currentTarget.value;
    setForm((f) => ({ ...f, [k]: valor }));
  };

  const create = useMutation({
    mutationFn: () =>
      api('/drivers', {
        method: 'POST',
        body: {
          name: form.name,
          employeeNumber: form.employeeNumber || null,
          phone: form.phone || null,
          email: form.email || null,
          nationalId: form.nationalId || null,
          licenseNumber: form.licenseNumber || null,
          licenseCategories: form.licenseCategories || null,
          licenseExpiresAt: form.licenseExpiresAt || null,
          cardNumber: form.cardNumber || null,
          cardExpiresAt: form.cardExpiresAt || null,
          medicalExpiresAt: form.medicalExpiresAt || null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Motorista registado', message: form.name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['drivers'] });
      setForm({});
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Registar motorista" centered>
      <Stack gap="sm">
        <TextInput label="Nome" required value={form.name ?? ''} onChange={set('name')} />
        <Group grow>
          <TextInput
            label="Nº de funcionário"
            value={form.employeeNumber ?? ''}
            onChange={set('employeeNumber')}
          />
          <TextInput label="Telefone" value={form.phone ?? ''} onChange={set('phone')} />
        </Group>
        <Group grow>
          <TextInput
            label="Nº da carta"
            value={form.licenseNumber ?? ''}
            onChange={set('licenseNumber')}
          />
          <TextInput
            label="Categorias"
            placeholder="B, C, D"
            value={form.licenseCategories ?? ''}
            onChange={set('licenseCategories')}
          />
        </Group>
        <TextInput
          label="Validade da carta"
          type="date"
          description="Sem esta data o sistema não consegue avisar antes de caducar."
          value={form.licenseExpiresAt ?? ''}
          onChange={set('licenseExpiresAt')}
        />
        <Group grow>
          <TextInput label="Nº do cartão de motorista" value={form.cardNumber ?? ''} onChange={set('cardNumber')} />
          <TextInput label="Validade do cartão" type="date" value={form.cardExpiresAt ?? ''} onChange={set('cardExpiresAt')} />
          <TextInput label="Exame médico até" type="date" value={form.medicalExpiresAt ?? ''} onChange={set('medicalExpiresAt')} />
        </Group>
        <Text size="xs" c="dimmed">
          Um motorista não precisa de conta no IMBONDEIRO OS. Se também usar o sistema, associe a
          conta depois na ficha.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!form.name?.trim()}
            loading={create.isPending}
            onClick={() => create.mutate()}
          >
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function AssignModal({ driver, onClose }: { driver: Driver | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [assetId, setAssetId] = useState<string | null>(null);
  const [primary, setPrimary] = useState<string | null>('true');

  const { data: assets } = useQuery({
    queryKey: ['assets', 'para-atribuir'],
    queryFn: () => api<{ content: Asset[] }>('/assets?size=200'),
    enabled: !!driver,
  });

  const assign = useMutation({
    mutationFn: () =>
      api('/driver-assignments', {
        method: 'POST',
        body: {
          driverId: driver!.id,
          assetId,
          primaryDriver: primary === 'true',
        },
      }),
    onSuccess: () => {
      notifications.show({
        title: 'Atribuído',
        message: `${driver!.name} passa a conduzir este ativo.`,
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['drivers'] });
      setAssetId(null);
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível atribuir', message: e.message, color: 'red' }),
  });

  return (
    <Modal
      opened={!!driver}
      onClose={onClose}
      title={`Atribuir ativo a ${driver?.name ?? ''}`}
      centered
    >
      <Stack gap="sm">
        <Select
          label="Ativo"
          placeholder="escolher"
          searchable
          data={(assets?.content ?? []).map((a) => ({
            value: a.id,
            label: `${a.tag} — ${a.name}`,
          }))}
          value={assetId}
          onChange={setAssetId}
        />
        <Select
          label="Papel"
          description="O titular é quem responde quando há titular e ajudante ao mesmo tempo."
          data={[
            { value: 'true', label: 'Condutor titular' },
            { value: 'false', label: 'Condutor secundário' },
          ]}
          value={primary}
          onChange={setPrimary}
          allowDeselect={false}
        />
        <Text size="xs" c="dimmed">
          Atribuir um titular novo encerra o anterior no mesmo instante — sem intervalo por
          explicar entre os dois.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!assetId} loading={assign.isPending} onClick={() => assign.mutate()}>
            Atribuir
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/**
 * A escala da empresa nos próximos 7 dias: quem está com que viatura e
 * quando. Os turnos escalam-se na ficha de cada motorista.
 */
function EscalaDaEmpresa() {
  const { data } = useQuery({
    queryKey: ['roster'],
    queryFn: () => api<{ id: string; driverName: string; assetTag?: string | null; startsAt: string; endsAt: string; kindLabel: string; notes?: string | null }[]>('/drivers/roster'),
  });
  const turnos = data ?? [];
  return (
    <Painel titulo="Escala dos próximos 7 dias">
      {turnos.length === 0 ? (
        <Text size="sm" c="dimmed">
          Sem turnos escalados. Abra a ficha de um motorista (duplo clique) → Escala → Escalar turno.
        </Text>
      ) : (
        <Group gap="xs" wrap="wrap">
          {turnos.map((t) => (
            <Card key={t.id} p="xs" withBorder style={{ minWidth: 220 }}>
              <Text size="sm" fw={600}>
                {t.driverName}
              </Text>
              <Text size="xs">
                {fmtDateTime(t.startsAt)} → {fmtDateTime(t.endsAt)}
              </Text>
              <Text size="xs" c="dimmed">
                {t.kindLabel}
                {t.assetTag ? ` · ${t.assetTag}` : ''}
                {t.notes ? ` · ${t.notes}` : ''}
              </Text>
            </Card>
          ))}
        </Group>
      )}
    </Painel>
  );
}
