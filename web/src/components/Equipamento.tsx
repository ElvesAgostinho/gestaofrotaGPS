/**
 * Silhuetas de equipamento pesado.
 *
 * <p>Os ícones de linha genéricos — um camião de três traços — não dizem a um
 * gestor de frota se está a olhar para uma retroescavadora ou para um gerador.
 * Estas são silhuetas cheias, desenhadas de lado, com as proporções da máquina
 * real: reconhecem-se a 24 px numa lista.
 *
 * <p>São vetores nossos, não fotografias. Fotografia de equipamento pertence a
 * quem a tirou; a do parque do cliente entra pelo ecrã do ativo, onde ele
 * carrega as suas — e é essa que interessa, porque é a máquina dele.
 */

interface Props {
  size?: number;
  color?: string;
  title?: string;
}

const BASE = { fill: 'currentColor', shapeRendering: 'geometricPrecision' as const };

function Svg({
  size = 24,
  color,
  title,
  children,
}: Props & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      role={title ? 'img' : 'presentation'}
      aria-label={title}
      style={{ color, display: 'block' }}
    >
      {title && <title>{title}</title>}
      {children}
    </svg>
  );
}

/** Retroescavadora: lança à frente, braço e concha atrás. */
export function IconeRetroescavadora(p: Props) {
  return (
    <Svg {...p} title={p.title ?? 'Retroescavadora'}>
      <g {...BASE}>
        {/* cabine */}
        <path d="M24 18h11a2 2 0 0 1 2 2v12H22V20a2 2 0 0 1 2-2Z" />
        {/* chassi */}
        <path d="M17 32h25v8H17z" />
        {/* braço da retro, atrás */}
        <path d="M40 21l10 6 4 12-3 1.6-4.2-11.4-8.4-5.2z" />
        {/* concha traseira */}
        <path d="M50 40h8v3.5c0 2.6-2 4.5-4.6 4.5H50z" />
        {/* lança e balde frontais */}
        <path d="M17 30 6 36v2.6l11-5.2z" />
        <path d="M3 36h7v9H3a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1Z" />
        {/* rodas: a traseira é maior, como na máquina real */}
        <circle cx="21" cy="47" r="7" />
        <circle cx="45" cy="48" r="6" />
      </g>
    </Svg>
  );
}

/** Camião basculante: cabine curta e caixa inclinável. */
export function IconeCamiao(p: Props) {
  return (
    <Svg {...p} title={p.title ?? 'Camião'}>
      <g {...BASE}>
        {/* caixa */}
        <path d="M26 18h33v18H26z" />
        {/* cabine */}
        <path d="M9 24h13v12H9a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z" />
        {/* chassi */}
        <path d="M5 36h54v6H5z" />
        <circle cx="16" cy="47" r="6" />
        <circle cx="42" cy="47" r="6" />
        <circle cx="54" cy="47" r="6" />
      </g>
    </Svg>
  );
}

/** Gerador: contentor fechado com grelha de ventilação e escape. */
export function IconeGerador(p: Props) {
  return (
    <Svg {...p} title={p.title ?? 'Gerador'}>
      <g {...BASE}>
        {/* escape */}
        <path d="M45 10h5v9h-5z" />
        {/* contentor */}
        <path d="M8 19h48a2 2 0 0 1 2 2v22a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V21a2 2 0 0 1 2-2Z" />
        {/* base */}
        <path d="M4 45h56v5H4z" />
        {/* pés */}
        <path d="M10 50h7v4h-7zM47 50h7v4h-7z" />
      </g>
      {/* grelha de ventilação, aberta no corpo */}
      <g fill="#fff" opacity="0.92">
        <path d="M12 25h16v2.6H12zM12 30h16v2.6H12zM12 35h16v2.6H12z" />
        <circle cx="45" cy="32" r="7" />
      </g>
      <g {...BASE}>
        <circle cx="45" cy="32" r="3.4" />
      </g>
    </Svg>
  );
}

/** Empilhadora: mastro à frente e contrapeso atrás. */
export function IconeEmpilhadora(p: Props) {
  return (
    <Svg {...p} title={p.title ?? 'Empilhadora'}>
      <g {...BASE}>
        <path d="M22 16h4v28h-4z" />
        <path d="M26 40h14v4H26z" />
        <path d="M30 20h16a3 3 0 0 1 3 3v17H30z" />
        <path d="M49 26h7a3 3 0 0 1 3 3v11h-10z" />
        <circle cx="35" cy="48" r="6" />
        <circle cx="53" cy="48" r="5" />
      </g>
    </Svg>
  );
}

/** Ligeiro, para as frotas mistas. */
export function IconeLigeiro(p: Props) {
  return (
    <Svg {...p} title={p.title ?? 'Ligeiro'}>
      <g {...BASE}>
        <path d="M14 34l5-11a4 4 0 0 1 3.7-2.5h18.6A4 4 0 0 1 45 23l5 11z" />
        <path d="M8 34h48a3 3 0 0 1 3 3v7H5v-7a3 3 0 0 1 3-3Z" />
        <circle cx="18" cy="46" r="6" />
        <circle cx="46" cy="46" r="6" />
      </g>
    </Svg>
  );
}

/** Autocarro de passageiros: a faixa de janelas é o que o distingue à distância. */
export function IconeAutocarro({ size, color, title }: Props) {
  return (
    <Svg size={size} color={color} title={title ?? 'Autocarro'}>
      <path
        {...BASE}
        d="M3 6.5C3 5.7 3.7 5 4.5 5h15c.8 0 1.5.7 1.5 1.5v8.2H3V6.5Zm1.6 1.1v3.1h4.1V7.6H4.6Zm5.4 0v3.1h4V7.6h-4Zm5.3 0v3.1h4.1V7.6h-4.1ZM3 15.6h18v1.9h-1.1a2.2 2.2 0 1 0-4.4 0H8.5a2.2 2.2 0 1 0-4.4 0H3v-1.9Z"
      />
      <circle {...BASE} cx="6.3" cy="17.8" r="1.5" />
      <circle {...BASE} cx="17.7" cy="17.8" r="1.5" />
    </Svg>
  );
}

/** Reboque ou alfaia: sem cabina, com barra de tração. */
export function IconeReboque({ size, color, title }: Props) {
  return (
    <Svg size={size} color={color} title={title ?? 'Reboque'}>
      <path
        {...BASE}
        d="M2 12.6h3.4v1.3H2v-1.3Zm1.1-.7a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6ZM7 7.5h13.4c.9 0 1.6.7 1.6 1.6v6.2H7V7.5Zm-1.2 8.5H22v1.4h-1.5a2 2 0 1 0-4 0h-2.8a2 2 0 1 0-4 0H5.8v-1.4Z"
      />
      <circle {...BASE} cx="12.7" cy="18" r="1.4" />
      <circle {...BASE} cx="18.5" cy="18" r="1.4" />
    </Svg>
  );
}

/**
 * Escolhe a silhueta a partir do que a máquina é.
 *
 * <p>Adivinha pelo nome do tipo de ativo, que é texto livre escrito pelo
 * cliente. Quando não reconhece, devolve o camião — é o que uma frota tem
 * mais.
 */
export function IconeAtivo({
  tipo,
  familia,
  ...props
}: Props & { tipo?: string | null; familia?: string | null }) {
  // A família decidida pelo servidor manda: é a mesma que escolhe o plano e a
  // inspeção, e assim o ícone nunca discorda do resto do sistema.
  switch (familia) {
    case 'GENERATOR':
      return <IconeGerador {...props} />;
    case 'BUS':
      return <IconeAutocarro {...props} />;
    case 'FORKLIFT':
      return <IconeEmpilhadora {...props} />;
    case 'IMPLEMENT':
      return <IconeReboque {...props} />;
    case 'LIGHT_VEHICLE':
      return <IconeLigeiro {...props} />;
    case 'TRUCK_HEAVY':
      return <IconeCamiao {...props} />;
    case 'RETROESCAVADORA':
      return <IconeRetroescavadora {...props} />;
    default:
      break;
  }
  // Sem família (listas antigas em cache), adivinha-se pelo nome do tipo.
  const t = (tipo ?? '').toLowerCase();
  if (/(gerador|generator|grupo eletrog|electrog)/.test(t)) {
    return <IconeGerador {...props} />;
  }
  if (/(autocarro|onibus|ónibus|bus|minibus|passageir)/.test(t)) {
    return <IconeAutocarro {...props} />;
  }
  if (/(empilhad|forklift)/.test(t)) {
    return <IconeEmpilhadora {...props} />;
  }
  if (/(alfaia|implemento|reboque|atrelado|semirreboque|cisterna)/.test(t)) {
    return <IconeReboque {...props} />;
  }
  if (/(retro|escavad|backhoe|pá carreg|carregadora|bulldoz|trator|tractor)/.test(t)) {
    return <IconeRetroescavadora {...props} />;
  }
  if (/(ligeiro|carro|autom|pick|jipe|suv|carrinha|van|viatura leve)/.test(t)) {
    return <IconeLigeiro {...props} />;
  }
  return <IconeCamiao {...props} />;
}
