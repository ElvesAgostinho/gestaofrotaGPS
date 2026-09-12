import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconMail, IconPlugConnected, IconRefresh } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { fmtDateTime } from '../../lib/format';

interface EmailStatus {
  configured: boolean;
  host?: string | null;
  remetente?: string | null;
  mensagem: string;
}

interface ProviderHealth {
  configured: boolean;
  reachable: boolean;
  name: string;
  version?: string | null;
  failureReason?: string | null;
}

interface Device {
  id: string;
  externalId: string;
  name?: string | null;
  assetTag?: string | null;
  status: string;
}

interface DeviceSync {
  externalId: string;
  protocol?: string | null;
  status: string;
  online: boolean;
  supportedCommands: string[];
  immobiliserSupported: boolean;
  syncedAt: string;
}

/**
 * Estado das integrações externas.
 *
 * <p>Configurar SMTP e Traccar é sempre uma sequência de tentativas. Sem uma
 * forma de testar, só se descobre que está mal quando um convite não chega ou
 * quando um bloqueio não acontece — e aí ninguém sabe porquê.
 */
export function IntegrationsCard() {
  return (
    <Stack gap="lg">
      <EmailSection />
      <TraccarSection />
    </Stack>
  );
}

function EmailSection() {
  const [to, setTo] = useState('');

  const { data: status } = useQuery({
    queryKey: ['email', 'status'],
    queryFn: () => api<EmailStatus>('/email/status'),
  });

  const test = useMutation({
    mutationFn: () =>
      api<{ sent: boolean; destinatario: string; mensagem: string }>(
        `/email/test${to.trim() ? `?to=${encodeURIComponent(to.trim())}` : ''}`,
        { method: 'POST' },
      ),
    onSuccess: (r) =>
      notifications.show({
        title: r.sent ? 'Email enviado' : 'Não foi enviado',
        message: r.mensagem,
        color: r.sent ? 'green' : 'red',
        autoClose: 10_000,
      }),
    onError: (e: Error) =>
      notifications.show({ title: 'Falhou', message: e.message, color: 'red' }),
  });

  return (
    <Card p="md">
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <IconMail size={18} />
          <Title order={2} size="h4">
            Envio de email
          </Title>
        </Group>
        <Badge variant="light" color={status?.configured ? 'green' : 'gray'}>
          {status?.configured ? 'Configurado' : 'Não configurado'}
        </Badge>
      </Group>

      <Alert variant="light" color={status?.configured ? 'blue' : 'orange'} mb="sm">
        {status?.mensagem}
      </Alert>

      {status?.configured && (
        <Stack gap="sm">
          <Group gap="xl">
            <Field label="Servidor" value={status.host ?? '—'} />
            <Field label="Remetente" value={status.remetente ?? '—'} />
          </Group>
          <Group align="flex-end" gap="xs">
            <TextInput
              label="Enviar teste para"
              description="Vazio = para o seu próprio endereço."
              placeholder="opcional"
              value={to}
              onChange={(e) => setTo(e.currentTarget.value)}
              style={{ flex: 1, maxWidth: 320 }}
            />
            <Button onClick={() => test.mutate()} loading={test.isPending}>
              Enviar email de teste
            </Button>
          </Group>
        </Stack>
      )}
    </Card>
  );
}

function TraccarSection() {
  const queryClient = useQueryClient();

  const { data: health, refetch, isFetching } = useQuery({
    queryKey: ['traccar', 'status'],
    queryFn: () => api<ProviderHealth>('/telemetry/traccar/status'),
  });

  const { data: devices } = useQuery({
    queryKey: ['gps-devices'],
    queryFn: () => api<Device[]>('/gps-devices'),
  });

  const [synced, setSynced] = useState<Record<string, DeviceSync>>({});

  const sync = useMutation({
    mutationFn: (deviceId: string) =>
      api<DeviceSync>(`/gps-devices/${deviceId}/sync`, { method: 'POST' }),
    onSuccess: (result, deviceId) => {
      setSynced((prev) => ({ ...prev, [deviceId]: result }));
      notifications.show({
        title: `Protocolo ${result.protocol ?? 'desconhecido'}`,
        message: result.immobiliserSupported
          ? 'Este aparelho aceita o comando de imobilização.'
          : 'ATENÇÃO: este aparelho NÃO declara o comando de imobilização.',
        color: result.immobiliserSupported ? 'green' : 'red',
        autoClose: 10_000,
      });
      queryClient.invalidateQueries({ queryKey: ['lock'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível sincronizar', message: e.message, color: 'red' }),
  });

  return (
    <Card p="md">
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <IconPlugConnected size={18} />
          <Title order={2} size="h4">
            Servidor de comandos (Traccar)
          </Title>
        </Group>
        <Group gap="xs">
          <Badge
            variant="light"
            color={!health?.configured ? 'gray' : health.reachable ? 'green' : 'red'}
          >
            {!health?.configured
              ? 'Não configurado'
              : health.reachable
                ? 'Ligado'
                : 'Sem resposta'}
          </Badge>
          <Button
            size="xs"
            variant="default"
            leftSection={<IconRefresh size={14} />}
            onClick={() => refetch()}
            loading={isFetching}
          >
            Testar ligação
          </Button>
        </Group>
      </Group>

      {health && !health.reachable && (
        <Alert variant="light" color={health.configured ? 'red' : 'orange'} mb="sm">
          {health.failureReason}
        </Alert>
      )}
      {health?.reachable && (
        <Alert variant="light" color="green" mb="sm">
          Ligado a {health.name}
          {health.version ? ` — versão ${health.version}` : ''}.
        </Alert>
      )}

      <Text size="sm" c="dimmed" mb="sm">
        Sincronizar lê o protocolo do aparelho e os comandos que ele aceita.{' '}
        <b>Não envia nada ao aparelho.</b> Sem isto, o sistema não sabe se o rastreador suporta
        imobilização — e há protocolos que não suportam.
      </Text>

      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Aparelho</Table.Th>
            <Table.Th>Ativo</Table.Th>
            <Table.Th>Protocolo</Table.Th>
            <Table.Th>Imobilização</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(devices ?? []).length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={5}>
                <Text c="dimmed" ta="center" py="md" size="sm">
                  Nenhum aparelho de GPS registado.
                </Text>
              </Table.Td>
            </Table.Tr>
          )}
          {(devices ?? []).map((d) => {
            const result = synced[d.id];
            return (
              <Table.Tr key={d.id}>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {d.externalId}
                  </Text>
                  {d.name && (
                    <Text size="xs" c="dimmed">
                      {d.name}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{d.assetTag ?? '—'}</Table.Td>
                <Table.Td>{result?.protocol ?? '—'}</Table.Td>
                <Table.Td>
                  {result ? (
                    <Badge
                      variant="light"
                      color={result.immobiliserSupported ? 'green' : 'red'}
                    >
                      {result.immobiliserSupported ? 'Suportada' : 'Não suportada'}
                    </Badge>
                  ) : (
                    <Text size="xs" c="dimmed">
                      por verificar
                    </Text>
                  )}
                  {result && (
                    <Text size="xs" c="dimmed" mt={2}>
                      {fmtDateTime(result.syncedAt)}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Button
                    size="xs"
                    variant="default"
                    disabled={!health?.configured}
                    loading={sync.isPending && sync.variables === d.id}
                    onClick={() => sync.mutate(d.id)}
                  >
                    Sincronizar
                  </Button>
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
    </Card>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text size="sm" fw={500}>
        {value}
      </Text>
    </div>
  );
}
