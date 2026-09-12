import { createTheme, type MantineColorsTuple } from '@mantine/core';

/**
 * Identidade IMBONDEIRO OS — preto e âmbar industrial.
 *
 * <p>O imbondeiro é a árvore que aguenta a seca, dá sombra onde não há
 * mais nada e fica de pé durante séculos. É o que se pede a um sistema
 * que gere equipamento pesado em Angola.
 *
 * <p>É um redesenho do IMBONDEIRO OS, não um produto novo: as mesmas páginas, os
 * mesmos dados, outra pele. O grafite veste a moldura (barra lateral, cabeçalho)
 * e o dourado marca o que está selecionado e a ação principal.
 *
 * <p>O dourado <b>não</b> serve para estado. Numa frota, verde/laranja/vermelho
 * já querem dizer coisas concretas — operacional, atenção, parado — e pintar
 * uma marca por cima disso tornaria o ecrã ilegível ao fim de duas linhas.
 */

/**
 * Âmbar industrial. O tom base (#F5A800) fica no índice 6.
 *
 * <p>É o amarelo da maquinaria pesada — o mesmo que veste um gerador, uma
 * retroescavadora ou um colete de estaleiro. Não é gosto: é a cor que a
 * indústria escolheu porque se vê de longe e com pó pelo meio.
 *
 * <p>Sobre âmbar o texto é <b>preto</b>, nunca branco: #F5A800 com branco por
 * cima fica muito abaixo do mínimo de contraste, e um botão principal que se
 * lê mal é um botão que se carrega por engano. Onde for preciso texto branco,
 * usa-se o índice 8 ou 9.
 */
const gold: MantineColorsTuple = [
  '#fff8e1',
  '#ffecb5',
  '#ffdf85',
  '#ffd254',
  '#ffc62f',
  '#ffba18',
  '#f5a800',
  '#c98700',
  '#a06a00',
  '#7a5000',
];

/** Grafite da moldura. #18181B — o tom mais escuro — fica no índice 9. */
const graphite: MantineColorsTuple = [
  '#f4f4f5',
  '#e4e4e7',
  '#d4d4d8',
  '#a1a1aa',
  '#71717a',
  '#52525b',
  '#3f3f46',
  '#27272a',
  '#1c1c1f',
  '#141416',
];

/** O nome, num só sítio: muda aqui e muda em todo o lado. */
export const NOME_MARCA = 'IMBONDEIRO';
export const NOME_MARCA_SUFIXO = 'OS';

export const MARCA = {
  /** Preto da moldura: cabeçalho, barra lateral, barras de título. */
  graphite: '#141416',
  /** Âmbar industrial da faixa de navegação e da ação principal. */
  gold: '#F5A800',
  /** Cinzento de fundo dos ecrãs. */
  surface: '#EFEFF1',
  /** Cinzento escuro do rodapé e das zonas secundárias. */
  charcoal: '#2D2D2F',
} as const;

export const theme = createTheme({
  primaryColor: 'gold',
  // Índice 6 é o âmbar base; o texto por cima é preto (ver a tupla acima).
  primaryShade: { light: 6, dark: 6 },
  colors: { gold, graphite },
  // Inter quando existir; as alternativas cobrem Windows e Android sem
  // descarregar nada — numa ligação móvel angolana isso é a diferença entre
  // abrir depressa e parecer avariado.
  fontFamily:
    'Barlow, Inter, Roboto, -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
  // Títulos e navegação em condensado: cabe mais texto por linha e dá a leitura
  // direita da sinalética industrial.
  headings: {
    fontFamily: '"Barlow Condensed", Barlow, Impact, sans-serif',
    fontWeight: '700',
    // Títulos compactos: estes ecrãs são para ver muitas linhas de uma vez, não
    // para causar impressão.
    sizes: {
      h1: { fontSize: '1.55rem', lineHeight: '1.2' },
      h2: { fontSize: '1.3rem', lineHeight: '1.25' },
      h3: { fontSize: '1.1rem', lineHeight: '1.3' },
      h4: { fontSize: '1rem', lineHeight: '1.3' },
    },
  },
  defaultRadius: 2,
  // Escala apertada: estes ecrãs servem para ver muitas linhas de uma vez.
  fontSizes: {
    xs: '11px', sm: '12.5px', md: '13px', lg: '15px', xl: '17px',
  },
  spacing: {
    xs: '6px', sm: '9px', md: '12px', lg: '16px', xl: '22px',
  },
  components: {
    Card: { defaultProps: { withBorder: true, shadow: 'none', radius: 2, p: 'sm' } },
    Paper: { defaultProps: { radius: 'sm' } },
    Table: { defaultProps: { highlightOnHover: true, verticalSpacing: 4, horizontalSpacing: 8 } },
    Button: { defaultProps: { fw: 600, radius: 2, size: 'sm' } },
    TextInput: { defaultProps: { size: 'sm' } },
    NumberInput: { defaultProps: { size: 'sm' } },
    Select: { defaultProps: { size: 'sm' } },
    Textarea: { defaultProps: { size: 'sm' } },
    Modal: { defaultProps: { radius: 2, centered: true } },
    Badge: { defaultProps: { radius: 'sm', fw: 600 } },
    Tabs: { defaultProps: { keepMounted: false } },
  },
  other: MARCA,
});

export const CRITICALITY = {
  CRITICAL: { label: 'CRÍTICA', color: 'red' },
  HIGH: { label: 'ALTA', color: 'orange' },
  MEDIUM: { label: 'MÉDIA', color: 'yellow' },
  LOW: { label: 'BAIXA', color: 'green' },
} as const;
