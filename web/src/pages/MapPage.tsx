import { ActionIcon, Badge, Card, Group, SegmentedControl, Stack, Text, Title, Tooltip } from '@mantine/core';
import { IconFocus2, IconSatellite } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import {
  desenharMarcador,
  deslizar,
  ehNoiteEmLuanda,
  escaparHtml as escapeHtml,
} from './map/MarcadorViatura';
import { useLivePositions, type LivePosition } from '../lib/useLivePositions';
import { BASEMAPS, CENTRO_OMISSAO as DEFAULT_CENTER, styleFor } from './map/basemaps';

export interface LiveAsset {
  assetId: string;
  tag: string;
  name: string;
  category?: string;
  status?: string;
  latitude: number;
  longitude: number;
  positionAt?: string;
  speedKph?: number | null;
  heading?: number | null;
  moving?: boolean | null;
  secondsSincePosition?: number | null;
  deviceStatus?: string;
}



export function MapPage() {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<maplibregl.Map | null>(null);
  const markers = useRef<Map<string, maplibregl.Marker>>(new Map());
  // De noite abre no fundo escuro; de dia, no satélite. O utilizador troca
  // à vontade — só a escolha inicial é que segue a hora de Luanda.
  const [basemap, setBasemap] = useState<keyof typeof BASEMAPS>(
    () => (ehNoiteEmLuanda() ? 'noite' : 'satelite'),
  );
  const [seleccionado, setSeleccionado] = useState<string | null>(null);
  const animacoes = useRef<Map<string, () => void>>(new Map());
  const [ready, setReady] = useState(false);

  // Estado inicial da frota: uma leitura, e daí em diante é o fluxo que manda.
  const { data: fleet } = useQuery({
    queryKey: ['telemetry', 'live'],
    queryFn: () => api<LiveAsset[]>('/telemetry/live'),
    // Rede de segurança: se o fluxo cair e o browser demorar a religar,
    // o mapa não fica congelado sem ninguém dar por isso.
    refetchInterval: 120_000,
  });

  const { connected, positions } = useLivePositions();

  // A posição do fluxo tem prioridade sobre a da leitura inicial.
  const assets = useMemo(() => {
    const byId = new Map<string, LiveAsset>();
    (fleet ?? []).forEach((a) => byId.set(a.assetId, a));
    positions.forEach((p) => {
      const existing = byId.get(p.assetId);
      byId.set(p.assetId, {
        ...(existing ?? { assetId: p.assetId, tag: p.tag, name: p.name }),
        assetId: p.assetId,
        tag: p.tag,
        name: p.name,
        latitude: p.latitude,
        longitude: p.longitude,
        speedKph: p.speedKph,
        heading: p.heading,
        moving: p.moving,
        positionAt: p.recordedAt,
        secondsSincePosition: 0,
      } as LiveAsset);
    });
    return [...byId.values()];
  }, [fleet, positions]);

  // ---- criar o mapa uma única vez -----------------------------------
  useEffect(() => {
    if (!container.current || map.current) return;
    const instance = new maplibregl.Map({
      container: container.current,
      style: styleFor(ehNoiteEmLuanda() ? 'noite' : 'satelite'),
      center: DEFAULT_CENTER,
      zoom: 11,
      attributionControl: { compact: true },
    });
    instance.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right');
    instance.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    instance.on('load', () => setReady(true));
    map.current = instance;

    return () => {
      instance.remove();
      map.current = null;
    };
  }, []);

  // ---- trocar o fundo sem perder a posição ---------------------------
  useEffect(() => {
    if (!map.current || !ready) return;
    map.current.setStyle(styleFor(basemap));
  }, [basemap, ready]);

  // ---- marcadores ----------------------------------------------------
  useEffect(() => {
    const instance = map.current;
    if (!instance || !ready) return;

    const seen = new Set<string>();
    assets.forEach((asset) => {
      if (asset.latitude == null || asset.longitude == null) return;
      seen.add(asset.assetId);

      const existing = markers.current.get(asset.assetId);
      if (existing) {
        // Mover em vez de recriar: recriar faz o ícone piscar a cada posição.
        // E deslizar em vez de saltar: as posições chegam de trinta em trinta
        // segundos, e um salto lê-se como avaria em vez de movimento.
        const antes = existing.getLngLat();
        animacoes.current.get(asset.assetId)?.();
        animacoes.current.set(
          asset.assetId,
          deslizar(
            (lng, lat) => existing.setLngLat([lng, lat]),
            [antes.lng, antes.lat],
            [asset.longitude, asset.latitude],
          ),
        );
        desenharMarcador(existing.getElement(), asset, seleccionado === asset.assetId);
        existing.getPopup()?.setHTML(popupHtml(asset));
        return;
      }
      const element = document.createElement('div');
      desenharMarcador(element, asset, seleccionado === asset.assetId);
      const marker = new maplibregl.Marker({ element })
        .setLngLat([asset.longitude, asset.latitude])
        .setPopup(new maplibregl.Popup({ offset: 18 }).setHTML(popupHtml(asset)))
        .addTo(instance);
      // Clicar marca a viatura com um anel dourado: numa frota de trinta,
      // perde-se de vista aquela que se estava a seguir.
      element.addEventListener('click', () =>
        setSeleccionado((actual) => (actual === asset.assetId ? null : asset.assetId)),
      );
      markers.current.set(asset.assetId, marker);
    });

    // Ativos que deixaram de ter posição saem do mapa.
    markers.current.forEach((marker, id) => {
      if (!seen.has(id)) {
        animacoes.current.get(id)?.();
        animacoes.current.delete(id);
        marker.remove();
        markers.current.delete(id);
      }
    });
  }, [assets, ready]);

  const fitFleet = useCallback(() => {
    const instance = map.current;
    const located = assets.filter((a) => a.latitude != null && a.longitude != null);
    if (!instance || located.length === 0) return;

    if (located.length === 1) {
      instance.flyTo({ center: [located[0].longitude, located[0].latitude], zoom: 16 });
      return;
    }
    const bounds = new maplibregl.LngLatBounds();
    located.forEach((a) => bounds.extend([a.longitude, a.latitude]));
    instance.fitBounds(bounds, { padding: 80, maxZoom: 16 });
  }, [assets]);

  const moving = assets.filter((a) => a.moving).length;

  return (
    <Stack gap="md" h="calc(100vh - 96px)">
      <Group justify="space-between" wrap="nowrap">
        <Group gap="sm">
          <Title order={1} size="h2">
            GPS / Rastreamento
          </Title>
          <Badge
            variant="light"
            color={connected ? 'green' : 'gray'}
            leftSection={<IconSatellite size={12} />}
          >
            {connected ? 'Em direto' : 'A ligar…'}
          </Badge>
          <Text size="sm" c="dimmed">
            {assets.length} localizados · {moving} em movimento
          </Text>
        </Group>

        <Group gap="xs" wrap="nowrap">
          <SegmentedControl
            size="xs"
            value={basemap}
            onChange={(v) => setBasemap(v as keyof typeof BASEMAPS)}
            data={Object.entries(BASEMAPS).map(([value, b]) => ({ value, label: b.label }))}
          />
          <Tooltip label="Enquadrar toda a frota">
            <ActionIcon variant="default" size="lg" onClick={fitFleet}>
              <IconFocus2 size={18} />
            </ActionIcon>
          </Tooltip>
        </Group>
      </Group>

      <Card p={0} style={{ flex: 1, overflow: 'hidden' }}>
        <div ref={container} style={{ width: '100%', height: '100%' }} />
      </Card>
    </Stack>
  );
}


/**
 * Ícone do ativo. Aponta na direção do rumo quando ele é conhecido — num mapa
 * de frota, saber para onde a máquina segue vale tanto como saber onde está.
 */
function popupHtml(asset: LiveAsset) {
  const speed = asset.speedKph != null ? `${asset.speedKph} km/h` : '—';
  const ago = asset.secondsSincePosition;
  const when =
    ago == null ? '—' : ago < 60 ? 'agora mesmo' : `há ${Math.round(ago / 60)} min`;
  return `
    <div style="font-family:inherit;min-width:170px;">
      <div style="font-weight:700;margin-bottom:2px;">${escapeHtml(asset.tag)}</div>
      <div style="color:#666;font-size:12px;margin-bottom:6px;">${escapeHtml(asset.name)}</div>
      <div style="font-size:12px;">Velocidade: <b>${speed}</b></div>
      <div style="font-size:12px;">Última posição: ${when}</div>
      <a href="/ativos/${asset.assetId}" style="font-size:12px;display:inline-block;margin-top:6px;">Abrir ficha</a>
    </div>`;
}

export type { LivePosition };
