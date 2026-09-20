import { Badge, Group, Text } from '@mantine/core';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useEffect, useMemo, useRef, useState } from 'react';
import { BASEMAPS, CENTRO_OMISSAO, styleFor } from '../pages/map/basemaps';

export interface PontoRota {
  latitude: number;
  longitude: number;
  label?: string | null;
  /** O papel no percurso: muda a cor e o rótulo do pino. */
  tipo?: 'origem' | 'passagem' | 'destino';
}

export interface LinhaReal {
  /** Coordenadas [lon, lat] do percurso realmente andado. */
  coords: [number, number][];
  label?: string;
}

/**
 * O percurso desenhado: a linha prevista pelas estradas, os pontos de
 * passagem numerados e, por cima, o que a viatura andou de facto.
 *
 * <p>Uma rota escrita em números («480 km») não se discute. Desenhada, vê-se:
 * o desvio que alguém fez por fora, a paragem onde não devia, a estrada que
 * não é a que o gestor tinha em cabeça. É o mesmo dado a dizer muito mais.
 */
export function MapaPercurso({
  pathGeojson,
  pontos = [],
  reais = [],
  altura = 320,
  fundo = 'satelite',
}: {
  /** GeoJSON da rota (LineString), vindo do motor de rotas. */
  pathGeojson?: string | null;
  pontos?: PontoRota[];
  reais?: LinhaReal[];
  altura?: number | string;
  fundo?: keyof typeof BASEMAPS;
}) {
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const marcadores = useRef<maplibregl.Marker[]>([]);
  // O mapa nasce depois do primeiro render: sem este sinal, o desenho corria
  // uma vez com o mapa ainda por criar e a linha nunca aparecia.
  const [pronto, setPronto] = useState(false);

  // A linha prevista: aceita LineString, Feature ou FeatureCollection.
  const linhaPrevista = useMemo<[number, number][]>(() => {
    if (!pathGeojson) return [];
    try {
      const g = JSON.parse(pathGeojson);
      const geom = g?.type === 'Feature' ? g.geometry : g?.type === 'FeatureCollection' ? g.features?.[0]?.geometry : g;
      if (geom?.type === 'LineString') return geom.coordinates as [number, number][];
      if (geom?.type === 'MultiLineString') return (geom.coordinates as [number, number][][]).flat();
      return [];
    } catch {
      return [];
    }
  }, [pathGeojson]);

  useEffect(() => {
    if (!container || map.current) return;
    const instance = new maplibregl.Map({
      container,
      style: styleFor(fundo),
      center: CENTRO_OMISSAO,
      zoom: 9,
      attributionControl: { compact: true },
    });
    instance.addControl(new maplibregl.NavigationControl({ visualizePitch: false }), 'top-right');
    instance.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    instance.on('load', () => setPronto(true));
    map.current = instance;
    return () => {
      instance.remove();
      map.current = null;
      setPronto(false);
    };
  }, [container, fundo]);

  useEffect(() => {
    const instance = map.current;
    if (!instance || !pronto) return;

    const desenhar = () => {
      // Limpar o que estava: a rota muda enquanto se clica nos pontos.
      ['rota-linha', 'rota-contorno', ...reais.map((_, i) => `real-${i}`)].forEach((id) => {
        if (instance.getLayer(id)) instance.removeLayer(id);
      });
      ['rota', ...reais.map((_, i) => `real-src-${i}`)].forEach((id) => {
        if (instance.getSource(id)) instance.removeSource(id);
      });
      marcadores.current.forEach((m) => m.remove());
      marcadores.current = [];

      const limites = new maplibregl.LngLatBounds();
      let temAlgo = false;

      if (linhaPrevista.length > 1) {
        instance.addSource('rota', {
          type: 'geojson',
          data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: linhaPrevista } },
        });
        // Contorno escuro por baixo: sobre satélite, uma linha só perde-se.
        instance.addLayer({ id: 'rota-contorno', type: 'line', source: 'rota', paint: { 'line-color': '#1f2126', 'line-width': 8, 'line-opacity': 0.55 } });
        instance.addLayer({ id: 'rota-linha', type: 'line', source: 'rota', paint: { 'line-color': '#f5a800', 'line-width': 4 } });
        linhaPrevista.forEach((c) => limites.extend(c));
        temAlgo = true;
      }

      reais.forEach((r, i) => {
        if (r.coords.length < 2) return;
        instance.addSource(`real-src-${i}`, {
          type: 'geojson',
          data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: r.coords } },
        });
        instance.addLayer({
          id: `real-${i}`,
          type: 'line',
          source: `real-src-${i}`,
          paint: { 'line-color': '#2563eb', 'line-width': 3, 'line-dasharray': [2, 1.5], 'line-opacity': 0.95 },
        });
        r.coords.forEach((c) => limites.extend(c));
        temAlgo = true;
      });

      pontos.forEach((p, i) => {
        const tipo = p.tipo ?? (i === 0 ? 'origem' : i === pontos.length - 1 ? 'destino' : 'passagem');
        const cor = tipo === 'origem' ? '#16a34a' : tipo === 'destino' ? '#b91c1c' : '#1f2126';
        const texto = tipo === 'origem' ? 'A' : tipo === 'destino' ? 'B' : String(i);
        const el = document.createElement('div');
        el.style.cssText =
          `width:24px;height:24px;border-radius:50%;background:${cor};color:#fff;border:2px solid #fff;` +
          'font:700 11px sans-serif;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 4px rgba(0,0,0,.45)';
        el.textContent = texto;
        const m = new maplibregl.Marker({ element: el }).setLngLat([p.longitude, p.latitude]);
        if (p.label) {
          m.setPopup(new maplibregl.Popup({ offset: 14 }).setText(p.label));
        }
        m.addTo(instance);
        marcadores.current.push(m);
        limites.extend([p.longitude, p.latitude]);
        temAlgo = true;
      });

      if (temAlgo && !limites.isEmpty()) {
        // Dentro de um modal o mapa nasce com o tamanho errado; sem isto o
        // enquadramento sai calculado para uma caixa que já não é esta.
        instance.resize();
        instance.fitBounds(limites, { padding: 60, maxZoom: 14, duration: 0 });
      }
    };

    desenhar();
    instance.on('style.load', desenhar);
    return () => {
      instance.off('style.load', desenhar);
    };
  }, [linhaPrevista, pontos, reais, pronto]);

  const semNada = linhaPrevista.length < 2 && pontos.length === 0 && reais.length === 0;

  return (
    <div>
      <div
        ref={setContainer}
        style={{ width: '100%', height: altura, borderRadius: 6, overflow: 'hidden', background: '#e9e9ec' }}
      />
      <Group gap="sm" mt={6}>
        {linhaPrevista.length > 1 && (
          <Group gap={4}>
            <div style={{ width: 18, height: 4, background: '#f5a800', borderRadius: 2 }} />
            <Text size="xs" c="dimmed">
              previsto
            </Text>
          </Group>
        )}
        {reais.length > 0 && (
          <Group gap={4}>
            <div style={{ width: 18, height: 3, background: '#2563eb', borderRadius: 2 }} />
            <Text size="xs" c="dimmed">
              andado pelo GPS
            </Text>
          </Group>
        )}
        {semNada && (
          <Badge variant="light" color="gray">
            Escolha a origem e o destino para ver o percurso
          </Badge>
        )}
        {!semNada && linhaPrevista.length < 2 && (
          <Text size="xs" c="dimmed">
            Sem traçado pelas estradas — carregue em «Calcular percurso».
          </Text>
        )}
      </Group>
    </div>
  );
}
