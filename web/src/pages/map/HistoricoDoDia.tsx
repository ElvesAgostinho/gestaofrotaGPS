import { ActionIcon, Badge, Button, Card, Group, Loader, Select, Slider, Stack, Text, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconPlayerPause, IconPlayerPlay, IconX } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import maplibregl from 'maplibre-gl';
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import { fmtNumber } from '../../lib/format';
import type { LiveAsset } from '../MapPage';

interface Ponto {
  at: string;
  latitude: number;
  longitude: number;
  speedKph?: number | null;
  heading?: number | null;
  ignition?: boolean | null;
}

interface Paragem {
  order: number;
  startedAt: string;
  endedAt: string;
  minutes: number;
  latitude: number;
  longitude: number;
  idlingMinutes?: number | null;
}

interface Dia {
  assetId: string;
  date: string;
  points: number;
  distanceKm: number;
  maxSpeedKph?: number | null;
  firstMovementAt?: string | null;
  lastMovementAt?: string | null;
  movingMinutes: number;
  stoppedMinutes: number;
  idlingMinutes?: number | null;
  ignitionKnown: boolean;
  stops: Paragem[];
  track: Ponto[];
}

const hora = (s: string) => new Date(s).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Luanda' });
const horasMin = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`);
const isoDia = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * O dia de uma viatura, para ver de novo: o percurso desenhado, as paragens
 * numeradas (onde e quanto tempo), o ralenti — e um cursor que repete o dia
 * ao ritmo que se quiser. O que se procura é responder a «onde esteve às 11h»
 * e «quanto tempo ficou com o motor ligado parado» sem sair do mapa.
 */
export function HistoricoDoDia({ map, assets, fechar }: { map: maplibregl.Map | null; assets: LiveAsset[]; fechar: () => void }) {
  const [assetId, setAssetId] = useState<string | null>(assets[0]?.assetId ?? null);
  const [dia, setDia] = useState<Date | null>(new Date());
  const [cursor, setCursor] = useState(0);
  const [aTocar, setATocar] = useState(false);
  const marcadorCursor = useRef<maplibregl.Marker | null>(null);
  const marcadoresParagem = useRef<maplibregl.Marker[]>([]);

  const data = dia ? isoDia(dia) : null;
  const { data: d, isLoading } = useQuery({
    queryKey: ['day', assetId, data],
    queryFn: () => api<Dia>(`/assets/${assetId}/day?date=${data}`),
    enabled: !!assetId && !!data,
  });

  const linha = useMemo(() => (d?.track ?? []).map((p) => [p.longitude, p.latitude] as [number, number]), [d]);

  // ---- desenhar a linha e as paragens ---------------------------------
  useEffect(() => {
    if (!map) return;
    const desenhar = () => {
      if (map.getLayer('dia-linha')) map.removeLayer('dia-linha');
      if (map.getSource('dia')) map.removeSource('dia');
      marcadoresParagem.current.forEach((m) => m.remove());
      marcadoresParagem.current = [];
      if (linha.length < 2) return;
      map.addSource('dia', { type: 'geojson', data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: linha } } });
      map.addLayer({ id: 'dia-linha', type: 'line', source: 'dia', paint: { 'line-color': '#f5a800', 'line-width': 4, 'line-opacity': 0.9 } });
      (d?.stops ?? []).forEach((s) => {
        const el = document.createElement('div');
        el.style.cssText =
          'width:22px;height:22px;border-radius:50%;background:#1f2126;color:#ffd254;border:2px solid #ffd254;font:700 11px sans-serif;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 4px rgba(0,0,0,.4)';
        el.textContent = String(s.order);
        const m = new maplibregl.Marker({ element: el })
          .setLngLat([s.longitude, s.latitude])
          .setPopup(new maplibregl.Popup({ offset: 14 }).setHTML(
            `<div style="font-size:12px"><b>Paragem ${s.order}</b><br/>${hora(s.startedAt)} – ${hora(s.endedAt)} · ${horasMin(s.minutes)}` +
              (s.idlingMinutes != null ? `<br/>Motor ligado parado: <b>${horasMin(s.idlingMinutes)}</b>` : '') + '</div>',
          ))
          .addTo(map);
        marcadoresParagem.current.push(m);
      });
      const bounds = new maplibregl.LngLatBounds();
      linha.forEach((c) => bounds.extend(c));
      map.fitBounds(bounds, { padding: 80, maxZoom: 15 });
    };
    if (map.isStyleLoaded()) desenhar();
    else map.once('load', desenhar);
    // Trocar o fundo (satélite/noite) apaga as camadas: volta-se a desenhar.
    map.on('style.load', desenhar);
    return () => {
      map.off('style.load', desenhar);
      if (map.getLayer('dia-linha')) map.removeLayer('dia-linha');
      if (map.getSource('dia')) map.removeSource('dia');
      marcadoresParagem.current.forEach((m) => m.remove());
      marcadoresParagem.current = [];
    };
  }, [map, linha, d]);

  // ---- cursor do replay --------------------------------------------------
  useEffect(() => {
    setCursor(0);
    setATocar(false);
  }, [assetId, data]);

  useEffect(() => {
    if (!map) return;
    const p = d?.track?.[cursor];
    if (!p) {
      marcadorCursor.current?.remove();
      marcadorCursor.current = null;
      return;
    }
    if (!marcadorCursor.current) {
      const el = document.createElement('div');
      el.style.cssText = 'width:18px;height:18px;border-radius:50%;background:#2563eb;border:3px solid #fff;box-shadow:0 0 0 3px rgba(37,99,235,.35)';
      marcadorCursor.current = new maplibregl.Marker({ element: el }).setLngLat([p.longitude, p.latitude]).addTo(map);
    } else {
      marcadorCursor.current.setLngLat([p.longitude, p.latitude]);
    }
    return undefined;
  }, [map, d, cursor]);

  useEffect(
    () => () => {
      marcadorCursor.current?.remove();
    },
    [],
  );

  useEffect(() => {
    if (!aTocar || !d) return;
    const total = d.track.length;
    const id = window.setInterval(() => {
      setCursor((c) => {
        if (c + 1 >= total) {
          setATocar(false);
          return c;
        }
        return c + 1;
      });
    }, 250);
    return () => window.clearInterval(id);
  }, [aTocar, d]);

  const ponto = d?.track?.[cursor];

  return (
    <Card p="sm" withBorder style={{ width: 360, maxHeight: '100%', overflow: 'auto' }}>
      <Stack gap="sm">
        <Group justify="space-between">
          <Text fw={700}>Histórico do dia</Text>
          <ActionIcon variant="subtle" color="gray" onClick={fechar} aria-label="Fechar">
            <IconX size={16} />
          </ActionIcon>
        </Group>
        <Select
          data={assets.map((a) => ({ value: a.assetId, label: `${a.tag} — ${a.name}` }))}
          value={assetId}
          onChange={setAssetId}
          searchable
          placeholder="Viatura"
          size="sm"
        />
        <DateInput value={dia} onChange={setDia} valueFormat="DD/MM/YYYY" maxDate={new Date()} size="sm" />
        {isLoading && <Loader size="sm" />}
        {d && d.points === 0 && (
          <Text size="sm" c="dimmed">
            Sem posições neste dia.
          </Text>
        )}
        {d && d.points > 0 && (
          <>
            <Group gap={6}>
              <Badge variant="light">{fmtNumber(d.distanceKm, 1)} km</Badge>
              <Badge variant="light" color="green">
                a andar {horasMin(d.movingMinutes)}
              </Badge>
              <Badge variant="light" color="gray">
                parado {horasMin(d.stoppedMinutes)}
              </Badge>
              {d.ignitionKnown ? (
                <Tooltip label="Motor ligado sem andar: gasóleo a queimar-se parado">
                  <Badge variant="light" color={(d.idlingMinutes ?? 0) >= 30 ? 'red' : 'orange'}>
                    ralenti {horasMin(d.idlingMinutes ?? 0)}
                  </Badge>
                </Tooltip>
              ) : (
                <Tooltip label="O aparelho não reporta a ignição; ligue o fio ACC do rastreador para medir o ralenti">
                  <Badge variant="outline" color="gray">
                    ralenti desconhecido
                  </Badge>
                </Tooltip>
              )}
              {d.maxSpeedKph != null && <Badge variant="light" color="blue">máx. {fmtNumber(d.maxSpeedKph, 0)} km/h</Badge>}
            </Group>
            {d.firstMovementAt && (
              <Text size="xs" c="dimmed">
                Primeiro movimento {hora(d.firstMovementAt)} · último {d.lastMovementAt ? hora(d.lastMovementAt) : '—'}
              </Text>
            )}
            <Group gap="xs" wrap="nowrap">
              <ActionIcon variant="filled" onClick={() => setATocar((v) => !v)} aria-label={aTocar ? 'Pausa' : 'Repetir o dia'}>
                {aTocar ? <IconPlayerPause size={16} /> : <IconPlayerPlay size={16} />}
              </ActionIcon>
              <Slider style={{ flex: 1 }} min={0} max={Math.max(0, d.track.length - 1)} value={cursor} onChange={setCursor} label={null} size="sm" />
              <Text size="xs" w={44} ta="right">
                {ponto ? hora(ponto.at) : ''}
              </Text>
            </Group>
            {ponto && (
              <Text size="xs" c="dimmed">
                {ponto.speedKph != null ? `${fmtNumber(ponto.speedKph, 0)} km/h` : ''}
                {ponto.ignition != null ? ` · motor ${ponto.ignition ? 'ligado' : 'desligado'}` : ''}
              </Text>
            )}
            <div>
              <Text size="sm" fw={600} mb={4}>
                Paragens ({d.stops.length})
              </Text>
              {d.stops.length === 0 && (
                <Text size="xs" c="dimmed">
                  Nenhuma paragem de 3 minutos ou mais.
                </Text>
              )}
              <Stack gap={4}>
                {d.stops.map((s) => (
                  <Button
                    key={s.order}
                    variant="subtle"
                    size="compact-xs"
                    justify="flex-start"
                    onClick={() => map?.flyTo({ center: [s.longitude, s.latitude], zoom: 16 })}
                  >
                    {s.order}. {hora(s.startedAt)}–{hora(s.endedAt)} · {horasMin(s.minutes)}
                    {s.idlingMinutes != null && s.idlingMinutes > 0 ? ` · ralenti ${horasMin(s.idlingMinutes)}` : ''}
                  </Button>
                ))}
              </Stack>
            </div>
          </>
        )}
      </Stack>
    </Card>
  );
}
