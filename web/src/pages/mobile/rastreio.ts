/**
 * O modo viagem: o telemóvel como aparelho de localização.
 *
 * <p>Três coisas que não são óbvias e que decidem se isto funciona numa
 * estrada de Angola ou só numa demonstração:
 *
 * <ol>
 *   <li><b>O ecrã tem de ficar ligado.</b> Um PWA não corre em segundo plano:
 *       com o ecrã apagado o browser suspende a leitura do GPS em segundos.
 *       Por isso pede-se o <i>wake lock</i> e diz-se ao motorista, sem rodeios,
 *       para pôr o telemóvel no suporte e no carregador.</li>
 *   <li><b>As posições vão em lote.</b> Uma por segundo pela rede seria bateria
 *       e dados deitados fora; guarda-se e envia-se de trinta em trinta
 *       segundos.</li>
 *   <li><b>Sem rede, não se perde nada.</b> A fila fica no armazenamento local
 *       e sobe quando a ligação voltar — que é o que acontece a cada vale.</li>
 * </ol>
 */
import { api } from '../../api/client';

export interface Posicao {
  latitude: number;
  longitude: number;
  speedKph?: number | null;
  accuracyM?: number | null;
  heading?: number | null;
  recordedAt: string;
}

const FILA = 'imbondeiro.rastreio.fila';
/** De quanto em quanto tempo se tenta enviar o que está em fila. */
const INTERVALO_ENVIO_MS = 30_000;
/** Abaixo disto a leitura é ruído de cidade e não se guarda. */
const MIN_METROS = 25;
/** Não se guardam mais do que isto sem rede: são cerca de três horas de viagem. */
const MAX_EM_FILA = 400;

function lerFila(): Posicao[] {
  try {
    const bruto = localStorage.getItem(FILA);
    return bruto ? (JSON.parse(bruto) as Posicao[]) : [];
  } catch {
    return [];
  }
}

function gravarFila(fila: Posicao[]) {
  try {
    localStorage.setItem(FILA, JSON.stringify(fila.slice(-MAX_EM_FILA)));
  } catch {
    /* sem armazenamento, a viagem continua — perde-se o histórico offline */
  }
}

/** Distância entre dois pontos, em metros (fórmula de haversine). */
function metros(a: Posicao, b: Posicao): number {
  const R = 6_371_000;
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export interface EstadoRastreio {
  aLigar: boolean;
  aSeguir: boolean;
  porEnviar: number;
  ultima: Posicao | null;
  erro: string | null;
}

type Ouvinte = (e: EstadoRastreio) => void;

/**
 * O rastreio vive fora do React: uma viagem não pode parar porque o ecrã
 * mudou de separador.
 */
class Rastreio {
  private watchId: number | null = null;
  private timer: number | null = null;
  private wakeLock: { release: () => Promise<void> } | null = null;
  private assetId: string | null = null;
  private ouvintes = new Set<Ouvinte>();
  private estado: EstadoRastreio = {
    aLigar: false,
    aSeguir: false,
    porEnviar: lerFila().length,
    ultima: null,
    erro: null,
  };

  subscrever(o: Ouvinte): () => void {
    this.ouvintes.add(o);
    o(this.estado);
    return () => this.ouvintes.delete(o);
  }

  private mudar(parcial: Partial<EstadoRastreio>) {
    this.estado = { ...this.estado, ...parcial };
    this.ouvintes.forEach((o) => o(this.estado));
  }

  get aSeguir() {
    return this.estado.aSeguir;
  }

  async iniciar(assetId: string | null) {
    if (this.estado.aSeguir) return;
    if (!('geolocation' in navigator)) {
      this.mudar({ erro: 'Este telemóvel não dá acesso à localização.' });
      return;
    }
    this.assetId = assetId;
    this.mudar({ aLigar: true, erro: null });

    // O ecrã ligado é o que mantém o GPS a ler num PWA.
    try {
      const nav = navigator as Navigator & {
        wakeLock?: { request: (t: 'screen') => Promise<{ release: () => Promise<void> }> };
      };
      if (nav.wakeLock) {
        this.wakeLock = await nav.wakeLock.request('screen');
      }
    } catch {
      /* sem wake lock continua, mas o ecrã pode apagar-se */
    }

    this.watchId = navigator.geolocation.watchPosition(
      (p) => this.receber(p),
      (e) => this.mudar({ aLigar: false, erro: mensagemDeErro(e) }),
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 30_000 },
    );
    this.timer = window.setInterval(() => void this.enviar(), INTERVALO_ENVIO_MS);
    this.mudar({ aLigar: false, aSeguir: true });
  }

  async parar() {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    if (this.timer !== null) {
      window.clearInterval(this.timer);
      this.timer = null;
    }
    if (this.wakeLock) {
      try {
        await this.wakeLock.release();
      } catch {
        /* já libertado */
      }
      this.wakeLock = null;
    }
    this.mudar({ aSeguir: false });
    await this.enviar();
  }

  private receber(p: GeolocationPosition) {
    const nova: Posicao = {
      latitude: p.coords.latitude,
      longitude: p.coords.longitude,
      speedKph: p.coords.speed != null && p.coords.speed >= 0 ? p.coords.speed * 3.6 : null,
      accuracyM: p.coords.accuracy ?? null,
      heading: p.coords.heading != null && !Number.isNaN(p.coords.heading) ? p.coords.heading : null,
      recordedAt: new Date(p.timestamp).toISOString(),
    };
    const fila = lerFila();
    const anterior = fila[fila.length - 1] ?? this.estado.ultima;
    // Parado no semáforo não precisa de uma posição por segundo.
    if (anterior && metros(anterior, nova) < MIN_METROS && (nova.speedKph ?? 0) < 5) {
      this.mudar({ ultima: nova });
      return;
    }
    fila.push(nova);
    gravarFila(fila);
    this.mudar({ ultima: nova, porEnviar: fila.length, erro: null });
  }

  /** Envia o que está em fila; se falhar, fica para a próxima. */
  async enviar(): Promise<void> {
    const fila = lerFila();
    if (fila.length === 0) return;
    try {
      await api('/mobile/positions', {
        method: 'POST',
        body: { assetId: this.assetId ?? undefined, positions: fila },
      });
      gravarFila([]);
      this.mudar({ porEnviar: 0 });
    } catch {
      // Sem rede: fica tudo onde está, e tenta-se outra vez daqui a pouco.
      this.mudar({ porEnviar: fila.length });
    }
  }
}

function mensagemDeErro(e: GeolocationPositionError): string {
  switch (e.code) {
    case e.PERMISSION_DENIED:
      return 'Precisa de autorizar a localização para o gestor o acompanhar na rota.';
    case e.POSITION_UNAVAILABLE:
      return 'Sem sinal de GPS. Saia de dentro de edifícios ou aguarde um pouco.';
    default:
      return 'Não foi possível ler a localização.';
  }
}

export const rastreio = new Rastreio();
