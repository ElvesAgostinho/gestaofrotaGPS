import {
  Alert,
  Badge,
  Card,
  Group,
  Progress,
  SimpleGrid,
  Skeleton,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { IconCircleCheck } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { fmtNumber } from '../lib/format';

interface Metric {
  key: string;
  name: string;
  value: number | null;
  unit: string;
  target: number | null;
  targetDirection: 'MIN' | 'MAX';
  meetsTarget: boolean;
  formula: string;
}

interface Dashboard {
  assetsTotal: number;
  assetsCritical: number;
  assetsDown?: number;
  tasksOverdue: number;
  tasksDueSoon?: number;
  workOrdersOpen?: number;
  lowStockParts?: number;
  upcoming: {
    assetId: string;
    assetTag: string;
    title: string;
    status: string;
    remainingDays?: number | null;
    remainingMeter?: number | null;
  }[];
  kpis: { metrics: Metric[] };
}

export function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<Dashboard>('/dashboard'),
  });

  if (isLoading || !data) {
    return (
      <Stack>
        <Skeleton h={32} w={200} />
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} h={110} radius="md" />
          ))}
        </SimpleGrid>
        <Skeleton h={260} radius="md" />
      </Stack>
    );
  }

  return (
    <Stack gap="lg">
      <div style={{ borderLeft: '4px solid var(--erp-dourado)', paddingLeft: 10 }}>
        <Text
          component="h1"
          fw={700}
          style={{
            fontFamily: '"Barlow Condensed", Barlow, sans-serif',
            fontSize: 27,
            textTransform: 'uppercase',
            letterSpacing: '0.02em',
            margin: 0,
          }}
        >
          Painel
        </Text>
      </div>

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
        <Stat label="Ativos" value={data.assetsTotal} to="/ativos" />
        <Stat
          label="Criticidade crítica"
          value={data.assetsCritical}
          color={data.assetsCritical > 0 ? 'red' : undefined}
          to="/ativos"
        />
        <Stat
          label="Manutenções vencidas"
          value={data.tasksOverdue}
          color={data.tasksOverdue > 0 ? 'orange' : undefined}
          to="/ordens"
        />
        <Stat label="Ordens abertas" value={data.workOrdersOpen ?? 0} to="/ordens" />
      </SimpleGrid>

      <Card p="lg">
        <Title order={2} size="h4" mb="md">
          Indicadores
        </Title>
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
          {data.kpis.metrics.map((m) => (
            <KpiCard key={m.key} metric={m} />
          ))}
        </SimpleGrid>
      </Card>

      <Card p="lg">
        <Group justify="space-between" mb="md">
          <Title order={2} size="h4">
            Próximas manutenções
          </Title>
          <Badge variant="light">{data.upcoming.length}</Badge>
        </Group>

        {data.upcoming.length === 0 ? (
          <Alert color="green" variant="light" icon={<IconCircleCheck size={18} />}>
            Nada vencido nem a vencer. Todos os planos em dia.
          </Alert>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Ativo</Table.Th>
                <Table.Th>Tarefa</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th ta="right">Falta</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data.upcoming.map((t, i) => (
                <Table.Tr key={`${t.assetId}-${i}`}>
                  <Table.Td>
                    <Text component={Link} to={`/ativos/${t.assetId}`} fw={600} size="sm">
                      {t.assetTag}
                    </Text>
                  </Table.Td>
                  <Table.Td>{t.title}</Table.Td>
                  <Table.Td>
                    <Badge color={t.status === 'OVERDUE' ? 'red' : 'yellow'} variant="light">
                      {t.status === 'OVERDUE' ? 'Vencida' : 'A vencer'}
                    </Badge>
                  </Table.Td>
                  <Table.Td ta="right">{remaining(t)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
    </Stack>
  );
}

function Stat({
  label,
  value,
  color,
  to,
}: {
  label: string;
  value: number;
  color?: string;
  to?: string;
}) {
  const card = (
    <Card p="md" h="100%">
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {label}
      </Text>
      <Text fw={800} c={color} mt={4} style={{ fontSize: 30 }}>
        {value}
      </Text>
    </Card>
  );

  return to ? (
    <Link to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
      {card}
    </Link>
  ) : (
    card
  );
}

function KpiCard({ metric }: { metric: Metric }) {
  // Sem dados suficientes o valor vem nulo — mostrar "0" seria dizer que o
  // indicador está péssimo quando na verdade ainda não se sabe.
  const unknown = metric.value === null || metric.value === undefined;
  const color = unknown ? 'gray' : metric.meetsTarget ? 'green' : 'red';

  return (
    <Card p="md" bg="var(--mantine-color-gray-0)">
      <Text size="xs" c="dimmed" tt="uppercase" fw={700}>
        {metric.name}
      </Text>
      <Group gap={6} align="baseline" mt={4}>
        <Text fw={800} style={{ fontSize: 26 }} c={unknown ? 'dimmed' : color}>
          {unknown ? '—' : fmtNumber(metric.value)}
        </Text>
        {!unknown && (
          <Text size="sm" c="dimmed">
            {metric.unit}
          </Text>
        )}
      </Group>

      {unknown ? (
        <Text size="xs" c="dimmed" mt={6}>
          Ainda sem dados suficientes.
        </Text>
      ) : (
        <>
          <Progress
            value={progress(metric)}
            color={color}
            size="sm"
            mt="xs"
            aria-label={metric.name}
          />
          <Text size="xs" c="dimmed" mt={6}>
            Meta: {metric.targetDirection === 'MIN' ? '≥' : '≤'} {fmtNumber(metric.target)}{' '}
            {metric.unit}
          </Text>
        </>
      )}
    </Card>
  );
}

/** Barra proporcional à meta, limitada a 100 % para não sair do cartão. */
function progress(m: Metric) {
  if (m.value === null || m.target === null || m.target === 0) return 0;
  const ratio = m.targetDirection === 'MIN' ? m.value / m.target : m.target / m.value;
  return Math.max(0, Math.min(100, ratio * 100));
}

function remaining(t: { remainingDays?: number | null; remainingMeter?: number | null }) {
  if (t.remainingMeter !== null && t.remainingMeter !== undefined) {
    return t.remainingMeter < 0
      ? `${fmtNumber(Math.abs(t.remainingMeter))} a mais`
      : `${fmtNumber(t.remainingMeter)}`;
  }
  if (t.remainingDays !== null && t.remainingDays !== undefined) {
    return t.remainingDays < 0
      ? `há ${Math.abs(t.remainingDays)} dias`
      : `${t.remainingDays} dias`;
  }
  return '—';
}
