/**
 * As cores e as medidas da aplicação do motorista.
 *
 * <p>As mesmas do sistema — preto e âmbar —, mas aplicadas de outra maneira:
 * aqui o ecrã é lido de relance, ao sol, dentro de uma cabina, muitas vezes
 * com luvas. Por isso o preto é o fundo (menos reflexo, menos bateria em ecrãs
 * OLED, menos encandeamento à noite) e o âmbar é só para o que interessa.
 */
export const PRETO = '#141416';
export const PRETO_CARTAO = '#1C1D21';
export const PRETO_LINHA = '#2A2C32';
export const AMBAR = '#FFC62F';
export const AMBAR_ESCURO = '#C98700';
export const BRANCO = '#F4F4F5';
export const CINZA = '#A1A1AA';
export const VERDE = '#22C55E';
export const VERMELHO = '#EF4444';
export const LARANJA = '#F97316';

/** O tipo de letra condensado dos títulos, igual ao do sistema. */
export const TITULO = '"Barlow Condensed", Barlow, sans-serif';

/** Alvos de toque: 56 px é o mínimo para um dedo com luva. */
export const TOQUE = 56;

/** As cores de estado, ditas por palavras e não só por cor. */
export function corDoEstado(estado?: string | null): string {
  switch (estado) {
    case 'OPERATIONAL':
      return VERDE;
    case 'MAINTENANCE':
      return LARANJA;
    case 'DOWN':
    case 'RETIRED':
      return VERMELHO;
    default:
      return CINZA;
  }
}

export function nomeDoEstado(estado?: string | null): string {
  switch (estado) {
    case 'OPERATIONAL':
      return 'operacional';
    case 'MAINTENANCE':
      return 'em manutenção';
    case 'DOWN':
      return 'parada';
    case 'RETIRED':
      return 'abatida';
    default:
      return (estado ?? '').toLowerCase();
  }
}
