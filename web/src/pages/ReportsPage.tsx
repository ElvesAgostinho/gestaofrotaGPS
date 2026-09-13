import { Button, Card, Group, Select, SimpleGrid, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { notifications } from '@mantine/notifications';
import {
  IconBuildingStore,
  IconWheel,
  IconClipboardList,
  IconClockPause,
  IconCoin,
  IconDownload,
  IconFileText,
  IconMap2,
  IconPackage,
  IconTrendingUp,
  IconTruck,
  IconWaveSine,
} from '@tabler/icons-react';
import { downloadFile, openFile } from '../api/client';

interface Report {
  path: string;
  label: string;
  description: string;
  icon: typeof IconTruck;
}

const REPORTS: Report[] = [
  {
    path: 'assets',
    label: 'Inventário de ativos',
    description: 'Todos os ativos com tipo, local, estado e criticidade.',
    icon: IconTruck,
  },
  {
    path: 'work-orders',
    label: 'Ordens de manutenção',
    description: 'Ordens com datas, mão de obra, custo de peças e resolução.',
    icon: IconClipboardList,
  },
  {
    path: 'maintenance-by-asset',
    label: 'Custo de manutenção por ativo',
    description:
      'Responde à pergunta que decide o destino de uma viatura: vale a pena continuar a repará-la? Leva o custo por km e os dias parada.',
    icon: IconCoin,
  },
  {
    path: 'maintenance-by-supplier',
    label: 'Manutenção por oficina',
    description: 'Quanto se gastou em cada oficina, com NIF, e quanto tempo demoraram.',
    icon: IconBuildingStore,
  },
  {
    path: 'maintenance-downtime',
    label: 'Imobilização de viaturas',
    description:
      'Quanto tempo cada viatura esteve parada e o que isso custou — números que não estão em fatura nenhuma.',
    icon: IconClockPause,
  },
  {
    path: 'kpis',
    label: 'Indicadores',
    description: 'Disponibilidade, MTBF, MTTR e cumprimento, com os números que os produzem.',
    icon: IconTrendingUp,
  },
  {
    path: 'stock',
    label: 'Stock por armazém',
    description: 'Quantidades, mínimos e o que está abaixo do mínimo.',
    icon: IconPackage,
  },
  {
    path: 'documents',
    label: 'Documentos e validades',
    description: 'Seguros, inspeções e licenças com dias restantes.',
    icon: IconFileText,
  },
  {
    path: 'tyres',
    label: 'Pneus',
    description: 'Cada pneu com os km feitos, o custo por km e os alertas de sulco e pressão.',
    icon: IconWheel,
  },
  {
    path: 'predictive',
    label: 'Manutenção preditiva',
    description: 'Programas de monitorização e datas previstas.',
    icon: IconWaveSine,
  },
  {
    path: 'trips',
    label: 'Viagens',
    description: 'Deslocações dos últimos 30 dias com distância e velocidade máxima.',
    icon: IconMap2,
  },
];

/**
 * Pede o relatório ao servidor e guarda-o.
 *
 * <p>Antes disto o cartão era um `<a href>` para a rota da API. Como o token
 * vai no cabeçalho, o browser abria a ligação sem autenticação e o servidor
 * respondia 401: nenhum dos relatórios descarregava.
 */
type Formato = 'csv' | 'xlsx' | 'pdf';

/** CSV e Excel descarregam-se; o PDF abre-se noutro separador, pronto a imprimir. */
async function descarregar(path: string, formato: Formato) {
  try {
    if (formato === 'pdf') {
      await openFile(`/reports/${path}.pdf`);
    } else {
      await downloadFile(`/reports/${path}.${formato}`, `${path}.${formato}`);
    }
  } catch (e) {
    notifications.show({
      title: 'Não foi possível descarregar',
      message: (e as Error).message,
      color: 'red',
    });
  }
}

export function ReportsPage() {
  return (
    <Stack gap="lg">
      <div>
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
            Relatórios
          </Text>
        </div>
        <Text c="dimmed" size="sm">
          Cada relatório sai em Excel (para filtrar), CSV (para outros sistemas) ou PDF com o timbre da empresa (para imprimir e arquivar).
        </Text>
      </div>

      <RelatorioMensal />

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
        {REPORTS.map((r) => (
          <Card key={r.path} p="md">
            <Group gap="sm" mb="xs">
              <r.icon size={20} stroke={1.6} />
              <Text fw={700}>{r.label}</Text>
            </Group>
            <Text size="sm" c="dimmed">
              {r.description}
            </Text>
            <Group gap={6} mt="sm">
              <Button size="compact-xs" variant="filled" leftSection={<IconDownload size={13} />} onClick={() => descarregar(r.path, 'xlsx')}>
                Excel
              </Button>
              <Button size="compact-xs" variant="default" onClick={() => descarregar(r.path, 'pdf')}>
                PDF
              </Button>
              <Button size="compact-xs" variant="subtle" color="gray" onClick={() => descarregar(r.path, 'csv')}>
                CSV
              </Button>
            </Group>
          </Card>
        ))}
      </SimpleGrid>
    </Stack>
  );
}

/** Os últimos 12 meses, do mais recente para o mais antigo, como AAAA-MM. */
function ultimosMeses(): { value: string; label: string }[] {
  const out: { value: string; label: string }[] = [];
  const d = new Date();
  d.setDate(1);
  for (let i = 0; i < 12; i++) {
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const label = d.toLocaleDateString('pt-PT', { month: 'long', year: 'numeric' });
    out.push({ value, label: label.charAt(0).toUpperCase() + label.slice(1) });
    d.setMonth(d.getMonth() - 1);
  }
  return out;
}

/**
 * O relatório mensal: o mesmo PDF que no dia 1 segue sozinho por email (e em
 * resumo no telemóvel) ao dono e aos gestores. Aqui abre-se qualquer mês.
 */
function RelatorioMensal() {
  const { org } = useAuth();
  const [params] = useSearchParams();
  const meses = ultimosMeses();
  const [mes, setMes] = useState<string | null>(params.get('mes') ?? meses[1]?.value ?? meses[0].value);
  return (
    <Card p="md" style={{ borderLeft: '4px solid var(--erp-dourado)' }}>
      <Group justify="space-between" align="flex-end" wrap="wrap">
        <div>
          <Text fw={700}>Relatório mensal da frota</Text>
          <Text size="sm" c="dimmed">
            Custo por viatura, disponibilidade, ordens, combustível e o que ficou pendente — numa página.{' '}
            {org?.monthlyReportEnabled === false
              ? 'O envio automático está desligado nas Definições.'
              : 'Segue sozinho no dia 1 de cada mês, por email (PDF) e em resumo no telemóvel, ao dono e aos gestores.'}
          </Text>
        </div>
        <Group gap="xs">
          <Select data={meses} value={mes} onChange={setMes} allowDeselect={false} w={190} />
          <Button
            onClick={async () => {
              try {
                await openFile(`/reports/monthly.pdf?month=${mes}`);
              } catch (e) {
                notifications.show({ title: 'Não foi possível abrir', message: (e as Error).message, color: 'red' });
              }
            }}
          >
            Abrir PDF
          </Button>
        </Group>
      </Group>
    </Card>
  );
}
