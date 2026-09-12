import {
  Alert,
  Badge,
  Button,
  Card,
  CopyButton,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconCopy, IconInfoCircle, IconKey, IconUserPlus } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { PermissoesMembro } from './team/PermissoesMembro';
import { useAuth } from '../auth/AuthContext';
import { fmtDate } from '../lib/format';

interface Member {
  id: string;
  userId: string;
  name: string;
  email?: string;
  role: string;
  roleLabel: string;
  jobTitle?: string | null;
  suspended: boolean;
  joinedAt: string;
  permissions?: string[];
  granted?: string[];
  denied?: string[];
}

interface Invitation {
  id: string;
  email: string;
  roleLabel: string;
  status: string;
  expiresAt: string;
}

interface InviteResult {
  demoMode: boolean;
  message: string;
  acceptUrl: string;
  invitation: Invitation;
}

export function TeamPage() {
  const { can, user } = useAuth();
  const queryClient = useQueryClient();
  const [inviting, setInviting] = useState(false);
  const [permissoesDe, setPermissoesDe] = useState<Member | null>(null);
  const [lastInvite, setLastInvite] = useState<InviteResult | null>(null);

  const { data: members } = useQuery({
    queryKey: ['team', 'members'],
    queryFn: () => api<Member[]>('/team/members'),
  });

  const { data: invitations } = useQuery({
    queryKey: ['team', 'invitations'],
    queryFn: () => api<Invitation[]>('/team/invitations'),
    enabled: can('MANAGER'),
  });

  const { data: roles } = useQuery({
    queryKey: ['team', 'roles'],
    queryFn: () => api<{ code: string; label: string; description: string }[]>('/team/roles'),
  });

  const form = useForm({
    initialValues: { email: '', name: '', jobTitle: '', role: 'TECHNICIAN' },
    validate: { email: (v) => (/^\S+@\S+\.\S+$/.test(v) ? null : 'Indique um email válido.') },
  });

  const invite = useMutation({
    mutationFn: (values: typeof form.values) =>
      api<InviteResult>('/team/invitations', { method: 'POST', body: values }),
    onSuccess: (result) => {
      setLastInvite(result);
      setInviting(false);
      form.reset();
      queryClient.invalidateQueries({ queryKey: ['team'] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) =>
      api(`/team/members/${id}`, { method: 'PATCH', body }),
    onSuccess: () => {
      notifications.show({ message: 'Equipa atualizada.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['team', 'members'] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="lg">

      {/* Sem serviço de email o convite não sai daqui — o link tem de ser
          entregue à mão, e isso tem de ficar evidente. */}
      {lastInvite && (
        <Alert color="blue" variant="light" icon={<IconInfoCircle size={18} />} withCloseButton
               onClose={() => setLastInvite(null)}>
          <Stack gap="xs">
            <Text size="sm">{lastInvite.message}</Text>
            <Group gap="xs">
              <TextInput readOnly value={lastInvite.acceptUrl} style={{ flex: 1 }} size="xs" />
              <CopyButton value={lastInvite.acceptUrl}>
                {({ copied, copy }) => (
                  <Button
                    size="xs"
                    variant={copied ? 'filled' : 'default'}
                    color={copied ? 'green' : undefined}
                    onClick={copy}
                    leftSection={copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                  >
                    {copied ? 'Copiado' : 'Copiar link'}
                  </Button>
                )}
              </CopyButton>
            </Group>
          </Stack>
        </Alert>
      )}

      <Painel
        titulo="Utilizadores e permissões"
        semPadding
        acoes={
          can('OWNER') ? (
            <BotaoBarra
              icone={<IconUserPlus size={15} />}
              onClick={() => setInviting(true)}
              destaque
            >
              Convidar
            </BotaoBarra>
          ) : undefined
        }
      >
        <Grelha
          id="equipa"
          linhas={members ?? []}
          chave={(m) => m.id}
          altura="calc(100vh - 420px)"
          vazio="Ainda não há membros."
          colunas={[
            {
              id: 'nome',
              titulo: 'Nome',
              fixa: true,
              valor: (m) => m.name,
              render: (m) => {
                const isMe = m.userId === user?.id;
                return (
                  <Group gap={6}>
                    <Text fw={600} size="sm">
                      {m.name}
                    </Text>
                    {isMe && (
                      <Badge size="xs" variant="light">
                        eu
                      </Badge>
                    )}
                    {m.suspended && (
                      <Badge size="xs" color="red" variant="light">
                        suspenso
                      </Badge>
                    )}
                  </Group>
                );
              },
            },
            { id: 'email', titulo: 'Email', valor: (m) => m.email ?? null },
            {
              id: 'papel',
              titulo: 'Papel',
              largura: 170,
              valor: (m) => m.roleLabel,
              render: (m) =>
                can('OWNER') && m.userId !== user?.id ? (
                  <Select
                    size="xs"
                    w={150}
                    value={m.role}
                    data={(roles ?? []).map((r) => ({ value: r.code, label: r.label }))}
                    onChange={(role) => role && update.mutate({ id: m.id, body: { role } })}
                  />
                ) : (
                  <Badge variant="light">{m.roleLabel}</Badge>
                ),
            },
            { id: 'cargo', titulo: 'Cargo', largura: 160, valor: (m) => m.jobTitle ?? null },
            {
              id: 'desde',
              titulo: 'Desde',
              largura: 110,
              alinhar: 'right',
              valor: (m) => m.joinedAt,
              render: (m) => fmtDate(m.joinedAt),
            },
            ...(can('OWNER')
              ? [
                  {
                    id: 'acoes',
                    titulo: '',
                    largura: 220,
                    render: (m: Member) =>
                      m.userId !== user?.id ? (
                        <Group gap={4} wrap="nowrap">
                          <Button
                            size="xs"
                            variant="default"
                            leftSection={<IconKey size={13} />}
                            onClick={() => setPermissoesDe(m)}
                          >
                            Permissões
                            {(m.granted?.length ?? 0) + (m.denied?.length ?? 0) > 0 && (
                              <Badge size="xs" variant="filled" color="gold" ml={6}>
                                {(m.granted?.length ?? 0) + (m.denied?.length ?? 0)}
                              </Badge>
                            )}
                          </Button>
                          <Button
                            size="xs"
                            variant="subtle"
                            color={m.suspended ? 'green' : 'orange'}
                            onClick={() => update.mutate({ id: m.id, body: { suspended: !m.suspended } })}
                          >
                            {m.suspended ? 'Reativar' : 'Suspender'}
                          </Button>
                        </Group>
                      ) : null,
                  },
                ]
              : []),
          ]}
        />
            </Painel>

      {can('MANAGER') && !!invitations?.length && (
        <Card p="md">
          <Title order={2} size="h4" mb="md">
            Convites
          </Title>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Email</Table.Th>
                <Table.Th>Papel</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th>Válido até</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {invitations.map((i) => (
                <Table.Tr key={i.id}>
                  <Table.Td>{i.email}</Table.Td>
                  <Table.Td>{i.roleLabel}</Table.Td>
                  <Table.Td>
                    <Badge
                      variant="light"
                      color={
                        i.status === 'ACEITE' ? 'green' : i.status === 'PENDENTE' ? 'blue' : 'gray'
                      }
                    >
                      {i.status}
                    </Badge>
                  </Table.Td>
                  <Table.Td>{fmtDate(i.expiresAt)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      )}

      <PermissoesMembro membro={permissoesDe} aberto={permissoesDe != null} fechar={() => setPermissoesDe(null)} />

      <Modal opened={inviting} onClose={() => setInviting(false)} title="Convidar para a equipa">
        <form onSubmit={form.onSubmit((v) => invite.mutate(v))}>
          <Stack gap="sm">
            <TextInput label="Email" placeholder="tecnico@empresa.ao" {...form.getInputProps('email')} />
            <TextInput label="Nome" placeholder="Opcional" {...form.getInputProps('name')} />
            <TextInput label="Cargo" placeholder="Chefe de oficina" {...form.getInputProps('jobTitle')} />
            <Select
              label="Papel"
              data={(roles ?? []).map((r) => ({ value: r.code, label: r.label }))}
              {...form.getInputProps('role')}
            />
            <Text size="xs" c="dimmed">
              {roles?.find((r) => r.code === form.values.role)?.description}
            </Text>
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={() => setInviting(false)}>
                Cancelar
              </Button>
              <Button type="submit" loading={invite.isPending}>
                Convidar
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
