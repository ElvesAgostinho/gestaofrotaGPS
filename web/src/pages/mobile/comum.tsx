import { Alert, Button, Select, Stack, Text, Title } from '@mantine/core';
import { IconCircleCheck } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';

export interface AssetPick {
  id: string;
  tag: string;
  name: string;
  plate?: string | null;
  status: string;
  mine: boolean;
}

/** A viatura do motorista, com o que interessa hoje. */
export interface MinhaViatura {
  id: string;
  tag: string;
  name: string;
  plate?: string | null;
  status: string;
  meterLabel?: string | null;
  meterUnit?: string | null;
  meterValue?: number | null;
  nextMaintenance?: string | null;
  nextMaintenanceIn?: string | null;
  nextMaintenanceStatus?: string | null;
  inspectionDoneToday: boolean;
  lastInspectionAt?: string | null;
  fuelLevelLiters?: number | null;
}

/** A rota que o gestor marcou para hoje. */
export interface RotaHoje {
  assignmentId: string;
  routeId: string;
  name: string;
  code?: string | null;
  assetId?: string | null;
  assetTag?: string | null;
  distanceKm?: number | null;
  expectedMinutes?: number | null;
  hasPath: boolean;
}

export interface Home {
  userName: string;
  assets: AssetPick[];
  myOpenOrders: number;
  hasAssignedAssets: boolean;
  myAssets?: MinhaViatura[];
  route?: RotaHoje | null;
  warnings?: string[];
}

export function useHome() {
  return useQuery({ queryKey: ['mobile', 'home'], queryFn: () => api<Home>('/mobile/home') });
}

/** Escolher a viatura: as atribuídas a quem está a ver vêm primeiro e pré-escolhidas. */
export function EscolherViatura({
  assets,
  value,
  onChange,
}: {
  assets: AssetPick[];
  value: string | null;
  onChange: (id: string | null) => void;
}) {
  const dados = assets.map((a) => ({
    value: a.id,
    label: `${a.tag}${a.plate ? ' · ' + a.plate : ''} — ${a.name}${a.mine ? '  (a minha)' : ''}`,
  }));
  return (
    <Select
      label="Viatura"
      placeholder="Escolher a viatura…"
      data={dados}
      value={value}
      onChange={onChange}
      searchable
      nothingFoundMessage="Nenhuma viatura com esse nome"
      size="md"
      required
    />
  );
}

/** A viatura pré-escolhida: a única atribuída a mim, ou nenhuma. */
export function viaturaInicial(assets: AssetPick[] | undefined): string | null {
  if (!assets) return null;
  const minhas = assets.filter((a) => a.mine);
  if (minhas.length === 1) return minhas[0].id;
  if (assets.length === 1) return assets[0].id;
  return null;
}

/** O ecrã de «feito»: uma frase, o que aconteceu, e para onde ir a seguir. */
export function Feito({ titulo, texto, children }: { titulo: string; texto?: string; children?: React.ReactNode }) {
  return (
    <Stack align="center" gap="md" pt="xl">
      <IconCircleCheck size={64} style={{ color: 'var(--mantine-color-green-6)' }} />
      <Title order={2} ta="center">
        {titulo}
      </Title>
      {texto && (
        <Text ta="center" c="dimmed">
          {texto}
        </Text>
      )}
      {children}
      <Button component={Link} to="/m" variant="default" size="md" mt="md">
        Voltar ao início
      </Button>
    </Stack>
  );
}

export function Erro({ mensagem }: { mensagem: string | null }) {
  if (!mensagem) return null;
  return (
    <Alert color="red" variant="light">
      {mensagem}
    </Alert>
  );
}

export function mensagemDe(e: unknown): string {
  return e instanceof Error ? e.message : 'Não foi possível concluir. Tente de novo.';
}
