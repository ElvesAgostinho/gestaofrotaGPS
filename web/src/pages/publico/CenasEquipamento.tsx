/**
 * Cenas de equipamento em movimento, desenhadas em vetor.
 *
 * <p>São ilustrações nossas, animadas, e não fotografias. A razão é prática e
 * não artística: fotografia de maquinaria pertence a quem a tirou, e uma
 * página comercial que a use sem licença é um problema legal à espera de
 * acontecer — logo na página que serve para vender.
 *
 * <p>Em troca, estas cenas fazem o que uma fotografia não faz: mexem-se,
 * escalam sem perder nitidez em qualquer ecrã, e pesam poucos kilobytes — o
 * que numa ligação móvel angolana é a diferença entre a página abrir e o
 * cliente desistir.
 *
 * <p>O gerador não anda, e por isso não desliza: vibra e deita fumo, que é o
 * que um grupo electrogéneo faz quando está a trabalhar.
 */

const AMBAR = '#F5A800';
const AMBAR_ESCURO = '#c98700';
const PRETO = '#141416';
const VIDRO = '#6ba4c8';

/** Roda com raios, que giram enquanto a máquina anda. */
function Roda({
  cx,
  cy,
  r,
  segundos = 0.9,
}: {
  cx: number;
  cy: number;
  r: number;
  segundos?: number;
}) {
  return (
    <g>
      <circle cx={cx} cy={cy} r={r} fill="#1c1c1f" />
      <circle cx={cx} cy={cy} r={r * 0.55} fill="#3f3f46" />
      <g>
        {[0, 60, 120].map((a) => (
          <rect
            key={a}
            x={cx - r * 0.5}
            y={cy - 1.6}
            width={r}
            height={3.2}
            rx={1.6}
            fill="#71717a"
            transform={`rotate(${a} ${cx} ${cy})`}
          />
        ))}
        <animateTransform
          attributeName="transform"
          type="rotate"
          from={`0 ${cx} ${cy}`}
          to={`360 ${cx} ${cy}`}
          dur={`${segundos}s`}
          repeatCount="indefinite"
        />
      </g>
      <circle cx={cx} cy={cy} r={r * 0.18} fill="#a1a1aa" />
    </g>
  );
}

/** A estrada que passa por baixo — é isto que dá a sensação de avanço. */
function Estrada({ largura, y }: { largura: number; y: number }) {
  return (
    <g>
      <rect x="0" y={y} width={largura} height="26" fill="#232326" />
      <rect x="0" y={y} width={largura} height="2" fill="#3f3f46" />
      <g>
        {Array.from({ length: 9 }, (_, i) => (
          <rect key={i} x={i * 72} y={y + 12} width="40" height="3.5" rx="1.75" fill={AMBAR} />
        ))}
        {/* Desloca-se exactamente um período, para o ciclo não dar saltos. */}
        <animateTransform
          attributeName="transform"
          type="translate"
          from="0 0"
          to="-72 0"
          dur="0.85s"
          repeatCount="indefinite"
        />
      </g>
    </g>
  );
}

/** Poeira levantada pelo rodado. */
function Poeira({ x, y }: { x: number; y: number }) {
  return (
    <g opacity="0.5">
      {[0, 0.5, 1].map((atraso, i) => (
        <circle key={i} cx={x} cy={y} r="4" fill="#a1a1aa">
          <animate
            attributeName="cx"
            from={x}
            to={x - 70}
            dur="1.5s"
            begin={`${atraso}s`}
            repeatCount="indefinite"
          />
          <animate
            attributeName="r"
            from="2"
            to="13"
            dur="1.5s"
            begin={`${atraso}s`}
            repeatCount="indefinite"
          />
          <animate
            attributeName="opacity"
            from="0.55"
            to="0"
            dur="1.5s"
            begin={`${atraso}s`}
            repeatCount="indefinite"
          />
        </circle>
      ))}
    </g>
  );
}

/** Camião pesado em andamento. */
export function CenaCamiao({ altura = 210 }: { altura?: number }) {
  return (
    <svg viewBox="0 0 620 210" style={{ width: '100%', height: altura }} role="img"
         aria-label="Camião pesado em andamento">
      <Estrada largura={620} y={162} />
      <Poeira x={140} y={172} />
      <g>
        {/* Suspensão: a caixa oscila ligeiramente, como na estrada. */}
        <animateTransform attributeName="transform" type="translate"
          values="0 0; 0 -1.6; 0 0; 0 1.2; 0 0" dur="0.7s" repeatCount="indefinite" />
        {/* reboque */}
        <path d="M232 52h306v86H232z" fill={AMBAR} />
        <path d="M232 52h306v10H232z" fill={AMBAR_ESCURO} />
        <path d="M240 70h290v58H240z" fill="#141416" opacity=".12" />
        {/* cabine */}
        <path d="M96 74h96v64H96a8 8 0 0 1-8-8V82a8 8 0 0 1 8-8Z" fill={AMBAR} />
        <path d="M104 82h56v30h-56z" fill={VIDRO} />
        <path d="M104 82h56v10h-56z" fill="#8fc3e0" opacity=".7" />
        {/* escape */}
        <path d="M196 40h9v36h-9z" fill="#52525b" />
        {/* chassi */}
        <path d="M88 138h452v12H88z" fill="#2d2d2f" />
        {/* faróis acesos */}
        <circle cx="94" cy="118" r="5" fill="#fff8e1" />
      </g>
      <Roda cx={132} cy={155} r={22} />
      <Roda cx={396} cy={155} r={22} />
      <Roda cx={470} cy={155} r={22} />
    </svg>
  );
}

/** Retroescavadora a trabalhar: desloca-se e mexe a lança. */
export function CenaRetroescavadora({ altura = 210 }: { altura?: number }) {
  return (
    <svg viewBox="0 0 620 210" style={{ width: '100%', height: altura }} role="img"
         aria-label="Retroescavadora em obra">
      <Estrada largura={620} y={162} />
      <Poeira x={200} y={170} />
      <g>
        <animateTransform attributeName="transform" type="translate"
          values="0 0; 0 -2; 0 0; 0 1.4; 0 0" dur="0.9s" repeatCount="indefinite" />
        {/* corpo e cabine */}
        <path d="M232 66h104a8 8 0 0 1 8 8v64H232z" fill={AMBAR} />
        <path d="M244 76h74v34h-74z" fill={VIDRO} />
        <path d="M196 108h176v34H196z" fill={AMBAR_ESCURO} />
        {/* braço traseiro, que sobe e desce */}
        <g>
          <animateTransform attributeName="transform" type="rotate"
            values="0 350 96; -13 350 96; 0 350 96; 6 350 96; 0 350 96"
            dur="3.4s" repeatCount="indefinite" />
          <path d="M344 84l74 40 28 66-19 9-26-62-63-36z" fill={AMBAR} />
          <path d="M424 186h52v18c0 11-9 20-20 20h-32z" fill="#52525b" />
        </g>
        {/* lança e balde frontais */}
        <g>
          <animateTransform attributeName="transform" type="rotate"
            values="0 196 122; 9 196 122; 0 196 122" dur="4s" repeatCount="indefinite" />
          <path d="M196 116 96 146v13l100-27z" fill={AMBAR} />
          <path d="M62 142h44v40H70a8 8 0 0 1-8-8z" fill="#52525b" />
        </g>
      </g>
      <Roda cx={248} cy={156} r={26} segundos={1.5} />
      <Roda cx={352} cy={158} r={20} segundos={1.2} />
    </svg>
  );
}

/** Gerador a trabalhar. Não anda: vibra e deita fumo. */
export function CenaGerador({ altura = 210 }: { altura?: number }) {
  return (
    <svg viewBox="0 0 620 210" style={{ width: '100%', height: altura }} role="img"
         aria-label="Gerador diesel em funcionamento">
      {/* Base de betão: um gerador assenta, não roda. */}
      <rect x="0" y="176" width="620" height="16" fill="#232326" />
      <rect x="0" y="176" width="620" height="2" fill="#3f3f46" />

      {/* fumo do escape */}
      <g opacity="0.42">
        {[0, 0.8, 1.6].map((atraso, i) => (
          <circle key={i} cx="452" cy="46" r="6" fill="#a1a1aa">
            <animate attributeName="cy" from="46" to="-6" dur="2.4s"
              begin={`${atraso}s`} repeatCount="indefinite" />
            <animate attributeName="r" from="4" to="17" dur="2.4s"
              begin={`${atraso}s`} repeatCount="indefinite" />
            <animate attributeName="opacity" from="0.5" to="0" dur="2.4s"
              begin={`${atraso}s`} repeatCount="indefinite" />
          </circle>
        ))}
      </g>

      <g>
        {/* Vibração: pequena e rápida, como um motor a 1500 rpm. */}
        <animateTransform attributeName="transform" type="translate"
          values="0 0; 0.7 -0.7; 0 0; -0.7 0.7; 0 0" dur="0.12s" repeatCount="indefinite" />
        <path d="M446 50h14v34h-14z" fill="#52525b" />
        <path d="M150 84h312a8 8 0 0 1 8 8v76H150a8 8 0 0 1-8-8V92a8 8 0 0 1 8-8Z" fill={AMBAR} />
        <path d="M142 84h320v11H142z" fill={AMBAR_ESCURO} />
        {/* grelha de ventilação */}
        <g fill={PRETO} opacity=".34">
          {[0, 1, 2, 3, 4].map((i) => (
            <rect key={i} x="168" y={108 + i * 13} width="104" height="7" rx="3.5" />
          ))}
        </g>
        {/* painel de controlo, com sinal a piscar */}
        <rect x="300" y="106" width="120" height="52" rx="3" fill={PRETO} opacity=".82" />
        <circle cx="318" cy="122" r="5" fill="#16a34a">
          <animate attributeName="opacity" values="1;0.25;1" dur="1.6s" repeatCount="indefinite" />
        </circle>
        <rect x="332" y="117" width="72" height="5" rx="2.5" fill="#52525b" />
        <rect x="332" y="130" width="52" height="5" rx="2.5" fill="#52525b" />
        <rect x="332" y="143" width="62" height="5" rx="2.5" fill="#3f3f46" />
      </g>
      {/* pés */}
      <rect x="166" y="168" width="36" height="10" fill="#2d2d2f" />
      <rect x="408" y="168" width="36" height="10" fill="#2d2d2f" />
    </svg>
  );
}
