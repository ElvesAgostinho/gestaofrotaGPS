import { Badge, Button, Group, Loader, Modal, SegmentedControl, Stack, Text, TextInput } from '@mantine/core';
import { IconMapPin, IconSearch } from '@tabler/icons-react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { BASEMAPS, CENTRO_OMISSAO, styleFor, type Basemap } from '../pages/map/basemaps';

export interface Ponto {
  latitude: number;
  longitude: number;
}

/** Um sítio já registado, desenhado no mapa para dar referência. */
export interface PontoReferencia extends Ponto {
  id: string;
  nome: string;
}

interface Sugestao {
  name: string;
  detail: string;
  latitude: number;
  longitude: number;
  source: 'EMPRESA' | 'MAPA';
  locationId?: string | null;
}

interface Props {
  aberto: boolean;
  aoFechar: () => void;
  /** Ponto já escolhido, se houver — o mapa abre em cima dele. */
  inicial?: Ponto | null;
  referencias?: PontoReferencia[];
  titulo?: string;
  aoEscolher: (ponto: Ponto) => void;
}

/**
 * Escolher um ponto apontando para ele.
 *
 * <p>Existe para tirar a latitude e a longitude das mãos de quem as escrevia.
 * Um sinal trocado num campo de texto — «8,83» em vez de «-8,83» — põe a filial
 * de Benguela no hemisfério norte, e o erro só aparece semanas depois, quando
 * um relatório de distâncias sai absurdo e ninguém percebe porquê.
 *
 * <p>Os números continuam à vista, em baixo. Não são para escrever: são para
 * conferir contra o que o mapa mostra, e para poderem ser ditos ao telefone.
 */
export function MapaSeletor({
  aberto,
  aoFechar,
  inicial,
  referencias = [],
  titulo = 'Marcar no mapa',
  aoEscolher,
}: Props) {
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const pino = useRef<maplibregl.Marker | null>(null);
  const [fundo, setFundo] = useState<Basemap>('satelite');
  const [ponto, setPonto] = useState<Ponto | null>(inicial ?? null);

  // ---- procurar pelo nome ---------------------------------------------------
  // Escreve-se «Lobito» ou «Rua Rainha Ginga, Luanda»; os locais da empresa
  // vêm primeiro, o OpenStreetMap a seguir. O pedido passa pelo servidor, que
  // se identifica e respeita o ritmo do serviço público.
  const [termo, setTermo] = useState('');
  const [sugestoes, setSugestoes] = useState<Sugestao[]>([]);
  const [aProcurar, setAProcurar] = useState(false);
  const [semMapa, setSemMapa] = useState(false);
  // Depois de escolher, o nome fica no campo; não se volta a procurar por ele.
  const escolhaFeita = useRef(false);

  useEffect(() => {
    if (!aberto) return;
    if (escolhaFeita.current) {
      escolhaFeita.current = false;
      return;
    }
    const q = termo.trim();
    if (q.length < 2) {
      setSugestoes([]);
      return;
    }
    let cancelado = false;
    setAProcurar(true);
    // Meio segundo depois da última tecla: não se pede ao servidor a cada letra.
    const t = setTimeout(async () => {
      try {
        const r = await api<{ results: Sugestao[]; externalAvailable: boolean }>(
          `/geo/search?q=${encodeURIComponent(q)}`,
        );
        if (cancelado) return;
        setSugestoes(r.results);
        setSemMapa(!r.externalAvailable);
      } catch {
        if (!cancelado) setSugestoes([]);
      } finally {
        if (!cancelado) setAProcurar(false);
      }
    }, 500);
    return () => {
      cancelado = true;
      clearTimeout(t);
    };
  }, [termo, aberto]);

  function irPara(s: Sugestao) {
    const instance = map.current;
    if (!instance) return;
    instance.flyTo({ center: [s.longitude, s.latitude], zoom: s.source === 'EMPRESA' ? 15 : 14, duration: 900 });
    colocar(instance, s.latitude, s.longitude);
    setSugestoes([]);
    escolhaFeita.current = true;
    setTermo(s.name);
  }

  // O modal só monta o conteúdo quando abre, por isso o mapa nasce aqui e
  // morre ao fechar. Manter um mapa vivo por trás de um modal fechado gasta
  // GPU a desenhar tiles que ninguém vê.
  useEffect(() => {
    if (!aberto || !container || map.current) return;

    const centro: [number, number] = inicial
      ? [inicial.longitude, inicial.latitude]
      : referencias.length > 0
        ? [referencias[0].longitude, referencias[0].latitude]
        : CENTRO_OMISSAO;

    const instance = new maplibregl.Map({
      container,
      style: styleFor(fundo),
      center: centro,
      zoom: inicial ? 15 : 11,
      attributionControl: { compact: true },
    });
    instance.addControl(new maplibregl.NavigationControl(), 'top-right');
    instance.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');

    // Os sítios já registados ficam como pontos pequenos: servem para a pessoa
    // se situar («o novo armazém é ali ao lado da filial»), não para competir
    // com o pino que está a ser colocado.
    instance.on('load', () => {
      for (const r of referencias) {
        const el = document.createElement('div');
        el.style.cssText =
          'width:10px;height:10px;border-radius:50%;background:#2563eb;'
          + 'border:2px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.35);';
        new maplibregl.Marker({ element: el })
          .setLngLat([r.longitude, r.latitude])
          .setPopup(new maplibregl.Popup({ offset: 12 }).setText(r.nome))
          .addTo(instance);
      }
    });

    instance.on('click', (e) => colocar(instance, e.lngLat.lat, e.lngLat.lng));

    if (inicial) colocar(instance, inicial.latitude, inicial.longitude);

    map.current = instance;
    return () => {
      instance.remove();
      map.current = null;
      pino.current = null;
    };
    // Corre uma vez por abertura: o fundo e as referências são lidos no momento
    // em que o mapa nasce, e o fundo troca pelo seu próprio efeito.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [aberto, container]);

  useEffect(() => {
    map.current?.setStyle(styleFor(fundo));
  }, [fundo]);

  function colocar(instance: maplibregl.Map, lat: number, lon: number) {
    const coordenadas: [number, number] = [lon, lat];
    if (!pino.current) {
      const el = document.createElement('div');
      el.style.cssText =
        'width:22px;height:22px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);'
        + 'background:var(--erp-dourado,#f5a800);border:2px solid #141416;'
        + 'box-shadow:0 2px 6px rgba(0,0,0,.45);cursor:grab;';
      pino.current = new maplibregl.Marker({ element: el, draggable: true, offset: [0, -11] })
        .setLngLat(coordenadas)
        .addTo(instance);
      // Arrastar afina o sítio sem ter de acertar no clique à primeira.
      pino.current.on('dragend', () => {
        const p = pino.current!.getLngLat();
        setPonto({ latitude: p.lat, longitude: p.lng });
      });
    } else {
      pino.current.setLngLat(coordenadas);
    }
    setPonto({ latitude: lat, longitude: lon });
  }

  return (
    <Modal opened={aberto} onClose={aoFechar} title={titulo} size="xl" centered>
      <Stack gap="sm">
        <Group justify="space-between">
          <SegmentedControl
            size="xs"
            value={fundo}
            onChange={(v) => setFundo(v as Basemap)}
            data={Object.entries(BASEMAPS).map(([k, b]) => ({ value: k, label: b.label }))}
          />
          <Text size="xs" c="dimmed">
            Clique no mapa para marcar; arraste o pino para afinar.
          </Text>
        </Group>

        <div style={{ position: 'relative' }}>
          <TextInput
            placeholder="Escreva o sítio: «Lobito», «Rua Rainha Ginga, Luanda», ou o nome de uma filial"
            leftSection={aProcurar ? <Loader size={14} /> : <IconSearch size={15} />}
            value={termo}
            onChange={(e) => setTermo(e.currentTarget.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && sugestoes.length > 0) {
                e.preventDefault();
                irPara(sugestoes[0]);
              }
              if (e.key === 'Escape') setSugestoes([]);
            }}
            autoFocus
          />
          {sugestoes.length > 0 && (
            <div
              style={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                zIndex: 5,
                background: '#fff',
                border: '1px solid var(--erp-moldura, #c4c4c9)',
                boxShadow: '0 6px 18px rgba(0,0,0,.18)',
                maxHeight: 260,
                overflowY: 'auto',
              }}
            >
              {sugestoes.map((sug, i) => (
                <div
                  key={`${sug.source}-${sug.locationId ?? i}-${sug.latitude}`}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    irPara(sug);
                  }}
                  style={{
                    padding: '6px 10px',
                    cursor: 'pointer',
                    borderBottom: '1px solid var(--erp-moldura-suave, #e2e2e6)',
                    display: 'flex',
                    gap: 8,
                    alignItems: 'baseline',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--erp-realce, #fff4d6)')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = '#fff')}
                >
                  <Badge size="xs" variant={sug.source === 'EMPRESA' ? 'filled' : 'light'} color={sug.source === 'EMPRESA' ? 'gold' : 'gray'}>
                    {sug.source === 'EMPRESA' ? 'da empresa' : 'mapa'}
                  </Badge>
                  <div style={{ minWidth: 0 }}>
                    <Text size="sm" fw={600} truncate>
                      {sug.name}
                    </Text>
                    <Text size="xs" c="dimmed" truncate>
                      {sug.detail}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
          )}
          {semMapa && termo.trim().length >= 2 && !aProcurar && (
            <Text size="xs" c="orange" mt={2}>
              Sem ligação ao mapa: só os locais da empresa aparecem.
            </Text>
          )}
        </div>

        <div
          ref={setContainer}
          style={{ width: '100%', height: 440, border: '1px solid var(--erp-moldura, #c4c4c9)' }}
        />

        <Group justify="space-between">
          <Text size="sm" ff="monospace">
            {ponto
              ? `${ponto.latitude.toFixed(6)}, ${ponto.longitude.toFixed(6)}`
              : 'Nenhum ponto marcado'}
          </Text>
          <Group gap="xs">
            <Button variant="default" onClick={aoFechar}>
              Cancelar
            </Button>
            <Button
              leftSection={<IconMapPin size={16} />}
              disabled={!ponto}
              onClick={() => {
                if (ponto) {
                  aoEscolher(ponto);
                  aoFechar();
                }
              }}
            >
              Usar este ponto
            </Button>
          </Group>
        </Group>
      </Stack>
    </Modal>
  );
}
