import {
  Badge,
  Button,
  Card,
  Group,
  SegmentedControl,
  Stack,
  Switch,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { notifications as toast } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { fmtDateTime } from '../lib/format';

interface NotificationItem {
  id: string;
  category: string;
  categoryLabel: string;
  severity: string;
  title: string;
  body?: string | null;
  link?: string | null;
  assetTag?: string | null;
  emailState: string;
  read: boolean;
  createdAt: string;
}

interface Preference {
  category: string;
  label: string;
  inApp: boolean;
  email: boolean;
}

const SEVERITY: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'orange',
  CRITICAL: 'red',
};

export function NotificationsPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState('todas');

  const { data } = useQuery({
    queryKey: ['notifications', filter],
    queryFn: () =>
      api<{ content: NotificationItem[] }>(
        `/notifications?size=50${filter === 'porler' ? '&unread=true' : ''}`,
      ),
  });

  const { data: preferences } = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: () => api<Preference[]>('/notifications/preferences'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

  const markRead = useMutation({
    mutationFn: (id: string) => api(`/notifications/${id}/read`, { method: 'POST' }),
    onSuccess: invalidate,
  });

  const markAll = useMutation({
    mutationFn: () => api('/notifications/read-all', { method: 'POST' }),
    onSuccess: invalidate,
  });

  const setPreference = useMutation({
    mutationFn: ({ category, body }: { category: string; body: Record<string, boolean> }) =>
      api(`/notifications/preferences/${category}`, { method: 'PATCH', body }),
    onSuccess: () => {
      toast.show({ message: 'Preferência guardada.', color: 'green' });
      invalidate();
    },
  });

  const rows = data?.content ?? [];
  // Sem servidor de email configurado, nenhum aviso sai daqui — dizê-lo evita
  // que alguém fique à espera de um email que nunca vai chegar.
  const demoMode = rows.some((n) => n.emailState === 'DEMO_MODE');

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <Title order={1} size="h2">
          Notificações
        </Title>
        <Group gap="xs">
          <SegmentedControl
            size="xs"
            value={filter}
            onChange={setFilter}
            data={[
              { value: 'todas', label: 'Todas' },
              { value: 'porler', label: 'Por ler' },
            ]}
          />
          <Button size="xs" variant="default" onClick={() => markAll.mutate()}>
            Marcar todas como lidas
          </Button>
        </Group>
      </Group>

      <Card p="md">
        <Table>
          <Table.Tbody>
            {rows.length === 0 && (
              <Table.Tr>
                <Table.Td>
                  <Text c="dimmed" ta="center" py="xl">
                    Sem notificações.
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {rows.map((n) => (
              <Table.Tr key={n.id} bg={n.read ? undefined : 'var(--mantine-color-blue-0)'}>
                <Table.Td>
                  <Group justify="space-between" wrap="nowrap" align="flex-start">
                    <div>
                      <Group gap={6} mb={2}>
                        <Badge size="xs" variant="light" color={SEVERITY[n.severity]}>
                          {n.categoryLabel}
                        </Badge>
                        {n.assetTag && (
                          <Text size="xs" c="dimmed">
                            {n.assetTag}
                          </Text>
                        )}
                      </Group>
                      <Text fw={n.read ? 400 : 700} size="sm">
                        {n.link ? (
                          <Text component={Link} to={n.link} inherit>
                            {n.title}
                          </Text>
                        ) : (
                          n.title
                        )}
                      </Text>
                      {n.body && (
                        <Text size="sm" c="dimmed">
                          {n.body}
                        </Text>
                      )}
                      <Text size="xs" c="dimmed" mt={2}>
                        {fmtDateTime(n.createdAt)}
                      </Text>
                    </div>
                    {!n.read && (
                      <Button size="xs" variant="subtle" onClick={() => markRead.mutate(n.id)}>
                        Marcar lida
                      </Button>
                    )}
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>

      <Card p="md">
        <Title order={2} size="h4" mb="xs">
          Preferências
        </Title>
        {demoMode && (
          <Text size="sm" c="dimmed" mb="md">
            Ainda não há servidor de email configurado: os avisos existem aqui dentro, mas nenhum
            email é enviado.
          </Text>
        )}
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Categoria</Table.Th>
              <Table.Th w={140}>Na aplicação</Table.Th>
              <Table.Th w={140}>Por email</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {(preferences ?? []).map((p) => (
              <Table.Tr key={p.category}>
                <Table.Td>{p.label}</Table.Td>
                <Table.Td>
                  <Switch
                    checked={p.inApp}
                    onChange={(e) =>
                      setPreference.mutate({
                        category: p.category,
                        body: { inApp: e.currentTarget.checked },
                      })
                    }
                  />
                </Table.Td>
                <Table.Td>
                  <Switch
                    checked={p.email}
                    onChange={(e) =>
                      setPreference.mutate({
                        category: p.category,
                        body: { email: e.currentTarget.checked },
                      })
                    }
                  />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}
