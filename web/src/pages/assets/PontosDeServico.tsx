/**
 * Diagrama dos pontos de serviço, por família de equipamento.
 *
 * <p>O manual do fabricante traz sempre um desenho da máquina com os pontos de
 * lubrificação numerados. É a peça que falta a quase todos os sistemas de
 * manutenção: o técnico lê «lubrificar os pinos da lança» e fica sem saber
 * onde estão, quantos são e quanta massa levam.
 *
 * <p>Isto é o mesmo desenho, mas vivo: carrega-se no número e a ficha do ponto
 * abre ao lado — o que fazer, com que produto, de quantas em quantas horas.
 *
 * <p>São vetores nossos, adaptados a cada família. Não são o desenho do manual
 * de nenhum fabricante — esses pertencem a quem os desenhou. Se quiser a página
 * original do manual, carregue-a como documento do ativo.
 */
import { Alert, Badge, Group, Stack, Text } from '@mantine/core';
import { IconDroplet, IconInfoCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { BarraFicha, BotaoFichaDoPosto, CaixaFicha, FerramentasEMateriais, InspecaoDiariaFicha } from './FichaTecnica';

const AMBAR = '#F5A800';
const PRETO = '#141416';

export interface PontoServico {
  numero: number;
  nome: string;
  /** Onde o ponto fica no desenho, em coordenadas do viewBox. */
  x: number;
  y: number;
  accao: string;
  produto?: string;
  intervaloHoras?: number;
  quantidade?: string;
  /** Um ponto que, se falhar, para a máquina ou magoa alguém. */
  critico?: boolean;
}

interface Familia {
  nome: string;
  desenho: (destacado: number | null) => JSX.Element;
  /** A janela do desenho, sem margens mortas à volta da máquina. */
  vista: string;
  pontos: PontoServico[];
  nota?: string;
}

// ==== Desenhos =============================================================

/*
  O traço é o do manual: linha preta grossa, chapa em âmbar, vidro claro,
  metal cinzento e o solo hachurado por baixo. Tudo o que está aqui dentro
  herda o contorno preto do <g> que envolve o desenho, por isso cada peça só
  diz a cor com que é preenchida. Um desenho assim lê-se numa fotocópia e
  numa folha plastificada cheia de gordura — que é onde vai acabar.
*/
const METAL = '#E4E4E7';
const CHAPA = AMBAR;
const SOMBRA = '#C98700';
const VIDRO = '#CFE6F5';
const BORRACHA = '#3F3F46';

/** O chão, hachurado como nos cortes técnicos. */
function Solo({ y = 348 }: { y?: number }) {
  const riscos = [];
  for (let x = 30; x <= 540; x += 18) {
    riscos.push(<line key={x} x1={x} y1={y} x2={x - 13} y2={y + 11} strokeWidth="1" />);
  }
  return (
    <g>
      <line x1="20" y1={y} x2="546" y2={y} strokeWidth="2.5" />
      <g opacity=".5">{riscos}</g>
    </g>
  );
}

/** Um pneu com piso e raios, em vez de um círculo preto. */
function Roda({ cx, cy, r }: { cx: number; cy: number; r: number }) {
  const raios = [0, 45, 90, 135].map((g) => {
    const a = (g * Math.PI) / 180;
    const rr = r * 0.45;
    return (
      <line
        key={g}
        x1={cx - Math.cos(a) * rr}
        y1={cy - Math.sin(a) * rr}
        x2={cx + Math.cos(a) * rr}
        y2={cy + Math.sin(a) * rr}
        strokeWidth="1.2"
      />
    );
  });
  const piso = [];
  for (let g = 0; g < 360; g += 20) {
    const a = (g * Math.PI) / 180;
    piso.push(
      <line
        key={g}
        x1={cx + Math.cos(a) * (r - 1)}
        y1={cy + Math.sin(a) * (r - 1)}
        x2={cx + Math.cos(a) * (r - r * 0.22)}
        y2={cy + Math.sin(a) * (r - r * 0.22)}
        stroke="#fff"
        strokeWidth="1.6"
        opacity=".55"
      />,
    );
  }
  return (
    <g>
      <circle cx={cx} cy={cy} r={r} fill={BORRACHA} />
      {piso}
      <circle cx={cx} cy={cy} r={r * 0.55} fill={METAL} />
      <circle cx={cx} cy={cy} r={r * 0.16} fill="#fff" />
      {raios}
    </g>
  );
}

/** Uma haste de cilindro hidráulico: o tubo, a haste e o olhal. */
function Cilindro({ x1, y1, x2, y2 }: { x1: number; y1: number; x2: number; y2: number }) {
  const mx = x1 + (x2 - x1) * 0.55;
  const my = y1 + (y2 - y1) * 0.55;
  return (
    <g>
      <line x1={x1} y1={y1} x2={mx} y2={my} stroke={PRETO} strokeWidth="9" strokeLinecap="round" />
      <line x1={x1} y1={y1} x2={mx} y2={my} stroke={METAL} strokeWidth="6" strokeLinecap="round" />
      <line x1={mx} y1={my} x2={x2} y2={y2} stroke={PRETO} strokeWidth="4.5" strokeLinecap="round" />
      <circle cx={x2} cy={y2} r="4" fill="#fff" />
    </g>
  );
}

/** Corpo da retroescavadora, visto de lado. */
function DesenhoRetroescavadora() {
  return (
    <g>
      <Solo />
      {/* chassi */}
      <path d="M150 250h250v40H150z" fill={METAL} />
      {/* corpo e cabine */}
      <path d="M215 150h110a10 10 0 0 1 10 10v90H215z" fill={CHAPA} />
      <path d="M228 162h78v46h-78z" fill={VIDRO} />
      <line x1="267" y1="162" x2="267" y2="208" strokeWidth="1.2" />
      <path d="M186 208h180v44H186z" fill={SOMBRA} />
      {/* capot e filtro de ar */}
      <path d="M186 208h58v44h-58z" fill={CHAPA} />
      <rect x="192" y="170" width="24" height="16" rx="3" fill={METAL} />
      {/* braço traseiro */}
      <path d="M338 168l86 46 32 78-22 10-30-72-73-42z" fill={CHAPA} />
      <Cilindro x1={352} y1={196} x2={412} y2={230} />
      <path d="M430 300h58v22c0 13-10 24-24 24h-34z" fill={METAL} />
      <g opacity=".6">
        <line x1="444" y1="324" x2="444" y2="344" strokeWidth="1.2" />
        <line x1="462" y1="322" x2="462" y2="344" strokeWidth="1.2" />
      </g>
      {/* lança e balde frontais */}
      <path d="M186 222 78 258v16l108-30z" fill={CHAPA} />
      <Cilindro x1={196} y1={246} x2={120} y2={262} />
      <path d="M42 254h48v46H50a8 8 0 0 1-8-8z" fill={METAL} />
      <g opacity=".6">
        <line x1="42" y1="296" x2="90" y2="296" strokeWidth="1.2" />
      </g>
      {/* rodas */}
      <Roda cx={232} cy={300} r={46} />
      <Roda cx={372} cy={304} r={34} />
    </g>
  );
}

/** Camião pesado, visto de lado. */
function DesenhoCamiao() {
  return (
    <g>
      <Solo y={330} />
      {/* caixa de carga */}
      <path d="M232 120h268v120H232z" fill={CHAPA} />
      <path d="M232 120h268v14H232z" fill={SOMBRA} />
      <g opacity=".5">
        {[280, 328, 376, 424, 472].map((x) => (
          <line key={x} x1={x} y1="134" x2={x} y2="240" strokeWidth="1.2" />
        ))}
      </g>
      {/* cabina */}
      <path d="M96 160h110v80H96a10 10 0 0 1-10-10v-60a10 10 0 0 1 10-10Z" fill={CHAPA} />
      <path d="M106 172h70v40h-70z" fill={VIDRO} />
      <rect x="92" y="214" width="16" height="10" rx="2" fill="#FFF8E1" />
      <path d="M204 96h12v58h-12z" fill={METAL} />
      {/* chassi e quinta roda */}
      <path d="M86 240h414v22H86z" fill={METAL} />
      <path d="M222 228h34v14h-34z" fill={SOMBRA} />
      {/* depósito */}
      <rect x="240" y="262" width="52" height="22" rx="10" fill={METAL} />
      <Roda cx={146} cy={286} r={38} />
      <Roda cx={372} cy={286} r={38} />
      <Roda cx={446} cy={286} r={38} />
    </g>
  );
}

/** Ligeiro de passageiros, visto de lado. */
function DesenhoLigeiro() {
  return (
    <g>
      <Solo y={320} />
      {/* tejadilho e vidros */}
      <path d="M186 138h178a16 16 0 0 1 13 7l34 51H150l22-51a16 16 0 0 1 14-7Z" fill={CHAPA} />
      <path d="M198 152h74v42h-92z" fill={VIDRO} />
      <path d="M286 152h66a8 8 0 0 1 6 3l26 39h-98z" fill={VIDRO} />
      <line x1="278" y1="150" x2="278" y2="196" strokeWidth="1.5" />
      {/* corpo */}
      <path d="M110 196h348a14 14 0 0 1 14 14v50H96v-50a14 14 0 0 1 14-14Z" fill={CHAPA} />
      <path d="M96 236h376v24H96z" fill={SOMBRA} />
      {/* portas e puxadores */}
      <g opacity=".65">
        <line x1="212" y1="198" x2="212" y2="252" strokeWidth="1.4" />
        <line x1="292" y1="198" x2="292" y2="252" strokeWidth="1.4" />
        <line x1="382" y1="198" x2="382" y2="252" strokeWidth="1.4" />
      </g>
      <rect x="252" y="214" width="16" height="5" rx="2" fill={METAL} />
      {/* faróis */}
      <rect x="96" y="206" width="20" height="14" rx="3" fill="#FFF8E1" />
      <rect x="452" y="206" width="20" height="14" rx="3" fill="#FCA5A5" />
      <Roda cx={168} cy={272} r={40} />
      <Roda cx={398} cy={272} r={40} />
    </g>
  );
}

/** Gerador em contentor, visto de lado. */
function DesenhoGerador() {
  return (
    <g>
      <Solo y={338} />
      {/* escape */}
      <path d="M418 88h22v54h-22z" fill={METAL} />
      <g opacity=".5">
        <line x1="418" y1="104" x2="440" y2="104" strokeWidth="1.2" />
        <line x1="418" y1="120" x2="440" y2="120" strokeWidth="1.2" />
      </g>
      {/* carcaça */}
      <path
        d="M110 142h330a12 12 0 0 1 12 12v128a12 12 0 0 1-12 12H110a12 12 0 0 1-12-12V154a12 12 0 0 1 12-12Z"
        fill={CHAPA}
      />
      <path d="M98 142h354v16H98z" fill={SOMBRA} />
      {/* grelhas de ventilação */}
      <g fill={METAL}>
        {[0, 1, 2, 3].map((i) => (
          <rect key={i} x="130" y={186 + i * 22} width="110" height="11" rx="5" />
        ))}
      </g>
      {/* painel de comando */}
      <rect x="290" y="182" width="130" height="76" rx="4" fill="#fff" />
      <rect x="298" y="190" width="114" height="26" rx="2" fill={VIDRO} />
      <circle cx="312" cy="234" r="7" fill="#16A34A" />
      <circle cx="336" cy="234" r="7" fill="#FCA5A5" />
      <rect x="356" y="228" width="52" height="12" rx="3" fill={METAL} />
      {/* base e calços */}
      <path d="M88 294h374v18H88z" fill={METAL} />
      <rect x="126" y="312" width="42" height="18" fill={BORRACHA} />
      <rect x="382" y="312" width="42" height="18" fill={BORRACHA} />
    </g>
  );
}

// ==== Pontos por família ===================================================

/**
 * Os pontos e o que se faz em cada um.
 *
 * <p>Os da retroescavadora são os cinco do documento do fabricante — pinos da
 * lança, pinos da concha, articulações, cilindros hidráulicos e eixo
 * dianteiro — mais os de nível que se verificam na mesma passagem.
 */
const FAMILIAS: Record<string, Familia> = {
  RETROESCAVADORA: {
    nome: 'Retroescavadora',
    desenho: DesenhoRetroescavadora,
    vista: '30 130 480 242',
    nota:
      'Aplicar massa até sair massa limpa pela folga e limpar o excesso: a massa que fica ' +
      'fora atrai areia e transforma o ponto lubrificado num ponto abrasivo.',
    pontos: [
      {
        numero: 1,
        nome: 'Eixo dianteiro',
        x: 232,
        y: 300,
        accao: 'Lubrificar os copos do eixo e dos cubos.',
        produto: 'Massa EP2',
        intervaloHoras: 50,
        quantidade: '4 a 6 bombadas por copo',
      },
      {
        numero: 2,
        nome: 'Pinos da lança',
        x: 150,
        y: 240,
        accao: 'Lubrificar todos os pinos da lança frontal.',
        produto: 'Massa EP2',
        intervaloHoras: 50,
        quantidade: '4 bombadas por pino',
        critico: true,
      },
      {
        numero: 3,
        nome: 'Pinos da concha',
        x: 64,
        y: 272,
        accao: 'Lubrificar os pinos e as bielas da concha.',
        produto: 'Massa EP2',
        intervaloHoras: 50,
        quantidade: '4 bombadas por pino',
        critico: true,
      },
      {
        numero: 4,
        nome: 'Articulações do braço traseiro',
        x: 392,
        y: 214,
        accao: 'Lubrificar as articulações do braço e do balde traseiro.',
        produto: 'Massa EP2',
        intervaloHoras: 50,
        quantidade: '4 bombadas por ponto',
        critico: true,
      },
      {
        numero: 5,
        nome: 'Cilindros hidráulicos',
        x: 452,
        y: 316,
        accao: 'Lubrificar as bases e as hastes dos cilindros.',
        produto: 'Massa EP2',
        intervaloHoras: 50,
        quantidade: '3 bombadas por base',
      },
      {
        numero: 6,
        nome: 'Nível do óleo do motor',
        x: 276,
        y: 196,
        accao: 'Verificar na vareta, com a máquina nivelada e o motor frio.',
        intervaloHoras: 10,
        critico: true,
      },
      {
        numero: 7,
        nome: 'Nível do óleo hidráulico',
        x: 336,
        y: 232,
        accao: 'Verificar no visor com os cilindros recolhidos.',
        intervaloHoras: 10,
        critico: true,
      },
      {
        numero: 8,
        nome: 'Filtro de ar',
        x: 200,
        y: 178,
        accao: 'Verificar o indicador de restrição; limpar ou substituir.',
        intervaloHoras: 250,
      },
    ],
  },
  CAMIAO: {
    nome: 'Camião pesado',
    desenho: DesenhoCamiao,
    vista: '70 84 452 274',
    pontos: [
      {
        numero: 1,
        nome: 'Nível do óleo do motor',
        x: 150,
        y: 200,
        accao: 'Verificar na vareta antes do arranque, com a viatura nivelada.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 2,
        nome: 'Líquido de arrefecimento',
        x: 112,
        y: 176,
        accao: 'Verificar no depósito de expansão, com o motor frio.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 3,
        nome: 'Pinos de mola e jumelos',
        x: 250,
        y: 264,
        accao: 'Lubrificar os pinos das molas e os jumelos.',
        produto: 'Massa de lítio EP2',
        intervaloHoras: 250,
        quantidade: '3 bombadas por ponto',
      },
      {
        numero: 4,
        nome: 'Veio de transmissão',
        x: 300,
        y: 252,
        accao: 'Lubrificar as cruzetas e a junta deslizante.',
        produto: 'Massa de lítio EP2',
        intervaloHoras: 250,
        critico: true,
      },
      {
        numero: 5,
        nome: 'Travões e tambores',
        x: 372,
        y: 286,
        accao: 'Medir a espessura das pastilhas e registar na ordem.',
        intervaloHoras: 500,
        critico: true,
      },
      {
        numero: 6,
        nome: 'Pressão e piso dos pneus',
        x: 446,
        y: 286,
        accao: 'Medir a pressão a frio e a profundidade do piso em cada posição.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 7,
        nome: 'Quinta roda',
        x: 236,
        y: 236,
        accao: 'Limpar e lubrificar o prato; verificar a folga do travamento.',
        produto: 'Massa de lítio EP2',
        intervaloHoras: 250,
      },
    ],
  },
  LIGEIRO: {
    nome: 'Ligeiro',
    desenho: DesenhoLigeiro,
    vista: '84 124 406 222',
    nota:
      'Um ligeiro não tem pontos de massa: os rolamentos e as juntas vêm selados de ' +
      'fábrica. O que se faz é verificar níveis, travagem e pneus — e é aí que estão as ' +
      'avarias que deixam alguém na estrada.',
    pontos: [
      {
        numero: 1,
        nome: 'Nível do óleo do motor',
        x: 168,
        y: 214,
        accao: 'Verificar na vareta com o carro nivelado e o motor frio há pelo menos cinco minutos.',
        intervaloHoras: 168,
        critico: true,
      },
      {
        numero: 2,
        nome: 'Líquido de arrefecimento',
        x: 124,
        y: 194,
        accao: 'Verificar entre as marcas do depósito de expansão, com o motor frio.',
        intervaloHoras: 168,
        critico: true,
      },
      {
        numero: 3,
        nome: 'Correia de acessórios',
        x: 206,
        y: 226,
        accao: 'Inspecionar fendas, desfiamento e tensão. Substituir aos 60.000 km.',
        intervaloHoras: 2000,
      },
      {
        numero: 4,
        nome: 'Bateria',
        x: 250,
        y: 214,
        accao: 'Medir a tensão em repouso (12,4 a 12,9 V) e limpar os terminais.',
        intervaloHoras: 720,
        critico: true,
      },
      {
        numero: 5,
        nome: 'Travões dianteiros',
        x: 168,
        y: 272,
        accao: 'Medir a espessura das pastilhas e do disco. Registar os valores na ordem.',
        intervaloHoras: 1000,
        critico: true,
      },
      {
        numero: 6,
        nome: 'Travões traseiros',
        x: 398,
        y: 272,
        accao: 'Medir pastilhas ou maxilas e verificar o travão de mão.',
        intervaloHoras: 1000,
        critico: true,
      },
      {
        numero: 7,
        nome: 'Pneus e pressões',
        x: 300,
        y: 292,
        accao: 'Medir a pressão a frio e o piso nas quatro posições. Mínimo legal: 1,6 mm.',
        intervaloHoras: 168,
        critico: true,
      },
      {
        numero: 8,
        nome: 'Filtro de habitáculo',
        x: 240,
        y: 176,
        accao: 'Substituir. Em Angola, com a poeira, dura menos do que o manual indica.',
        intervaloHoras: 2000,
      },
      {
        numero: 9,
        nome: 'Escovas e água do limpa-vidros',
        x: 196,
        y: 152,
        accao: 'Verificar o estado da borracha e atestar o depósito.',
        intervaloHoras: 168,
      },
      {
        numero: 10,
        nome: 'Amortecedores',
        x: 430,
        y: 248,
        accao: 'Procurar fugas de óleo e folgas nos apoios.',
        intervaloHoras: 2000,
      },
    ],
  },
  GERADOR: {
    nome: 'Gerador diesel',
    desenho: DesenhoGerador,
    vista: '76 78 402 286',
    nota:
      'O gerador não se lubrifica como uma máquina móvel: o que se verifica são níveis, ' +
      'estanquidade e o estado eléctrico. A prova de carga é que diz se ele arranca mesmo.',
    pontos: [
      {
        numero: 1,
        nome: 'Nível do óleo do motor',
        x: 176,
        y: 196,
        accao: 'Verificar na vareta com o grupo parado e nivelado.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 2,
        nome: 'Líquido de arrefecimento',
        x: 176,
        y: 240,
        accao: 'Verificar no radiador e confirmar a protecção anticongelante.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 3,
        nome: 'Depósito de combustível',
        x: 132,
        y: 282,
        accao: 'Verificar o nível e drenar a água do separador.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 4,
        nome: 'Bateria de arranque',
        x: 260,
        y: 272,
        accao: 'Medir a tensão em repouso e limpar os terminais.',
        intervaloHoras: 168,
        critico: true,
      },
      {
        numero: 5,
        nome: 'Painel de comando',
        x: 352,
        y: 220,
        accao: 'Confirmar que está em automático e sem alarmes.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 6,
        nome: 'Escape e ventilação',
        x: 428,
        y: 108,
        accao: 'Confirmar que a saída de gases e as grelhas estão desobstruídas.',
        intervaloHoras: 24,
        critico: true,
      },
      {
        numero: 7,
        nome: 'Alternador',
        x: 404,
        y: 268,
        accao: 'Medir a resistência de isolamento e inspecionar as ligações.',
        intervaloHoras: 8760,
      },
    ],
  },
};

/** Escolhe a família a partir do tipo de ativo, que é texto livre do cliente. */
function familiaDe(tipo?: string | null): Familia {
  const t = (tipo ?? '').toLowerCase();
  if (/(gerador|generator|grupo eletrog|electrog)/.test(t)) return FAMILIAS.GERADOR;
  if (/(retro|escavad|backhoe|pá carreg|carregadora|bulldoz|trator|tractor|máquina|maquina|empilhad)/.test(t)) {
    return FAMILIAS.RETROESCAVADORA;
  }
  // O ligeiro vem antes do camião: «pick-up» e «carrinha» não são pesados, e
  // mostrar-lhes o desenho de um camião com quinta roda é mostrar peças que a
  // viatura não tem.
  if (/(ligeiro|ligeira|carro|autom|pick|jipe|suv|carrinha|van|viatura leve|sedan)/.test(t)) {
    return FAMILIAS.LIGEIRO;
  }
  return FAMILIAS.CAMIAO;
}

// ==== Componente ===========================================================

export function PontosDeServico({ tipo, assetId }: { tipo?: string | null; assetId: string }) {
  const familia = familiaDe(tipo);
  const [activo, setActivo] = useState<number | null>(null);
  const ponto = familia.pontos.find((p) => p.numero === activo) ?? null;

  // Os pontos agrupados pelo intervalo, como o manual os organiza: quem vai
  // lubrificar às 50 horas faz os cinco pontos de uma vez, não um por dia.
  const grupos = new Map<number, PontoServico[]>();
  for (const p of familia.pontos) {
    const chave = p.intervaloHoras ?? 0;
    grupos.set(chave, [...(grupos.get(chave) ?? []), p]);
  }
  const ordenados = [...grupos.entries()].sort((a, b) => a[0] - b[0]);
  const materiais = [...new Set(familia.pontos.map((p) => p.produto).filter((x): x is string => !!x))];

  return (
    <Stack gap="lg">
      <Group justify="space-between" wrap="wrap">
        <Text size="sm" c="dimmed">
          A folha de manutenção desta família, como vem no manual — com os pontos numerados no desenho.
        </Text>
        <BotaoFichaDoPosto assetId={assetId} />
      </Group>

      {/* ====== O desenho com os pontos ====== */}
      <div>
        <BarraFicha
          titulo={`${familia.nome} — pontos de serviço`}
          direita={`${familia.pontos.length} pontos · carregue num número`}
        />
        <CaixaFicha>
          <div
            style={{
              display: 'flex',
              gap: 18,
              flexWrap: 'wrap',
              alignItems: 'flex-start',
            }}
          >
            <div style={{ flex: '2 1 380px', minWidth: 300 }}>
              <svg
                viewBox={familia.vista}
                style={{ width: '100%', display: 'block' }}
                role="img"
                aria-label={`Diagrama de pontos de serviço — ${familia.nome}`}
              >
                <g stroke={PRETO} strokeWidth="2" strokeLinejoin="round">
                  {familia.desenho(activo)}
                </g>
                {familia.pontos.map((p) => {
                  const seleccionado = activo === p.numero;
                  return (
                    <g
                      key={p.numero}
                      onClick={() => setActivo(seleccionado ? null : p.numero)}
                      style={{ cursor: 'pointer' }}
                    >
                      {/* Como no manual: círculo âmbar, traço preto, número preto.
                          Lê-se sobre a máquina, sobre a sombra e em fotocópia. */}
                      <circle cx={p.x} cy={p.y} r={seleccionado ? 19 : 15} fill="#fff" />
                      <circle
                        cx={p.x}
                        cy={p.y}
                        r={seleccionado ? 17 : 13.5}
                        fill={p.critico ? '#dc2626' : AMBAR}
                        stroke={PRETO}
                        strokeWidth="2.5"
                      />
                      <text
                        x={p.x}
                        y={p.y + 5}
                        textAnchor="middle"
                        fill={p.critico ? '#fff' : PRETO}
                        style={{
                          fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                          fontWeight: 700,
                          fontSize: seleccionado ? 18 : 15,
                          pointerEvents: 'none',
                        }}
                      >
                        {p.numero}
                      </text>
                    </g>
                  );
                })}
              </svg>
            </div>

            <div style={{ flex: '1 1 280px', minWidth: 250 }}>
              {ponto ? (
                <Stack gap={6}>
                  <Group gap="xs">
                    <div
                      style={{
                        width: 28,
                        height: 28,
                        borderRadius: '50%',
                        background: ponto.critico ? '#dc2626' : AMBAR,
                        border: `2px solid ${PRETO}`,
                        color: ponto.critico ? '#fff' : PRETO,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 700,
                        fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                      }}
                    >
                      {ponto.numero}
                    </div>
                    <Text
                      fw={700}
                      style={{
                        fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                        fontSize: 19,
                        textTransform: 'uppercase',
                      }}
                    >
                      {ponto.nome}
                    </Text>
                    {ponto.critico && (
                      <Badge size="xs" color="red" variant="light">
                        Crítico
                      </Badge>
                    )}
                  </Group>

                  <Text size="sm">{ponto.accao}</Text>

                  {ponto.produto && (
                    <Group gap={6}>
                      <IconDroplet size={15} color={AMBAR} />
                      <Text size="sm">
                        <b>{ponto.produto}</b>
                        {ponto.quantidade ? ` · ${ponto.quantidade}` : ''}
                      </Text>
                    </Group>
                  )}

                  {ponto.intervaloHoras != null && (
                    <Text size="sm" c="dimmed">
                      A cada <b>{legendaIntervalo(ponto.intervaloHoras)}</b>
                    </Text>
                  )}
                </Stack>
              ) : (
                <Stack gap={4}>
                  <Text size="sm" c="dimmed">
                    Carregue num número do desenho para ver o que se faz nesse ponto: o produto, a quantidade e de
                    quanto em quanto tempo.
                  </Text>
                  <Text size="xs" c="dimmed" mt={4}>
                    Os pontos a <b style={{ color: '#dc2626' }}>vermelho</b> são os que, se falharem, param a máquina ou
                    magoam alguém.
                  </Text>
                </Stack>
              )}
            </div>
          </div>

          {familia.nota && (
            <Alert color="gray" variant="light" p="xs" mt="sm" icon={<IconInfoCircle size={15} />}>
              <Text size="xs">{familia.nota}</Text>
            </Alert>
          )}
        </CaixaFicha>
      </div>

      {/* ====== Uma ficha por intervalo, como no manual ====== */}
      {ordenados.map(([horas, pontos]) => (
        <div key={horas}>
          <BarraFicha
            titulo={horas === 0 ? 'Sem intervalo definido' : `Manutenção a cada ${legendaIntervalo(horas)}`}
            direita={`${pontos.length} ponto(s)`}
          />
          <CaixaFicha>
            <Stack gap={4}>
              {pontos.map((p) => (
                <Group
                  key={p.numero}
                  gap={8}
                  wrap="nowrap"
                  align="flex-start"
                  onClick={() => setActivo(p.numero)}
                  style={{ cursor: 'pointer' }}
                >
                  <div
                    style={{
                      minWidth: 22,
                      height: 22,
                      borderRadius: '50%',
                      background: p.critico ? '#dc2626' : AMBAR,
                      border: `2px solid ${PRETO}`,
                      color: p.critico ? '#fff' : PRETO,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: 12,
                      fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                    }}
                  >
                    {p.numero}
                  </div>
                  <div>
                    <Text size="sm" fw={600}>
                      {p.nome}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {p.accao}
                      {p.produto ? ` · ${p.produto}` : ''}
                      {p.quantidade ? ` · ${p.quantidade}` : ''}
                    </Text>
                  </div>
                </Group>
              ))}
            </Stack>
          </CaixaFicha>
        </div>
      ))}

      {/* ====== Inspeção diária ====== */}
      <InspecaoDiariaFicha assetId={assetId} />

      {/* ====== Ferramentas e materiais ====== */}
      <FerramentasEMateriais itens={[...materiais, 'Panos de limpeza', 'EPIs: luvas, óculos e botas']} />
    </Stack>
  );
}

/** Horas em linguagem de oficina: 24 h é «dia», 168 h é «semana». */
function legendaIntervalo(horas: number) {
  if (horas <= 12) return `${horas} horas de trabalho`;
  if (horas === 24) return 'dia (antes do arranque)';
  if (horas === 168) return 'semana';
  if (horas >= 8760) return 'ano';
  return `${horas} horas`;
}
