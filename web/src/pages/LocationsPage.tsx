import { Badge, Button, Group, Modal, NumberInput, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconBuildingStore, IconMapPin, IconPlus } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura, filtrar } from '../components/Procura';
import { Kpi } from '../components/Kpi';
import { MapaSeletor } from '../components/MapaSeletor';

interface Location {
  id: string;
  name: string;
  code?: string | null;
  kind: string;
  parentId?: string | null;
  parentName?: string | null;
  active: boolean;
  latitude?: number | null;
  longitude?: number | null;
  costCenter?: string | null;
  city?: string | null;
  province?: string | null;
  phone?: string | null;
  radiusMeters?: number | null;
  assetCount: number;
}

const KIND: Record<string, { label: string; color: string }> = {
  BRANCH: { label: 'Filial', color: 'gold' },
  SITE: { label: 'Obra', color: 'blue' },
  YARD: { label: 'Parque', color: 'cyan' },
  WAREHOUSE: { label: 'Armazém', color: 'grape' },
  DEPARTMENT: { label: 'Departamento', color: 'gray' },
  OTHER: { label: 'Outro', color: 'gray' },
};

/**
 * Filiais e locais.
 *
 * <p>Uma filial <b>é</b> um local, com <code>kind = BRANCH</code> e centro de
 * custo. Não tem hierarquia à parte: uma segunda árvore obrigaria a decidir, em
 * cada conta de custos, qual delas manda.
 *
 * <p>As coordenadas e o raio não são enfeite — é com eles que o sistema
 * reconhece que uma viatura estava na filial, e que dá nome aos extremos de uma
 * viagem sem depender de um serviço de mapas pago.
 */
export function LocationsPage() {
  const [novo, setNovo] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['locations'],
    queryFn: () => api<Location[]>('/locations'),
  });

  const [procura, setProcura] = useState('');
  const todasRows = data ?? [];
  // Procura que ignora acentos e aceita as palavras por qualquer ordem.
  const rows = useMemo(
    () => filtrar(todasRows, procura, (l) => [l.name, l.code, l.city, l.province, l.costCenter, l.parentName, l.kind]),
    [todasRows, procura],
  );
  const filiais = rows.filter((l) => l.kind === 'BRANCH');
  const comCoordenadas = rows.filter((l) => l.latitude != null).length;

  return (
    <Stack gap="lg">

      <Group gap="sm" wrap="wrap">
        <Kpi label="Filiais" value={filiais.length} tone="brand" />
        <Kpi label="Locais no total" value={rows.length} />
        <Kpi
          label="Com coordenadas"
          value={comCoordenadas}
          tone={comCoordenadas < rows.length ? 'warning' : 'good'}
          hint="Sem coordenadas, o sistema não consegue dar nome aos extremos de uma viagem nem confirmar que a viatura estava aqui quando abasteceu."
          footnote={
            comCoordenadas < rows.length
              ? `${rows.length - comCoordenadas} local(is) sem posição no mapa`
              : undefined
          }
        />
      </Group>

      <Painel
        titulo="Filiais e locais"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
              Novo local
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Nome, cidade, centro de custo…" />
          </>
        }
      >
        <Grelha
          id="locais"
          linhas={rows}
          chave={(l) => l.id}
          carregando={isLoading}
          vazio="Ainda não há locais registados."
          colunas={[
            {
              id: 'nome',
              titulo: 'Local',
              fixa: true,
              valor: (l) => l.name,
              render: (l) => (
                <Group gap={6} wrap="nowrap">
                  {l.kind === 'BRANCH' && (
                    <IconBuildingStore size={15} style={{ color: '#B08D3C', flexShrink: 0 }} />
                  )}
                  <div>
                    <Text size="sm" fw={600}>
                      {l.name}
                    </Text>
                    {l.parentName && (
                      <Text size="xs" c="dimmed">
                        dentro de {l.parentName}
                      </Text>
                    )}
                  </div>
                </Group>
              ),
            },
            {
              id: 'tipo',
              titulo: 'Tipo',
              largura: 120,
              valor: (l) => KIND[l.kind]?.label ?? l.kind,
              render: (l) => (
                <Badge variant="light" color={KIND[l.kind]?.color ?? 'gray'} size="sm">
                  {KIND[l.kind]?.label ?? l.kind}
                </Badge>
              ),
            },
            {
              id: 'centro',
              titulo: 'Centro de custo',
              largura: 140,
              valor: (l) => l.costCenter ?? null,
            },
            {
              id: 'onde',
              titulo: 'Onde',
              valor: (l) => [l.city, l.province].filter(Boolean).join(', ') || null,
            },
            {
              id: 'mapa',
              titulo: 'No mapa',
              largura: 110,
              valor: (l) => (l.latitude != null ? 1 : 0),
              render: (l) =>
                l.latitude != null ? (
                  <Text size="xs" c="dimmed">
                    raio {l.radiusMeters ?? 200} m
                  </Text>
                ) : (
                  <Text size="xs" c="orange">
                    sem posição
                  </Text>
                ),
            },
            {
              id: 'ativos',
              titulo: 'Ativos',
              largura: 90,
              alinhar: 'right',
              valor: (l) => l.assetCount,
            },
            {
              id: 'coordenadas',
              titulo: 'Coordenadas',
              escondida: true,
              valor: (l) =>
                l.latitude != null ? `${Number(l.latitude).toFixed(5)}, ${Number(l.longitude).toFixed(5)}` : null,
            },
          ]}
        />
            </Painel>

      <NewLocationModal opened={novo} onClose={() => setNovo(false)} parents={todasRows} />
    </Stack>
  );
}

function NewLocationModal({
  opened,
  onClose,
  parents,
}: {
  opened: boolean;
  onClose: () => void;
  parents: Location[];
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [kind, setKind] = useState<string | null>('BRANCH');
  const [parentId, setParentId] = useState<string | null>(null);
  const [costCenter, setCostCenter] = useState('');
  const [city, setCity] = useState('');
  const [province, setProvince] = useState('');
  const [latitude, setLatitude] = useState<number | string>('');
  const [longitude, setLongitude] = useState<number | string>('');
  const [radius, setRadius] = useState<number | string>(300);
  const [mapaAberto, setMapaAberto] = useState(false);

  const eFilial = kind === 'BRANCH';

  const create = useMutation({
    mutationFn: () =>
      api('/locations', {
        method: 'POST',
        body: {
          name,
          kind,
          parentId: parentId || null,
          costCenter: costCenter || null,
          city: city || null,
          province: province || null,
          latitude: latitude === '' ? null : Number(latitude),
          longitude: longitude === '' ? null : Number(longitude),
          radiusMeters: radius === '' ? null : Number(radius),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Local criado', message: name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['locations'] });
      setName('');
      setCostCenter('');
      setCity('');
      setLatitude('');
      setLongitude('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível criar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Criar local" centered>
      <Stack gap="sm">
        <TextInput
          label="Nome"
          required
          placeholder="Filial de Benguela"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Select
          label="Tipo"
          data={Object.entries(KIND).map(([value, k]) => ({ value, label: k.label }))}
          value={kind}
          onChange={setKind}
          allowDeselect={false}
        />
        <Select
          label="Dentro de"
          placeholder="nenhum — é um local de topo"
          clearable
          data={parents.map((p) => ({ value: p.id, label: p.name }))}
          value={parentId}
          onChange={setParentId}
        />

        {eFilial && (
          <>
            <TextInput
              label="Centro de custo"
              description="É por aqui que a contabilidade da empresa reconcilia os custos da frota."
              placeholder="CC-BG-01"
              value={costCenter}
              onChange={(e) => setCostCenter(e.currentTarget.value)}
            />
            <Group grow>
              <TextInput
                label="Cidade"
                value={city}
                onChange={(e) => setCity(e.currentTarget.value)}
              />
              <TextInput
                label="Província"
                value={province}
                onChange={(e) => setProvince(e.currentTarget.value)}
              />
            </Group>
          </>
        )}

        <Group grow align="flex-end">
          <NumberInput
            label="Latitude"
            description="Marcada no mapa"
            value={latitude}
            readOnly
            decimalScale={6}
          />
          <NumberInput
            label="Longitude"
            description="Marcada no mapa"
            value={longitude}
            readOnly
            decimalScale={6}
          />
          <Button
            variant="default"
            leftSection={<IconMapPin size={16} />}
            onClick={() => setMapaAberto(true)}
          >
            {latitude === '' ? 'Marcar no mapa' : 'Mudar o ponto'}
          </Button>
        </Group>

        <MapaSeletor
          aberto={mapaAberto}
          aoFechar={() => setMapaAberto(false)}
          titulo="Onde fica este local"
          inicial={
            latitude === '' || longitude === ''
              ? null
              : { latitude: Number(latitude), longitude: Number(longitude) }
          }
          referencias={parents
            .filter((p) => p.latitude != null && p.longitude != null)
            .map((p) => ({
              id: p.id,
              nome: p.name,
              latitude: Number(p.latitude),
              longitude: Number(p.longitude),
            }))}
          aoEscolher={(ponto) => {
            setLatitude(ponto.latitude);
            setLongitude(ponto.longitude);
          }}
        />
        <NumberInput
          label="Raio (metros)"
          description="Distância até à qual uma viatura conta como estando aqui. Sem coordenadas, o sistema não consegue dar nome às viagens nem confirmar abastecimentos."
          value={radius}
          onChange={setRadius}
          min={0}
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!name.trim()} loading={create.isPending} onClick={() => create.mutate()}>
            Criar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
