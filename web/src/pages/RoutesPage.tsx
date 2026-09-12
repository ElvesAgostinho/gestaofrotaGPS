import { Badge, Group, Stack, Text } from '@mantine/core';
import { IconArrowRight, IconPlus } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura, filtrar } from '../components/Procura';
import { fmtNumber } from '../lib/format';
import { NovaRotaForm } from './routes/NovaRotaForm';

interface Waypoint {
  id: string;
  label: string;
}

interface Route {
  id: string;
  code?: string | null;
  name: string;
  originName: string;
  destinationName: string;
  expectedDistanceKm?: number | null;
  expectedDurationMinutes?: number | null;
  expectedFuelLiters?: number | null;
  tolerancePercent: number;
  active: boolean;
  /** MANUAL, ENGINE ou STRAIGHT. */
  distanceSource?: string | null;
  notes?: string | null;
  waypoints: Waypoint[];
}

/**
 * Rotas previstas.
 *
 * <p>Uma rota é o termo de comparação que falta a quase todas as frotas. Sem um
 * previsto credível, "esta viagem gastou muito" é uma opinião; com ele passa a
 * ser uma diferença que se põe em cima da mesa e se explica.
 */
export function RoutesPage() {
  const [nova, setNova] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['routes'],
    queryFn: () => api<Route[]>('/routes'),
  });

  const [procura, setProcura] = useState('');
  const todasRows = data ?? [];
  // Procura que ignora acentos e aceita as palavras por qualquer ordem.
  const rows = useMemo(
    () => filtrar(todasRows, procura, (r) => [r.name, r.code, r.originName, r.destinationName]),
    [todasRows, procura],
  );

  return (
    <Stack gap="lg">

      <Painel
        titulo="Rotas e percursos"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNova(true)} destaque>
              Nova rota
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Nome, código, origem, destino…" />
          </>
        }
      >
        <Grelha
          id="rotas"
          linhas={rows}
          chave={(r) => r.id}
          carregando={isLoading}
          vazio="Ainda não há rotas definidas."
          colunas={[
            {
              id: 'nome',
              titulo: 'Rota',
              fixa: true,
              valor: (r) => r.name,
              render: (r) => (
                <>
                  <Group gap={6} wrap="nowrap">
                    <Text size="sm" fw={600}>
                      {r.name}
                    </Text>
                    {!r.active && (
                      <Badge variant="light" color="gray" size="sm">
                        inativa
                      </Badge>
                    )}
                  </Group>
                  {r.code && (
                    <Text size="xs" c="dimmed">
                      {r.code}
                    </Text>
                  )}
                </>
              ),
            },
            {
              id: 'percurso',
              titulo: 'Percurso',
              valor: (r) => `${r.originName} → ${r.destinationName}`,
              render: (r) => (
                <>
                  <Group gap={6} wrap="nowrap">
                    <Text size="sm">{r.originName}</Text>
                    <IconArrowRight size={13} style={{ color: '#a1a1aa', flexShrink: 0 }} />
                    <Text size="sm">{r.destinationName}</Text>
                  </Group>
                  {r.waypoints.length > 0 && (
                    <Text size="xs" c="dimmed">
                      via {r.waypoints.map((w) => w.label).join(', ')}
                    </Text>
                  )}
                </>
              ),
            },
            {
              id: 'distancia',
              titulo: 'Distância',
              largura: 130,
              alinhar: 'right',
              valor: (r) => r.expectedDistanceKm ?? null,
              render: (r) => (
                <>
                  <Text size="sm">
                    {r.expectedDistanceKm != null ? `${fmtNumber(r.expectedDistanceKm, 0)} km` : '—'}
                  </Text>
                  {r.expectedDistanceKm != null && (
                    <Text size="xs" c={r.distanceSource === 'ENGINE' ? 'teal' : 'dimmed'}>
                      {r.distanceSource === 'ENGINE'
                        ? 'pelas estradas'
                        : r.distanceSource === 'STRAIGHT'
                          ? 'estimativa'
                          : 'escrita à mão'}
                    </Text>
                  )}
                </>
              ),
            },
            {
              id: 'duracao',
              titulo: 'Duração',
              largura: 110,
              alinhar: 'right',
              valor: (r) => r.expectedDurationMinutes ?? null,
              render: (r) =>
                r.expectedDurationMinutes != null ? formatDuration(r.expectedDurationMinutes) : '—',
            },
            {
              id: 'combustivel',
              titulo: 'Combustível',
              largura: 120,
              alinhar: 'right',
              valor: (r) => r.expectedFuelLiters ?? null,
              render: (r) =>
                r.expectedFuelLiters != null ? (
                  `${fmtNumber(r.expectedFuelLiters, 0)} L`
                ) : (
                  <Text size="xs" c="dimmed">
                    por apurar
                  </Text>
                ),
            },
            {
              id: 'tolerancia',
              titulo: 'Tolerância',
              largura: 100,
              alinhar: 'right',
              valor: (r) => r.tolerancePercent,
              render: (r) => `±${fmtNumber(r.tolerancePercent, 0)}%`,
            },
            {
              id: 'notas',
              titulo: 'Notas',
              escondida: true,
              valor: (r) => r.notes ?? null,
            },
          ]}
        />
            </Painel>

      <NovaRotaForm opened={nova} onClose={() => setNova(false)} />
    </Stack>
  );
}

function formatDuration(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}
