/**
 * O marcador da viatura no mapa — um veículo visto de cima, não um ponto.
 *
 * <p>A diferença face a um círculo não é decorativa: numa frota de trinta
 * viaturas, a forma e a orientação dizem de relance quem vai para onde. Um
 * ponto obriga a abrir cada um para saber.
 *
 * <p>O movimento é <b>interpolado</b>. As posições chegam de dez em dez ou de
 * trinta em trinta segundos; sem interpolação a viatura salta de sítio, o que
 * se lê como avaria. Com ela, desliza — que é o que faz o mapa parecer vivo.
 */

export interface EstadoViatura {
  latitude: number;
  longitude: number;
  heading?: number | null;
  speedKph?: number | null;
  moving?: boolean | null;
  secondsSincePosition?: number | null;
  tag: string;
}

/** Sem notícias há mais de meia hora, a posição já não representa o presente. */
const SEGUNDOS_ATE_DESACTUALIZAR = 1800;

/** Quanto tempo a viatura leva a deslizar até à posição nova. */
const DURACAO_DESLIZE_MS = 1200;

export function corDaViatura(a: EstadoViatura): string {
  if ((a.secondsSincePosition ?? 0) > SEGUNDOS_ATE_DESACTUALIZAR) return '#71717a';
  if (a.moving) return '#16a34a';
  return '#2563eb';
}

/**
 * Desenha o veículo.
 *
 * <p>A seta do rumo fica <b>fora</b> do corpo do veículo: sobreposta, a
 * rotação tornava a forma irreconhecível nos tamanhos pequenos.
 */
export function desenharMarcador(el: HTMLElement, a: EstadoViatura, seleccionado: boolean) {
  const cor = corDaViatura(a);
  const rumo = a.heading ?? 0;
  const parado = !a.moving;
  const velocidade = a.speedKph != null ? Math.round(a.speedKph) : null;

  el.style.cursor = 'pointer';
  el.style.willChange = 'transform';
  el.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <div style="position:relative;width:34px;height:34px;">
        ${
          a.moving
            ? `<div style="
                 position:absolute;inset:-4px;border-radius:50%;
                 border:2px solid ${cor};opacity:.35;
                 animation:pulsoViatura 2s ease-out infinite;"></div>`
            : ''
        }
        <div style="
          position:absolute;inset:0;border-radius:50%;
          background:${cor};border:2.5px solid #fff;
          box-shadow:0 2px 6px rgba(0,0,0,.45);
          display:flex;align-items:center;justify-content:center;">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="#fff"
               style="transform:rotate(${rumo}deg);transition:transform .5s ease-out;">
            <!-- Camião visto de cima, apontado para norte antes de rodar. -->
            <path d="M12 2.2 8.9 6.1h1.6v4.2H6.2c-.7 0-1.2.5-1.2 1.2v6.9c0 .6.5 1.1 1.2 1.1h1.1
                     a1.9 1.9 0 0 0 3.7 0h2.1a1.9 1.9 0 0 0 3.7 0h1.1c.7 0 1.2-.5 1.2-1.1v-6.9
                     c0-.7-.5-1.2-1.2-1.2h-4.3V6.1h1.6L12 2.2Z"/>
          </svg>
        </div>
        ${
          seleccionado
            ? `<div style="position:absolute;inset:-7px;border-radius:50%;
                 border:2px solid #B08D3C;"></div>`
            : ''
        }
      </div>
      <span style="
        background:rgba(24,24,27,.86);color:#fff;font-size:10px;font-weight:700;
        padding:1px 5px;border-radius:3px;white-space:nowrap;
        border:1px solid rgba(255,255,255,.25);">
        ${escaparHtml(a.tag)}${velocidade != null && !parado ? ` · ${velocidade}` : ''}
      </span>
    </div>`;
}

/**
 * Faz a viatura deslizar da posição antiga para a nova.
 *
 * <p>Devolve uma função que cancela a animação — é preciso chamá-la quando
 * chega uma posição nova, ou duas animações disputam o mesmo marcador e a
 * viatura treme.
 */
export function deslizar(
  aplicar: (lng: number, lat: number) => void,
  de: [number, number],
  para: [number, number],
): () => void {
  // Salto grande (perda de sinal, viatura desligada e ligada longe): põe-se lá
  // logo. Animar 40 km faria a viatura atravessar a cidade a voar.
  const distancia = Math.hypot(para[0] - de[0], para[1] - de[1]);
  if (distancia > 0.05) {
    aplicar(para[0], para[1]);
    return () => {};
  }

  const inicio = performance.now();
  let pedido = 0;
  let cancelado = false;

  const passo = (agora: number) => {
    if (cancelado) return;
    const t = Math.min(1, (agora - inicio) / DURACAO_DESLIZE_MS);
    // Suavização: começa e acaba devagar, como um veículo a sério.
    const f = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    aplicar(de[0] + (para[0] - de[0]) * f, de[1] + (para[1] - de[1]) * f);
    if (t < 1) {
      pedido = requestAnimationFrame(passo);
    }
  };
  pedido = requestAnimationFrame(passo);

  return () => {
    cancelado = true;
    cancelAnimationFrame(pedido);
  };
}

/** O nome do ativo é escrito por um utilizador e vai para dentro de HTML. */
export function escaparHtml(valor: string) {
  return valor.replace(
    /[&<>"']/g,
    (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string,
  );
}

/**
 * É noite no fuso de Luanda?
 *
 * <p>O satélite é fotografia tirada de dia — sempre. À noite, um mapa de ruas
 * escuro diz a verdade sobre a hora e cansa menos os olhos de quem vigia a
 * frota de madrugada.
 */
export function ehNoiteEmLuanda(agora = new Date()): boolean {
  const hora = Number(
    new Intl.DateTimeFormat('pt-PT', {
      timeZone: 'Africa/Luanda',
      hour: '2-digit',
      hour12: false,
    }).format(agora),
  );
  return hora >= 18 || hora < 6;
}
