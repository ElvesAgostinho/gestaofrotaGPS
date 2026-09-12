import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Divider,
  Group,
  List,
  Loader,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  ThemeIcon,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconInfoCircle,
  IconLock,
  IconLockOpen,
  IconShieldCheck,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime, fmtNumber } from '../../lib/format';

export interface SafetyView {
  safe: boolean;
  stoppedReadings: number;
  reason: string;
}

export interface CommandView {
  id: string;
  assetTag: string;
  kind: 'ENGINE_STOP' | 'ENGINE_RESUME';
  status: string;
  statusLabel: string;
  reason: string;
  reasonCategoryLabel?: string | null;
  requestedByName?: string | null;
  requestedAt: string;
  approvedByName?: string | null;
  approvedAt?: string | null;
  sentAt?: string | null;
  confirmedAt?: string | null;
  cancelledByName?: string | null;
  failureReason?: string | null;
  expiresAt: string;
  confirmationLabel?: string | null;
  confirmationSource?: string | null;
  providerQueued: boolean;
  /** Identificador do comando no servidor de comandos, para cruzar registos. */
  providerCommandId?: string | null;
  previousLockState?: string | null;
  resultingLockState?: string | null;
  requestSpeedKph?: number | null;
  requestLatitude?: number | null;
  requestLongitude?: number | null;
}

export interface LockStatus {
  assetId: string;
  assetTag: string;
  locked: boolean;
  lockedSince?: string | null;
  lockedConfirmationLabel?: string | null;
  lockedConfirmationSource?: string | null;
  pending?: CommandView | null;
  providerConfigured: boolean;
  providerName: string;
  deviceExternalId?: string | null;
  deviceProtocol?: string | null;
  immobiliserSupported?: boolean | null;
  commandsSyncedLabel: string;
  safety?: SafetyView;
}

interface ReasonCategory {
  code: string;
  label: string;
}

/** Dados da viatura mostrados no diálogo de confirmação. */
export interface AssetContext {
  tag: string;
  name: string;
  plate?: string | null;
  responsible?: string | null;
  speedKph?: number | null;
  latitude?: number | null;
  longitude?: number | null;
}

export function LockPanel({ assetId, asset }: { assetId: string; asset: AssetContext }) {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [action, setAction] = useState<'lock' | 'unlock' | null>(null);

  const {
    data: status,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['lock', assetId],
    queryFn: () => api<LockStatus>(`/assets/${assetId}/lock`),
    refetchInterval: 30_000,
  });

  const { data: history } = useQuery({
    queryKey: ['commands', assetId],
    queryFn: () => api<{ content: CommandView[] }>(`/assets/${assetId}/commands?size=20`),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['lock', assetId] });
    queryClient.invalidateQueries({ queryKey: ['commands', assetId] });
  };

  const act = useMutation({
    mutationFn: (body: { path: string; payload?: Record<string, unknown> }) =>
      api<CommandView>(body.path, { method: 'POST', body: body.payload }),
    onSuccess: (cmd) => {
      notifications.show({
        title: cmd.statusLabel,
        message: describeOutcome(cmd),
        color: outcomeColour(cmd.status),
        autoClose: 10_000,
      });
      setAction(null);
      invalidate();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  // 🔴 A auditoria encontrou um `Loader` eterno quando a chamada falhava.
  if (isError) {
    return (
      <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
        <Stack gap="xs" align="flex-start">
          <Text size="sm">
            Não foi possível ler o estado do bloqueio: {(error as Error).message}
          </Text>
          <Button size="xs" variant="default" onClick={() => refetch()}>
            Tentar de novo
          </Button>
        </Stack>
      </Alert>
    );
  }
  if (isLoading || !status) return <Loader size="sm" />;

  const safety = status.safety;
  const pending = status.pending;
  const busy = act.isPending;

  return (
    <Stack>
      <ProviderWarning status={status} />

      <Card p="md">
        <Group justify="space-between" align="flex-start">
          <div>
            <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
              Estado do motor
            </Text>
            <Group gap="xs" align="center">
              <ThemeIcon
                variant="light"
                color={status.locked ? 'red' : 'green'}
                size="lg"
                radius="sm"
              >
                {status.locked ? <IconLock size={18} /> : <IconLockOpen size={18} />}
              </ThemeIcon>
              <Text fw={700} style={{ fontSize: 20 }} c={status.locked ? 'red' : 'green'}>
                {status.locked ? 'Bloqueado' : 'Livre'}
              </Text>
            </Group>
            {status.lockedSince && (
              <Text size="xs" c="dimmed" mt={4}>
                Desde {fmtDateTime(status.lockedSince)}
              </Text>
            )}
            {/* Declarado por uma pessoa não é prova. Tem de se ver. */}
            {status.locked && status.lockedConfirmationSource === 'MANUAL' && (
              <Badge color="orange" variant="light" size="sm" mt={6}>
                {status.lockedConfirmationLabel}
              </Badge>
            )}
            {status.locked && status.lockedConfirmationSource !== 'MANUAL' && (
              <Badge color="green" variant="light" size="sm" mt={6}>
                {status.lockedConfirmationLabel}
              </Badge>
            )}
          </div>

          <Stack gap="xs" align="flex-end">
            <Badge
              variant="light"
              color={safety?.safe ? 'green' : 'gray'}
              leftSection={safety?.safe ? <IconShieldCheck size={12} /> : undefined}
            >
              {safety?.safe ? 'Seguro cortar agora' : 'Não é seguro cortar'}
            </Badge>
            {can('OWNER') && (
              <Group gap="xs">
                <Button
                  size="sm"
                  color="red"
                  variant={status.locked ? 'light' : 'filled'}
                  leftSection={<IconLock size={16} />}
                  disabled={busy || !!pending || status.locked}
                  onClick={() => setAction('lock')}
                >
                  Bloquear motor
                </Button>
                <Button
                  size="sm"
                  variant="default"
                  leftSection={<IconLockOpen size={16} />}
                  disabled={busy || !!pending}
                  onClick={() => setAction('unlock')}
                >
                  Desbloquear
                </Button>
              </Group>
            )}
          </Stack>
        </Group>

        <Text size="sm" c="dimmed" mt="sm">
          {safety?.reason}
        </Text>
      </Card>

      {pending && (
        <PendingCommand
          command={pending}
          busy={busy}
          canAct={can('OWNER')}
          onApprove={() => act.mutate({ path: `/commands/${pending.id}/approve` })}
          onCancel={() => act.mutate({ path: `/commands/${pending.id}/cancel` })}
          onDeclare={() => act.mutate({ path: `/commands/${pending.id}/confirm` })}
        />
      )}

      <CommandHistory rows={history?.content ?? []} />

      <ConfirmDialog
        action={action}
        asset={asset}
        status={status}
        busy={busy}
        onClose={() => setAction(null)}
        onConfirm={(payload) =>
          act.mutate({
            path: `/assets/${assetId}/${action === 'lock' ? 'lock' : 'unlock'}`,
            payload,
          })
        }
      />
    </Stack>
  );
}

// ---- avisos do fornecedor -------------------------------------------------
function ProviderWarning({ status }: { status: LockStatus }) {
  if (!status.providerConfigured) {
    return (
      <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
        <b>Não é possível bloquear viaturas.</b> Não há servidor de comandos configurado
        ({status.providerName}). O IMBONDEIRO OS recebe posições mas não mantém ligação aos
        aparelhos — o bloqueio é entregue por um servidor Traccar, que tem de ser
        configurado. Ver <code>docs/TRACCAR.md</code>.
      </Alert>
    );
  }
  if (status.immobiliserSupported === false) {
    return (
      <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
        <b>Este aparelho não suporta imobilização.</b> O protocolo{' '}
        <b>{status.deviceProtocol}</b> não declara o comando <code>engineStop</code>. Um
        pedido de bloqueio vai ser recusado.
      </Alert>
    );
  }
  if (status.immobiliserSupported === null || status.immobiliserSupported === undefined) {
    return (
      <Alert color="blue" variant="light" icon={<IconInfoCircle size={18} />}>
        {status.commandsSyncedLabel} Sincronize o aparelho em Definições para saber se este
        protocolo aceita o comando de imobilização.
      </Alert>
    );
  }
  return null;
}

// ---- comando em curso -----------------------------------------------------
function PendingCommand({
  command,
  busy,
  canAct,
  onApprove,
  onCancel,
  onDeclare,
}: {
  command: CommandView;
  busy: boolean;
  canAct: boolean;
  onApprove: () => void;
  onCancel: () => void;
  onDeclare: () => void;
}) {
  return (
    <Card p="md" withBorder style={{ borderColor: 'var(--mantine-color-yellow-4)' }}>
      <Group justify="space-between" align="flex-start">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
            Comando em curso
          </Text>
          <Text fw={700}>
            {command.kind === 'ENGINE_STOP' ? 'Bloqueio' : 'Desbloqueio'} — {command.statusLabel}
          </Text>
          <Text size="sm" c="dimmed">
            {command.reasonCategoryLabel ? `${command.reasonCategoryLabel}: ` : ''}
            {command.reason}
          </Text>
          <Text size="xs" c="dimmed" mt={4}>
            Pedido por {command.requestedByName} em {fmtDateTime(command.requestedAt)} · caduca{' '}
            {fmtDateTime(command.expiresAt)}
          </Text>
          {command.providerQueued && (
            <Alert color="orange" variant="light" mt="sm" p="xs">
              O aparelho está offline. O comando ficou em fila no servidor e só será executado
              quando ele ligar — <b>a viatura não está bloqueada</b>.
            </Alert>
          )}
        </div>

        {canAct && (
          <Group gap="xs">
            {command.status === 'PENDING_APPROVAL' && (
              <Button size="xs" color="red" loading={busy} onClick={onApprove}>
                Aprovar
              </Button>
            )}
            {command.status === 'SENT' && (
              <Button size="xs" variant="default" loading={busy} onClick={onDeclare}>
                Declarar confirmado
              </Button>
            )}
            <Button size="xs" variant="subtle" loading={busy} onClick={onCancel}>
              Anular
            </Button>
          </Group>
        )}
      </Group>
    </Card>
  );
}

// ---- histórico ------------------------------------------------------------
function CommandHistory({ rows }: { rows: CommandView[] }) {
  return (
    <Card p="md">
      <Text fw={700} mb="sm">
        Histórico de comandos
      </Text>
      {rows.length === 0 ? (
        <Text c="dimmed" size="sm">
          Nunca foi pedido nenhum bloqueio a esta viatura.
        </Text>
      ) : (
        <Table.ScrollContainer minWidth={760}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Data</Table.Th>
                <Table.Th>Ação</Table.Th>
                <Table.Th>Motivo</Table.Th>
                <Table.Th>Pedido por</Table.Th>
                <Table.Th>Aprovado por</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th>Confirmação</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rows.map((c) => (
                <Table.Tr key={c.id}>
                  <Table.Td>{fmtDateTime(c.requestedAt)}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={c.kind === 'ENGINE_STOP' ? 'red' : 'green'}>
                      {c.kind === 'ENGINE_STOP' ? 'Bloqueio' : 'Desbloqueio'}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{c.reasonCategoryLabel ?? '—'}</Text>
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      {c.reason}
                    </Text>
                  </Table.Td>
                  <Table.Td>{c.requestedByName ?? '—'}</Table.Td>
                  <Table.Td>{c.approvedByName ?? '—'}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={outcomeColour(c.status)}>
                      {c.statusLabel}
                    </Badge>
                    {c.failureReason && (
                      <Text size="xs" c="dimmed" lineClamp={2}>
                        {c.failureReason}
                      </Text>
                    )}
                    {/* Chave para cruzar com o registo do servidor de comandos
                        quando e preciso perceber porque e que um bloqueio nao
                        funcionou. Sem ela, resta adivinhar qual foi. */}
                    {c.providerCommandId && (
                      <Text size="xs" c="dimmed" ff="monospace">
                        n.º {c.providerCommandId} no servidor
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    {c.confirmationLabel ? (
                      <Text size="xs" c={c.confirmationSource === 'MANUAL' ? 'orange' : 'green'}>
                        {c.confirmationLabel}
                      </Text>
                    ) : (
                      <Text size="xs" c="dimmed">
                        —
                      </Text>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Card>
  );
}

// ---- diálogo de confirmação ----------------------------------------------
function ConfirmDialog({
  action,
  asset,
  status,
  busy,
  onClose,
  onConfirm,
}: {
  action: 'lock' | 'unlock' | null;
  asset: AssetContext;
  status: LockStatus;
  busy: boolean;
  onClose: () => void;
  onConfirm: (payload: Record<string, unknown>) => void;
}) {
  const [reason, setReason] = useState('');
  const [category, setCategory] = useState<string | null>('THEFT');
  const [acknowledged, setAcknowledged] = useState(false);

  const { data: categories } = useQuery({
    queryKey: ['commands', 'reason-categories'],
    queryFn: () => api<ReasonCategory[]>('/commands/reason-categories'),
    enabled: !!action,
  });

  const locking = action === 'lock';
  const ready = reason.trim().length >= 5 && acknowledged && !!category;

  const close = () => {
    setReason('');
    setAcknowledged(false);
    onClose();
  };

  return (
    <Modal
      opened={!!action}
      onClose={close}
      title={locking ? 'Bloquear o motor desta viatura' : 'Desbloquear o motor'}
      size="lg"
    >
      <Stack gap="md">
        {/* Quem autoriza um corte de motor tem de ver contra o que está a agir. */}
        <Card p="sm" bg="var(--mantine-color-gray-0)">
          <Table withRowBorders={false} verticalSpacing={4}>
            <Table.Tbody>
              <Row label="Viatura" value={`${asset.tag} — ${asset.name}`} />
              {asset.plate && <Row label="Matrícula" value={asset.plate} />}
              {asset.responsible && <Row label="Responsável" value={asset.responsible} />}
              <Row label="Estado atual" value={status.locked ? 'Bloqueado' : 'Livre'} />
              <Row
                label="Velocidade"
                value={
                  asset.speedKph != null ? `${fmtNumber(asset.speedKph)} km/h` : 'sem leitura'
                }
              />
              <Row
                label="Localização"
                value={
                  asset.latitude != null && asset.longitude != null
                    ? `${asset.latitude}, ${asset.longitude}`
                    : 'desconhecida'
                }
              />
              <Row label="Aparelho" value={status.deviceExternalId ?? 'sem aparelho'} />
            </Table.Tbody>
          </Table>
        </Card>

        {locking ? (
          <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />}>
            <Text size="sm" fw={600} mb={4}>
              O que vai acontecer
            </Text>
            <List size="sm" spacing={2}>
              <List.Item>O pedido fica registado com o seu nome e o motivo.</List.Item>
              <List.Item>Precisa de ser aprovado num segundo passo.</List.Item>
              <List.Item>
                <b>Não é enviado com a viatura em movimento</b> — fica em fila até ela parar.
              </List.Item>
              <List.Item>Caduca em 4 horas se não houver condições de segurança.</List.Item>
              <List.Item>
                O comando impede o <b>próximo arranque</b>; não mata o motor em andamento.
              </List.Item>
            </List>
          </Alert>
        ) : (
          <Alert color="blue" variant="light" icon={<IconInfoCircle size={18} />}>
            O desbloqueio é imediato e não precisa de aprovação: não conseguir desbloquear uma
            viatura é, por si só, um perigo.
          </Alert>
        )}

        <Select
          label="Motivo"
          placeholder="Escolha a categoria"
          data={(categories ?? []).map((c) => ({ value: c.code, label: c.label }))}
          value={category}
          onChange={setCategory}
        />

        <Textarea
          label="Detalhe do motivo"
          description="Fica no registo permanente. Seja específico: número de participação, ordem, contrato."
          placeholder="Ex.: furto participado na 3.ª esquadra, auto n.º 123/2026"
          minRows={3}
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
        />

        <Divider />

        <Checkbox
          checked={acknowledged}
          onChange={(e) => setAcknowledged(e.currentTarget.checked)}
          label={
            locking
              ? 'Confirmo que compreendo o efeito deste comando e que o pedido fica registado em meu nome.'
              : 'Confirmo que quero desbloquear esta viatura.'
          }
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={close} disabled={busy}>
            Cancelar
          </Button>
          <Button
            color={locking ? 'red' : 'blue'}
            disabled={!ready || busy}
            // `loading` desativa o botão enquanto o pedido corre: é isto que
            // impede o duplo clique criar dois comandos.
            loading={busy}
            onClick={() =>
              onConfirm({
                reason: reason.trim(),
                reasonCategory: category,
                acknowledged: true,
              })
            }
          >
            {locking ? 'Pedir bloqueio' : 'Desbloquear'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Table.Tr>
      <Table.Td w={130}>
        <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
          {label}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" fw={500}>
          {value}
        </Text>
      </Table.Td>
    </Table.Tr>
  );
}

function outcomeColour(status: string) {
  switch (status) {
    case 'CONFIRMED':
      return 'green';
    case 'FAILED':
    case 'EXPIRED':
      return 'red';
    case 'CANCELLED':
    case 'SUPERSEDED':
      return 'gray';
    case 'SENT':
      return 'blue';
    // Laranja, nem verde nem vermelho: o comando saiu e nao se sabe o que
    // aconteceu. Pintar de vermelho sugeria que falhou; de verde, que resultou.
    case 'UNCONFIRMED':
      return 'orange';
    default:
      return 'yellow';
  }
}

function describeOutcome(cmd: CommandView) {
  if (cmd.status === 'FAILED') {
    return cmd.failureReason ?? 'O comando não foi entregue.';
  }
  if (cmd.status === 'SENT' && cmd.providerQueued) {
    return 'O aparelho está offline; o comando ficou em fila. A viatura não está bloqueada.';
  }
  if (cmd.status === 'SENT') {
    return 'Entregue ao servidor. Aguarda confirmação do aparelho.';
  }
  if (cmd.status === 'QUEUED') {
    return 'Em fila: sai assim que a viatura estiver parada.';
  }
  if (cmd.status === 'UNCONFIRMED') {
    return (
      cmd.failureReason ??
      'Enviado, mas o aparelho nunca confirmou. Nao se sabe o estado da viatura; confirme no local.'
    );
  }
  if (cmd.status === 'SUPERSEDED') {
    return cmd.failureReason ?? 'Substituido por um comando posterior.';
  }
  return cmd.reason;
}
