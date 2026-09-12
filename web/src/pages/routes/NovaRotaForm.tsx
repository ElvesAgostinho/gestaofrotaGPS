import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconMapPin,
  IconRoute,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { MapaSeletor, type Ponto } from '../../components/MapaSeletor';

interface Local {
  id: string;
  name: string;
  city?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

/** Um extremo ou uma passagem do percurso. */
interface Paragem {
  /** Local registado, quando foi escolhido da lista. */
  locationId: string | null;
  /** Ponto marcado no mapa, quando o sítio não está registado. */
  ponto: Ponto | null;
  label: string;
}

const VAZIA: Paragem = { locationId: null, ponto: null, label: '' };

/** Valor especial da lista: o sítio não está registado, marca-se no mapa. */
const NO_MAPA = '__mapa__';

interface Calculo {
  distanceKm: number | null;
  durationMinutes: number | null;
  fuelLiters: number | null;
  geojson: string | null;
  source: 'ENGINE' | 'STRAIGHT';
  warning: string | null;
}

/**
 * Criar uma rota sem escrever coordenadas nem distâncias.
 *
 * <p>Antes, a origem e o destino eram texto livre e a distância era um número
 * que alguém digitava. «Luanda → Lobito, 40 km» entrava sem uma queixa, e a
 * partir daí todos os desvios de consumo medidos contra essa rota eram lixo.
 *
 * <p>Agora os extremos são locais que a empresa já registou — que já têm ponto
 * no mapa — ou um ponto apontado no mapa. A distância e a duração vêm do motor
 * de rotas. Continuam a poder ser corrigidas à mão: quem conhece a estrada sabe
 * coisas que o mapa não sabe, e a correção fica assinalada como sendo de uma
 * pessoa.
 */
export function NovaRotaForm({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();

  const { data: locais } = useQuery({
    queryKey: ['locations'],
    queryFn: () => api<Local[]>('/locations'),
    enabled: opened,
  });

  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [origem, setOrigem] = useState<Paragem>(VAZIA);
  const [destino, setDestino] = useState<Paragem>(VAZIA);
  const [passagens, setPassagens] = useState<Paragem[]>([]);
  const [consumo, setConsumo] = useState<number | string>('');

  const [distance, setDistance] = useState<number | string>('');
  const [duration, setDuration] = useState<number | string>('');
  const [fuel, setFuel] = useState<number | string>('');
  const [calculo, setCalculo] = useState<Calculo | null>(null);
  /** Verdadeiro assim que alguém mexe nos números depois do cálculo. */
  const [corrigido, setCorrigido] = useState(false);

  /** Qual paragem está a ser marcada no mapa: 'origem', 'destino' ou o índice. */
  const [aMarcar, setAMarcar] = useState<string | null>(null);

  const comPonto = (locais ?? []).filter((l) => l.latitude != null && l.longitude != null);

  const opcoes = [
    ...comPonto.map((l) => ({
      value: l.id,
      label: l.city ? `${l.name} — ${l.city}` : l.name,
    })),
    { value: NO_MAPA, label: '➜ Outro ponto — marcar no mapa' },
  ];

  const todas = [origem, ...passagens, destino];
  const prontoParaCalcular = todas.every((p) => p.locationId != null || p.ponto != null);

  function pontoDe(p: Paragem) {
    return p.locationId
      ? { locationId: p.locationId }
      : { latitude: p.ponto!.latitude, longitude: p.ponto!.longitude };
  }

  const calcular = useMutation({
    mutationFn: () =>
      api<Calculo>('/routes/calculate', {
        method: 'POST',
        body: {
          points: todas.map(pontoDe),
          litersPer100Km: consumo === '' ? null : Number(consumo),
        },
      }),
    onSuccess: (r) => {
      setCalculo(r);
      setCorrigido(false);
      if (r.distanceKm != null) setDistance(r.distanceKm);
      if (r.durationMinutes != null) setDuration(r.durationMinutes);
      if (r.fuelLiters != null) setFuel(r.fuelLiters);
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível calcular', message: e.message, color: 'red' }),
  });

  const create = useMutation({
    mutationFn: () =>
      api('/routes', {
        method: 'POST',
        body: {
          name,
          code: code || null,
          originLocationId: origem.locationId,
          destinationLocationId: destino.locationId,
          originLabel: origem.locationId ? null : nomeDe(origem, 'Origem'),
          destinationLabel: destino.locationId ? null : nomeDe(destino, 'Destino'),
          expectedDistanceKm: distance === '' ? null : Number(distance),
          expectedDurationMinutes: duration === '' ? null : Number(duration),
          expectedFuelLiters: fuel === '' ? null : Number(fuel),
          // Um número corrigido à mão deixa de ser uma medição, e diz-se.
          distanceSource: !calculo || corrigido ? 'MANUAL' : calculo.source,
          pathGeojson: !calculo || corrigido ? null : calculo.geojson,
          waypoints: passagens.map((p, i) => ({
            label: nomeDe(p, `Passagem ${i + 1}`),
            locationId: p.locationId,
            latitude: p.ponto?.latitude ?? null,
            longitude: p.ponto?.longitude ?? null,
          })),
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Rota criada', message: name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['routes'] });
      limpar();
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível criar', message: e.message, color: 'red' }),
  });

  function nomeDe(p: Paragem, omissao: string) {
    if (p.label.trim()) return p.label.trim();
    if (p.locationId) {
      return comPonto.find((l) => l.id === p.locationId)?.name ?? omissao;
    }
    return p.ponto
      ? `${p.ponto.latitude.toFixed(4)}, ${p.ponto.longitude.toFixed(4)}`
      : omissao;
  }

  function limpar() {
    setName('');
    setCode('');
    setOrigem(VAZIA);
    setDestino(VAZIA);
    setPassagens([]);
    setConsumo('');
    setDistance('');
    setDuration('');
    setFuel('');
    setCalculo(null);
    setCorrigido(false);
  }

  /** Aplica uma alteração à paragem identificada por chave. */
  function mudar(chave: string, novo: Paragem) {
    if (chave === 'origem') setOrigem(novo);
    else if (chave === 'destino') setDestino(novo);
    else {
      const i = Number(chave);
      setPassagens((ps) => ps.map((p, j) => (j === i ? novo : p)));
    }
    // Mexer nos pontos invalida o cálculo anterior: deixá-lo no ecrã seria
    // mostrar a distância de um percurso que já não é este.
    setCalculo(null);
  }

  function paragemDe(chave: string): Paragem {
    if (chave === 'origem') return origem;
    if (chave === 'destino') return destino;
    return passagens[Number(chave)] ?? VAZIA;
  }

  const semLocais = comPonto.length === 0;

  return (
    <>
      <Modal opened={opened} onClose={onClose} title="Criar rota" size="lg" centered>
        <Stack gap="sm">
          <TextInput
            label="Nome"
            required
            placeholder="Luanda — Lobito"
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
          />
          <TextInput
            label="Código"
            placeholder="LAD-LOB"
            value={code}
            onChange={(e) => setCode(e.currentTarget.value)}
          />

          {semLocais && (
            <Alert color="yellow" icon={<IconAlertTriangle size={16} />}>
              Nenhum local seu tem ponto no mapa ainda. Marque as filiais em «Filiais e
              centros de custo» — depois é só escolhê-las aqui, e a distância sai sozinha.
            </Alert>
          )}

          <CampoParagem
            rotulo="Origem"
            paragem={origem}
            opcoes={opcoes}
            aoMudar={(novo) => mudar('origem', novo)}
            aoMarcar={() => setAMarcar('origem')}
          />

          {passagens.map((_, i) => (
            <Group key={i} gap="xs" align="flex-end" wrap="nowrap">
              <div style={{ flex: 1 }}>
                <CampoParagem
                  rotulo={`Passagem ${i + 1}`}
                  paragem={passagens[i]}
                  opcoes={opcoes}
                  aoMudar={(novo) => mudar(String(i), novo)}
                  aoMarcar={() => setAMarcar(String(i))}
                />
              </div>
              <Button
                variant="subtle"
                color="red"
                onClick={() => {
                  setPassagens((ps) => ps.filter((_, j) => j !== i));
                  setCalculo(null);
                }}
              >
                <IconTrash size={16} />
              </Button>
            </Group>
          ))}

          <CampoParagem
            rotulo="Destino"
            paragem={destino}
            opcoes={opcoes}
            aoMudar={(novo) => mudar('destino', novo)}
            aoMarcar={() => setAMarcar('destino')}
          />

          <Group justify="space-between">
            <Button
              variant="subtle"
              size="compact-sm"
              onClick={() => setPassagens((ps) => [...ps, { ...VAZIA }])}
            >
              + Ponto de passagem
            </Button>
            <NumberInput
              label="Consumo da viatura (L/100 km)"
              description="Para prever o combustível"
              value={consumo}
              onChange={setConsumo}
              min={0}
              decimalScale={1}
              w={230}
            />
          </Group>

          <Group>
            <Button
              variant="default"
              leftSection={<IconRoute size={16} />}
              disabled={!prontoParaCalcular}
              loading={calcular.isPending}
              onClick={() => calcular.mutate()}
            >
              Calcular percurso
            </Button>
            {calculo && (
              <Badge color={calculo.source === 'ENGINE' ? 'green' : 'yellow'} variant="light">
                {calculo.source === 'ENGINE' ? 'Calculado pelas estradas' : 'Estimativa em linha reta'}
              </Badge>
            )}
            {corrigido && (
              <Badge color="gray" variant="light">
                corrigido à mão
              </Badge>
            )}
          </Group>

          {calculo?.warning && (
            <Alert color="yellow" icon={<IconAlertTriangle size={16} />}>
              {calculo.warning}
            </Alert>
          )}

          <Group grow>
            <NumberInput
              label="Distância (km)"
              value={distance}
              onChange={(v) => {
                setDistance(v);
                if (calculo) setCorrigido(true);
              }}
              min={0}
              decimalScale={1}
            />
            <NumberInput
              label="Duração (min)"
              value={duration}
              onChange={(v) => {
                setDuration(v);
                if (calculo) setCorrigido(true);
              }}
              min={0}
            />
          </Group>
          <NumberInput
            label="Combustível previsto (L)"
            description="Sai do consumo da viatura acima; deixe vazio se ainda não sabe."
            value={fuel}
            onChange={setFuel}
            min={0}
            decimalScale={1}
          />

          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancelar
            </Button>
            <Button
              disabled={!name.trim() || !prontoParaCalcular}
              loading={create.isPending}
              onClick={() => create.mutate()}
            >
              Criar
            </Button>
          </Group>

          {!prontoParaCalcular && (
            <Text size="xs" c="dimmed">
              Escolha a origem e o destino para poder criar a rota.
            </Text>
          )}
        </Stack>
      </Modal>

      <MapaSeletor
        aberto={aMarcar != null}
        aoFechar={() => setAMarcar(null)}
        titulo="Marcar o ponto"
        inicial={aMarcar ? paragemDe(aMarcar).ponto : null}
        referencias={comPonto.map((l) => ({
          id: l.id,
          nome: l.name,
          latitude: Number(l.latitude),
          longitude: Number(l.longitude),
        }))}
        aoEscolher={(ponto) => {
          if (aMarcar) mudar(aMarcar, { ...paragemDe(aMarcar), locationId: null, ponto });
        }}
      />
    </>
  );
}

/**
 * Um extremo ou uma passagem do percurso.
 *
 * <p>Vive fora do formulário de propósito. Declarado lá dentro, seria uma
 * função diferente a cada render, o React desmontaria o campo e remontaria
 * outro, e a procura perderia o foco a cada tecla.
 */
function CampoParagem({
  rotulo,
  paragem,
  opcoes,
  aoMudar,
  aoMarcar,
}: {
  rotulo: string;
  paragem: Paragem;
  opcoes: { value: string; label: string }[];
  aoMudar: (novo: Paragem) => void;
  aoMarcar: () => void;
}) {
  return (
    <Group gap="xs" align="flex-end" wrap="nowrap">
      <Select
        label={rotulo}
        placeholder="Escolha um local registado"
        data={opcoes}
        searchable
        value={paragem.locationId ?? (paragem.ponto ? NO_MAPA : null)}
        onChange={(v) => {
          if (v === NO_MAPA) {
            aoMudar({ ...paragem, locationId: null });
            aoMarcar();
          } else {
            aoMudar({ locationId: v, ponto: null, label: '' });
          }
        }}
        style={{ flex: 1 }}
      />
      {paragem.ponto && (
        <Button variant="default" leftSection={<IconMapPin size={15} />} onClick={aoMarcar}>
          {paragem.ponto.latitude.toFixed(4)}, {paragem.ponto.longitude.toFixed(4)}
        </Button>
      )}
    </Group>
  );
}
