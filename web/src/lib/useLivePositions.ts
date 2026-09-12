import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';

export interface LivePosition {
  assetId: string;
  tag: string;
  name: string;
  latitude: number;
  longitude: number;
  speedKph?: number | null;
  heading?: number | null;
  ignition?: boolean | null;
  moving?: boolean | null;
  recordedAt: string;
  tripId?: string | null;
}

/**
 * Liga-se ao fluxo de posições em tempo real.
 *
 * <p>O `EventSource` do browser não deixa enviar cabeçalhos, por isso não pode
 * levar o token de acesso. Em vez disso pede-se ao servidor um bilhete de uso
 * único e curta duração e é esse que vai no URL — se alguém o apanhar nos
 * registos do proxy, já não serve para nada.
 *
 * <p>O `EventSource` religa-se sozinho quando a rede cai, mas o bilhete só
 * serve uma vez: em cada corte é preciso pedir outro, o que se faz aqui.
 */
export function useLivePositions() {
  const [connected, setConnected] = useState(false);
  const [positions, setPositions] = useState<Map<string, LivePosition>>(new Map());
  const source = useRef<EventSource | null>(null);
  const retry = useRef<number | null>(null);
  const attempts = useRef(0);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;

    async function connect() {
      if (!alive.current) return;
      try {
        const ticket = await api<{ ticket: string; url: string }>('/telemetry/stream-ticket', {
          method: 'POST',
        });
        if (!alive.current) return;

        const es = new EventSource(ticket.url);
        source.current = es;

        es.addEventListener('ligado', () => {
          attempts.current = 0;
          setConnected(true);
        });

        es.addEventListener('posicao', (event) => {
          try {
            const p = JSON.parse((event as MessageEvent).data) as LivePosition;
            setPositions((prev) => {
              const next = new Map(prev);
              next.set(p.assetId, p);
              return next;
            });
          } catch {
            // Um evento malformado não deve derrubar o mapa inteiro.
          }
        });

        es.onerror = () => {
          setConnected(false);
          es.close();
          source.current = null;
          scheduleReconnect();
        };
      } catch {
        setConnected(false);
        scheduleReconnect();
      }
    }

    function scheduleReconnect() {
      if (!alive.current || retry.current) return;
      // Espera crescente até 30 s: se o servidor está em baixo, martelá-lo de
      // segundo a segundo com pedidos de bilhete só piora a situação.
      const delay = Math.min(30_000, 1000 * 2 ** attempts.current);
      attempts.current += 1;
      retry.current = window.setTimeout(() => {
        retry.current = null;
        void connect();
      }, delay);
    }

    void connect();

    return () => {
      alive.current = false;
      if (retry.current) window.clearTimeout(retry.current);
      source.current?.close();
      source.current = null;
    };
  }, []);

  return { connected, positions: [...positions.values()] };
}
