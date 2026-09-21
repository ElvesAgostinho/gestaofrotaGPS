import { Loader, SegmentedControl, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCloudUpload,
  IconMapPin,
  IconPlayerPlay,
  IconPlayerStop,
  IconRoute,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { MapaPercurso, type PontoRota } from '../../components/MapaPercurso';
import { BotaoGrande, Cartao, Etiqueta, Linha, Numero, Seccao } from './pecas';
import { rastreio, type EstadoRastreio } from './rastreio';
import { AMBAR, CINZA, LARANJA, TITULO, VERDE, VERMELHO } from './tema';

interface Ponto {
  label?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  tipo?: string | null;
}

interface RotaDetalhe {
  routeId: string;
  name: string;
  code?: string | null;
  originLabel?: string | null;
  destinationLabel?: string | null;
  distanceKm?: number | null;
  expectedMinutes?: number | null;
  corridorMeters?: number | null;
  pathGeojson?: string | null;
  points: Ponto[];
  assetId?: string | null;
  assetTag?: string | null;
  notes?: string | null;
}

/**
 * A rota de hoje no telemóvel do motorista.
 *
 * <p>Duas vistas, porque são duas perguntas diferentes: <b>o percurso do
 * gestor</b> («por onde a empresa quer que eu vá») e <b>o caminho directo</b>
 * («como chego lá a partir de onde estou agora»). Quem conhece a estrada quer
 * a primeira; quem não conhece quer a segunda.
 *
 * <p>Por cima de qualquer delas anda a linha do que ele já fez — a mesma que o
 * gestor vê no escritório, ao mesmo tempo.
 */
export function MRotaPage() {
  const [vista, setVista] = useState<'gestor' | 'directo'>('gestor');
  const [estado, setEstado] = useState<EstadoRastreio | null>(null);

  useEffect(() => rastreio.subscrever(setEstado), []);

  const { data, isLoading } = useQuery({
    queryKey: ['mobile', 'route'],
    queryFn: () => api<RotaDetalhe | null>('/mobile/route'),
  });

  // O que já andámos nesta sessão, para a linha se ver a crescer no mapa.
  const [trilho, setTrilho] = useState<[number, number][]>([]);
  useEffect(() => {
    if (estado?.ultima) {
      setTrilho((t) => {
        const ponto: [number, number] = [estado.ultima!.longitude, estado.ultima!.latitude];
        const ultimo = t[t.length - 1];
        if (ultimo && ultimo[0] === ponto[0] && ultimo[1] === ponto[1]) return t;
        return [...t, ponto];
      });
    }
  }, [estado?.ultima]);

  if (isLoading) return <Loader color="yellow" />;

  if (!data) {
    return (
      <Stack gap="md">
        <Seccao>Rota</Seccao>
        <Cartao>
          <Text size="sm" style={{ color: CINZA }}>
            Não há rota marcada para si hoje. Quando o gestor lhe atribuir uma, aparece aqui com o
            percurso desenhado no mapa.
          </Text>
        </Cartao>
        <ModoViagem estado={estado} assetId={null} />
      </Stack>
    );
  }

  const pontos: PontoRota[] = (data.points ?? [])
    .filter((p) => p.latitude != null && p.longitude != null)
    .map((p) => ({
      latitude: Number(p.latitude),
      longitude: Number(p.longitude),
      label: p.label,
      tipo: (p.tipo as PontoRota['tipo']) ?? 'passagem',
    }));

  return (
    <Stack gap="md">
      <div>
        <Text style={{ fontFamily: TITULO, fontSize: 24, fontWeight: 700, lineHeight: 1.1 }}>
          {data.name}
        </Text>
        <Text size="sm" style={{ color: CINZA }}>
          {data.originLabel ?? '—'} → {data.destinationLabel ?? '—'}
        </Text>
      </div>

      <Cartao padding={0}>
        <div style={{ padding: 10 }}>
          <SegmentedControl
            fullWidth
            size="sm"
            value={vista}
            onChange={(v) => setVista(v as 'gestor' | 'directo')}
            data={[
              { value: 'gestor', label: 'Percurso do gestor' },
              { value: 'directo', label: 'Caminho directo' },
            ]}
          />
        </div>
        <MapaPercurso
          pathGeojson={vista === 'gestor' ? data.pathGeojson : null}
          pontos={pontos}
          reais={trilho.length > 1 ? [{ coords: trilho, label: 'o que já andei' }] : []}
          altura={320}
          fundo="ruas"
          viatura={
            estado?.ultima
              ? {
                  latitude: estado.ultima.latitude,
                  longitude: estado.ultima.longitude,
                  heading: estado.ultima.heading ?? null,
                  speedKph: estado.ultima.speedKph ?? null,
                  moving: (estado.ultima.speedKph ?? 0) > 3,
                  tag: data.assetTag ?? 'a minha viatura',
                }
              : null
          }
          seguir={estado?.aSeguir ?? false}
        />
        {vista === 'directo' && (
          <div style={{ padding: '8px 12px' }}>
            <Text size="xs" style={{ color: CINZA }}>
              A vista directa mostra só os pontos de origem e destino: siga pela estrada que
              conhecer. O percurso do gestor é o que a empresa espera — sair dele dá aviso.
            </Text>
          </div>
        )}
      </Cartao>

      <Cartao>
        <Linha rotulo="Viatura" valor={data.assetTag ?? '—'} />
        <Linha
          rotulo="Distância prevista"
          valor={data.distanceKm ? `${Math.round(Number(data.distanceKm))} km` : '—'}
        />
        <Linha
          rotulo="Duração prevista"
          valor={
            data.expectedMinutes
              ? `${Math.floor(data.expectedMinutes / 60)} h ${data.expectedMinutes % 60} min`
              : '—'
          }
        />
        <Linha
          rotulo="Corredor"
          valor={data.corridorMeters ? `${data.corridorMeters} m` : '500 m'}
        />
        {data.notes && (
          <Text size="xs" style={{ color: CINZA }} mt={8}>
            {data.notes}
          </Text>
        )}
      </Cartao>

      {pontos.length > 2 && (
        <div>
          <Seccao>Paragens</Seccao>
          <Cartao>
            {pontos.map((p, i) => (
              <Linha
                key={`${p.label}-${i}`}
                rotulo={`${i + 1}. ${p.label ?? 'ponto'}`}
                valor={
                  <IconMapPin
                    size={15}
                    style={{ color: p.tipo === 'destino' ? VERDE : p.tipo === 'origem' ? AMBAR : CINZA }}
                  />
                }
              />
            ))}
          </Cartao>
        </div>
      )}

      <ModoViagem estado={estado} assetId={data.assetId ?? null} />
    </Stack>
  );
}

/**
 * O modo viagem: o que liga o telemóvel ao mapa do gestor.
 *
 * <p>Diz sem rodeios o que é preciso para funcionar — ecrã ligado, telemóvel
 * no suporte — porque a alternativa é o gestor achar que a viatura parou
 * quando o que parou foi o browser.
 */
function ModoViagem({ estado, assetId }: { estado: EstadoRastreio | null; assetId: string | null }) {
  const aSeguir = estado?.aSeguir ?? false;

  return (
    <div>
      <Seccao direita={estado && estado.porEnviar > 0 ? (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: LARANJA, fontSize: 12 }}>
          <IconCloudUpload size={14} />
          {estado.porEnviar} por enviar
        </span>
      ) : undefined}
      >
        Modo viagem
      </Seccao>
      <Cartao destaque={aSeguir}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <IconRoute size={24} style={{ color: aSeguir ? VERDE : CINZA }} />
            <div>
              <Text fw={700} size="sm">
                {aSeguir ? 'A enviar a sua posição' : 'Parado'}
              </Text>
              <Text size="xs" style={{ color: CINZA }}>
                {aSeguir
                  ? 'O gestor vê onde vai e a hora prevista de chegada.'
                  : 'Ligue ao sair e desligue quando chegar.'}
              </Text>
            </div>
          </div>
          {aSeguir && <Etiqueta cor={VERDE}>ao vivo</Etiqueta>}
        </div>

        {estado?.ultima && (
          <div style={{ display: 'flex', gap: 22, marginBottom: 12 }}>
            <Numero
              valor={`${(estado.ultima.speedKph ?? 0).toFixed(0)}`}
              legenda="km/h"
            />
            <Numero
              valor={estado.ultima.accuracyM ? `±${Math.round(estado.ultima.accuracyM)} m` : '—'}
              legenda="precisão"
            />
          </div>
        )}

        {estado?.erro && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 10 }}>
            <IconAlertTriangle size={16} style={{ color: VERMELHO, flexShrink: 0, marginTop: 2 }} />
            <Text size="sm" style={{ color: VERMELHO }}>
              {estado.erro}
            </Text>
          </div>
        )}

        {aSeguir ? (
          <BotaoGrande
            onClick={() => {
              void rastreio.parar();
              notifications.show({ message: 'Viagem terminada. O que faltava enviar foi enviado.', color: 'green' });
            }}
            icone={<IconPlayerStop size={20} />}
            cor={VERMELHO}
          >
            Terminar viagem
          </BotaoGrande>
        ) : (
          <BotaoGrande onClick={() => void rastreio.iniciar(assetId)} icone={<IconPlayerPlay size={20} />}>
            Iniciar viagem
          </BotaoGrande>
        )}

        <Text size="xs" style={{ color: CINZA }} mt={10}>
          Deixe o telemóvel no suporte e ligado ao carregador, com o ecrã aceso: a aplicação mantém o
          ecrã ligado durante a viagem, mas se o telemóvel se desligar a localização pára. Sem rede, o
          percurso fica guardado e sobe assim que houver ligação.
        </Text>
      </Cartao>
    </div>
  );
}
